package util

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

fun CommandSourceStack.requirePlayer(): Player? {
    return this.sender as? Player ?: run {
        this.sender.sendMessage(Lang.component("general.players-only"))
        null
    }
}