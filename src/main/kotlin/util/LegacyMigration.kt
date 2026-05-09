package util

import org.bukkit.configuration.file.YamlConfiguration
import plugin
import java.io.File
import java.util.UUID

object LegacyMigration {
    fun importLegacyData() {
        var importedHomes = 0
        var importedWarps = 0
        var importedPlayerWarps = 0

        val currentRoot = plugin.dataFolder
        val oldRoot = plugin.dataFolder.parentFile?.let { File(it, "SystemHomes-old") }

        if (currentRoot.exists()) {
            val homesLegacy = File(currentRoot, "homes.yml")
            val warpsLegacyDir = File(currentRoot, "warps")
            val playerWarpsLegacyDir = File(currentRoot, "playerwarps")

            if (listOf(homesLegacy, warpsLegacyDir, playerWarpsLegacyDir).any { it.exists() }) {
                plugin.logger.info(Lang.raw("migration.found-legacy"))
                importedHomes += importHomes(homesLegacy)
                importedWarps += importWarpsFromDirectory(warpsLegacyDir)
                importedPlayerWarps += importPlayerWarpsFromDirectory(playerWarpsLegacyDir)
            }
        }

        if (oldRoot != null && oldRoot.exists()) {
            val homesLegacy = File(oldRoot, "homes.yml")
            val warpsLegacy = File(oldRoot, "warps.yml")
            val playerWarpsLegacy = File(oldRoot, "playerwarps.yml")

            if (listOf(homesLegacy, warpsLegacy, playerWarpsLegacy).any { it.exists() }) {
                plugin.logger.info(Lang.raw("migration.found-legacy"))
                importedHomes += importHomes(homesLegacy)
                importedWarps += importWarpsFromFlatFile(warpsLegacy)
                importedPlayerWarps += importPlayerWarpsFromFlatFile(playerWarpsLegacy)
            }
        }

        if (importedHomes > 0) {
            plugin.logger.info(Lang.raw("migration.imported-homes", "count" to importedHomes.toString()))
        }
        if (importedWarps > 0) {
            plugin.logger.info(Lang.raw("migration.imported-warps", "count" to importedWarps.toString()))
        }
        if (importedPlayerWarps > 0) {
            plugin.logger.info(Lang.raw("migration.imported-pwarps", "count" to importedPlayerWarps.toString()))
        }
    }

    private fun importHomes(legacyFile: File): Int {
        if (!legacyFile.exists()) return 0
        val legacy = YamlConfiguration.loadConfiguration(legacyFile)
        val homesSection = legacy.getConfigurationSection("homes") ?: return 0
        val targetDir = File(plugin.dataFolder, "homes")
        if (!targetDir.exists()) targetDir.mkdirs()

        var imported = 0
        homesSection.getKeys(false).forEach { playerKey ->
            val playerSection = homesSection.getConfigurationSection(playerKey) ?: return@forEach
            val playerId = runCatching { UUID.fromString(playerKey) }.getOrNull() ?: return@forEach
            val targetFile = File(targetDir, "$playerId.yml")
            val target = if (targetFile.exists()) YamlConfiguration.loadConfiguration(targetFile) else YamlConfiguration()
            val targetHomes = target.getConfigurationSection("homes") ?: target.createSection("homes")

            playerSection.getKeys(false).forEach { homeName ->
                if (targetHomes.getConfigurationSection(homeName) != null) return@forEach
                val sourceHome = playerSection.getConfigurationSection(homeName) ?: return@forEach
                val destHome = targetHomes.createSection(homeName)
                copyLocationSection(sourceHome, destHome)
                imported++
            }

            target.save(targetFile)
        }
        return imported
    }

    private fun importWarpsFromFlatFile(legacyFile: File): Int {
        if (!legacyFile.exists()) return 0
        val targetFile = File(plugin.dataFolder, "warps.yml")
        val target = if (targetFile.exists()) YamlConfiguration.loadConfiguration(targetFile) else YamlConfiguration()
        val targetSection = target.getConfigurationSection("warps") ?: target.createSection("warps")

        var imported = 0
        val legacy = YamlConfiguration.loadConfiguration(legacyFile)
        val warpsSection = legacy.getConfigurationSection("warps") ?: return 0

        warpsSection.getKeys(false).forEach { warpName ->
            val sourceWarp = warpsSection.getConfigurationSection(warpName) ?: return@forEach
            if (targetSection.getConfigurationSection(warpName) != null) return@forEach
            val destWarp = targetSection.createSection(warpName)
            copyLocationSection(sourceWarp, destWarp)
            destWarp.set("name", warpName)
            imported++
        }

        if (imported > 0) target.save(targetFile)
        markMigrated(legacyFile)

        return imported
    }

