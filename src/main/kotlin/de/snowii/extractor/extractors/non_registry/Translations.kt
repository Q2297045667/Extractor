package de.snowii.extractor.extractors.non_registry

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import de.snowii.extractor.Lang
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class Translations : Extractor.Extractor {
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val logger = LoggerFactory.getLogger("translations_extractor")

    /** Lazily reflect into net.minecraft.client.Minecraft to avoid compile-time dependency. */
    private val clientReflection by lazy { ClientReflection.resolve() }

    override fun fileName() = "translations.json"

    override fun extract(server: MinecraftServer): JsonElement {
        val langDir = Paths.get(Extractor.OUTPUT_DIR, "lang")
        Files.createDirectories(langDir)
        val summary = JsonArray()

        for (langCode in clientReflection?.languageCodes ?: listOf("en_us")) {
            val stream = loadLangStream(langCode) ?: run {
                logger.warn(Lang.fmt("extractor.translations.not_found", langCode))
                continue
            }
            try {
                stream.use { s ->
                    val translations = gson.fromJson(
                        InputStreamReader(s, StandardCharsets.UTF_8),
                        JsonObject::class.java
                    ) as JsonObject
                    FileWriter(langDir.resolve("${langCode}_java.json").toFile(), StandardCharsets.UTF_8).use { w ->
                        gson.toJson(translations, w)
                    }
                }
                summary.add(langCode)
                logger.info(Lang.fmt("extractor.translations.wrote", langCode))
            } catch (e: Exception) {
                logger.warn(Lang.fmt("extractor.translations.failed", langCode), e)
            }
        }

        logger.info(Lang.fmt("extractor.translations.summary", summary.size(), langDir.toAbsolutePath()))
        return summary
    }

    private fun loadLangStream(langCode: String): InputStream? {
        clientReflection?.let { ref ->
            try {
                val id = Identifier.fromNamespaceAndPath("minecraft", "lang/$langCode.json")
                val resources = ref.getResourceStack(id)
                if (resources.isNotEmpty()) {
                    return resources.first().open() as InputStream
                }
            } catch (_: Exception) { }
        }
        return this.javaClass.getResourceAsStream("/assets/minecraft/lang/$langCode.json")
    }

    /**
     * Cached reflective handle to the client Minecraft instance.
     * Null when running on a dedicated server (no client available).
     */
    private class ClientReflection private constructor(
        private val getResourceStack: (Identifier) -> List<*>,
        val languageCodes: List<String>
    ) {
        companion object {
            @Suppress("UNCHECKED_CAST")
            fun resolve(): ClientReflection? {
                return try {
                    val mc = Class.forName("net.minecraft.client.Minecraft")
                    val client = mc.getMethod("getInstance").invoke(null) ?: return null
                    val rm = mc.getMethod("getResourceManager").invoke(client)
                    val stackMethod = rm.javaClass.getMethod("getResourceStack", Identifier::class.java)

                    val lm = mc.getMethod("getLanguageManager").invoke(client)
                    val languages = lm.javaClass.getMethod("getLanguages").invoke(lm) as Map<String, *>

                    ClientReflection(
                        getResourceStack = { id -> stackMethod.invoke(rm, id) as List<*> },
                        languageCodes = languages.keys.sorted()
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
