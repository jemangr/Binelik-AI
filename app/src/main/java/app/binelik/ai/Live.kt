package app.binelik.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import app.binelik.data.Character
import app.binelik.data.Gender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64
import java.util.concurrent.TimeUnit

/** What the Live server tells us, already read. */
sealed class LiveEvent {
    data object Ready : LiveEvent()
    data class Audio(val pcm: ByteArray) : LiveEvent()
    /** What the user said (in pieces). */
    data class Heard(val text: String) : LiveEvent()
    /** What the character said (in pieces). */
    data class Said(val text: String) : LiveEvent()
    data object Interrupted : LiveEvent()
    data object TurnComplete : LiveEvent()
    data object GoAway : LiveEvent()
}

/** The Gemini Live API messages, without Android, so they can be tested. */
object LiveProtocol {
    const val MODEL = "gemini-3.8-live"
    const val IN_RATE = 16000
    const val OUT_RATE = 24000
    const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /** Gemini's prebuilt voices, by gender: each character gets its own one by default. */
    val maleVoices = listOf("Orus", "Charon", "Fenrir", "Puck")
    val femaleVoices = listOf("Kore", "Aoede", "Leda", "Zephyr")

    fun voices(g: Gender) = if (g == Gender.MALE) maleVoices else femaleVoices

    /** The character's voice: the one chosen in the settings, or its own by default. */
    fun voiceFor(c: Character, g: Gender, chosen: Int?): String {
        val list = voices(g)
        val i = chosen ?: (c.id.fold(0) { a, ch -> a * 31 + ch.code }.mod(list.size))
        return list[i.mod(list.size)]
    }

    data class Options(val voice: String?, val language: String?, val thinking: String?)

