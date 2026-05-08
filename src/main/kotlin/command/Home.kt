package command

import util.HomeStorage
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
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

@Suppress("unused")
@CommandContainer
class Home {
    private val mm = MiniMessage.miniMessage()
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
                onlinePlayer.sendMessage(mm.deserialize("<gray>You have no homes yet. Use <white>/sethome <name></white> to create one."))
                return@listHomeNamesAsync
            }

            val header = Component.text("Your homes ")
                .color(NamedTextColor.WHITE)
                .append(Component.text("(${homes.size}/$maxHomes)", NamedTextColor.DARK_GRAY))
                .append(Component.text(": ", NamedTextColor.WHITE))

            val homeComponents = homes.mapIndexed { index, homeName ->
                val component = Component.text(homeName, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/home $homeName"))
                    .hoverEvent(HoverEvent.showText(
                        Component.text("Click to teleport", NamedTextColor.GREEN)
                    ))

                if (index < homes.size - 1) {
                    component.append(Component.text(", ", NamedTextColor.DARK_GRAY))
                } else {
                    component
                }
            }

            val message = homeComponents.fold(header) { acc, component -> acc.append(component) }
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
            player.sendMessage(mm.deserialize("<red>Home names must be 1-16 chars and use only letters, numbers, _ or -.</red>"))
            return
        }

        HomeStorage.snapshotHomesAsync(playerId) { homes ->
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@snapshotHomesAsync
            val homeCount = homes.size
            val existingHome = homes.containsKey(sanitizedName)
            if (!existingHome && homeCount >= maxHomes) {
                onlinePlayer.sendMessage(mm.deserialize("<red>You reached the home limit <dark_gray>($maxHomes)</dark_gray>. Delete one first with <white>/delhome <name></white>."))
                return@snapshotHomesAsync
            }
            if (existingHome && !forced) {
                onlinePlayer.sendMessage(mm.deserialize("<yellow>Home <white>$sanitizedName</white> already exists.</yellow>\n<white><click:run_command:'/sethome $sanitizedName --force'><hover:show_text:'Confirm overwriting home.'><b>Click Here</b></hover></click></white><yellow> to overwrite home.</yellow>"))
                return@snapshotHomesAsync
            }

            HomeStorage.saveHomeAsync(playerId, sanitizedName, homeLocation) { outcome ->
                val refreshedPlayer = Bukkit.getPlayer(playerId) ?: return@saveHomeAsync
                when (outcome) {
                    HomeStorage.SaveOutcome.CREATED -> refreshedPlayer.sendMessage(mm.deserialize("<green>Home <white>$sanitizedName</white> created."))
                    HomeStorage.SaveOutcome.UPDATED -> refreshedPlayer.sendMessage(mm.deserialize("<yellow>Home <white>$sanitizedName</white> updated."))
                    HomeStorage.SaveOutcome.FAILED -> refreshedPlayer.sendMessage(mm.deserialize("<red>Could not save home right now. Please try again.</red>"))
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
            player.sendMessage(mm.deserialize("<red>Invalid home name.</red>"))
            return
        }

        HomeStorage.loadHomeAsync(playerId, sanitizedName) { home ->
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@loadHomeAsync
            val targetHome = home ?: run {
                onlinePlayer.sendMessage(mm.deserialize("<red>Home <white>$sanitizedName</white> does not exist.</red>"))
                return@loadHomeAsync
            }

            val success = onlinePlayer.teleport(targetHome)
            if (success) {
                onlinePlayer.sendMessage(mm.deserialize("<green>Teleported to <white>$sanitizedName</white>."))
            } else {
                onlinePlayer.sendMessage(mm.deserialize("<red>Teleport failed. The location may be invalid.</red>"))
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
            player.sendMessage(mm.deserialize("<red>Invalid home name.</red>"))
            return
        }
        if (!forced) {
            player.sendMessage(mm.deserialize("<yellow>Are you sure you want to delete <white>$sanitizedName</white>?\n<white><click:run_command:'/delhome $sanitizedName --force'><hover:show_text:'Confirm deleting home.'><b>Click Here</b></hover></click></white><yellow> to delete home.</yellow>"))
            return
        }

        HomeStorage.deleteHomeAsync(playerId, sanitizedName) { deleted ->
            val onlinePlayer = Bukkit.getPlayer(playerId) ?: return@deleteHomeAsync
            if (deleted) {
                onlinePlayer.sendMessage(mm.deserialize("<green>Deleted home <white>$sanitizedName</white>."))
            } else {
                onlinePlayer.sendMessage(mm.deserialize("<red>Home <white>$sanitizedName</white> does not exist.</red>"))
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

    fun CommandSourceStack.requirePlayer(): Player? {
        return this.sender as? Player ?: run {
            this.sender.sendMessage("<red>Only players can use this command.</red>")
            null
        }
    }
}