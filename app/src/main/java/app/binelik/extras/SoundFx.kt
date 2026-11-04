package app.binelik.extras

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Small sounds made in code (no recordings, nothing to license): a horn for the taxi driver, a whistle
 * for the sergeant, a stadium for the fan, a phone for the boss, a pan for the mums, plates breaking.
 */
object SoundFx {
    private const val RATE = 22050

    fun play(characterId: String) {
        val pcm = when (characterId) {
            "taxitzis" -> horn()
            "lochias" -> whistle()
            "opados" -> stadium()
            "afentiko" -> ring()
            "mana", "giagia", "pethera" -> pan()
            "dikigoros" -> gavel()
            "kavgatzis" -> bell()
            else -> return
        }
        out(pcm, 0.6f)
    }

    /** A plate breaking: a crack, then the shards. */
    fun plate() = out(mix(noise(0.05, 1.0, 40.0), shards()), 0.8f)

    /** The fire for «Write it and burn it». */
    fun fire() = out(noise(1.6, 0.35, 1.5, crackle = true), 0.5f)

    private fun out(pcm: ShortArray, volume: Float) = Thread {
        runCatching {
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
            t.write(pcm, 0, pcm.size); t.setVolume(volume); t.play()
            Thread.sleep(pcm.size * 1000L / RATE + 100)
            t.release()
        }
    }.start()

    private fun samples(sec: Double) = (sec * RATE).toInt()

    private fun tone(sec: Double, f: (Double) -> Double, env: (Double) -> Double): ShortArray =
        ShortArray(samples(sec)) { i -> val x = i.toDouble() / RATE; (f(x) * env(x) * 12000).toInt().coerceIn(-32767, 32767).toShort() }

    private fun mix(vararg parts: ShortArray): ShortArray {
        val n = parts.maxOf { it.size }
        return ShortArray(n) { i -> parts.sumOf { p -> if (i < p.size) p[i].toInt() else 0 }.coerceIn(-32767, 32767).toShort() }
    }

    private fun join(vararg parts: ShortArray): ShortArray = parts.fold(ShortArray(0)) { a, b -> a + b }
    private fun silence(sec: Double) = ShortArray(samples(sec))
    private fun square(x: Double, f: Double) = if (sin(2 * PI * f * x) >= 0) 0.6 else -0.6

    private fun horn(): ShortArray {
        val beep = tone(0.35, { x -> square(x, 400.0) + square(x, 500.0) }, { x -> if (x < 0.3) 1.0 else (0.35 - x) / 0.05 })
        return join(beep, silence(0.08), beep)
    }

    private fun whistle(): ShortArray = tone(0.9, { x -> sin(2 * PI * (2900 + 120 * sin(2 * PI * 28 * x)) * x) }, { x -> minOf(1.0, x * 20) * if (x > 0.75) (0.9 - x) / 0.15 else 1.0 })

    private fun stadium(): ShortArray = mix(noise(1.6, 0.5, 1.0, swell = true), tone(1.6, { x -> 0.25 * sin(2 * PI * 220 * x) + 0.2 * sin(2 * PI * 330 * x) }, { x -> sin(PI * x / 1.6) }))

    private fun ring(): ShortArray {
        val r = tone(0.4, { x -> 0.5 * sin(2 * PI * 440 * x) + 0.5 * sin(2 * PI * 480 * x) }, { 1.0 })
        return join(r, silence(0.2), r)
    }

    private fun pan(): ShortArray = tone(1.2, { x -> listOf(523.0, 1279.0, 2011.0, 2957.0).withIndex().sumOf { (k, f) -> sin(2 * PI * f * x) / (k + 1) } }, { x -> exp(-4 * x) })

    private fun gavel(): ShortArray {
        val hit = tone(0.18, { x -> sin(2 * PI * 140 * x) + 0.4 * sin(2 * PI * 420 * x) }, { x -> exp(-30 * x) })
        return join(hit, silence(0.12), hit)
    }

    private fun bell(): ShortArray {
        val ding = tone(0.55, { x -> sin(2 * PI * 1250 * x) + 0.5 * sin(2 * PI * 3100 * x) }, { x -> exp(-6 * x) })
        return join(ding, ding)
    }

    private fun shards(): ShortArray = mix(*Array(9) { k ->
        val start = silence(0.03 + Random.nextDouble(0.25))
        join(start, tone(0.12, { x -> sin(2 * PI * (3000 + Random.nextDouble(4000.0)) * x) }, { x -> exp(-35 * x) * 0.5 }))
    })

    private fun noise(sec: Double, level: Double, decay: Double, swell: Boolean = false, crackle: Boolean = false): ShortArray {
        var last = 0.0
        return ShortArray(samples(sec)) { i ->
            val x = i.toDouble() / RATE
            // A soft low-pass, so it sounds like a crowd or a fire, not a radio.
            last += (Random.nextDouble(-1.0, 1.0) - last) * if (crackle) 0.5 else 0.25
            val env = when { swell -> sin(PI * x / sec); else -> exp(-decay * x) }
            val pop = if (crackle && Random.nextInt(400) == 0) 2.5 else 1.0
            (last * level * env * pop * 20000).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
