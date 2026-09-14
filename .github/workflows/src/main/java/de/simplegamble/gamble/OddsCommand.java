package de.simplegamble.gamble;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * /odds - zeigt die aktuell konfigurierten Wahrscheinlichkeiten je Multiplikator,
 * getrennt nach "kleiner Einsatz" (bis zum Plateau) und "Höchsteinsatz", sowie
 * den daraus berechneten Hausvorteil/Spielervorteil (RTP) und die Jackpot-Eckdaten.
 */
public class OddsCommand implements CommandExecutor {

    private final SimpleGamblePlugin plugin;

    public OddsCommand(SimpleGamblePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cfg = plugin.getConfig();

        if (!sender.hasPermission("simplegamble.use")) {
            sender.sendMessage(color(cfg.getString("messages.prefix", "") + cfg.getString("messages.no-permission")));
            return true;
        }

        List<MultiplierEntry> entries = plugin.getMultipliers();
        double plateau = cfg.getDouble("bet-scaling.plateau-until", 20000);
        double max = cfg.getDouble("bet.max", 80000);

        double totalLow = entries.stream().mapToDouble(MultiplierEntry::getLowWeight).sum();
        double totalHigh = entries.stream().mapToDouble(MultiplierEntry::getHighWeight).sum();

        double evLow = 0;
        double evHigh = 0;
        for (MultiplierEntry e : entries) {
            evLow += e.getValue() * (e.getLowWeight() / totalLow);
            evHigh += e.getValue() * (e.getHighWeight() / totalHigh);
        }

        String prefix = cfg.getString("messages.prefix", "");

        sender.sendMessage(color(prefix + "&6&l--- Gamble-Odds ---"));
        sender.sendMessage(color(String.format(Locale.GERMANY,
                "&7Einsätze bis &f%s&7: &aRTP %.1f%% &7(Spielervorteil-Anteil) &7/ &cHausvorteil %.1f%%",
                formatNumber(plateau), evLow * 100, (1 - evLow) * 100)));
        sender.sendMessage(color(String.format(Locale.GERMANY,
                "&7Höchsteinsatz &f%s&7: &aRTP %.1f%% &7/ &cHausvorteil %.1f%%",
                formatNumber(max), evHigh * 100, (1 - evHigh) * 100)));
        sender.sendMessage(color(String.format(Locale.GERMANY,
                "&7Durchschnitt über alle Einsätze: &c~%.1f%% Hausvorteil",
                100 - (evLow + evHigh) / 2 * 100)));

        sender.sendMessage(color("&6Multiplikator  &7| &6Chance (klein)  &7| &6Chance (max)"));
        for (MultiplierEntry e : entries) {
            double pLow = e.getLowWeight() / totalLow * 100;
            double pHigh = e.getHighWeight() / totalHigh * 100;
            sender.sendMessage(color(String.format(Locale.GERMANY,
                    "&fx%-6s &7| &e%7.3f%% &7| &e%7.3f%%",
                    formatNumber(e.getValue()), pLow, pHigh)));
        }

        if (cfg.getBoolean("jackpot.enabled", true)) {
            double contribution = cfg.getDouble("jackpot.contribution-percent", 10.0);
            double chance = cfg.getDouble("jackpot.win-chance-percent", 0.05);
            double pool = plugin.getJackpotManager() != null ? plugin.getJackpotManager().getPool() : 0;
            sender.sendMessage(color(String.format(Locale.GERMANY,
                    "&6Jackpot: &7%.1f%% jedes Einsatzes fließt rein, &7%.3f%% Chance pro /gamble ihn zu knacken",
                    contribution, chance)));
            sender.sendMessage(color("&6Aktueller Jackpot-Topf: &e" + format(pool)));
        }

        return true;
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String format(double amount) {
        if (plugin.getEconomy() != null) {
            try {
                return plugin.getEconomy().format(amount);
            } catch (Exception ignored) {
            }
        }
        return String.format(Locale.GERMANY, "%.2f", amount);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
