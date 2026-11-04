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
    /** Everything the user has said in this turn so far (the phone's recogniser gives it whole). */
    data class HeardSoFar(val text: String) : LiveEvent()
}

/** A voice conversation in progress: Gemini Live, or the phone's own voice with a text AI. */
interface Conversation {
    var micMuted: Boolean
    var voiceMuted: Boolean
    fun stop()
    fun interrupt() {}
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

    data class Options(val voice: String?, val language: String?, val thinking: String?, val search: Boolean = true)

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
            // Google search, for real answers about the weather, scores, news.
            if (o.search) put("tools", buildJsonArray { add(buildJsonObject { putJsonObject("googleSearch") {} }) })
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

    fun text(s: String): String = buildJsonObject { putJsonObject("realtimeInput") { put("text", s) } }.toString()

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
        o.search && (reason.contains("tool", true) || reason.contains("search", true)) -> o.copy(search = false)
        else -> null
    }
}

/**
 * One Live conversation: the microphone goes to Gemini, the character's voice comes back.
 * Runs only while the app is on screen (no microphone in the background).
 */
/**
 * Where the voice comes out. A voice conversation uses the phone's «call» audio (for echo
 * cancellation), which by default goes to the earpiece and is very quiet: so the output goes to
 * a headset, Bluetooth or the car if one is connected, otherwise the loudspeaker, and the volume
 * keys change it (4/11: «ακούγεται πολύ σιγά και με την ένταση δεν φτιάχνει»).
 */
object Speaker {
    private var on = false

    fun start(context: android.content.Context) {
        val am = context.getSystemService(android.media.AudioManager::class.java) ?: return
        am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val devices = am.availableCommunicationDevices
            val pick = listOf(
                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            ).firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
            pick?.let { runCatching { am.setCommunicationDevice(it) } }
        } else {
            @Suppress("DEPRECATION")
            if (!am.isWiredHeadsetOn && !am.isBluetoothScoOn) am.isSpeakerphoneOn = true
        }
        on = true
    }

    fun stop(context: android.content.Context) {
        if (!on) return
        val am = context.getSystemService(android.media.AudioManager::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { am.clearCommunicationDevice() }
        else @Suppress("DEPRECATION") { am.isSpeakerphoneOn = false }
        am.mode = android.media.AudioManager.MODE_NORMAL
        on = false
    }

    /** Louder than the phone allows, if asked (Settings → voice volume): PCM16 times [gain], clipped. */
    fun boost(pcm: ByteArray, gain: Float): ByteArray {
        if (gain <= 1f) return pcm
        val out = ByteArray(pcm.size)
        var i = 0
        while (i + 1 < pcm.size) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            val v = (s * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i] = (v and 0xFF).toByte(); out[i + 1] = (v shr 8).toByte()
            i += 2
        }
        return out
    }

    /** The gain chosen in the settings: 1, 1.6 or 2.2. */
    @Volatile var gain = 1f
}

class LiveSession(
    private val context: android.content.Context,
    private val key: String,
    private val scope: CoroutineScope,
    private val onEvent: (LiveEvent) -> Unit,
    private val onClosed: (error: String?) -> Unit,
    /** False for the voice sample in the settings: no microphone, only [firstText]. */
    private val listen: Boolean = true,
    private val firstText: String? = null,
) : Conversation {
    @Volatile override var micMuted = false
        set(v) { field = v; if (v) socket?.send(LiveProtocol.audioEnd) }
    @Volatile override var voiceMuted = false
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

    /** An error while handling the server's messages stops the conversation with the error, instead of closing the app. */
    private fun handle(text: String) = try { handleEvents(text) } catch (e: Throwable) {
        runCatching { socket?.close(1000, null) }
        finish("${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)}")
    }

    private fun handleEvents(text: String) {
        for (e in LiveProtocol.parse(text)) {
            when (e) {
                LiveEvent.Ready -> { ready = true; startAudio(); firstText?.let { socket?.send(LiveProtocol.text(it)) } }
                is LiveEvent.Audio -> playback.trySend(e.pcm)
                LiveEvent.Interrupted -> { while (playback.tryReceive().isSuccess) Unit; track?.run { pause(); flush(); play() } }
                else -> Unit
            }
            onEvent(e)
        }
    }

    @SuppressLint("MissingPermission") // checked by the screen before starting
    private fun startAudio() {
        if (!listen) { startPlayback(); return }
        val inSize = maxOf(AudioRecord.getMinBufferSize(LiveProtocol.IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, LiveProtocol.IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, inSize * 2)
        // Another app may hold the microphone (a call, a recorder): then the AudioRecord does not open.
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); error("mic-busy") }
        if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true }
        if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.enabled = true }
        record = rec
        startPlayback()
        rec.startRecording()
        micJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(3200) // 100 ms
            while (isActive && !closed) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0 && !micMuted) socket?.send(LiveProtocol.audio(buf, n))
            }
        }
    }

    private fun startPlayback() {
        Speaker.start(context)
        val outSize = AudioTrack.getMinBufferSize(LiveProtocol.OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(LiveProtocol.OUT_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(outSize * 4).setTransferMode(AudioTrack.MODE_STREAM).build()
        t.setVolume(if (voiceMuted) 0f else 1f)
        t.play()
        track = t
        playJob = scope.launch(Dispatchers.IO) {
            for (pcm in playback) { if (closed) break; val b = Speaker.boost(pcm, Speaker.gain); t.write(b, 0, b.size) }
        }
    }

    private fun stopAudio() {
        micJob?.cancel(); playJob?.cancel()
        runCatching { record?.stop() }; runCatching { record?.release() }; record = null
        runCatching { track?.stop() }; runCatching { track?.release() }; track = null
        Speaker.stop(context)
        while (playback.tryReceive().isSuccess) Unit
    }

    private fun finish(error: String?) {
        if (closed && error == null && socket == null) return
        closed = true
        stopAudio()
        socket = null
        onClosed(error)
    }

    override fun stop() {
        closed = true
        runCatching { socket?.close(1000, null) }
        finish(null)
    }
}
