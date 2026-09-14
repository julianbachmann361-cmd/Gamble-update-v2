package de.simplegamble.gamble;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SimpleGamblePlugin extends JavaPlugin {

    private static SimpleGamblePlugin instance;
    private Economy economy;
    private List<MultiplierEntry> multipliers;
    private JackpotManager jackpotManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Kein Vault-Economy-Provider gefunden! " +
                    "Stelle sicher, dass Vault UND ein Economy-Plugin (z.B. EssentialsX) installiert sind.");
            getLogger().severe("Das Plugin bleibt geladen, aber /gamble wird bis dahin fehlschlagen.");
        }

        loadMultipliers();
        this.jackpotManager = new JackpotManager(this);

        GambleCommand gambleCommand = new GambleCommand(this);
        getCommand("gamble").setExecutor(gambleCommand);
        getCommand("gamble").setTabCompleter(gambleCommand);

        getCommand("jackpot").setExecutor(new JackpotCommand(this));
        getCommand("odds").setExecutor(new OddsCommand(this));

        getLogger().info("SimpleGamble wurde aktiviert. Aktueller Jackpot: " + jackpotManager.getPool());
    }

    @Override
    public void onDisable() {
        if (jackpotManager != null) {
            jackpotManager.save();
        }
        getLogger().info("SimpleGamble wurde deaktiviert.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public void loadMultipliers() {
        reloadConfig();
        FileConfiguration config = getConfig();
        List<MultiplierEntry> list = new ArrayList<>();
        for (Object raw : config.getList("multipliers")) {
            if (raw instanceof java.util.Map<?, ?> map) {
                double value = ((Number) map.get("value")).doubleValue();
                double lowWeight = ((Number) map.get("low-weight")).doubleValue();
                double highWeight = ((Number) map.get("high-weight")).doubleValue();
                list.add(new MultiplierEntry(value, lowWeight, highWeight));
            }
        }
        if (list.isEmpty()) {
            getLogger().warning("Keine Multiplikatoren in der config.yml gefunden! Nutze Standardwerte.");
            list.add(new MultiplierEntry(0.9, 73.363, 44.329));
            list.add(new MultiplierEntry(0.7, 24.454, 54.180));
            list.add(new MultiplierEntry(2.5, 1.276, 0.827));
            list.add(new MultiplierEntry(5.0, 0.491, 0.318));
            list.add(new MultiplierEntry(10.0, 0.196, 0.127));
            list.add(new MultiplierEntry(25.0, 0.15, 0.15));
            list.add(new MultiplierEntry(50.0, 0.05, 0.05));
            list.add(new MultiplierEntry(100.0, 0.02, 0.02));
        }
        this.multipliers = list;

        double lowTotal = list.stream().mapToDouble(MultiplierEntry::getLowWeight).sum();
        double highTotal = list.stream().mapToDouble(MultiplierEntry::getHighWeight).sum();
        double evLow = 0;
        double evHigh = 0;
        for (MultiplierEntry entry : list) {
            evLow += entry.getValue() * (entry.getLowWeight() / lowTotal);
            evHigh += entry.getValue() * (entry.getHighWeight() / highTotal);
        }
        getLogger().info(String.format(
                "Multiplikatoren geladen. RTP bei Mindesteinsatz: %.1f%% | RTP bei Höchsteinsatz: %.1f%% " +
                        "(Durchschnitt ca. %.1f%%)",
                evLow * 100, evHigh * 100, (evLow + evHigh) / 2 * 100));
    }

    /**
     * Berechnet den "Risikofaktor" (0.0 = beste Odds, 1.0 = schlechteste Odds) für einen Einsatz.
     * Bis einschließlich bet-scaling.plateau-until bleiben die Odds konstant auf dem besten Niveau
     * (low-weight Profil). Erst darüber verschlechtern sie sich linear, bis bei bet.max das
     * schlechteste Niveau (high-weight Profil) erreicht ist.
     */
    public double getBetRiskFactor(double bet) {
        double max = getConfig().getDouble("bet.max", 80000);
        double plateau = getConfig().getDouble("bet-scaling.plateau-until", 20000);
        if (bet <= plateau) {
            return 0.0;
        }
        if (max <= plateau) {
            return 1.0;
        }
        double r = (bet - plateau) / (max - plateau);
        return Math.max(0.0, Math.min(1.0, r));
    }

    public List<MultiplierEntry> getMultipliers() {
        return multipliers;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean isEconomyReady() {
        return economy != null;
    }

    public JackpotManager getJackpotManager() {
        return jackpotManager;
    }

    public static SimpleGamblePlugin getInstance() {
        return instance;
    }
}
