package util

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player

fun CommandSourceStack.requirePlayer(): Player? {
    return this.sender as? Player ?: run {
        this.sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Only players can use this command.</red>"))
        null
    }
}