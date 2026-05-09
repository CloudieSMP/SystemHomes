package util

import logger
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import plugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object HomeStorage {
    enum class SaveOutcome {
        CREATED,
        UPDATED,
        FAILED
    }

    data class StoredHome(
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    ) {
        fun toLocation(): Location? {
            val bukkitWorld = Bukkit.getWorld(world) ?: return null
            return Location(bukkitWorld, x, y, z, yaw, pitch)
        }

        companion object {
            fun fromLocation(location: Location): StoredHome? {
                val worldName = location.world?.name ?: return null
                return StoredHome(worldName, location.x, location.y, location.z, location.yaw, location.pitch)
            }
        }
    }

    private data class PlayerHomesCache(
        val homes: MutableMap<String, StoredHome> = mutableMapOf(),
        var dirty: Boolean = false
    )

    private val homesDir: File
        get() = File(plugin.dataFolder, "homes")

    private val cache = ConcurrentHashMap<UUID, PlayerHomesCache>()
    private val loadCallbacks = ConcurrentHashMap<UUID, MutableList<(PlayerHomesCache) -> Unit>>()
    private val queuedSaves = ConcurrentHashMap.newKeySet<UUID>()

    private fun homeFile(playerId: UUID): File = File(homesDir, "$playerId.yml")

    fun preload(playerId: UUID) {
        ensureLoaded(playerId) { }
    }

    fun listHomeNamesCached(playerId: UUID): List<String> {
        val cachedHomes = cache[playerId]
        if (cachedHomes == null) {
            preload(playerId)
            return emptyList()
        }

        return synchronized(cachedHomes) {
            cachedHomes.homes.keys.sorted()
        }
    }

    fun listHomeNamesAsync(playerId: UUID, callback: (List<String>) -> Unit) {
        ensureLoaded(playerId) { loadedHomes ->
            callback(synchronized(loadedHomes) { loadedHomes.homes.keys.sorted() })
        }
    }

    fun snapshotHomesAsync(playerId: UUID, callback: (Map<String, StoredHome>) -> Unit) {
        ensureLoaded(playerId) { loadedHomes ->
            callback(synchronized(loadedHomes) { loadedHomes.homes.toMap() })
        }
    }

    fun loadHomeAsync(playerId: UUID, name: String, callback: (Location?) -> Unit) {
        val normalizedName = name.lowercase()
        ensureLoaded(playerId) { loadedHomes ->
            val storedHome = synchronized(loadedHomes) { loadedHomes.homes[normalizedName] }
            callback(storedHome?.toLocation())
        }
    }

    fun saveHomeAsync(playerId: UUID, name: String, location: Location, callback: (SaveOutcome) -> Unit) {
        val normalizedName = name.lowercase()
        ensureLoaded(playerId) { loadedHomes ->
            val storedHome = StoredHome.fromLocation(location) ?: run {
                callback(SaveOutcome.FAILED)
                return@ensureLoaded
            }

            val outcome = synchronized(loadedHomes) {
                val alreadyExists = loadedHomes.homes.put(normalizedName, storedHome) != null
                loadedHomes.dirty = true
                if (alreadyExists) SaveOutcome.UPDATED else SaveOutcome.CREATED
            }

            scheduleSave(playerId)
            callback(outcome)
        }
    }

    fun deleteHomeAsync(playerId: UUID, name: String, callback: (Boolean) -> Unit) {
        val normalizedName = name.lowercase()
        ensureLoaded(playerId) { loadedHomes ->
            val deleted = synchronized(loadedHomes) {
                val removed = loadedHomes.homes.remove(normalizedName) != null
                if (removed) {
                    loadedHomes.dirty = true
                }
                removed
            }

            if (deleted) {
                scheduleSave(playerId)
            }

            callback(deleted)
        }
    }

    fun flushAllSync() {
        cache.forEach { (playerId, loadedHomes) ->
            flush(playerId, loadedHomes)
        }
    }

    private fun ensureLoaded(playerId: UUID, callback: (PlayerHomesCache) -> Unit) {
        val cachedHomes = cache[playerId]
        if (cachedHomes != null) {
            callback(cachedHomes)
            return
        }

        var shouldLoad = false
        loadCallbacks.compute(playerId) { _, existingCallbacks ->
            val callbacks = existingCallbacks ?: mutableListOf()
            callbacks.add(callback)
            if (existingCallbacks == null) {
                shouldLoad = true
            }
            callbacks
        }

        if (!shouldLoad) {
            return
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val loadedHomes = loadFromDisk(playerId)
            cache[playerId] = loadedHomes
            val callbacks = loadCallbacks.remove(playerId).orEmpty()

            Bukkit.getScheduler().runTask(plugin, Runnable {
                callbacks.forEach { it(loadedHomes) }
            })
        })
    }

    private fun scheduleSave(playerId: UUID) {
        if (!queuedSaves.add(playerId)) {
            return
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
            queuedSaves.remove(playerId)
            val loadedHomes = cache[playerId] ?: return@Runnable
            flush(playerId, loadedHomes)
        }, 20L)
    }

    private fun loadFromDisk(playerId: UUID): PlayerHomesCache {
        if (!homesDir.exists()) {
            homesDir.mkdirs()
        }

        val config = YamlConfiguration.loadConfiguration(homeFile(playerId))
        val section = config.getConfigurationSection("homes")
        val homes = mutableMapOf<String, StoredHome>()

        section?.getKeys(false)?.forEach { homeName ->
            val homeSection = section.getConfigurationSection(homeName) ?: return@forEach
            val worldName = homeSection.getString("world") ?: return@forEach
            homes[homeName.lowercase()] = StoredHome(
                world = worldName,
                x = homeSection.getDouble("x"),
                y = homeSection.getDouble("y"),
                z = homeSection.getDouble("z"),
                yaw = homeSection.getDouble("yaw").toFloat(),
                pitch = homeSection.getDouble("pitch").toFloat()
            )
        }

        return PlayerHomesCache(homes = homes)
    }

    private fun flush(playerId: UUID, loadedHomes: PlayerHomesCache) {
        val snapshot = synchronized(loadedHomes) {
            if (!loadedHomes.dirty) {
                null
            } else {
                loadedHomes.dirty = false
                loadedHomes.homes.toMap()
            }
        } ?: return

        if (!saveSnapshot(playerId, snapshot)) {
            synchronized(loadedHomes) {
                loadedHomes.dirty = true
            }
        }
    }

    private fun saveSnapshot(playerId: UUID, homes: Map<String, StoredHome>): Boolean {
        return try {
            if (!homesDir.exists()) {
                homesDir.mkdirs()
            }

            if (homes.isEmpty()) {
                val file = homeFile(playerId)
                if (file.exists()) {
                    file.delete()
                }
                return true
            }

            val config = YamlConfiguration()
            homes.toSortedMap().forEach { (homeName, storedHome) ->
                val key = "homes.$homeName"
                config.set("$key.world", storedHome.world)
                config.set("$key.x", storedHome.x)
                config.set("$key.y", storedHome.y)
                config.set("$key.z", storedHome.z)
                config.set("$key.yaw", storedHome.yaw.toDouble())
                config.set("$key.pitch", storedHome.pitch.toDouble())
            }

            config.save(homeFile(playerId))
            true
        } catch (exception: Exception) {
            logger.warning("Could not save homes for player $playerId: ${exception.message}")
            false
        }
    }
}