package de.simplegamble.gamble;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Eigenständiger /jackpot Befehl - zeigt nur den aktuellen Jackpot-Stand an.
 * (Funktional identisch zu /gamble jackpot, aber als kurzer eigener Befehl.)
 */
public class JackpotCommand implements CommandExecutor {

    private final SimpleGamblePlugin plugin;

    public JackpotCommand(SimpleGamblePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cfg = plugin.getConfig();

        if (!sender.hasPermission("simplegamble.use")) {
            sender.sendMessage(color(cfg.getString("messages.prefix", "") + cfg.getString("messages.no-permission")));
            return true;
        }

        double pool = plugin.getJackpotManager() != null ? plugin.getJackpotManager().getPool() : 0;
        String amount = format(pool);

        String template = cfg.getString("messages.jackpot-info", "&6Aktueller Jackpot: &e%jackpot%");
        String out = template.replace("%jackpot%", amount);

        sender.sendMessage(color(cfg.getString("messages.prefix", "") + out));
        return true;
    }

    private String format(double amount) {
        if (plugin.getEconomy() != null) {
            try {
                return plugin.getEconomy().format(amount);
            } catch (Exception ignored) {
            }
        }
        return String.format("%.2f", amount);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
