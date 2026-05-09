package command

import util.Lang
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.CommandDescription
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.processing.CommandContainer
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import plugin
import util.requirePlayer
import java.util.UUID


@Suppress("unused")
@CommandContainer
class Tpa {
    private val tpaRequests = mutableListOf<TpaRequest>()

    private val enable: Boolean
        get() = plugin.config.tpa.enable

    private val requestExpireTime: Int
        get() = plugin.config.tpa.requestExpireTime

    private val tpaDelay: Int
        get() = plugin.config.tpa.teleportDelay

    @Command("tpa <player>")
    @CommandDescription("Request to teleport to another player.")
    @Permission("systemhomes.cmd.tpa")
    fun tpa(css: CommandSourceStack, @Argument("player") targetPlayer: Player) {
        createRequest(css, targetPlayer, TpaType.TPA_THERE)
    }

    @Command("tpahere <player>")
    @CommandDescription("Request to teleport another player here.")
    @Permission("systemhomes.cmd.tpa")
    fun tpahere(css: CommandSourceStack, @Argument("player") targetPlayer: Player) {
        createRequest(css, targetPlayer, TpaType.TPA_HERE)
    }

    @Command("tpaccept|tpyes [player]")
    @CommandDescription("Accept a tpa request.")
    @Permission("systemhomes.cmd.tpa")
    fun tpaccept(
        css: CommandSourceStack,
        @Argument(value = "player", suggestions = "incoming-tpa-requesters") requesterName: String?
    ) {
        val player = css.requirePlayer() ?: return
        val tpaRequest = resolveIncomingRequest(player, requesterName) ?: return

        val targetPlayer: Player = Bukkit.getPlayer(tpaRequest.requester) ?: run {
            player.sendMessage(Lang.component("tpa.player-offline"))
            tpaRequests.remove(tpaRequest)
            return
        }

        tpaRequests.remove(tpaRequest)

        if (tpaRequest.type == TpaType.TPA_THERE) {
            player.sendMessage(Lang.component("tpa.accepted-tpa-there-accepter",
                "requester" to targetPlayer.name, "delay" to tpaDelay.toString()))
            targetPlayer.sendMessage(Lang.component("tpa.accepted-tpa-there-requester",
                "accepter" to player.name, "delay" to tpaDelay.toString()))
            scheduleDelayedTeleport(targetPlayer.uniqueId, player.uniqueId, player.name, targetPlayer.name)
        } else {
            player.sendMessage(Lang.component("tpa.accepted-tpa-here-accepter",
                "requester" to targetPlayer.name, "delay" to tpaDelay.toString()))
            targetPlayer.sendMessage(Lang.component("tpa.accepted-tpa-here-requester",
                "accepter" to player.name, "delay" to tpaDelay.toString()))
            scheduleDelayedTeleport(player.uniqueId, targetPlayer.uniqueId, targetPlayer.name, player.name)
        }
    }

    @Command("tpdeny|tpno [player]")
    @CommandDescription("Deny a tpa request.")
    @Permission("systemhomes.cmd.tpa")
    fun tpdeny(
        css: CommandSourceStack,
        @Argument(value = "player", suggestions = "incoming-tpa-requesters") requesterName: String?
    ) {
        val player = css.requirePlayer() ?: return
        val tpaRequest = resolveIncomingRequest(player, requesterName) ?: return

        val target = Bukkit.getPlayer(tpaRequest.requester)
        val targetOffline = Bukkit.getOfflinePlayer(tpaRequest.requester)
        tpaRequests.remove(tpaRequest)

        player.sendMessage(Lang.component("tpa.denied-accepter", "requester" to (targetOffline.name ?: tpaRequest.requesterName)))
        target?.sendMessage(Lang.component("tpa.denied-requester", "accepter" to player.name))
    }

    @Command("tpacancel")
    @CommandDescription("Cancel your outgoing TPA requests.")
    @Permission("systemhomes.cmd.tpa")
    fun tpacancel(css: CommandSourceStack) {
        val player = css.requirePlayer() ?: return
        for (request in tpaRequests) {
            if (request.requester == player.uniqueId) {
                Bukkit.getPlayer(request.target)?.sendMessage(
                    Lang.component("tpa.cancelled-target", "requester" to player.name)
                )
            }
        }
        val removed = tpaRequests.removeIf { it.requester == player.uniqueId }
        if (removed) player.sendMessage(Lang.component("tpa.cancelled-all"))
        else player.sendMessage(Lang.component("tpa.no-outgoing"))
    }

