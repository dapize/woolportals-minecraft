package com.hakunamatata.woolportals;

import org.bukkit.plugin.java.JavaPlugin;

public final class WoolPortals extends JavaPlugin {

    private PortalManager portalManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        configManager = new ConfigManager(this);
        configManager.load();

        portalManager = new PortalManager(this, configManager);
        portalManager.loadPortals();

        getServer().getPluginManager().registerEvents(new PortalListener(this, portalManager), this);
        getCommand("woolportals").setExecutor(new WoolPortalsCommand(this, portalManager));

        getLogger().info("WoolPortals enabled! " + portalManager.getPortalCount() + " portal pairs loaded.");
    }

    @Override
    public void onDisable() {
        if (portalManager != null) {
            portalManager.savePortals();
        }
        getLogger().info("WoolPortals disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }
}
