package de.snowii.extractor

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * Loads the mod's en_us.json language file and exposes [fmt] for formatted lookups.
 * Log strings are kept in the lang file as the single source of truth;
 * at runtime only the English fallback is resolved (logs are developer-facing).
 */
object Lang {
    private val entries: Map<String, String> by lazy {
        val stream = Lang::class.java.classLoader
            .getResourceAsStream("assets/extractor/lang/en_us.json")
        if (stream == null) {
            emptyMap()
        } else {
            stream.use { s ->
                Gson().fromJson(
                    InputStreamReader(s, Charsets.UTF_8),
                    object : TypeToken<Map<String, String>>() {}.type
                )
            }
        }
    }

    /** Look up key and format with classic String.format semantics. */
    fun fmt(key: String, vararg args: Any?): String {
        val template = entries[key] ?: key
        return if (args.isEmpty()) template else template.format(*args)
    }
}
