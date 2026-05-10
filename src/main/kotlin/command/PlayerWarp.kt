package command

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
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
import util.Lang
import util.PlayerWarpStorage
import util.requirePlayer

@Suppress("unused")
@CommandContainer
class PlayerWarp {
    private val teleportDelay: Int
        get() = plugin.config.pwarp.teleportDelay

    private val maxWarps: Int
        get() = plugin.config.pwarp.maxWarps

    @Command("pwarp|playerwarp <name>")
    @CommandDescription("Teleport to a player warp.")
    @Permission("systemhomes.cmd.pwarp")
    fun pwarp(css: CommandSourceStack, @Argument(value = "name", suggestions = "pwarp-names") name: String) {
        val player = css.requirePlayer() ?: return
        teleportToPlayerWarp(player, name)
    }

    @Command("pwarps|playerwarps")
    @CommandDescription("List available player warps.")
    @Permission("systemhomes.cmd.pwarp")
    fun pwarps(css: CommandSourceStack) {
        val player = css.requirePlayer() ?: return
        val warps = PlayerWarpStorage.listPlayerWarps()
        if (warps.isEmpty()) {
            player.sendMessage(Lang.component("pwarp.no-warps"))
            return
        }

        val header = Lang.component("pwarp.list-header", "count" to warps.size.toString())
        val separator = Component.text("\n")
        val message = warps.foldIndexed(header) { index, acc, warp ->
            val ownerName = Bukkit.getOfflinePlayer(warp.location.owner).name ?: warp.location.owner.toString().take(8)
            val entry = Lang.component(
                "pwarp.list-entry",
                "name" to warp.name,
                "owner" to ownerName,
                "world" to friendlyWorldName(warp.location.world),
                "x" to warp.location.x.toInt().toString(),
                "y" to warp.location.y.toInt().toString(),
                "z" to warp.location.z.toInt().toString(),
            )
                .clickEvent(ClickEvent.runCommand("/pwarp ${warp.name}"))
                .hoverEvent(HoverEvent.showText(Lang.component("pwarp.list-hover", "name" to warp.name)))

            if (index < warps.size - 1) acc.append(entry).append(separator) else acc.append(entry)
        }

        player.sendMessage(message)
    }

    @Command("setpwarp|setplayerwarp <name>")
    @CommandDescription("Set a player warp at your current position.")
    @Permission("systemhomes.cmd.pwarp")
    fun setpwarp(
        css: CommandSourceStack,
        @Argument("name") name: String,
        @Flag("force", aliases = ["f"]) forced: Boolean = false,
    ) {
        val player = css.requirePlayer() ?: return
        val sanitized = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("pwarp.needs-name"))
            return
        }

        val existing = PlayerWarpStorage.loadPlayerWarp(sanitized)
        val isAdmin = player.hasPermission("systemhomes.admin.pwarp")
        if (existing != null && existing.owner != player.uniqueId && !isAdmin) {
            player.sendMessage(Lang.component("pwarp.not-owned"))
            return
        }

        if (existing == null && !isAdmin) {
            val ownedWarps = PlayerWarpStorage.listPlayerWarpNames(player.uniqueId).size
            if (ownedWarps >= maxWarps) {
                player.sendMessage(Lang.component("pwarp.max-reached", "max" to maxWarps.toString()))
                return
            }
        }

        if (existing != null && !forced) {
            player.sendMessage(Lang.component("pwarp.overwrite-confirm", "name" to sanitized))
            return
        }

        val storageOwner = existing?.owner ?: player.uniqueId
        if (!PlayerWarpStorage.savePlayerWarp(sanitized, storageOwner, player.location.clone())) {
            player.sendMessage(Lang.component("pwarp.teleport-failed"))
            return
        }

        player.sendMessage(Lang.component("pwarp.set-success", "name" to sanitized))
    }

    @Command("delpwarp|delplayerwarp|rempwarp|remplayerwarp <name>")
    @CommandDescription("Delete a player warp.")
    @Permission("systemhomes.cmd.pwarp")
    fun delpwarp(css: CommandSourceStack, @Argument(value = "name", suggestions = "pwarp-names") name: String) {
        val player = css.requirePlayer() ?: return
        val sanitized = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("pwarp.delete-not-found"))
            return
        }

        val existing = PlayerWarpStorage.loadPlayerWarp(sanitized) ?: run {
            player.sendMessage(Lang.component("pwarp.delete-not-found"))
            return
        }

        val isAdmin = player.hasPermission("systemhomes.admin.pwarp")
        if (existing.owner != player.uniqueId && !isAdmin) {
            player.sendMessage(Lang.component("pwarp.not-owned"))
            return
        }

        if (!PlayerWarpStorage.deletePlayerWarp(sanitized)) {
            player.sendMessage(Lang.component("pwarp.delete-not-found"))
            return
        }

        player.sendMessage(Lang.component("pwarp.delete-success", "name" to sanitized))
    }

    @Suggestions("pwarp-names")
    fun pwarpNameSuggestions(context: CommandContext<CommandSourceStack>, input: String): List<String> {
        if (context.sender().sender !is Player) return emptyList()
        return PlayerWarpStorage.listPlayerWarpNames().filter { it.startsWith(input, ignoreCase = true) }
    }

    private fun teleportToPlayerWarp(player: Player, name: String) {
        val sanitized = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("pwarp.not-found", "name" to name))
            return
        }

        val warp = PlayerWarpStorage.loadPlayerWarp(sanitized) ?: run {
            player.sendMessage(Lang.component("pwarp.not-found", "name" to sanitized))
            return
        }

        val location = warp.toLocation() ?: run {
            player.sendMessage(Lang.component("pwarp.teleport-failed"))
            return
        }

        player.sendMessage(Lang.component("pwarp.teleporting", "name" to sanitized, "delay" to teleportDelay.toString()))
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val onlinePlayer = Bukkit.getPlayer(player.uniqueId) ?: return@Runnable
            if (onlinePlayer.teleport(location)) {
                onlinePlayer.sendMessage(Lang.component("pwarp.teleported", "name" to sanitized))
            } else {
                onlinePlayer.sendMessage(Lang.component("pwarp.teleport-failed"))
            }
        }, teleportDelay * 20L)
    }

    private fun sanitizeName(input: String): String? {
        val trimmed = input.trim().lowercase()
        if (trimmed.length !in 1..16) return null
        if (!trimmed.matches(Regex("^[a-z0-9_-]+$"))) return null
        return trimmed
    }

    private fun friendlyWorldName(world: String): String {
        return when (world.lowercase()) {
            "world" -> Lang.raw("general.world-overworld")
            "world_nether" -> Lang.raw("general.world-nether")
            "world_the_end" -> Lang.raw("general.world-end")
            else -> world
        }
    }
}