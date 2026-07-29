package com.hakunamatata.woolportals;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class ConfigManager {

    private final JavaPlugin plugin;

    private int cooldownSeconds;
    private int autoSaveIntervalTicks;
    private int maxPortalsPerPlayer;
    private Sound teleportSound;
    private boolean soundEnabled;
    private Particle teleportParticle;
    private boolean particleEnabled;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        cooldownSeconds = Math.max(0, config.getInt("cooldown-seconds", 3));
        autoSaveIntervalTicks = Math.max(200, config.getInt("auto-save-interval-ticks", 12000));
        maxPortalsPerPlayer = Math.max(0, config.getInt("max-portals-per-player", 10));

        String soundName = config.getString("teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
        soundEnabled = !soundName.equalsIgnoreCase("none");
        if (soundEnabled) {
            try {
                teleportSound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Sonido invalido en config: " + soundName + ". Usando default.");
                teleportSound = Sound.ENTITY_ENDERMAN_TELEPORT;
            }
        }

        String particleName = config.getString("teleport-particle", "PORTAL");
        particleEnabled = !particleName.equalsIgnoreCase("none");
        if (particleEnabled) {
            try {
                teleportParticle = Particle.valueOf(particleName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Particula invalida en config: " + particleName + ". Usando default.");
                teleportParticle = Particle.PORTAL;
            }
        }
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getAutoSaveIntervalTicks() {
        return autoSaveIntervalTicks;
    }

    public int getMaxPortalsPerPlayer() {
        return maxPortalsPerPlayer;
    }

    public Sound getTeleportSound() {
        return teleportSound;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public Particle getTeleportParticle() {
        return teleportParticle;
    }

    public boolean isParticleEnabled() {
        return particleEnabled;
    }
}
