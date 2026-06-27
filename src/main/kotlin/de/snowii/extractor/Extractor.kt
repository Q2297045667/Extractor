package de.snowii.extractor

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import de.snowii.extractor.extractors.*
import de.snowii.extractor.extractors.non_registry.*
import de.snowii.extractor.extractors.structures.StructureSet
import de.snowii.extractor.extractors.structures.Structures
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureTimeMillis


class Extractor : ModInitializer {
    private val logger = LoggerFactory.getLogger("pumpkin_extractor")

    companion object {
        const val OUTPUT_DIR = "pumpkin_extractor_output"
    }

    override fun onInitialize() {
        logger.info(Lang.fmt("extractor.log.starting"))

        val extractors = arrayOf(
            Dialog(), DialogActionType(), DialogBodyType(), DialogType(),
            InputControlType(), Advancement(), Effect(), SpawnEgg(),
            PotionBrewing(), Potion(), Sounds(), Recipes(),
            Biome(), BiomeMixerTest(), WorldEvent(), Carver(),
            Enchantments(), ScoreboardDisplaySlot(), Dimension(), Particles(),
            EntityAttributes(), ChunkStatus(), EntityStatuses(), MessageType(),
            SoundCategory(), EntityPose(), GameEvent(), GameRules(),
            SyncedRegistries(), ChunkGenSetting(), Packets(), Screens(),
            PlacedFeatures(), ConfiguredFeatures(), Tags(), JukeboxSong(),
            MetaDataType(), TrackedData(), NoiseParameters(), Structures(),
            StructureSet(), Entities(), Items(), DataComponent(), Blocks(),
            MultiNoise(), MultiNoise().Sample(), Translations(),
            DensityFunctions(), DensityFunctions().Tests(), DamageTypes(),
            Fluids(), Properties(), ComposterIncreaseChance(),
            FlowerPotTransformation(), Fuels(), RecipeRemainder(),
            VillagerData(), CustomStats(), Stats(), SlotRanges(),
        )

        val outputDirectory: Path
        try {
            outputDirectory = Files.createDirectories(Paths.get(OUTPUT_DIR))
            logger.info(Lang.fmt("extractor.log.output_dir", outputDirectory.toAbsolutePath()))
        } catch (e: IOException) {
            logger.error(Lang.fmt("extractor.log.output_dir_failed"), e)
            return
        }

        val gson = GsonBuilder().disableHtmlEscaping().create()

        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { server: MinecraftServer ->
            logger.info(Lang.fmt("extractor.log.server_started"))
            try {
                val timeInMillis = measureTimeMillis {
                    for (ext in extractors) {
                        try {
                            val out = outputDirectory.resolve(ext.fileName())
                            Files.createDirectories(out.parent)
                            FileWriter(out.toFile(), StandardCharsets.UTF_8).use { writer ->
                                gson.toJson(ext.extract(server), writer)
                            }
                            logger.info(Lang.fmt("extractor.log.wrote", out.toAbsolutePath()))
                        } catch (e: Exception) {
                            logger.error(Lang.fmt("extractor.log.extractor_failed", ext.fileName()), e)
                        }
                    }
                }
                logger.info(Lang.fmt("extractor.log.done", timeInMillis))
            } catch (e: Throwable) {
                logger.error(Lang.fmt("extractor.log.fatal_error"), e)
            }
        })
    }

    interface Extractor {
        fun fileName(): String

        @Throws(Exception::class)
        fun extract(server: MinecraftServer): JsonElement
    }
}
