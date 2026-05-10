package command

import io.papermc.paper.command.brigadier.CommandSourceStack
import logger
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.processing.CommandContainer
import plugin
import util.Lang

@Suppress("unused", "unstableApiUsage")
@CommandContainer
class Reload {
    @Command("systemhomes reload")
    @Permission("systemhomes.admin.reload")
    fun reloadConfig(css: CommandSourceStack) {
        try {
            plugin.reloadConfig()
            css.sender.sendMessage(Lang.component("reload.success"))
        } catch (e: Exception) {
            css.sender.sendMessage(Lang.component("reload.failed", "error" to (e.message ?: "Unknown error")))
            logger.severe("Error reloading configuration: ${e.message}")
            e.printStackTrace()
        }
    }
}