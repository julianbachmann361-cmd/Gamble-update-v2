package de.simplegamble.gamble;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Verwaltet den serverweiten Jackpot-Topf persistent in einer eigenen Datei
 * (jackpot-data.yml im Plugin-Datenordner), damit der Stand Server-Neustarts übersteht.
 */
public class JackpotManager {

    private final SimpleGamblePlugin plugin;
    private final File file;
    private double pool;

    public JackpotManager(SimpleGamblePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jackpot-data.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            pool = plugin.getConfig().getDouble("jackpot.starting-pool", 0);
            save();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        pool = yaml.getDouble("pool", plugin.getConfig().getDouble("jackpot.starting-pool", 0));
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("pool", pool);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte jackpot-data.yml nicht speichern: " + e.getMessage());
        }
    }

    public synchronized double getPool() {
        return pool;
    }

    public synchronized void addToPool(double amount) {
        pool += amount;
        save();
    }

    /**
     * Setzt den Topf zurück (nach einem Gewinn) und gibt den Betrag zurück, der ausgezahlt werden soll.
     */
    public synchronized double claimAndReset() {
        double won = pool;
        pool = plugin.getConfig().getDouble("jackpot.starting-pool", 0);
        save();
        return won;
    }
}