    private fun createRequest(css: CommandSourceStack, targetPlayer: Player, type: TpaType) {
        val player = css.requirePlayer() ?: return
        if (player.uniqueId == targetPlayer.uniqueId) {
            player.sendMessage(Lang.component("tpa.self-request"))
            return
        }
        if (hasOutgoingRequestTo(player.uniqueId, targetPlayer.uniqueId)) {
            player.sendMessage(Lang.component("tpa.already-pending"))
            return
        }

        val tpaRequest = TpaRequest(player.uniqueId, targetPlayer.uniqueId, player.name, type)
        tpaRequests.add(tpaRequest)
        deleteTpaAfterDelay(player, targetPlayer, requestExpireTime, tpaRequest)

        player.sendMessage(Lang.component("tpa.request-sent",
            "target" to targetPlayer.name, "timeout" to requestExpireTime.toString()))

        val typeKey = if (type == TpaType.TPA_THERE) "tpa.request-tpa-there" else "tpa.request-tpa-here"
        targetPlayer.sendMessage(Lang.component(typeKey, "requester" to player.name))
        targetPlayer.sendMessage(Lang.component("tpa.request-actions", "requester" to player.name))
    }

    private fun hasOutgoingRequestTo(requesterId: UUID, targetId: UUID): Boolean {
        return tpaRequests.any { it.requester == requesterId && it.target == targetId }
    }

    private fun findIncomingRequest(targetId: UUID, requesterName: String): TpaRequest? {
        return tpaRequests.firstOrNull {
            it.target == targetId && it.requesterName.equals(requesterName, ignoreCase = true)
        }
    }

    private fun findMostRecentIncomingRequest(targetId: UUID): TpaRequest? {
        return tpaRequests.lastOrNull { it.target == targetId }
    }

    private fun resolveIncomingRequest(player: Player, requesterName: String?): TpaRequest? {
        val resolvedName = requesterName?.takeIf { it.isNotBlank() }
            ?: findMostRecentIncomingRequest(player.uniqueId)?.requesterName.orEmpty()

        if (resolvedName.isBlank()) {
            player.sendMessage(Lang.component("tpa.no-incoming"))
            return null
        }

        val request = findIncomingRequest(player.uniqueId, resolvedName)
        if (request == null) {
            player.sendMessage(Lang.component("tpa.no-incoming-from", "requester" to resolvedName))
            return null
        }

        return request
    }

    @Suggestions("incoming-tpa-requesters")
    fun incomingRequesterSuggestions(context: CommandContext<CommandSourceStack>, input: String): List<String> {
        val player = context.sender().sender as? Player ?: return emptyList()
        return tpaRequests
            .asSequence()
            .filter { it.target == player.uniqueId }
            .map { it.requesterName }
            .distinct()
            .filter { it.startsWith(input, ignoreCase = true) }
            .sorted()
            .toList()
    }

    private fun scheduleDelayedTeleport(
        teleportingPlayerId: UUID,
        destinationPlayerId: UUID,
        destinationName: String,
        requesterName: String
    ) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val teleportingPlayer = Bukkit.getPlayer(teleportingPlayerId)
            val destinationPlayer = Bukkit.getPlayer(destinationPlayerId)

            if (teleportingPlayer == null || destinationPlayer == null) {
                teleportingPlayer?.sendMessage(Lang.component("tpa.teleport-cancelled-offline"))
                destinationPlayer?.sendMessage(Lang.component("tpa.teleport-cancelled-offline"))
                return@Runnable
            }

            if (!teleportingPlayer.teleport(destinationPlayer)) {
                teleportingPlayer.sendMessage(Lang.component("tpa.teleport-failed-to", "target" to destinationName))
                destinationPlayer.sendMessage(Lang.component("tpa.teleport-failed-for", "requester" to requesterName))
            }
        }, tpaDelay * 20L)
    }

    private fun deleteTpaAfterDelay(player: Player, target: Player, requestTimeout: Int, tpaRequest: TpaRequest) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (tpaRequests.remove(tpaRequest)) {
                Bukkit.getPlayer(player.uniqueId)?.sendMessage(
                    Lang.component("tpa.timed-out-requester", "target" to target.name))
                Bukkit.getPlayer(target.uniqueId)?.sendMessage(
                    Lang.component("tpa.timed-out-target", "requester" to player.name))
            }
        }, requestTimeout * 20L)
    }

    data class TpaRequest(
        val requester: UUID,
        val target: UUID,
        val requesterName: String,
        val type: TpaType = TpaType.TPA_THERE
    )

    enum class TpaType {
        TPA_THERE,
        TPA_HERE
    }
}