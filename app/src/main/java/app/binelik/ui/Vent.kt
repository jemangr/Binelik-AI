package app.binelik.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.binelik.ai.PhoneVoice
import app.binelik.data.Gender
import app.binelik.extras.SoundFx
import app.binelik.i18n.Lang
import app.binelik.i18n.t
import app.binelik.voice.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Scream score 0–100 from the loudest 50 ms of a recording (dB below full scale, −60…0). */
fun screamScore(peakRms: Double): Int {
    if (peakRms <= 1) return 0
    val db = 20 * log10(peakRms / 32767.0)
    return (((db + 60) / 60) * 100).toInt().coerceIn(0, 100)
}

/** «Ουρλιαχτόμετρο»: 3, 2, 1, scream for 3 seconds; the score, the record and a badge at 90. */
@SuppressLint("MissingPermission")
@Composable
fun ScreamScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = Engine.settings
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf("ready") } // ready, count, scream, done
    var count by remember { mutableIntStateOf(3) }
    var live by remember { mutableFloatStateOf(0f) }
    var score by remember { mutableIntStateOf(0) }
    var record by remember { mutableStateOf(false) }
    fun run() = scope.launch {
        phase = "count"
        for (i in 3 downTo 1) { count = i; delay(700) }
        phase = "scream"
        val peak = withContext(Dispatchers.IO) {
            val size = maxOf(AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 1600)
            val rec = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@withContext 0.0 }
            val buf = ShortArray(800) // 50 ms
            var best = 0.0
            rec.startRecording()
            val end = System.currentTimeMillis() + 3000
            while (isActive && System.currentTimeMillis() < end) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    var sum = 0.0; for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                    val rms = sqrt(sum / n); if (rms > best) best = rms
                    live = screamScore(rms) / 100f
                }
            }
            rec.stop(); rec.release(); best
        }
        score = screamScore(peak); live = 0f
        record = s.record("scream", score)
        phase = "done"
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) run() }
    Screen(t("Ουρλιαχτόμετρο", "Scream meter"), onBack) {
        Text(t("Φώναξε όσο πιο δυνατά μπορείς για 3 δευτερόλεπτα. Τίποτα δεν γράφεται, μετράμε μόνο την ένταση.", "Scream as loud as you can for 3 seconds. Nothing is recorded, only the loudness is measured."), color = C.muted, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().heightIn(min = 220.dp), contentAlignment = Alignment.Center) {
            when (phase) {
                "count" -> Text("$count", fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, color = C.amber)
                "scream" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("ΦΩΝΑΞΕ!", "SCREAM!"), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = C.danger)
                    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(22.dp).clip(RoundedCornerShape(11.dp)).background(C.panel2)) {
                        Box(Modifier.fillMaxWidth(live).height(22.dp).background(Brush.horizontalGradient(listOf(C.ok, C.amber, C.danger))))
                    }
                }
                "done" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score", fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, color = if (score >= 90) C.danger else if (score >= 60) C.amber else C.ok)
                    Text(
                        when { score >= 90 -> t("Λαρύγγι! Σε άκουσε όλη η πολυκατοικία.", "What a voice! The whole building heard you."); score >= 60 -> t("Καλό! Λίγο ακόμα και σπάνε τα τζάμια.", "Good! A bit more and the windows break."); else -> t("Αυτό ήταν; Η γιαγιά φωνάζει πιο δυνατά.", "Was that it? Grandma shouts louder.") },
                        textAlign = TextAlign.Center, fontSize = 15.sp,
                    )
                    if (record) Text(t("🏆 Νέο ρεκόρ!", "🏆 New record!"), color = C.amber, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
                else -> Text("🗣️", fontSize = 90.sp)
            }
        }
        Text(t("Ρεκόρ: ${s.best("scream")}", "Record: ${s.best("scream")}"), color = C.muted, fontSize = 13.sp)
        PrimaryButton(if (phase == "done") t("Ξανά", "Again") else t("Έτοιμος", "Ready"), {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) run() else ask.launch(Manifest.permission.RECORD_AUDIO)
        }, Modifier.fillMaxWidth(), enabled = phase == "ready" || phase == "done")
    }
}

private class Shard(val x: Float, val y: Float, val dx: Float, val dy: Float, val rot: Float)