    fun setup(model: String, system: String, o: Options): String = buildJsonObject {
        putJsonObject("setup") {
            put("model", "models/$model")
            putJsonObject("generationConfig") {
                put("responseModalities", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) })
                if (o.voice != null || o.language != null) putJsonObject("speechConfig") {
                    o.language?.let { put("languageCode", it) }
                    o.voice?.let { v -> putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", v) } } }
                }
                o.thinking?.let { t -> putJsonObject("thinkingConfig") { put("thinkingLevel", t) } }
            }
            putJsonObject("systemInstruction") { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) }
            putJsonObject("inputAudioTranscription") {}
            putJsonObject("outputAudioTranscription") {}
        }
    }.toString()

    fun audio(pcm: ByteArray, size: Int = pcm.size): String = buildJsonObject {
        putJsonObject("realtimeInput") {
            putJsonObject("audio") {
                put("data", Base64.getEncoder().encodeToString(if (size == pcm.size) pcm else pcm.copyOf(size)))
                put("mimeType", "audio/pcm;rate=$IN_RATE")
            }
        }
    }.toString()

    /** Sent when the microphone is muted, so the model answers what it already heard. */
    val audioEnd: String = buildJsonObject { putJsonObject("realtimeInput") { put("audioStreamEnd", true) } }.toString()

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<LiveEvent> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val out = mutableListOf<LiveEvent>()
        if ("setupComplete" in root) out += LiveEvent.Ready
        if ("goAway" in root) out += LiveEvent.GoAway
        val c = root["serverContent"] as? JsonObject ?: return out
        ((c["modelTurn"] as? JsonObject)?.get("parts") as? JsonArray)?.forEach { p ->
            val data = ((p as? JsonObject)?.get("inlineData") as? JsonObject)?.get("data")?.jsonPrimitive?.contentOrNull
            if (data != null) out += LiveEvent.Audio(Base64.getDecoder().decode(data))
        }
        (c["inputTranscription"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull?.let { out += LiveEvent.Heard(it) }
        (c["outputTranscription"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull?.let { out += LiveEvent.Said(it) }
        if (c["interrupted"]?.jsonPrimitive?.booleanOrNull == true) out += LiveEvent.Interrupted
        if (c["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) out += LiveEvent.TurnComplete
        return out
    }

    /**
     * When the server refuses the setup, what to drop for the next try (some models take no
     * language code or no voice, some want a thinking level). Null: give up.
     */
    fun retry(reason: String, o: Options): Options? = when {
        o.language != null && reason.contains("language", true) -> o.copy(language = null)
        o.voice != null && reason.contains("voice", true) -> o.copy(voice = null)
        o.thinking == null && reason.contains("thinking", true) -> o.copy(thinking = "low")
        else -> null
    }
}

/**
 * One Live conversation: the microphone goes to Gemini, the character's voice comes back.
 * Runs only while the app is on screen (no microphone in the background).
 */
class LiveSession(
    private val key: String,
    private val scope: CoroutineScope,
    private val onEvent: (LiveEvent) -> Unit,
    private val onClosed: (error: String?) -> Unit,
) {
    @Volatile var micMuted = false
        set(v) { field = v; if (v) socket?.send(LiveProtocol.audioEnd) }
    @Volatile var voiceMuted = false
        set(v) { field = v; track?.setVolume(if (v) 0f else 1f) }

    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private var socket: WebSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var micJob: Job? = null
    private var playJob: Job? = null
    private val playback = Channel<ByteArray>(Channel.UNLIMITED)
    @Volatile private var ready = false
    @Volatile private var closed = false

    fun start(system: String, options: LiveProtocol.Options) {
        closed = false
        ready = false
        val req = Request.Builder().url(LiveProtocol.URL).header("x-goog-api-key", key).build()
        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(LiveProtocol.setup(LiveProtocol.MODEL, system, options))
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = handle(bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (!ready && !closed) {
                    LiveProtocol.retry(reason, options)?.let { next -> stopAudio(); start(system, next); return }
                }
                finish(if (code == 1000 || closed) null else reason.ifBlank { "κωδικός $code" })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(if (closed) null else (response?.code?.let { "HTTP $it" } ?: t.message ?: "σφάλμα σύνδεσης"))
            }
        })
    }

    private fun handle(text: String) {
        for (e in LiveProtocol.parse(text)) {
            when (e) {
                LiveEvent.Ready -> { ready = true; startAudio() }
                is LiveEvent.Audio -> playback.trySend(e.pcm)
                LiveEvent.Interrupted -> { while (playback.tryReceive().isSuccess) Unit; track?.run { pause(); flush(); play() } }
                else -> Unit
            }
            onEvent(e)
        }
    }

    @SuppressLint("MissingPermission") // checked by the screen before starting
    private fun startAudio() {
        val inSize = maxOf(AudioRecord.getMinBufferSize(LiveProtocol.IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, LiveProtocol.IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inSize * 2)
        if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        record = rec
        val outSize = AudioTrack.getMinBufferSize(LiveProtocol.OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(LiveProtocol.OUT_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(outSize * 4).setTransferMode(AudioTrack.MODE_STREAM).build()
        t.setVolume(if (voiceMuted) 0f else 1f)
        t.play()
        track = t
        rec.startRecording()
        micJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(3200) // 100 ms
            while (isActive && !closed) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0 && !micMuted) socket?.send(LiveProtocol.audio(buf, n))
            }
        }
        playJob = scope.launch(Dispatchers.IO) {
            for (pcm in playback) { if (closed) break; t.write(pcm, 0, pcm.size) }
        }
    }

    private fun stopAudio() {
        micJob?.cancel(); playJob?.cancel()
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
        while (playback.tryReceive().isSuccess) Unit
    }

    private fun finish(error: String?) {
        if (closed && error == null && socket == null) return
        closed = true
        stopAudio()
        socket = null
        onClosed(error)
    }

    fun stop() {
        closed = true
        runCatching { socket?.close(1000, null) }
        finish(null)
    }
}
