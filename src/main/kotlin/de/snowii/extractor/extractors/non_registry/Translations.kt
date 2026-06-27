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
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
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

        val resourceManager = server.resourceManager
        val summary = JsonArray()

        // Scan for all language JSON files across all namespaces
        val langResources = resourceManager.listResources("lang") { identifier: Identifier ->
            identifier.path.endsWith(".json")
        }

        for ((identifier, resource) in langResources) {
            val langCode = identifier.path
                .removePrefix("lang/")
                .removeSuffix(".json")

            try {
                val langFile = langDir.resolve("$langCode.json")
                resource.open().use { stream ->
                    val translations = gson.fromJson(
                        InputStreamReader(stream, StandardCharsets.UTF_8),
                        JsonObject::class.java
                    ) as JsonObject

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
}
