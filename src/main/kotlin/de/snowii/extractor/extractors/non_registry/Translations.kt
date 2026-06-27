package de.snowii.extractor.extractors.non_registry

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Translations : Extractor.Extractor {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val logger: Logger = LoggerFactory.getLogger("translations_extractor")

    override fun fileName(): String {
        return "translations.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val langDir = Paths.get(Extractor.OUTPUT_DIR, "lang")
        Files.createDirectories(langDir)
        val summary = JsonArray()

        val langCodes = findLangCodes()

        for (langCode in langCodes) {
            val inputStream = loadLangFile(langCode)
            if (inputStream == null) {
                logger.warn("Could not find lang file for: $langCode")
                continue
            }

            try {
                inputStream.use { stream ->
                    val translations = gson.fromJson(
                        InputStreamReader(stream, StandardCharsets.UTF_8),
                        JsonObject::class.java
                    ) as JsonObject

                    val langFile = langDir.resolve("${langCode}_java.json")
                    FileWriter(langFile.toFile(), StandardCharsets.UTF_8).use { writer ->
                        gson.toJson(translations, writer)
                    }
                }
                summary.add(langCode)
                logger.info("Wrote language: $langCode")
            } catch (e: Exception) {
                logger.warn("Failed to load language: $langCode", e)
            }
        }

        logger.info("Extracted ${summary.size()} languages to ${langDir.toAbsolutePath()}")
        return summary
    }

    /**
     * Discovers all available language codes from the client-side LanguageManager.
     * Falls back to classpath scanning on dedicated servers.
     */
    @Suppress("UNCHECKED_CAST")
    private fun findLangCodes(): List<String> {
        try {
            val minecraftClass = Class.forName("net.minecraft.client.Minecraft")
            val client = minecraftClass.getMethod("getInstance").invoke(null) ?: return classpathFallback()
            val languageManager = minecraftClass.getMethod("getLanguageManager").invoke(client)
            val languages = languageManager.javaClass.getMethod("getLanguages").invoke(languageManager) as Map<String, *>
            if (languages.isNotEmpty()) {
                val codes = languages.keys.sorted()
                logger.info("Found ${codes.size} language(s) via client LanguageManager")
                return codes
            }
        } catch (e: Exception) {
            logger.info("Client LanguageManager not available, scanning classpath...")
        }
        return classpathFallback()
    }

    /**
     * Loads a language file from the client ResourceManager (which has access to the
     * full Minecraft assets at runtime), falling back to classpath for dedicated servers.
     */
    private fun loadLangFile(langCode: String): InputStream? {
        // Try client resource manager first (has full assets at runtime)
        try {
            val minecraftClass = Class.forName("net.minecraft.client.Minecraft")
            val client = minecraftClass.getMethod("getInstance").invoke(null) ?: return classpathLoad(langCode)
            val resourceManager = minecraftClass.getMethod("getResourceManager").invoke(client)
            val identifier = Identifier.fromNamespaceAndPath("minecraft", "lang/$langCode.json")
            val resources = resourceManager.javaClass
                .getMethod("getResourceStack", Identifier::class.java)
                .invoke(resourceManager, identifier) as List<*>
            if (resources.isNotEmpty()) {
                val resource = resources.first()!!
                return resource.javaClass.getMethod("open").invoke(resource) as InputStream
            }
        } catch (e: Exception) {
            logger.debug("Client resource load failed for $langCode, trying classpath...")
        }

        // Fallback: direct classpath loading (dedicated server or dev environment)
        return classpathLoad(langCode)
    }

    private fun classpathLoad(langCode: String): InputStream? {
        return this.javaClass.getResourceAsStream("/assets/minecraft/lang/$langCode.json")
    }

    private fun classpathFallback(): List<String> {
        return listOf("en_us")
    }
}
