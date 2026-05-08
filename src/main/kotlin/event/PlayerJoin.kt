package event

import util.HomeStorage
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoin : Listener {
    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        HomeStorage.preload(e.player.uniqueId)
    }
}