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
                    val raw = gson.fromJson(
                        InputStreamReader(s, StandardCharsets.UTF_8),
                        JsonObject::class.java
                    ) as JsonObject
                    val sorted = JsonObject()
                    raw.entrySet().sortedBy { it.key }.forEach { (k, v) -> sorted.add(k, v) }
                    FileWriter(langDir.resolve("${langCode}_java.json").toFile(), StandardCharsets.UTF_8).use { w ->
                        gson.toJson(sorted, w)
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
                ref.openResource(id)?.let { return it }
            } catch (_: Exception) { }
        }
        return this.javaClass.getResourceAsStream("/assets/minecraft/lang/$langCode.json")
    }

    /**
     * Cached reflective handle to the client Minecraft instance.
     * Null when running on a dedicated server (no client available).
     */
    private class ClientReflection private constructor(
        private val resourceManager: Any,
        private val getResourceStackMethod: java.lang.reflect.Method,
        val languageCodes: List<String>
    ) {
        @Suppress("UNCHECKED_CAST")
        fun openResource(identifier: Identifier): InputStream? {
            val resources = getResourceStackMethod.invoke(resourceManager, identifier) as List<*>
            if (resources.isEmpty()) return null
            val resource = resources.first() ?: return null
            return resource.javaClass.getMethod("open").invoke(resource) as InputStream
        }

        companion object {
            fun resolve(): ClientReflection? {
                return try {
                    val mc = Class.forName("net.minecraft.client.Minecraft")
                    val client = mc.getMethod("getInstance").invoke(null) ?: return null
                    val rm = mc.getMethod("getResourceManager").invoke(client)
                    val stackMethod = rm.javaClass.getMethod("getResourceStack", Identifier::class.java)

                    val lm = mc.getMethod("getLanguageManager").invoke(client)
                    @Suppress("UNCHECKED_CAST")
                    val languages = lm.javaClass.getMethod("getLanguages").invoke(lm) as Map<String, *>

                    ClientReflection(
                        resourceManager = rm,
                        getResourceStackMethod = stackMethod,
                        languageCodes = languages.keys.sorted()
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
