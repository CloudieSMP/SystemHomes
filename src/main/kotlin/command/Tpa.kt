package command

import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.MiniMessage
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
    private val mm = MiniMessage.miniMessage()
    private val tpaRequests = mutableListOf<TpaRequest>()

    private val requestExpireTime: Int
        get() = plugin.config.tpa.requestExpireTime

    private val tpaDelay: Int
        get() = plugin.config.tpa.tpaDelay

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
            player.sendMessage(mm.deserialize("<yellow>Player is no longer online."))
            tpaRequests.remove(tpaRequest)
            return
        }

        tpaRequests.remove(tpaRequest)

        if (tpaRequest.type == TpaType.TPA_THERE) {
            player.sendMessage(mm.deserialize(
                "<yellow>TPA request from <white>${targetPlayer.name}</white> accepted.\nThey will be teleported in <white>$tpaDelay</white> seconds."
            ))
            targetPlayer.sendMessage(mm.deserialize(
                "<yellow>TPA request to <white>${player.name}</white> accepted.\nYou will be teleported to them in <white>$tpaDelay</white> seconds."
            ))

            scheduleDelayedTeleport(
                teleportingPlayerId = targetPlayer.uniqueId,
                destinationPlayerId = player.uniqueId,
                destinationName = player.name,
                requesterName = targetPlayer.name
            )
        } else {
            player.sendMessage(mm.deserialize(
                "<yellow>TPA request from <white>${targetPlayer.name}</white> accepted.\nYou will be teleported in <white>$tpaDelay</white> seconds."
            ))
            targetPlayer.sendMessage(mm.deserialize(
                "<yellow>TPA request to <white>${player.name}</white> accepted.\nThey will be teleported to you in <white>$tpaDelay</white> seconds."
            ))

            scheduleDelayedTeleport(
                teleportingPlayerId = player.uniqueId,
                destinationPlayerId = targetPlayer.uniqueId,
                destinationName = targetPlayer.name,
                requesterName = player.name
            )
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

        player.sendMessage(mm.deserialize(
            "<red>TPA request from <yellow>${targetOffline.name}</yellow> denied."
        ))

        target?.sendMessage(mm.deserialize(
            "<red>TPA request to <yellow>${player.name}</yellow> denied."
        ))
    }

    @Command("tpacancel")
    @CommandDescription("Request to teleport another player here.")
    @Permission("systemhomes.cmd.tpa")
    fun tpacancel(css: CommandSourceStack) {
        val player = css.requirePlayer() ?: return
        for (request in tpaRequests) {
            if (request.requester == player.uniqueId) {
                val target = Bukkit.getPlayer(request.target)
                target?.sendMessage(mm.deserialize(
                    "<yellow>TPA request from <white>${player.name}</white> has been cancelled."
                ))
            }
        }
        val removal = tpaRequests.removeIf { it.requester == player.uniqueId }
        if (removal) player.sendMessage(mm.deserialize("<yellow>All your outgoing TPA requests have been cancelled."))
        else player.sendMessage(mm.deserialize("<yellow>You don't have any outgoing TPA requests to cancel."))
    }

    private fun createRequest(css: CommandSourceStack, targetPlayer: Player, type: TpaType) {
        val player = css.requirePlayer() ?: return
        if (player.uniqueId == targetPlayer.uniqueId) {
            player.sendMessage(mm.deserialize("<red>You cannot send a TPA request to yourself."))
            return
        }

        if (hasOutgoingRequestTo(player.uniqueId, targetPlayer.uniqueId)) {
            player.sendMessage(mm.deserialize("<red>You already have an outgoing TPA request to this person pending."))
            return
        }

        val tpaRequest = TpaRequest(player.uniqueId, targetPlayer.uniqueId, player.name, type)
        tpaRequests.add(tpaRequest)
        deleteTpaAfterDelay(player, targetPlayer, requestExpireTime, tpaRequest)

        player.sendMessage(mm.deserialize(
            "<yellow>Teleport request sent to <white>${targetPlayer.name}</white>.\nRequest will time out in <white>$requestExpireTime</white> seconds."
        ))
        when (type) {
            TpaType.TPA_THERE -> targetPlayer.sendMessage(mm.deserialize(
                "<yellow><b><white>${player.name}</white></b> is requesting to teleport <b>to you</b>:"
            ))
            TpaType.TPA_HERE -> targetPlayer.sendMessage(mm.deserialize(
                "<yellow><b><white>${player.name}</white></b> is requesting you to teleport <b>to them</b>:"
            ))
        }
        targetPlayer.sendMessage(mm.deserialize(
            "<yellow>Click/Type <click:run_command:'/tpaccept ${player.name}'><hover:show_text:'Accepts the TPA request.'><b><green>/tpaccept ${player.name}</green></b></hover></click> to accept\n" +
                    "Click/Type <click:run_command:'/tpdeny ${player.name}'><hover:show_text:'Denies the TPA request.'><b><red>/tpdeny ${player.name}</red></b></hover></click> to deny."
        ))
    }

    private fun hasOutgoingRequest(requesterId: UUID): Boolean {
        return tpaRequests.any { it.requester == requesterId }
    }

    private fun hasOutgoingRequestTo(requesterId: UUID, targetId: UUID): Boolean {
        return tpaRequests.any { it.requester == requesterId && it.target == targetId }
    }

    private fun findIncomingRequest(targetId: UUID, requesterName: String): TpaRequest? {
        return tpaRequests.firstOrNull {
            it.target == targetId && it.requesterName.equals(requesterName, ignoreCase = true)
        }
    }

    private fun resolveIncomingRequest(player: Player, requesterName: String?): TpaRequest? {
        val resolvedRequesterName = requesterName
            ?.takeIf { it.isNotBlank() }
            ?: findMostRecentIncomingRequest(player.uniqueId)?.requesterName.orEmpty()

        if (resolvedRequesterName.isBlank()) {
            player.sendMessage(mm.deserialize("<yellow>You don't have any incoming TPA requests."))
            return null
        }

        val tpaRequest = findIncomingRequest(player.uniqueId, resolvedRequesterName)
        if (tpaRequest == null) {
            player.sendMessage(mm.deserialize("<yellow>You don't have an incoming TPA request from <white>${resolvedRequesterName}</white>."))
            return null
        }

        return tpaRequest
    }

    private fun findMostRecentIncomingRequest(targetId: UUID): TpaRequest? {
        return tpaRequests.lastOrNull { it.target == targetId }
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
                teleportingPlayer?.sendMessage(mm.deserialize("<red>Teleport cancelled because the other player went offline.</red>"))
                destinationPlayer?.sendMessage(mm.deserialize("<red>Teleport cancelled because the other player went offline.</red>"))
                return@Runnable
            }

            val success = teleportingPlayer.teleport(destinationPlayer)
            if (!success) {
                teleportingPlayer.sendMessage(mm.deserialize("<red>Teleport to <white>$destinationName</white> failed.</red>"))
                destinationPlayer.sendMessage(mm.deserialize("<red>Teleport for <white>$requesterName</white> failed.</red>"))
            }
        }, tpaDelay * 20L)
    }

    private fun deleteTpaAfterDelay(player: Player, target: Player, requestTimeout: Int, tpaRequest: TpaRequest) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (tpaRequests.remove(tpaRequest)) {
                Bukkit.getPlayer(player.uniqueId)?.sendMessage(mm.deserialize("<yellow>TPA request to <white>${target.name}</white> has timed out."))
                Bukkit.getPlayer(target.uniqueId)?.sendMessage(mm.deserialize("<yellow>TPA request from <white>${player.name}</white> has timed out."))
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