package util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import plugin
import java.io.File
import java.util.UUID

object PlayerWarpStorage {
    data class StoredPlayerWarp(
        val owner: UUID,
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
    ) {
        fun toLocation(): Location? {
            val bukkitWorld = Bukkit.getWorld(world) ?: return null
            return Location(bukkitWorld, x, y, z, yaw, pitch)
        }

        companion object {
            fun fromLocation(owner: UUID, location: Location): StoredPlayerWarp? {
                val worldName = location.world?.name ?: return null
                return StoredPlayerWarp(owner, worldName, location.x, location.y, location.z, location.yaw, location.pitch)
            }
        }
    }

    data class PlayerWarpEntry(
        val name: String,
        val location: StoredPlayerWarp,
    )

    private val pwarpsDir: File
        get() = File(plugin.dataFolder, "playerwarps")

    private fun sanitizeName(input: String): String? {
        val name = input.trim().lowercase()
        if (name.length !in 1..16) return null
        if (!name.matches(Regex("^[a-z0-9_-]+$"))) return null
        return name
    }

    private fun pwarpFile(owner: UUID): File {
        return File(pwarpsDir, "$owner.yml")
    }

    private fun ensureDir() {
        if (!pwarpsDir.exists()) {
            pwarpsDir.mkdirs()
        }
    }

    private fun loadConfig(owner: UUID): YamlConfiguration {
        ensureDir()
        val file = pwarpFile(owner)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        return YamlConfiguration.loadConfiguration(file)
    }

    private fun listPlayerWarpFiles(): List<File> {
        ensureDir()
        return pwarpsDir.listFiles { file ->
            file.isFile && file.extension.equals("yml", ignoreCase = true) && runCatching { UUID.fromString(file.nameWithoutExtension) }.isSuccess
        }
            ?.sortedBy { it.nameWithoutExtension.lowercase() }
            .orEmpty()
    }

    fun containsPlayerWarp(name: String): Boolean {
        val normalized = sanitizeName(name) ?: return false
        return loadPlayerWarp(normalized) != null
    }

    fun listPlayerWarps(): List<PlayerWarpEntry> {
        return listPlayerWarpFiles().flatMap { file ->
            val owner = UUID.fromString(file.nameWithoutExtension)
            val config = YamlConfiguration.loadConfiguration(file)
            val section = config.getConfigurationSection("playerwarps") ?: return@flatMap emptyList()

            section.getKeys(false).sorted().mapNotNull { name ->
                val warpSection = section.getConfigurationSection(name) ?: return@mapNotNull null
                val world = warpSection.getString("world") ?: return@mapNotNull null
                PlayerWarpEntry(
                    name = name,
                    location = StoredPlayerWarp(
                        owner = owner,
                        world = world,
                        x = warpSection.getDouble("x"),
                        y = warpSection.getDouble("y"),
                        z = warpSection.getDouble("z"),
                        yaw = warpSection.getDouble("yaw").toFloat(),
                        pitch = warpSection.getDouble("pitch").toFloat(),
                    )
                )
            }
        }
    }

    fun listPlayerWarpNames(): List<String> {
        return listPlayerWarps().map { it.name }
    }

    fun listPlayerWarpNames(owner: UUID): List<String> {
        val config = loadConfig(owner)
        val section = config.getConfigurationSection("playerwarps") ?: return emptyList()
        return section.getKeys(false).sorted()
    }

    fun loadPlayerWarp(name: String): StoredPlayerWarp? {
        val normalized = sanitizeName(name) ?: return null
        return listPlayerWarpFiles().asSequence().mapNotNull { file ->
            val owner = UUID.fromString(file.nameWithoutExtension)
            val config = YamlConfiguration.loadConfiguration(file)
            val path = config.getConfigurationSection("playerwarps.$normalized") ?: return@mapNotNull null
            val world = path.getString("world") ?: return@mapNotNull null
            StoredPlayerWarp(
                owner = owner,
                world = world,
                x = path.getDouble("x"),
                y = path.getDouble("y"),
                z = path.getDouble("z"),
                yaw = path.getDouble("yaw").toFloat(),
                pitch = path.getDouble("pitch").toFloat(),
            )
        }.firstOrNull()
    }

    fun savePlayerWarp(name: String, owner: UUID, location: Location): Boolean {
        val normalized = sanitizeName(name) ?: return false
        val stored = StoredPlayerWarp.fromLocation(owner, location) ?: return false
        val config = loadConfig(owner)
        val key = "playerwarps.$normalized"
        config.set("$key.name", normalized)
        config.set("$key.player", stored.owner.toString())
        config.set("$key.world", stored.world)
        config.set("$key.x", stored.x)
        config.set("$key.y", stored.y)
        config.set("$key.z", stored.z)
        config.set("$key.yaw", stored.yaw.toDouble())
        config.set("$key.pitch", stored.pitch.toDouble())
        config.save(pwarpFile(owner))
        return true
    }

    fun deletePlayerWarp(name: String): Boolean {
        val normalized = sanitizeName(name) ?: return false
        val ownerFile = listPlayerWarpFiles().firstNotNullOfOrNull { file ->
            val config = YamlConfiguration.loadConfiguration(file)
            if (config.contains("playerwarps.$normalized")) file else null
        } ?: return false

        val config = YamlConfiguration.loadConfiguration(ownerFile)
        config.set("playerwarps.$normalized", null)
        if (config.getConfigurationSection("playerwarps")?.getKeys(false).isNullOrEmpty()) {
            return ownerFile.delete()
        }

        config.save(ownerFile)
        return true
    }
}


