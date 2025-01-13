package moe.sebiann.system.commands.marriage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.text.DecimalFormat;
import org.bukkit.plugin.java.JavaPlugin;

public class MarryCommand implements CommandExecutor {

    private final MarriageManager marriageManager;
    private final JavaPlugin plugin;

    public MarryCommand(MarriageManager marriageManager, JavaPlugin plugin) {
        this.marriageManager = marriageManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /marry <propose|list|kiss>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "propose":
                handleProposeSubcommand(player, args);
                break;
            case "list":
                handleListSubcommand(player);
                break;
            case "kiss":
                handleKissSubcommand(player);
                break;
            case "divorce":
                handleDivorceSubcommand(player);
                break;
            case "tp":
                handleTpSubcommand(player);
                break;
            default:
                player.sendMessage("§cUnknown subcommand. Use /marry <propose|list|kiss>");
                break;
        }

        return true;
    }

    private void handleKissSubcommand(Player player) {
        UUID spouseUuid = marriageManager.getSpouse(player.getUniqueId());
        if (spouseUuid == null) {
            player.sendMessage("§cYou are not married!");
            return;
        }

        Player spouse = Bukkit.getPlayer(spouseUuid);
        if (spouse == null || !spouse.isOnline()) {
            player.sendMessage("§cYour spouse is not online!");
            return;
        }

        // Spawn particles at the spouse's location
        Location spouseLocation = spouse.getLocation();
        for (int i = 0; i < 20; i++) {
            spouse.getWorld().spawnParticle(Particle.HEART, spouseLocation, 5, 0.5, 0.5, 0.5, 0.1);
        }

        player.sendMessage("§aYou sent a kiss to " + spouse.getName() + "!");
        spouse.sendMessage("§a" + player.getName() + " sent you a kiss!");
    }

    private void handleProposeSubcommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /marry propose <player|accept|deny>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "accept":
                acceptProposal(player);
                break;
            case "deny":
                denyProposal(player);
                break;
            default:
                proposeMarriage(player, args[1]);
                break;
        }
    }

    private void proposeMarriage(Player proposer, String proposedName) {
        Player proposed = Bukkit.getPlayer(proposedName);
        if (proposed == null || !proposed.isOnline()) {
            proposer.sendMessage("§cPlayer not found or not online!");
            return;
        }

        if (proposed.equals(proposer)) {
            proposer.sendMessage("§cYou cannot propose to yourself!");
            return;
        }

        for (Marriage marriage : marriageManager.getAllMarriages()) {
            if (marriage.getP().equals(proposer.getUniqueId().toString()) || marriage.getP2().equals(proposer.getUniqueId().toString())) {
                proposer.sendMessage("§cYou are already married!");
                return;
            }
            if (marriage.getP().equals(proposed.getUniqueId().toString()) || marriage.getP2().equals(proposed.getUniqueId().toString())) {
                proposer.sendMessage("§cThe player is already married!");
                return;
            }
        }

        marriageManager.addProposal(proposer.getUniqueId(), proposed.getUniqueId());

        proposer.sendMessage("§aYou proposed to " + proposed.getName() + "!");
        proposed.sendMessage("§e" + proposer.getName() + " proposed to you! Use /marry propose accept or /marry propose deny.");
    }

    private void acceptProposal(Player proposed) {
        UUID proposerUuid = marriageManager.getProposer(proposed.getUniqueId());
        if (proposerUuid == null) {
            proposed.sendMessage("§cYou have no marriage proposals!");
            return;
        }

        Player proposer = Bukkit.getPlayer(proposerUuid);
        if (proposer == null || !proposer.isOnline()) {
            proposed.sendMessage("§cThe proposer is no longer online.");
            marriageManager.removeProposal(proposed.getUniqueId());
            return;
        }

        Marriage marriage = new Marriage();
        marriage.setP(proposer.getUniqueId().toString());
        marriage.setP2(proposed.getUniqueId().toString());
        marriage.setType("STRAIGHT"); // Example type, can be dynamic
        marriage.setChildren(List.of());
        marriage.setMarriedSince(System.currentTimeMillis());

        marriageManager.addMarriage(marriage);
        marriageManager.removeProposal(proposed.getUniqueId());

        proposer.sendMessage("§a" + proposed.getName() + " accepted your proposal! You're now married!");
        proposed.sendMessage("§aYou accepted " + proposer.getName() + "'s proposal! You're now married!");
    }

    private void denyProposal(Player proposed) {
        UUID proposerUuid = marriageManager.getProposer(proposed.getUniqueId());
        if (proposerUuid == null) {
            proposed.sendMessage("§cYou have no marriage proposals!");
            return;
        }

        Player proposer = Bukkit.getPlayer(proposerUuid);
        if (proposer != null && proposer.isOnline()) {
            proposer.sendMessage("§c" + proposed.getName() + " denied your proposal.");
        }

        marriageManager.removeProposal(proposed.getUniqueId());
        proposed.sendMessage("§aYou denied the proposal.");
    }

    private void handleListSubcommand(Player player) {
        if (marriageManager.getAllMarriages().isEmpty()) {
            player.sendMessage("§cThere are no marriages yet!");
            return;
        }

        DecimalFormat decimalFormat = new DecimalFormat("#.0"); // Format to 1 decimal place

        player.sendMessage("§aCurrent marriages:");
        for (Marriage marriage : marriageManager.getAllMarriages()) {
            // Retrieve player names, handling offline players
            String player1Name = getPlayerName(UUID.fromString(marriage.getP()));
            String player2Name = getPlayerName(UUID.fromString(marriage.getP2()));

            // Calculate married duration
            long currentTime = System.currentTimeMillis();
            long marriedSince = marriage.getMarriedSince();
            double daysMarried = (currentTime - marriedSince) / (1000.0 * 60 * 60 * 24); // Convert to days with decimals

            // Format to 1 decimal place
            String formattedDays = decimalFormat.format(daysMarried);

            player.sendMessage(" - §e" + player1Name + " §7and§e " + player2Name);
            player.sendMessage("§7(married for §b" + formattedDays + " days§7)");
        }
    }
    private String getPlayerName(UUID uuid) {
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName(); // Return the name if the player is online
        }

        // Use Bukkit.getOfflinePlayer for offline players
        return Bukkit.getOfflinePlayer(uuid).getName() != null ? Bukkit.getOfflinePlayer(uuid).getName() : "Unknown";
    }
    private final Map<UUID, Long> divorceRequests = new HashMap<>();

    private void handleDivorceSubcommand(Player player) {
        UUID spouseUuid = marriageManager.getSpouse(player.getUniqueId());
        if (spouseUuid == null) {
            player.sendMessage("§cYou are not married!");
            return;
        }

        if (divorceRequests.containsKey(player.getUniqueId()) &&
                System.currentTimeMillis() - divorceRequests.get(player.getUniqueId()) < 10000) {
            Player spouse = Bukkit.getPlayer(spouseUuid);
            if (spouse != null && spouse.isOnline()) {
                spouse.sendMessage("§c" + player.getName() + " has divorced you.");
            }

            marriageManager.removeMarriage(player.getUniqueId(), spouseUuid);
            divorceRequests.remove(player.getUniqueId());
            player.sendMessage("§aYou have successfully divorced " + (spouse != null ? spouse.getName() : "your spouse") + ".");
        } else {
            divorceRequests.put(player.getUniqueId(), System.currentTimeMillis());
            player.sendMessage("§cType /marry divorce again within 10 seconds to confirm.");
        }
    }
    private void handleTpSubcommand(Player player) {
        UUID spouseUuid = marriageManager.getSpouse(player.getUniqueId());
        if (spouseUuid == null) {
            player.sendMessage("§cYou are not married!");
            return;
        }

        Player spouse = Bukkit.getPlayer(spouseUuid);
        if (spouse == null || !spouse.isOnline()) {
            player.sendMessage("§cYour spouse is not online!");
            return;
        }

        // Fetch delay from config
        int delayInSeconds = plugin.getConfig().getInt("marry.teleport_delay", 2); // Default to 3 seconds
        long delayInTicks = delayInSeconds * 20L;

        player.sendMessage("§eTeleporting to your spouse in " + delayInSeconds + " seconds...");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(spouse.getLocation());
            player.sendMessage("§aYou have been teleported to " + spouse.getName() + "!");
            spouse.sendMessage("§a" + player.getName() + " has teleported to you!");
        }, delayInTicks);
    }
}