/** «Σπάσε πιάτα»: tap a plate (or shake the phone) and it breaks. Όπα! */
@Composable
fun PlatesScreen(onBack: () -> Unit) {
    val s = Engine.settings
    val view = LocalView.current
    val context = LocalContext.current
    val broken = remember { mutableStateListOf(false, false, false, false, false, false) }
    var total by remember { mutableIntStateOf(s.count("plates")) }
    var opa by remember { mutableIntStateOf(0) }
    fun smash(i: Int) {
        if (broken[i]) return
        broken[i] = true; total++; opa++
        s.add("plates")
        SoundFx.plate()
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    // Shake: the next whole plate breaks.
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        var last = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
                if (g > 2.6f && System.currentTimeMillis() - last > 400) { last = System.currentTimeMillis(); broken.indexOfFirst { !it }.takeIf { it >= 0 }?.let { smash(it) } }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { sm.unregisterListener(l) }
    }
    LaunchedEffect(broken.toList()) { if (broken.all { it }) { delay(1200); for (i in broken.indices) broken[i] = false } }
    Screen(t("Σπάσε πιάτα", "Smash plates"), onBack) {
        Text(t("Πάτα ένα πιάτο ή κούνα το κινητό. Όπα!", "Tap a plate or shake the phone. Opa!"), color = C.muted, fontSize = 13.sp)
        Text(t("Σπασμένα: $total", "Smashed: $total") + if (total >= 100) " · 🏺 Ζορμπάς" else "", fontWeight = FontWeight.Bold, color = C.amber)
        for (row in 0 until 3) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (col in 0 until 2) { val i = row * 2 + col; Plate(broken[i], Modifier.weight(1f)) { smash(i) } }
        }
        if (opa > 0) Text(t("ΟΠΑ!", "OPA!"), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = C.danger, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun Plate(broken: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val k = remember { Animatable(0f) }
    val shards = remember(broken) { List(14) { Shard(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1, Random.nextFloat() * 360) } }
    LaunchedEffect(broken) { if (broken) { k.snapTo(0f); k.animateTo(1f, tween(700, easing = LinearEasing)) } else k.snapTo(0f) }
    Canvas(modifier.aspectRatio(1f).clickable(enabled = !broken, onClick = onClick)) {
        val r = size.minDimension / 2 * 0.9f
        val c = center
        if (!broken) {
            drawCircle(Color(0xFFF1F5F9), r, c)
            drawCircle(Color(0xFF93C5FD), r * 0.92f, c, style = Stroke(4.dp.toPx()))
            drawCircle(Color(0xFFE2E8F0), r * 0.6f, c)
            drawCircle(Color(0xFF93C5FD), r * 0.62f, c, style = Stroke(1.5.dp.toPx()))
        } else {
            val p = k.value
            shards.forEach { sh ->
                val o = Offset(c.x + (sh.x - 0.5f) * r + sh.dx * r * 1.4f * p, c.y + (sh.y - 0.5f) * r + sh.dy * r * 1.4f * p + r * 1.2f * p * p)
                val path = Path().apply {
                    val a = Math.toRadians(sh.rot.toDouble())
                    moveTo(o.x + (cos(a) * r * 0.22).toFloat(), o.y + (sin(a) * r * 0.22).toFloat())
                    lineTo(o.x + (cos(a + 2.2) * r * 0.16).toFloat(), o.y + (sin(a + 2.2) * r * 0.16).toFloat())
                    lineTo(o.x + (cos(a + 4.1) * r * 0.19).toFloat(), o.y + (sin(a + 4.1) * r * 0.19).toFloat())
                    close()
                }
                drawPath(path, Color(0xFFF1F5F9).copy(alpha = (1 - p).coerceIn(0f, 1f)))
            }
        }
    }
}

/** «Γράψ' το και κάψ' το»: what you write burns and is gone; it is never saved. */
@Composable
fun BurnScreen(onBack: () -> Unit) {
    val s = Engine.settings
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var burning by remember { mutableStateOf<String?>(null) }
    val fire = remember { Animatable(0f) }
    Screen(t("Γράψ' το και κάψ' το", "Write it and burn it"), onBack) {
        Text(t("Γράψε ό,τι σε τρώει. Με το κάψιμο σβήνεται για πάντα: δεν κρατιέται πουθενά, ούτε πηγαίνει στο AI.", "Write whatever is eating you. Burning deletes it forever: it is kept nowhere and doesn't go to the AI."), color = C.muted, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
            val b = burning
            if (b == null) OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth().heightIn(min = 200.dp), label = { Text(t("Γράψ' τα όλα…", "Write it all…")) })
            else {
                val p = fire.value
                Text(b, fontSize = 16.sp, color = Color(0xFFFFE0B2).copy(alpha = 1 - p), modifier = Modifier.scale(1f - 0.5f * p).alpha(1 - p))
                Text("🔥🔥🔥", fontSize = (40 + 40 * p).sp, modifier = Modifier.alpha(if (p < 0.9f) 1f else (1 - p) * 10))
            }
        }
        PrimaryButton(t("🔥 Κάψ' το", "🔥 Burn it"), {
            burning = text; text = ""
            SoundFx.fire()
            scope.launch {
                fire.snapTo(0f); fire.animateTo(1f, tween(2200))
                burning = null; s.add("burns")
            }
        }, Modifier.fillMaxWidth(), enabled = text.isNotBlank() && burning == null)
        Text(t("Κάηκαν μέχρι τώρα: ${s.count("burns")}", "Burned so far: ${s.count("burns")}"), color = C.dim, fontSize = 12.sp)
    }
}

/** «Ήρεμο κλείσιμο»: one minute of breathing (in 4, hold 4, out 6), with the patient one's voice. */
@Composable
fun BreatheScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = Engine.settings
    val size = remember { Animatable(0.45f) }
    var word by remember { mutableStateOf(t("Πάτα Ξεκίνα", "Press Start")) }
    var running by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var e: TextToSpeech? = null
        e = TextToSpeech(context) { ok ->
            if (ok == TextToSpeech.SUCCESS) e?.let { PhoneVoice.setup(it, Locale.forLanguageTag(Lang.tag), Gender.MALE, null); it.setSpeechRate(0.85f); tts.value = it }
        }
        onDispose { e.shutdown() }
    }
    fun say(w: String) { word = w; if (s.breatheVoice) tts.value?.speak(w, TextToSpeech.QUEUE_FLUSH, null, "b") }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        say(t("Ας ηρεμήσουμε λίγο. Ακολούθησε τον κύκλο.", "Let's calm down a little. Follow the circle."))
        delay(2500)
        for (i in 1..4) {
            round = i
            say(t("Εισπνοή", "Breathe in")); size.animateTo(1f, tween(4000))
            say(t("Κράτα", "Hold")); delay(4000)
            say(t("Εκπνοή", "Breathe out")); size.animateTo(0.45f, tween(6000))
        }
        say(t("Μπράβο. Πώς νιώθεις τώρα;", "Well done. How do you feel now?"))
        s.add("breathe"); running = false; round = 0
    }
    Screen(t("Ήρεμο κλείσιμο", "Calm down"), onBack) {
        Text(t("Ένα λεπτό αναπνοές: εισπνοή 4, κράτα 4, εκπνοή 6. Με τη φωνή του Υπομονετικού (του κινητού).", "One minute of breathing: in 4, hold 4, out 6. With the Patient one's voice (the phone's)."), color = C.muted, fontSize = 13.sp)
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(260.dp).scale(size.value).clip(androidx.compose.foundation.shape.CircleShape).background(Brush.radialGradient(listOf(Color(0xFF7DD3FC), Color(0xFF2563EB).copy(alpha = 0.6f), Color.Transparent))))
            Text(word, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        if (round > 0) Text(t("Γύρος $round από 4", "Round $round of 4"), color = C.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        PrimaryButton(if (running) t("Σταμάτα", "Stop") else t("Ξεκίνα", "Start"), { if (running) { running = false; tts.value?.stop(); word = t("Πάτα Ξεκίνα", "Press Start") } else running = true }, Modifier.fillMaxWidth())
        SwitchRow(t("Με φωνή", "With voice"), t("Η φωνή του κινητού λέει τι να κάνεις.", "The phone's voice tells you what to do."), s.breatheVoice) { s.breatheVoice = it }
    }
}
