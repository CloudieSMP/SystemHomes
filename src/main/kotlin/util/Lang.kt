package util

import mm
import net.kyori.adventure.text.Component
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import plugin
import java.io.InputStreamReader
import java.io.File
import java.nio.charset.StandardCharsets

object Lang {
    private var messages: YamlConfiguration = YamlConfiguration()

    fun load(language: String) {
        val langDir = File(plugin.dataFolder, "lang")
        if (!langDir.exists()) langDir.mkdirs()

        val langFile = File(langDir, "$language.yml")
        val bundled = loadBundledLanguage(language) ?: loadBundledLanguage("en")

        if (!langFile.exists()) {
            if (bundled != null) {
                langFile.parentFile?.mkdirs()
                bundled.save(langFile)
                messages = YamlConfiguration.loadConfiguration(langFile)
                plugin.logger.info("Loaded language '$language'.")
                return
            }

            messages = YamlConfiguration()
            plugin.logger.warning("Language '$language' not found and no bundled fallback could be loaded.")
            return
        }

        val existing = YamlConfiguration.loadConfiguration(langFile)
        var changed = false
        if (bundled != null) {
            changed = mergeMissingKeys(existing, bundled)
        }

        if (changed) {
            existing.save(langFile)
        }

        messages = existing
        plugin.logger.info("Loaded language '$language'.")
    }


    private fun loadBundledLanguage(language: String): YamlConfiguration? {
        val stream = plugin.getResource("lang/$language.yml") ?: return null
        return stream.use { input ->
            YamlConfiguration.loadConfiguration(InputStreamReader(input, StandardCharsets.UTF_8))
        }
    }

    private fun mergeMissingKeys(target: YamlConfiguration, source: ConfigurationSection, path: String = ""): Boolean {
        var changed = false

        source.getKeys(false).forEach { key ->
            val fullPath = if (path.isBlank()) key else "$path.$key"
            val sourceSection = source.getConfigurationSection(key)
            if (sourceSection != null) {
                if (target.getConfigurationSection(fullPath) == null) {
                    target.createSection(fullPath)
                    changed = true
                }
                changed = mergeMissingKeys(target, sourceSection, fullPath) || changed
            } else if (!target.contains(fullPath)) {
                target.set(fullPath, source.get(key))
                changed = true
            }
        }

        return changed
    }

    /**
     * Returns the raw MiniMessage string for the given key,
     * with all `{placeholder}` tokens replaced.
     */
    fun raw(key: String, vararg replacements: Pair<String, String>): String {
        var text = messages.getString(key) ?: "<red>[Missing lang key: $key]</red>"
        for ((placeholder, value) in replacements) {
            text = text.replace("{$placeholder}", value)
        }
        return text
    }

    /**
     * Deserializes the translated string into an Adventure Component.
     */
    fun component(key: String, vararg replacements: Pair<String, String>): Component {
        return mm.deserialize(raw(key, *replacements))
    }
}

