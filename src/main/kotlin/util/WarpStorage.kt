package util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import plugin
import java.io.File

object WarpStorage {
    data class StoredWarp(
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
            fun fromLocation(location: Location): StoredWarp? {
                val worldName = location.world?.name ?: return null
                return StoredWarp(worldName, location.x, location.y, location.z, location.yaw, location.pitch)
            }
        }
    }

    data class WarpEntry(
        val name: String,
        val location: StoredWarp,
    )

    private val warpsFile: File
        get() = File(plugin.dataFolder, "warps.yml")

    private fun sanitizeName(input: String): String? {
        val name = input.trim().lowercase()
        if (name.length !in 1..16) return null
        if (!name.matches(Regex("^[a-z0-9_-]+$"))) return null
        return name
    }

    private fun loadConfig(): YamlConfiguration {
        if (!warpsFile.exists()) {
            warpsFile.parentFile?.mkdirs()
            warpsFile.createNewFile()
        }
        return YamlConfiguration.loadConfiguration(warpsFile)
    }

    fun containsWarp(name: String): Boolean {
        val normalized = sanitizeName(name) ?: return false
        val config = loadConfig()
        return config.contains("warps.$normalized")
    }

    fun listWarps(): List<WarpEntry> {
        val config = loadConfig()
        val section = config.getConfigurationSection("warps") ?: return emptyList()

        return section.getKeys(false).sorted().mapNotNull { name ->
            loadWarp(name)?.let { location -> WarpEntry(name, location) }
        }
    }

    fun listWarpNames(): List<String> {
        return listWarps().map { it.name }
    }

    fun loadWarp(name: String): StoredWarp? {
        val normalized = sanitizeName(name) ?: return null
        val config = loadConfig()
        val path = config.getConfigurationSection("warps.$normalized") ?: return null
        val world = path.getString("world") ?: return null
        return StoredWarp(
            world = world,
            x = path.getDouble("x"),
            y = path.getDouble("y"),
            z = path.getDouble("z"),
            yaw = path.getDouble("yaw").toFloat(),
            pitch = path.getDouble("pitch").toFloat(),
        )
    }

    fun saveWarp(name: String, location: Location): Boolean {
        val normalized = sanitizeName(name) ?: return false
        val stored = StoredWarp.fromLocation(location) ?: return false
        val config = loadConfig()
        val key = "warps.$normalized"
        config.set("$key.name", normalized)
        config.set("$key.world", stored.world)
        config.set("$key.x", stored.x)
        config.set("$key.y", stored.y)
        config.set("$key.z", stored.z)
        config.set("$key.yaw", stored.yaw.toDouble())
        config.set("$key.pitch", stored.pitch.toDouble())
        config.save(warpsFile)
        return true
    }

    fun deleteWarp(name: String): Boolean {
        val normalized = sanitizeName(name) ?: return false
        val config = loadConfig()
        val path = "warps.$normalized"
        if (!config.contains(path)) return false

        config.set(path, null)
        if (config.getConfigurationSection("warps")?.getKeys(false).isNullOrEmpty()) {
            if (warpsFile.exists()) warpsFile.delete()
            return true
        }

        config.save(warpsFile)
        return true
    }
}