    private fun importWarpsFromDirectory(legacyDir: File): Int {
        if (!legacyDir.exists()) return 0

        val targetFile = File(plugin.dataFolder, "warps.yml")
        val target = if (targetFile.exists()) YamlConfiguration.loadConfiguration(targetFile) else YamlConfiguration()
        val targetSection = target.getConfigurationSection("warps") ?: target.createSection("warps")

        var imported = 0
        legacyDir.listFiles { file -> file.isFile && file.extension.equals("yml", ignoreCase = true) }?.forEach { legacyWarpFile ->
            val warpName = legacyWarpFile.nameWithoutExtension.lowercase()
            if (targetSection.getConfigurationSection(warpName) != null) return@forEach

            val legacy = YamlConfiguration.loadConfiguration(legacyWarpFile)
            val source = legacy.getConfigurationSection("warp") ?: return@forEach
            val destWarp = targetSection.createSection(warpName)
            copyLocationSection(source, destWarp)
            destWarp.set("name", warpName)
            imported++
            markMigrated(legacyWarpFile)
        }

        if (imported > 0) target.save(targetFile)
        return imported
    }

    private fun importPlayerWarpsFromFlatFile(legacyFile: File): Int {
        if (!legacyFile.exists()) return 0

        val legacy = YamlConfiguration.loadConfiguration(legacyFile)
        val warpsSection = legacy.getConfigurationSection("warps") ?: return 0
        val targetDir = File(plugin.dataFolder, "playerwarps")
        if (!targetDir.exists()) targetDir.mkdirs()

        var imported = 0

        warpsSection.getKeys(false).forEach { warpName ->
            val sourceWarp = warpsSection.getConfigurationSection(warpName) ?: return@forEach
            val owner = sourceWarp.getString("player")?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return@forEach
            writePlayerWarp(targetDir, owner, warpName, sourceWarp)?.let { imported += it }
        }

        if (imported > 0) markMigrated(legacyFile)

        return imported
    }

    private fun importPlayerWarpsFromDirectory(legacyDir: File): Int {
        if (!legacyDir.exists()) return 0

        val targetDir = File(plugin.dataFolder, "playerwarps")
        if (!targetDir.exists()) targetDir.mkdirs()

        var imported = 0
        legacyDir.listFiles { file ->
            file.isFile && file.extension.equals("yml", ignoreCase = true) && runCatching {
                UUID.fromString(file.nameWithoutExtension)
            }.isFailure
        }?.forEach { legacyWarpFile ->
            val warpName = legacyWarpFile.nameWithoutExtension.lowercase()
            val legacy = YamlConfiguration.loadConfiguration(legacyWarpFile)
            val source = legacy.getConfigurationSection("pwarp")
                ?: legacy.getConfigurationSection("playerwarp")
                ?: legacy.getConfigurationSection("warps.$warpName")
                ?: return@forEach
            val owner = source.getString("player")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@forEach
            writePlayerWarp(targetDir, owner, warpName, source)?.let { imported += it }
            markMigrated(legacyWarpFile)
        }

        return imported
    }

    private fun writePlayerWarp(targetDir: File, owner: UUID, warpName: String, source: org.bukkit.configuration.ConfigurationSection): Int? {
        val targetFile = File(targetDir, "$owner.yml")
        val target = if (targetFile.exists()) YamlConfiguration.loadConfiguration(targetFile) else YamlConfiguration()
        val targetSection = target.getConfigurationSection("playerwarps") ?: target.createSection("playerwarps")
        if (targetSection.getConfigurationSection(warpName) != null) return null

        val destWarp = targetSection.createSection(warpName)
        destWarp.set("name", warpName)
        destWarp.set("player", owner.toString())
        copyLocationSection(source, destWarp)
        target.save(targetFile)
        return 1
    }

    private fun copyLocationSection(source: org.bukkit.configuration.ConfigurationSection, destination: org.bukkit.configuration.ConfigurationSection) {
        destination.set("world", source.getString("world"))
        destination.set("x", source.getDouble("x"))
        destination.set("y", source.getDouble("y"))
        destination.set("z", source.getDouble("z"))
        destination.set("yaw", source.getDouble("yaw"))
        destination.set("pitch", source.getDouble("pitch"))
    }

    private fun markMigrated(file: File) {
        if (!file.exists()) return
        val migrated = File(file.parentFile, "${file.name}.migrated")
        if (migrated.exists()) return
        file.renameTo(migrated)
    }

}



