package app.binelik.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import app.binelik.ai.Provider
import kotlinx.coroutines.flow.Flow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The API keys, encrypted with a key that never leaves the phone's Android Keystore.
 * Nothing is sent anywhere except to the AI service the key belongs to.
 */
class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("keys", Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun save(p: Provider, key: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val out = c.iv + c.doFinal(key.toByteArray())
        prefs.edit().putString(p.name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun get(p: Provider): String? = runCatching {
        val raw = Base64.decode(prefs.getString(p.name, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, raw, 0, 12)) }
        String(c.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()

    fun has(p: Provider) = prefs.contains(p.name)
    fun remove(p: Provider) = prefs.edit().remove(p.name).apply()

    private companion object { const val ALIAS = "binelik_keys" }
}

/** Settings. Everything stays on the phone. */
class Settings(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var adult: Boolean
        get() = p.getBoolean("adult", false)
        set(v) = p.edit().putBoolean("adult", v).apply()

    var provider: Provider
        get() = runCatching { Provider.valueOf(p.getString("provider", null) ?: "") }.getOrDefault(Provider.GEMINI)
        set(v) = p.edit().putString("provider", v.name).apply()

    var characterId: String
        get() = p.getString("character", null) ?: Characters.all.first().id
        set(v) = p.edit().putString("character", v).apply()

    fun intensity(id: String): Intensity =
        runCatching { Intensity.valueOf(p.getString("int_$id", null) ?: "") }.getOrDefault(Intensity.MEDIUM)
    fun setIntensity(id: String, v: Intensity) = p.edit().putString("int_$id", v.name).apply()

    /** Male or female voice per character (used by the Live voice in 0.2). */
    fun gender(c: Character): Gender = genderOf(c.id, c.gender)
    fun genderOf(id: String, default: Gender): Gender =
        runCatching { Gender.valueOf(p.getString("g_$id", null) ?: "") }.getOrDefault(default)
    fun setGender(id: String, g: Gender) = p.edit().putString("g_$id", g.name).remove("v_$id").apply()

    /** Which of the gender's voices the character uses (null: its own default). */
    fun voiceIndex(id: String): Int? = p.getInt("v_$id", -1).takeIf { it >= 0 }
    fun setVoiceIndex(id: String, i: Int) = p.edit().putInt("v_$id", i).apply()

    var team: String
        get() = p.getString("team", null) ?: Characters.teams.first().el
        set(v) = p.edit().putString("team", v.trim().take(40)).apply()

    /** Look on GitHub for a newer version (on by default). */
    var checkUpdates: Boolean
        get() = p.getBoolean("upd", true)
        set(v) = p.edit().putBoolean("upd", v).apply()
    var lastUpdateCheck: Long
        get() = p.getLong("upd_at", 0)
        set(v) = p.edit().putLong("upd_at", v).apply()
    /** A version the user said «later» to: not shown again until a newer one. */
    var skippedVersion: String
        get() = p.getString("upd_skip", null) ?: ""
        set(v) = p.edit().putString("upd_skip", v).apply()

    /** The user's city, for the weather and «near me» questions (optional). */
    var city: String
        get() = p.getString("city", null) ?: ""
        set(v) = p.edit().putString("city", v.trim().take(60)).apply()

    var region: Region
        get() = runCatching { Region.valueOf(p.getString("region", null) ?: "") }.getOrDefault(Region.NONE)
        set(v) = p.edit().putString("region", v.name).apply()
    /** «Μπιπ»: the characters say «μπιπ» instead of swearing, and the text shows it too. */
    var bleep: Boolean
        get() = p.getBoolean("bleep", false)
        set(v) = p.edit().putBoolean("bleep", v).apply()
    fun style() = Style(region, bleep, city)

    // ---- 1.2 ----
    /** Hold a button to talk, instead of an open microphone (noise, the car). */
    var pushToTalk: Boolean
        get() = p.getBoolean("ptt", false)
        set(v) = p.edit().putBoolean("ptt", v).apply()
    /** A face in the voice circle, with a mouth that moves with the voice. */
    var face: Boolean
        get() = p.getBoolean("face", true)
        set(v) = p.edit().putBoolean("face", v).apply()
    /** A sound per character when Live starts (horn, whistle, stadium…). */
    var sounds: Boolean
        get() = p.getBoolean("sounds", true)
        set(v) = p.edit().putBoolean("sounds", v).apply()
    /** The voice in «Calm down» (phone voice); off: only the circle. */
    var breatheVoice: Boolean
        get() = p.getBoolean("breathe_voice", true)
        set(v) = p.edit().putBoolean("breathe_voice", v).apply()
    /** Models for the OpenAI-compatible services (they change often, so the user can change them). */
    fun model(provider: app.binelik.ai.Provider): String = p.getString("model_${provider.name}", null)?.ifBlank { null } ?: provider.model
    fun setModel(provider: app.binelik.ai.Provider, m: String) = p.edit().putString("model_${provider.name}", m.trim().take(100)).apply()

    /** Counters and records for the games and the badges (only on the phone). */
    fun count(k: String): Int = p.getInt("n_$k", 0)
    fun add(k: String, n: Int = 1) = p.edit().putInt("n_$k", count(k) + n).apply()
    fun best(k: String): Int = p.getInt("best_$k", 0)
    fun record(k: String, v: Int): Boolean = (v > best(k)).also { if (it) p.edit().putInt("best_$k", v).apply() }

    /** Anger meter before and after a voice conversation. */
    var angerMeter: Boolean
        get() = p.getBoolean("anger", true)
        set(v) = p.edit().putBoolean("anger", v).apply()
    /** A line of the day from a random character (off by default). */
    var dailyLine: Boolean
        get() = p.getBoolean("daily", false)
        set(v) = p.edit().putBoolean("daily", v).apply()
    /** In Android Auto, start with the taxi driver. */
    var carTaxi: Boolean
        get() = p.getBoolean("car_taxi", true)
        set(v) = p.edit().putBoolean("car_taxi", v).apply()
    /** Fingerprint, face or screen lock to open the app. */
    var lock: Boolean
        get() = p.getBoolean("lock", false)
        set(v) = p.edit().putBoolean("lock", v).apply()
    /** The discreet icon («Σημειώσεις») instead of the logo. */
    var discreet: Boolean
        get() = p.getBoolean("discreet", false)
        set(v) = p.edit().putBoolean("discreet", v).apply()

    /** Today's use, per AI (the free Gemini limits are per day). */
    private fun today() = java.time.LocalDate.now().toString()
    fun usage(provider: app.binelik.ai.Provider): Pair<Int, Int> {
        if (p.getString("use_day", null) != today()) return 0 to 0
        return p.getInt("use_t_${provider.name}", 0) to p.getInt("use_l_${provider.name}", 0)
    }
    /** One text request, or [liveSeconds] of voice. */
    fun used(provider: app.binelik.ai.Provider, text: Int = 0, liveSeconds: Int = 0) {
        val e = p.edit()
        if (p.getString("use_day", null) != today()) {
            app.binelik.ai.Provider.entries.forEach { e.remove("use_t_${it.name}").remove("use_l_${it.name}") }
            e.putString("use_day", today())
        }
        val (t0, l0) = if (p.getString("use_day", null) == today()) usage(provider) else 0 to 0
        e.putInt("use_t_${provider.name}", t0 + text).putInt("use_l_${provider.name}", l0 + liveSeconds).apply()
    }

    // ---- 1.3 ----
    /** The characters remember a few things from earlier conversations (only on the phone). */
    var memory: Boolean
        get() = p.getBoolean("memory", true)
        set(v) = p.edit().putBoolean("memory", v).apply()
    /** When the chosen AI can't answer (limit, load), another one with a key answers. */
    var fallback: Boolean
        get() = p.getBoolean("fallback", true)
        set(v) = p.edit().putBoolean("fallback", v).apply()
    /** Phone voice: the character stops when the user starts talking over it. */
    var bargeIn: Boolean
        get() = p.getBoolean("barge_in", true)
        set(v) = p.edit().putBoolean("barge_in", v).apply()
    /** Friday evening: «Πώς πήγε η βδομάδα;» with the week's anger meter. */
    var fridayNote: Boolean
        get() = p.getBoolean("friday", true)
        set(v) = p.edit().putBoolean("friday", v).apply()
    /** The free try without a key was done (shown once on the key step). */
    var triedDemo: Boolean
        get() = p.getBoolean("demo", false)
        set(v) = p.edit().putBoolean("demo", v).apply()

    /** Text size: 1.0, 1.15 or 1.3. */
    var textScale: Float
        get() = p.getFloat("scale", 1f)
        set(v) = p.edit().putFloat("scale", v).apply()
    /** How fast the phone's voice speaks (DeepSeek): 0.9, 1.05 or 1.25. */
    var speechRate: Float
        get() = p.getFloat("rate", 1.05f)
        set(v) = p.edit().putFloat("rate", v).apply()
    /** Voice volume on top of the phone's: 1 (normal), 1.6 (loud), 2.2 (very loud). */
    var voiceGain: Float
        get() = p.getFloat("gain", 1.6f)
        set(v) = p.edit().putFloat("gain", v).apply()

    /** The screen stays on during a voice conversation. */
    var keepScreenOn: Boolean
        get() = p.getBoolean("screen_on", true)
        set(v) = p.edit().putBoolean("screen_on", v).apply()

    /** Back to the start: all settings and keys (the conversations are deleted separately). */
    fun resetAll(context: Context) {
        p.edit().clear().apply()
        context.getSharedPreferences("keys", Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** English instead of Greek (screens and characters); first time, the phone's language decides. */
    var english: Boolean
        get() = p.getBoolean("en", java.util.Locale.getDefault().language != "el")
        set(v) = p.edit().putBoolean("en", v).apply()

    private fun ck(slot: Int, k: String) = if (slot == 1) "c_$k" else "c${slot}_$k"
    fun customName(slot: Int = 1): String = p.getString(ck(slot, "name"), null) ?: ""
    fun customDescription(slot: Int = 1): String = p.getString(ck(slot, "desc"), null) ?: ""
    fun customColor(slot: Int = 1): Long = p.getLong(ck(slot, "color"), if (slot == 1) 0xFFFFB020 else 0xFF4DA3FF)
    fun setCustom(slot: Int, name: String, description: String, color: Long) =
        p.edit().putString(ck(slot, "name"), name.take(40)).putString(ck(slot, "desc"), description.take(600)).putLong(ck(slot, "color"), color).apply()
    fun deleteCustom(slot: Int) = p.edit().remove(ck(slot, "name")).remove(ck(slot, "desc")).remove(ck(slot, "color")).apply()

    fun custom(slot: Int = 1): Character? =
        if (customDescription(slot).isBlank()) null
        else Characters.custom(customName(slot), customDescription(slot), customColor(slot), genderOf(Characters.customId(slot), Gender.MALE), slot)

    fun customs(): List<Character> = Characters.CUSTOM_SLOTS.mapNotNull { custom(it) }

    /** Every character the user can pick: the built-in ones, the packs from GitHub, their own. */
    fun everyone(): List<Character> = Characters.all + packs() + customs()

    /** Characters installed from the packs on GitHub (stored as JSON, see [app.binelik.extras.Packs]). */
    fun packs(): List<Character> = app.binelik.extras.Packs.parse(p.getString("packs", null).orEmpty())
    var packsJson: String
        get() = p.getString("packs", null).orEmpty()
        set(v) = p.edit().putString("packs", v).apply()

    fun character(id: String = characterId): Character =
        Characters.customSlot(id)?.let { custom(it) } ?: Characters.byId(id) ?: packs().firstOrNull { it.id == id } ?: Characters.all.first()
}

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val character: String,
    val fromUser: Boolean,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    /** A reply where the character stepped out of its role for safety. */
    val pause: Boolean = false,
    /** What a Google search found for this reply: «title\turl» lines, and «?\tquery» for the searches. */
    @androidx.room.ColumnInfo(defaultValue = "") val sources: String = "",
)

/** One reading of the anger meter: 1–10 before the conversation, and after (0 until given). */
@Entity(tableName = "anger")
data class Anger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val character: String,
    val before: Int,
    val after: Int = 0,
    val time: Long = System.currentTimeMillis(),
)

@Dao
interface AngerDao {
    @Insert suspend fun add(a: Anger): Long
    @Query("UPDATE anger SET after = :after WHERE id = :id") suspend fun setAfter(id: Long, after: Int)
    @Query("SELECT * FROM anger WHERE time >= :since ORDER BY time") suspend fun since(since: Long): List<Anger>
    @Query("DELETE FROM anger") suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE time >= :since ORDER BY id")
    suspend fun since(since: Long): List<Message>

    @Query("SELECT * FROM messages WHERE character = :character ORDER BY id")
    fun forCharacter(character: String): Flow<List<Message>>

    @Insert
    suspend fun add(m: Message): Long

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE character = :character")
    suspend fun deleteCharacter(character: String)

    /** The characters the user wrote to most (for the icon's shortcuts). */
    @Query("SELECT character FROM messages WHERE fromUser = 1 GROUP BY character ORDER BY COUNT(*) DESC LIMIT 3")
    suspend fun topCharacters(): List<String>
}

@Database(entities = [Message::class, Anger::class], version = 3, exportSchema = true, autoMigrations = [androidx.room.AutoMigration(from = 1, to = 2), androidx.room.AutoMigration(from = 2, to = 3)])
abstract class Db : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun anger(): AngerDao

    companion object {
        @Volatile private var db: Db? = null
        fun get(context: Context): Db = db ?: synchronized(this) {
            db ?: Room.databaseBuilder(context.applicationContext, Db::class.java, "binelik.db").build().also { db = it }
        }
    }
}
