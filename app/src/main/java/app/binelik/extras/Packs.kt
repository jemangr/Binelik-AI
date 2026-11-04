package app.binelik.extras

import app.binelik.data.Character
import app.binelik.data.Gender
import app.binelik.data.Texts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Character packs: new characters from `packs/characters.json` in the GitHub repository, without a new
 * version of the app. GitHub is asked only when the user opens «More characters»; nothing else is sent.
 */
object Packs {
    const val URL = "https://raw.githubusercontent.com/jemangr/Binelik-AI/main/packs/characters.json"

    @Serializable
    data class Side(val name: String, val tagline: String, val sample: String = "", val persona: String)

    @Serializable
    data class Pack(val id: String, val color: String, val gender: String = "MALE", val el: Side, val en: Side)

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()

    fun decode(text: String): List<Pack> = runCatching { json.decodeFromString(ListSerializer(Pack.serializer()), text) }.getOrDefault(emptyList())
        .filter { Regex("[a-z0-9_]{2,30}").matches(it.id) && it.el.persona.isNotBlank() && it.en.persona.isNotBlank() }
        .take(60)

    fun encode(list: List<Pack>): String = json.encodeToString(ListSerializer(Pack.serializer()), list)

    fun character(p: Pack): Character {
        val color = runCatching { java.lang.Long.parseLong(p.color.removePrefix("#").removePrefix("0x").let { if (it.length == 6) "FF$it" else it }, 16) }.getOrDefault(0xFF4DA3FF)
        fun tx(s: Side) = Texts(s.name.take(40), s.tagline.take(120), s.sample.take(200), s.persona.take(1500))
        return Character("pack:${p.id}", color, if (p.gender == "FEMALE") Gender.FEMALE else Gender.MALE, tx(p.el), tx(p.en))
    }

    /** The installed ones, from the settings. */
    fun parse(stored: String): List<Character> = if (stored.isBlank()) emptyList() else decode(stored).map(::character)

    /** The list on GitHub, or null without a connection. */
    suspend fun fetch(): List<Pack>? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(URL).build()).execute().use { r -> if (r.isSuccessful) decode(r.body.string()) else null }
        }.getOrNull()
    }
}
