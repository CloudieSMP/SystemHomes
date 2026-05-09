package util

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.net.URI
import java.net.HttpURLConnection

class UpdateChecker(private val plugin: JavaPlugin) {
    private val latestReleaseUrl = "https://api.github.com/repos/CloudieSMP/SystemHomes/releases/latest"

    fun checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val connection = (URI.create(latestReleaseUrl).toURL().openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "SystemHomes")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    plugin.logger.warning(Lang.raw("update.failed"))
                    return@Runnable
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val latestVersion = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(response)?.groupValues?.get(1)
                if (latestVersion.isNullOrBlank()) {
                    plugin.logger.warning(Lang.raw("update.failed"))
                    return@Runnable
                }

                val currentVersion = plugin.pluginMeta.version
                if (isNewerVersion(latestVersion, currentVersion)) {
                    plugin.logger.info(Lang.raw("update.available", "latest" to latestVersion, "current" to currentVersion))
                } else {
                    plugin.logger.info(Lang.raw("update.latest", "current" to currentVersion))
                }
            } catch (_: Exception) {
                plugin.logger.warning(Lang.raw("update.failed"))
            }
        })
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = normalizeVersion(latest)
        val currentParts = normalizeVersion(current)
        val max = maxOf(latestParts.size, currentParts.size)

        for (index in 0 until max) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) {
                return latestPart > currentPart
            }
        }

        return false
    }

    private fun normalizeVersion(version: String): List<Int> {
        return version
            .trim()
            .removePrefix("v")
            .split('.', '-', '_')
            .mapNotNull { segment -> segment.toIntOrNull() ?: segment.filter(Char::isDigit).toIntOrNull() }
    }
}

