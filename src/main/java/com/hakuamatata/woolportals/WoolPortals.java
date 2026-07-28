package com.hakuamatata.woolportals;

import org.bukkit.plugin.java.JavaPlugin;

public final class WoolPortals extends JavaPlugin {

    private static WoolPortals instance;
    private PortalManager portalManager;

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        portalManager = new PortalManager(this);
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

    public static WoolPortals getInstance() {
        return instance;
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }
}
