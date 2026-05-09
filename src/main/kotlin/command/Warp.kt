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
import util.WarpStorage
import util.requirePlayer

@Suppress("unused")
@CommandContainer
class Warp {
    private val teleportDelay: Int
        get() = plugin.config.warp.teleportDelay

    @Command("warp <name>")
    @CommandDescription("Teleport to a warp.")
    @Permission("systemhomes.cmd.warp")
    fun warp(css: CommandSourceStack, @Argument(value = "name", suggestions = "warp-names") name: String) {
        val player = css.requirePlayer() ?: return
        teleportToWarp(player, name)
    }

    @Command("spawn")
    @CommandDescription("Teleport to the spawn warp.")
    @Permission("systemhomes.cmd.warp")
    fun spawn(css: CommandSourceStack) {
        val player = css.requirePlayer() ?: return
        teleportToWarp(player, "spawn", spawnCommand = true)
    }

    @Command("warps")
    @CommandDescription("List available warps.")
    @Permission("systemhomes.cmd.warp")
    fun warps(css: CommandSourceStack) {
        val player = css.requirePlayer() ?: return
        val warps = WarpStorage.listWarps()
        if (warps.isEmpty()) {
            player.sendMessage(Lang.component("warp.no-warps"))
            return
        }

        val header = Lang.component("warp.list-header", "count" to warps.size.toString())
        val separator = Component.text("\n")
        val message = warps.foldIndexed(header) { index, acc, warp ->
            val entry = Lang.component(
                "warp.list-entry",
                "name" to warp.name,
                "world" to friendlyWorldName(warp.location.world),
                "x" to warp.location.x.toInt().toString(),
                "y" to warp.location.y.toInt().toString(),
                "z" to warp.location.z.toInt().toString(),
            )
                .clickEvent(ClickEvent.runCommand("/warp ${warp.name}"))
                .hoverEvent(HoverEvent.showText(Lang.component("warp.list-hover", "name" to warp.name)))

            if (index < warps.size - 1) acc.append(entry).append(separator) else acc.append(entry)
        }

        player.sendMessage(message)
    }

    @Command("setwarp <name>")
    @CommandDescription("Set a warp at your current position.")
    @Permission("systemhomes.admin.warp")
    fun setwarp(
        css: CommandSourceStack,
        @Argument("name") name: String,
        @Flag("force", aliases = ["f"]) forced: Boolean = false,
    ) {
        val player = css.requirePlayer() ?: return
        val sanitized = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("warp.needs-name"))
            return
        }

        val exists = WarpStorage.containsWarp(sanitized)
        if (exists && !forced) {
            player.sendMessage(Lang.component("warp.overwrite-confirm", "name" to sanitized))
            return
        }

        if (!WarpStorage.saveWarp(sanitized, player.location.clone())) {
            player.sendMessage(Lang.component("warp.teleport-failed"))
            return
        }

        player.sendMessage(Lang.component("warp.set-success", "name" to sanitized))
    }

    @Command("delwarp <name>")
    @CommandDescription("Delete a warp.")
    @Permission("systemhomes.admin.warp")
    fun delwarp(css: CommandSourceStack, @Argument(value = "name", suggestions = "warp-names") name: String) {
        val player = css.requirePlayer() ?: return
        val sanitized = sanitizeName(name) ?: run {
            player.sendMessage(Lang.component("warp.delete-not-found"))
            return
        }

        if (!WarpStorage.deleteWarp(sanitized)) {
            player.sendMessage(Lang.component("warp.delete-not-found"))
            return
        }

        player.sendMessage(Lang.component("warp.delete-success", "name" to sanitized))
    }

    @Suggestions("warp-names")
    fun warpNameSuggestions(context: CommandContext<CommandSourceStack>, input: String): List<String> {
        if (context.sender().sender !is Player) return emptyList()
        return WarpStorage.listWarpNames().filter { it.startsWith(input, ignoreCase = true) }
    }

    private fun teleportToWarp(player: Player, name: String, spawnCommand: Boolean = false) {
        val sanitized = sanitizeName(name) ?: run {
            player.sendMessage(if (spawnCommand) Lang.component("warp.spawn-missing") else Lang.component("warp.not-found", "name" to name))
            return
        }

        val warp = WarpStorage.loadWarp(sanitized) ?: run {
            player.sendMessage(if (spawnCommand) Lang.component("warp.spawn-missing") else Lang.component("warp.not-found", "name" to sanitized))
            return
        }

        val location = warp.toLocation() ?: run {
            player.sendMessage(Lang.component("warp.teleport-failed"))
            return
        }

        player.sendMessage(Lang.component("warp.teleporting", "name" to sanitized, "delay" to teleportDelay.toString()))
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val onlinePlayer = Bukkit.getPlayer(player.uniqueId) ?: return@Runnable
            if (onlinePlayer.teleport(location)) {
                onlinePlayer.sendMessage(Lang.component("warp.teleported", "name" to sanitized))
            } else {
                onlinePlayer.sendMessage(Lang.component("warp.teleport-failed"))
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

