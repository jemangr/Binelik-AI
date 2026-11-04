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
    private fun genderOf(id: String, default: Gender): Gender =
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

    /** English instead of Greek (screens and characters); first time, the phone's language decides. */
    var english: Boolean
        get() = p.getBoolean("en", java.util.Locale.getDefault().language != "el")
        set(v) = p.edit().putBoolean("en", v).apply()

    var customName: String
        get() = p.getString("c_name", null) ?: ""
        set(v) = p.edit().putString("c_name", v.take(40)).apply()
    var customDescription: String
        get() = p.getString("c_desc", null) ?: ""
        set(v) = p.edit().putString("c_desc", v.take(600)).apply()
    var customColor: Long
        get() = p.getLong("c_color", 0xFFFFB020)
        set(v) = p.edit().putLong("c_color", v).apply()

    fun custom(): Character? =
        if (customDescription.isBlank()) null
        else Characters.custom(customName, customDescription, customColor, genderOf(Characters.CUSTOM_ID, Gender.MALE))

    fun character(id: String = characterId): Character =
        if (id == Characters.CUSTOM_ID) custom() ?: Characters.all.first() else Characters.byId(id) ?: Characters.all.first()
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
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE character = :character ORDER BY id")
    fun forCharacter(character: String): Flow<List<Message>>

    @Insert
    suspend fun add(m: Message): Long

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE character = :character")
    suspend fun deleteCharacter(character: String)
}

@Database(entities = [Message::class], version = 1, exportSchema = true)
abstract class Db : RoomDatabase() {
    abstract fun messages(): MessageDao

    companion object {
        @Volatile private var db: Db? = null
        fun get(context: Context): Db = db ?: synchronized(this) {
            db ?: Room.databaseBuilder(context.applicationContext, Db::class.java, "binelik.db").build().also { db = it }
        }
    }
}
