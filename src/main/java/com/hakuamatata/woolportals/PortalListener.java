package com.hakuamatata.woolportals;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class PortalListener implements Listener {

    private final WoolPortals plugin;
    private final PortalManager portalManager;

    public PortalListener(WoolPortals plugin, PortalManager portalManager) {
        this.plugin = plugin;
        this.portalManager = portalManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!(event.getBlock().getState() instanceof Sign)) return;

        Sign sign = (Sign) event.getBlock().getState();

        String line0 = event.getLine(0) != null ? event.getLine(0).trim() : "";
        String line1 = event.getLine(1) != null ? event.getLine(1).trim() : "";

        if (!line0.startsWith("@")) return;
        if (line1.isEmpty()) return;

        Player player = event.getPlayer();

        PortalManager.CreateResult result = portalManager.validateAndCreatePortal(sign, player.getName(), line0, line1);

        switch (result.status) {
            case CREATED:
                // message is handled inside validateAndCreatePortal
                return;

            case LINKED:
                return;

            case FRAME_DETECTED:
                player.sendMessage(ChatColor.YELLOW + "¡Marco de portal detectado! " +
                    ChatColor.GRAY + "Coloca un botón dentro del portal para activarlo.");
                return;

            case INVALID_USER:
                player.sendMessage(ChatColor.RED + "La línea 1 debe ser exactamente @" + player.getName());
                return;

            case INVALID_NAME:
                player.sendMessage(ChatColor.RED + "La línea 2 debe tener el nombre del portal.");
                return;

            case DUPLICATE:
                player.sendMessage(ChatColor.RED + "Ya existe un portal con ese nombre y color.");
                return;

            case NO_WOOL:
                player.sendMessage(ChatColor.YELLOW + "Estructura no detectada. " +
                    ChatColor.GRAY + "Construye un marco de lana 3x4 (mismo color), " +
                    "letrero en el centro superior y botón adentro.");
                return;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        if (!clickedBlock.getType().name().contains("BUTTON")) return;

        Portal portal = portalManager.getPortalAtButton(clickedBlock);
        if (portal == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("woolportals.use")) {
            player.sendMessage(ChatColor.RED + "No tienes permiso para usar portales.");
            return;
        }

        Location playerLoc = player.getLocation();
        Location signLoc = null;

        Location signA = portal.getSignLocationA();
        Location signB = portal.getSignLocationB();
        Location btnA = portal.getButtonLocationA();
        Location btnB = portal.getButtonLocationB();

        if (btnA != null && btnA.equals(clickedBlock.getLocation())) {
            signLoc = signA;
        } else if (btnB != null && btnB.equals(clickedBlock.getLocation())) {
            signLoc = signB;
        }

        if (signLoc != null && playerLoc.getWorld().equals(signLoc.getWorld())) {
            double dx = Math.abs(playerLoc.getX() - (signLoc.getX() + 0.5));
            double dy = Math.abs(playerLoc.getY() - (signLoc.getY() - 2.0));
            double dz = Math.abs(playerLoc.getZ() - (signLoc.getZ() + 0.5));

            if (dx > 2.0 || dy > 3.0 || dz > 2.0) {
                player.sendMessage(ChatColor.RED + "Debes estar dentro del portal para usarlo.");
                return;
            }
        }

        portalManager.teleportPlayer(player, portal, clickedBlock);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getState() instanceof Sign) {
            Portal portal = portalManager.removePortalAtSign(block, player);
            if (portal != null) {
                return;
            }
        }

        Material type = block.getType();
        if (type.name().endsWith("_WOOL") || type.name().contains("BUTTON")) {
            for (Portal portal : portalManager.getAllPortals()) {
                if (isBlockInPortal(block, portal)) {
                    if (player.getGameMode() == GameMode.CREATIVE &&
                        player.hasPermission("woolportals.admin")) {
                        return;
                    }
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Este bloque pertenece a un portal. Rompe el letrero para destruirlo.");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isBlockInAnyPortal(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isBlockInAnyPortal);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isBlockInAnyPortal);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isBlockInAnyPortal(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isBlockInAnyPortal(Block block) {
        for (Portal portal : portalManager.getAllPortals()) {
            if (isBlockInPortal(block, portal)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockInPortal(Block block, Portal portal) {
        Location signA = portal.getSignLocationA();
        Location signB = portal.getSignLocationB();

        return isBlockNearPortalSign(block, signA) || isBlockNearPortalSign(block, signB);
    }

    private boolean isBlockNearPortalSign(Block block, Location signLoc) {
        if (signLoc == null) return false;
        if (block.getWorld() != signLoc.getWorld()) return false;

        int dx = Math.abs(block.getX() - signLoc.getBlockX());
        int dy = Math.abs(block.getY() - signLoc.getBlockY());
        int dz = Math.abs(block.getZ() - signLoc.getBlockZ());

        return dx <= 2 && dy <= 4 && dz <= 1;
    }
}
