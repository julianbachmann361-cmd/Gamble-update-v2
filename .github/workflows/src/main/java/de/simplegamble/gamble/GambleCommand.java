package de.simplegamble.gamble;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GambleCommand implements CommandExecutor, TabCompleter {

    private final SimpleGamblePlugin plugin;
    private final Set<UUID> currentlyGambling = new HashSet<>();
    private final Map<UUID, Long> lastGambleAt = new HashMap<>();

    public GambleCommand(SimpleGamblePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration cfg = plugin.getConfig();

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl nutzen.");
            return true;
        }

        if (!player.hasPermission("simplegamble.use")) {
            msg(player, cfg.getString("messages.no-permission"));
            return true;
        }

        // /gamble jackpot -> zeigt nur den aktuellen Topf an
        if (args.length == 1 && args[0].equalsIgnoreCase("jackpot")) {
            String out = cfg.getString("messages.jackpot-info", "&6Aktueller Jackpot: &e%jackpot%")
                    .replace("%jackpot%", format(plugin.getJackpotManager().getPool()));
            msg(player, out);
            return true;
        }

        if (!plugin.isEconomyReady()) {
            msg(player, cfg.getString("messages.economy-missing"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(color("&cNutzung: /gamble <betrag>  oder  /gamble jackpot"));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[0].replace(",", "."));
        } catch (NumberFormatException e) {
            msg(player, cfg.getString("messages.invalid-amount"));
            return true;
        }

        if (!cfg.getBoolean("bet.allow-decimal", false)) {
            amount = Math.floor(amount);
        }

        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            msg(player, cfg.getString("messages.invalid-amount"));
            return true;
        }

        double min = cfg.getDouble("bet.min", 10);
        double max = cfg.getDouble("bet.max", 80000);

        if (amount < min) {
            msg(player, cfg.getString("messages.min-bet").replace("%min%", format(min)));
            return true;
        }
        if (amount > max) {
            msg(player, cfg.getString("messages.max-bet").replace("%max%", format(max)));
            return true;
        }

        UUID uuid = player.getUniqueId();

        long cooldownSeconds = cfg.getLong("cooldown-seconds", 3);
        long now = System.currentTimeMillis();
        Long last = lastGambleAt.get(uuid);
        if (last != null) {
            long secondsLeft = cooldownSeconds - ((now - last) / 1000);
            if (secondsLeft > 0) {
                msg(player, cfg.getString("messages.cooldown").replace("%seconds%", String.valueOf(secondsLeft)));
                return true;
            }
        }

        if (currentlyGambling.contains(uuid)) {
            msg(player, cfg.getString("messages.already-gambling"));
            return true;
        }

        Economy economy = plugin.getEconomy();

        if (!economy.has(player, amount)) {
            msg(player, cfg.getString("messages.not-enough-money"));
            return true;
        }

        EconomyResponse withdrawResponse = economy.withdrawPlayer(player, amount);
        if (!withdrawResponse.transactionSuccess()) {
            msg(player, cfg.getString("messages.not-enough-money"));
            return true;
        }

        lastGambleAt.put(uuid, now);
        currentlyGambling.add(uuid);

        // Jackpot-Beitrag: X% des Einsatzes wandert IMMER (Gewinn oder Verlust) in den Topf
        if (cfg.getBoolean("jackpot.enabled", true)) {
            double contributionPercent = cfg.getDouble("jackpot.contribution-percent", 5.0);
            double contribution = round2(amount * (contributionPercent / 100.0));
            plugin.getJackpotManager().addToPool(contribution);
        }

        // Ergebnis VORHER auswürfeln (fair & nicht manipulierbar während der Animation)
        MultiplierEntry result = rollWeightedResult(amount);

        // Unabhängige Jackpot-Chance, komplett getrennt vom Multiplikator-Ergebnis
        boolean jackpotHit = false;
        double jackpotAmount = 0;
        if (cfg.getBoolean("jackpot.enabled", true)) {
            double chance = cfg.getDouble("jackpot.win-chance-percent", 0.05) / 100.0;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                jackpotHit = true;
                jackpotAmount = plugin.getJackpotManager().claimAndReset();
            }
        }

        startAnimation(player, amount, result, jackpotHit, jackpotAmount);

        return true;
    }

    private MultiplierEntry rollWeightedResult(double bet) {
        List<MultiplierEntry> entries = plugin.getMultipliers();
        double r = plugin.getBetRiskFactor(bet);

        double totalWeight = 0;
        for (MultiplierEntry entry : entries) {
            totalWeight += entry.getWeightAt(r);
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (MultiplierEntry entry : entries) {
            cumulative += entry.getWeightAt(r);
            if (roll <= cumulative) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private void startAnimation(Player player, double bet, MultiplierEntry finalResult,
                                 boolean jackpotHit, double jackpotAmount) {
        FileConfiguration cfg = plugin.getConfig();
        long durationMs = cfg.getLong("animation.duration-seconds", 10) * 1000L;
        long startIntervalMs = cfg.getLong("animation.start-interval-ms", 100);
        long endIntervalMs = cfg.getLong("animation.end-interval-ms", 700);
        String displayMode = cfg.getString("animation.display", "ACTIONBAR");
        List<MultiplierEntry> entries = plugin.getMultipliers();

        UUID uuid = player.getUniqueId();

        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finishGamble(player, bet, finalResult, displayMode, true, jackpotHit, jackpotAmount);
                    cancel();
                    currentlyGambling.remove(uuid);
                    return;
                }

                if (elapsed >= durationMs) {
                    finishGamble(player, bet, finalResult, displayMode, false, jackpotHit, jackpotAmount);
                    currentlyGambling.remove(uuid);
                    cancel();
                    return;
                }

                MultiplierEntry flash = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
                showAnimationFrame(player, flash, displayMode);
                playSound(player, cfg.getString("animation.sound-tick"));

                double progress = Math.min(1.0, (double) elapsed / durationMs);
                long currentInterval = (long) (startIntervalMs + (endIntervalMs - startIntervalMs) * progress);
                elapsed += currentInterval;

                long delayTicks = Math.max(1, currentInterval / 50);
                Bukkit.getScheduler().runTaskLater(plugin, this::run, delayTicks);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void showAnimationFrame(Player player, MultiplierEntry entry, String displayMode) {
        String text = color("&e&l🎰 &fx" + trimNumber(entry.getValue()) + " &e&l🎰");
        if ("TITLE".equalsIgnoreCase(displayMode)) {
            player.sendTitle(text, "", 0, 15, 5);
        } else {
            player.sendActionBar(toComponent(text));
        }
    }

    private void finishGamble(Player player, double bet, MultiplierEntry result, String displayMode,
                               boolean wasOffline, boolean jackpotHit, double jackpotAmount) {
        FileConfiguration cfg = plugin.getConfig();
        Economy economy = plugin.getEconomy();

        double winnings = round2(bet * result.getValue());
        economy.depositPlayer(player, winnings);

        if (jackpotHit && jackpotAmount > 0) {
            economy.depositPlayer(player, jackpotAmount);
        }

        boolean isWin = result.getValue() >= 1.0;

        if (!wasOffline) {
            String finalText = color("&f&lx" + trimNumber(result.getValue()) + (isWin ? " &a&l✔" : " &c&l✘"));
            if ("TITLE".equalsIgnoreCase(displayMode)) {
                player.sendTitle(finalText, color(isWin ? "&aGewonnen!" : "&cVerloren!"), 0, 40, 10);
            } else {
                player.sendActionBar(toComponent(finalText));
            }
            playSound(player, cfg.getString(isWin ? "animation.sound-win" : "animation.sound-lose"));

            String template = cfg.getString(isWin ? "messages.result-win" : "messages.result-loss");
            String out = template
                    .replace("%multiplier%", trimNumber(result.getValue()))
                    .replace("%amount%", format(winnings))
                    .replace("%bet%", format(bet));
            msg(player, out);

            if (jackpotHit && jackpotAmount > 0) {
                playSound(player, cfg.getString("animation.sound-jackpot"));
                String jackpotMsg = cfg.getString("messages.jackpot-win")
                        .replace("%jackpot%", format(jackpotAmount));
                msg(player, jackpotMsg);
            }
        }

        if (jackpotHit && jackpotAmount > 0) {
            String broadcastTemplate = cfg.getString("messages.jackpot-broadcast");
            String broadcastMsg = color(broadcastTemplate
                    .replace("%player%", player.getName())
                    .replace("%jackpot%", format(jackpotAmount)));
            Bukkit.broadcastMessage(broadcastMsg);
        } else if (cfg.getBoolean("broadcast.enabled", true) && winnings >= cfg.getDouble("broadcast.min-win-amount", 5000)) {
            String broadcastTemplate = cfg.getString("messages.broadcast-win");
            String broadcastMsg = color(broadcastTemplate
                    .replace("%player%", player.getName())
                    .replace("%multiplier%", trimNumber(result.getValue()))
                    .replace("%amount%", format(winnings)));
            Bukkit.broadcastMessage(broadcastMsg);
        }
    }

    private void playSound(Player player, String soundName) {
        if (soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String trimNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private String format(double amount) {
        Economy economy = plugin.getEconomy();
        if (economy != null) {
            try {
                return economy.format(amount);
            } catch (Exception ignored) {
            }
        }
        return String.format("%.2f", amount);
    }

    private void msg(Player player, String raw) {
        if (raw == null) return;
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        player.sendMessage(color(prefix + raw));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private Component toComponent(String legacyColored) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyColored);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("jackpot");
            if (sender instanceof Player player) {
                Economy economy = plugin.getEconomy();
                if (economy != null) {
                    double balance = economy.getBalance(player);
                    suggestions.add(String.valueOf((long) Math.min(balance, 100)));
                    suggestions.add(String.valueOf((long) (balance / 2)));
                    suggestions.add(String.valueOf((long) balance));
                }
            }
            return suggestions;
        }
        return List.of();
    }
}
