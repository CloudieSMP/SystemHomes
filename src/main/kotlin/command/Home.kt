package command

import util.HomeStorage
import util.Lang
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Flag
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.processing.CommandContainer
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import plugin
import util.requirePlayer

@Suppress("unused")
@CommandContainer
class Home {
    private val maxHomes: Int
        get() = plugin.config.home.maxHomes

    @Command("homes")
    @CommandDescription("List your saved homes.")
    @Permission("systemhomes.cmd.home")
    fun homes(css: CommandSourceStack) {
        val player = css.requirePlayer() ?: return
        HomeStorage.listHomeNamesAsync(player.uniqueId) { homes ->
            val onlinePlayer = Bukkit.getPlayer(player.uniqueId) ?: return@listHomeNamesAsync

            if (homes.isEmpty()) {
                onlinePlayer.sendMessage(Lang.component("home.no-homes"))
                return@listHomeNamesAsync
            }

            val header = Lang.component("home.list-header", "count" to homes.size.toString(), "max" to maxHomes.toString())
            val separator = Lang.component("home.list-separator")
            val entries = homes.map { name -> Lang.component("home.list-entry", "name" to name) }

            val message = entries.foldIndexed(header) { index, acc, entry ->
                val withEntry = acc.append(entry)
                if (index < entries.size - 1) withEntry.append(separator) else withEntry
            }
            onlinePlayer.sendMessage(message)
        }
    }

    @Command("sethome <name>")
    @CommandDescription("Set a home at your current position.")
    @Permission("systemhomes.cmd.home")
    fun sethome(
        css: CommandSourceStack,
        @Argument("name") name: String,
        @Flag("force", aliases = ["f"]) forced: Boolean = false) {
        val player = css.requirePlayer() ?: return
        val playerId = player.uniqueId
        val homeLocation = player.location.clone()
        val sanitizedName = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("home.invalid-name"))
            return
        }

        HomeStorage.snapshotHomesAsync(playerId) { homes ->
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@snapshotHomesAsync
            val existingHome = homes.containsKey(sanitizedName)
            if (!existingHome && homes.size >= maxHomes) {
                onlinePlayer.sendMessage(Lang.component("home.limit-reached", "max" to maxHomes.toString()))
                return@snapshotHomesAsync
            }
            if (existingHome && !forced) {
                onlinePlayer.sendMessage(Lang.component("home.already-exists", "name" to sanitizedName))
                return@snapshotHomesAsync
            }

            HomeStorage.saveHomeAsync(playerId, sanitizedName, homeLocation) { outcome ->
                val refreshedPlayer = Bukkit.getPlayer(playerId) ?: return@saveHomeAsync
                when (outcome) {
                    HomeStorage.SaveOutcome.CREATED -> refreshedPlayer.sendMessage(Lang.component("home.created", "name" to sanitizedName))
                    HomeStorage.SaveOutcome.UPDATED -> refreshedPlayer.sendMessage(Lang.component("home.updated", "name" to sanitizedName))
                    HomeStorage.SaveOutcome.FAILED  -> refreshedPlayer.sendMessage(Lang.component("home.save-failed"))
                }
            }
        }
    }

    @Command("home <name>")
    @CommandDescription("Teleport to one of your homes.")
    @Permission("systemhomes.cmd.home")
    fun homesTeleport(css: CommandSourceStack, @Argument(value = "name", suggestions = "player-homes") name: String) {
        val player = css.requirePlayer() ?: return
        val playerId = player.uniqueId
        val sanitizedName = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("home.invalid-name-short"))
            return
        }

        HomeStorage.loadHomeAsync(playerId, sanitizedName) { home ->
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@loadHomeAsync
            val targetHome = home ?: run {
                onlinePlayer.sendMessage(Lang.component("home.not-found", "name" to sanitizedName))
                return@loadHomeAsync
            }

            if (onlinePlayer.teleport(targetHome)) {
                onlinePlayer.sendMessage(Lang.component("home.teleported", "name" to sanitizedName))
            } else {
                onlinePlayer.sendMessage(Lang.component("home.teleport-failed"))
            }
        }
    }

    @Command("delhome <name>")
    @CommandDescription("Delete one of your homes.")
    @Permission("systemhomes.cmd.home")
    fun delhome(
        css: CommandSourceStack,
        @Argument(value = "name", suggestions = "player-homes") name: String,
        @Flag("force", aliases = ["f"]) forced: Boolean = false) {
        val player = css.requirePlayer() ?: return
        val playerId = player.uniqueId
        val sanitizedName = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("home.invalid-name-short"))
            return
        }
        if (!forced) {
            player.sendMessage(Lang.component("home.delete-confirm", "name" to sanitizedName))
            return
        }

        HomeStorage.deleteHomeAsync(playerId, sanitizedName) { deleted ->
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@deleteHomeAsync
            if (deleted) {
                onlinePlayer.sendMessage(Lang.component("home.deleted", "name" to sanitizedName))
            } else {
                onlinePlayer.sendMessage(Lang.component("home.not-found", "name" to sanitizedName))
            }
        }
    }

    private fun sanitizeName(input: String): String? {
        val trimmed = input.trim().lowercase()
        if (trimmed.length !in 1..16) return null
        if (!trimmed.matches(Regex("^[a-z0-9_-]+$"))) return null
        return trimmed
    }

    @Suggestions("player-homes")
    fun homeNameSuggestions(context: CommandContext<CommandSourceStack>, input: String): List<String> {
        val player = context.sender().sender as? Player ?: return emptyList()
        return HomeStorage.listHomeNamesCached(player.uniqueId)
            .filter { it.startsWith(input, ignoreCase = true) }
    }
}