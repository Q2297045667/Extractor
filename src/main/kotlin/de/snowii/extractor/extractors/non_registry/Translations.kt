package de.snowii.extractor.extractors.non_registry

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarFile

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
        val langCodes = findLangCodesInClasspath()

        for (langCode in langCodes) {
            val resourcePath = "/assets/minecraft/lang/$langCode.json"
            val inputStream = this.javaClass.getResourceAsStream(resourcePath)
            if (inputStream == null) {
                logger.warn("Could not find lang file: $resourcePath")
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
     * Scans the classpath for all language JSON files under /assets/minecraft/lang/.
     * Handles both JAR-packaged and file-system classpath layouts.
     */
    private fun findLangCodesInClasspath(): List<String> {
        val codes = mutableListOf<String>()
        val langDirUrl: URL = this.javaClass.getResource("/assets/minecraft/lang/")
            ?: return codes

        when (langDirUrl.protocol) {
            "jar" -> {
                // Running from a JAR (e.g. Minecraft client/server jar)
                val jarUrlStr = langDirUrl.path.substringBefore("!")
                val entryPrefix = langDirUrl.path.substringAfter("!").removePrefix("/")
                val jarFile = JarFile(File(URL(jarUrlStr).toURI()))
                jarFile.use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val name = entry.name
                        if (!entry.isDirectory
                            && name.startsWith(entryPrefix)
                            && name.endsWith(".json")
                        ) {
                            val filename = name.substringAfterLast("/")
                            codes.add(filename.removeSuffix(".json"))
                        }
                    }
                }
            }
            "file" -> {
                // Running from a file system (development environment)
                val langDir = File(langDirUrl.toURI())
                langDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".json")) {
                        codes.add(file.name.removeSuffix(".json"))
                    }
                }
            }
            else -> {
                logger.warn("Unsupported classpath protocol: ${langDirUrl.protocol}")
            }
        }

        logger.info("Found ${codes.size} language(s) in classpath")
        return codes
    }
}
