package com.hakuamatata.woolportals;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
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
        String line2 = event.getLine(2) != null ? event.getLine(2).trim() : "";

        Player player = event.getPlayer();

        Portal existing = portalManager.findPortalBySignBlock(sign.getBlock());
        if (existing != null) {
            handleEdit(event, existing, sign, player, line0, line1, line2);
            return;
        }

        if (!line0.startsWith("#")) return;
        if (line1.isEmpty()) return;

        PortalManager.CreateResult result = portalManager.validateAndCreatePortal(
            sign, player.getName(), line0, line1, line2);

        switch (result.status) {
            case CREATED:
            case REPAIRED:
            case LINKED:
                break;

            case FRAME_DETECTED:
                player.sendMessage(ChatColor.YELLOW + "Marco de portal detectado. " +
                    ChatColor.GRAY + "Coloca un boton dentro del portal para activarlo.");
                break;

            case INVALID_USER:
                player.sendMessage(ChatColor.RED + "La linea 1 debe ser exactamente #" + player.getName());
                event.setCancelled(true);
                break;

            case INVALID_NAME:
                player.sendMessage(ChatColor.RED + "La linea 2 debe tener el nombre del portal.");
                event.setCancelled(true);
                break;

            case DUPLICATE:
                player.sendMessage(ChatColor.RED + "Ya existe un portal con ese nombre y color.");
                event.setCancelled(true);
                break;

            case NO_WOOL:
                player.sendMessage(ChatColor.YELLOW + "Estructura no detectada. " +
                    ChatColor.GRAY + "Construye un marco de lana 3x4 (mismo color), " +
                    "letrero en el centro superior y boton adentro.");
                event.setCancelled(true);
                break;
        }
    }

    private void handleEdit(SignChangeEvent event, Portal portal, Sign sign, Player player,
                            String line0, String line1, String line2) {
        boolean isB = isPortalB(portal, sign);

        if (!line0.startsWith("#") || !line0.equalsIgnoreCase("#" + player.getName())) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "No puedes cambiar el dueno del portal.");
            return;
        }

        if (line1.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "El portal debe tener un nombre.");
            return;
        }

        boolean nameChanged = !line1.equalsIgnoreCase(portal.getName());
        boolean isDisabled = isB ? portal.isDisabledB() : portal.isDisabledA();

        BlockFace currentFacing = getSignFacingSafe(sign);
        if (isB) {
            if (portal.getFacingB() != currentFacing) portal.setFacingB(currentFacing);
        } else {
            if (portal.getFacingA() != currentFacing) portal.setFacingA(currentFacing);
        }

        if (nameChanged) {
            PortalManager.CreateStatus status = portalManager.reassignPortal(portal, isB, line1, player.getName());
            if (status == PortalManager.CreateStatus.DUPLICATE) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Ya existe un enlace activo con ese nombre y color. Rompe uno de los portales existentes para liberar el cupo.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Portal renombrado a '" + line1 + "'.");
            return;
        }

        if (isDisabled) {
            if (isB) {
                portal.setDisabledB(false);
            } else {
                portal.setDisabledA(false);
            }

            Bukkit.getScheduler().runTask(plugin,
                () -> {
                    Location locA = portal.getSignLocationA();
                    Location locB = portal.getSignLocationB();
                    if (locA != null) updateSignAt(locA, portal.isUsableA() && portal.isUsableB());
                    if (locB != null) updateSignAt(locB, portal.isUsableA() && portal.isUsableB());
                });

            player.sendMessage(ChatColor.GREEN + "Portal reactivado.");
        }
    }

    private void updateSignAt(Location woolLoc, boolean on) {
        for (BlockFace face : new BlockFace[]{
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            Block candidate = woolLoc.getBlock().getRelative(face);
            if (candidate.getState() instanceof Sign sign) {
                String color = on ? ChatColor.GREEN.toString() : ChatColor.DARK_GRAY.toString();
                String text = on ? "ON" : "OFF";
                sign.setLine(3, color + text);
                sign.update(true);
                return;
            }
        }
    }

    private boolean isPortalB(Portal portal, Sign sign) {
        Location signWool = getWoolLoc(sign);
        Location locB = portal.getSignLocationB();
        return locB != null && signWool != null && locB.equals(signWool);
    }

    private Location getWoolLoc(Sign sign) {
        BlockFace facing = getSignFacingSafe(sign);
        return sign.getBlock().getRelative(facing.getOppositeFace()).getLocation();
    }

    private BlockFace getSignFacingSafe(Sign sign) {
        if (sign.getBlock().getBlockData() instanceof WallSign ws) {
            return ws.getFacing();
        }
        return BlockFace.NORTH;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
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

        Location btnA = portal.getButtonLocationA();
        boolean isSideA = btnA != null && btnA.equals(clickedBlock.getLocation());
        if (!portalManager.isPlayerInsidePortal(player, portal, !isSideA)) {
            player.sendMessage(ChatColor.RED + "Debes estar dentro del portal para usarlo.");
            return;
        }

        portalManager.teleportPlayer(player, portal, clickedBlock);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getState() instanceof Sign) {
            Portal portal = portalManager.removePortalAtSign(block, player);
            if (portal != null) return;
        }

        if (block.getType().name().endsWith("_WOOL")) {
            for (Portal portal : portalManager.getAllPortals()) {
                Location locA = portal.getSignLocationA();
                Location locB = portal.getSignLocationB();
                BlockFace faceA = portal.getFacingA();
                BlockFace faceB = portal.getFacingB();
                if (faceA == null) faceA = BlockFace.NORTH;
                if (faceB == null) faceB = BlockFace.NORTH;

                if (locA != null && isBlockOnPortalPlane(block, locA, faceA)) {
                    final Portal p = portal;
                    final Player pl = player;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!portalManager.isFrameIntact(p, false)) {
                            portalManager.disablePortal(p, false);
                            pl.sendMessage(ChatColor.RED + "Marco del portal dañado. Portal desactivado (OFF).");
                        }
                    });
                    return;
                }
                if (locB != null && isBlockOnPortalPlane(block, locB, faceB)) {
                    final Portal p = portal;
                    final Player pl = player;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!portalManager.isFrameIntact(p, true)) {
                            portalManager.disablePortal(p, true);
                            pl.sendMessage(ChatColor.RED + "Marco del portal dañado. Portal desactivado (OFF).");
                        }
                    });
                    return;
                }
            }
        }
    }


    private boolean isBlockOnPortalPlane(Block block, Location woolLoc, BlockFace facing) {
        if (woolLoc == null || facing == null || block == null) return false;
        if (!block.getWorld().equals(woolLoc.getWorld())) return false;

        int relX = block.getX() - woolLoc.getBlockX();
        int relY = block.getY() - woolLoc.getBlockY();
        int relZ = block.getZ() - woolLoc.getBlockZ();

        switch (facing) {
            case NORTH: case SOUTH:
                if (relZ != 0) return false;
                return Math.abs(relX) <= 1 && Math.abs(relY) <= 3;
            case EAST: case WEST:
                if (relX != 0) return false;
                return Math.abs(relZ) <= 1 && Math.abs(relY) <= 3;
            default:
                return false;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        if (block.getType().name().contains("BUTTON")) {
            handleButtonPlace(block);
            return;
        }

        if (block.getType().name().endsWith("_WOOL")) {
            handleWoolPlace(block);
        }
    }

    private void handleButtonPlace(Block block) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block neighbor = block.getRelative(dx, dy, dz);
                    if (!(neighbor.getState() instanceof Sign sign)) continue;

                    String[] lines = sign.getLines();
                    String line0 = lines[0] != null ? lines[0].trim() : "";
                    String line1 = lines[1] != null ? lines[1].trim() : "";
                    String line2 = lines[2] != null ? lines[2].trim() : "";

                    if (!line0.startsWith("#")) continue;
                    if (line1.isEmpty()) continue;

                    String owner = line0.substring(1);
                    PortalManager.CreateResult result = portalManager.validateAndCreatePortal(
                        sign, owner, line0, line1, line2);

                    if (result.status == PortalManager.CreateStatus.CREATED ||
                        result.status == PortalManager.CreateStatus.LINKED ||
                        result.status == PortalManager.CreateStatus.REPAIRED) {
                        return;
                    }
                }
            }
        }
    }

    private void handleWoolPlace(Block block) {
        for (Portal portal : portalManager.getAllPortals()) {
            Location locA = portal.getSignLocationA();
            Location locB = portal.getSignLocationB();
            BlockFace faceA = portal.getFacingA();
            BlockFace faceB = portal.getFacingB();
            if (faceA == null) faceA = BlockFace.NORTH;
            if (faceB == null) faceB = BlockFace.NORTH;

            if (locA != null && portal.isDisabledA() && isBlockOnPortalPlane(block, locA, faceA)) {
                if (hasSignNear(locA) && portalManager.isFrameIntact(portal, false)) {
                    boolean bOk = !portal.hasPortalB()
                        || portalManager.isFrameIntact(portal, true);
                    if (bOk) {
                        portal.setDisabledA(false);
                        if (portal.hasPortalB() && portal.isDisabledB()) portal.setDisabledB(false);
                        syncSigns(portal);
                    }
                    return;
                }
            }

            if (locB != null && portal.isDisabledB() && isBlockOnPortalPlane(block, locB, faceB)) {
                if (hasSignNear(locB) && portalManager.isFrameIntact(portal, true)) {
                    boolean aOk = !portal.hasPortalA()
                        || portalManager.isFrameIntact(portal, false);
                    if (aOk) {
                        portal.setDisabledB(false);
                        if (portal.hasPortalA() && portal.isDisabledA()) portal.setDisabledA(false);
                        syncSigns(portal);
                    }
                    return;
                }
            }
        }
    }

    private void syncSigns(Portal portal) {
        boolean on = portal.isComplete();
        Bukkit.getScheduler().runTask(plugin,
            () -> {
                if (portal.getSignLocationA() != null) findSignAtLocAndSet(portal.getSignLocationA(), on);
                if (portal.getSignLocationB() != null) findSignAtLocAndSet(portal.getSignLocationB(), on);
            });
    }

    private boolean hasSignNear(Location woolLoc) {
        if (woolLoc == null || woolLoc.getWorld() == null) return false;
        for (BlockFace face : new BlockFace[]{
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            if (woolLoc.getBlock().getRelative(face).getState() instanceof Sign) {
                return true;
            }
        }
        return false;
    }

    private void findSignAtLocAndSet(Location woolLoc, boolean on) {
        if (woolLoc == null || woolLoc.getWorld() == null) return;
        for (BlockFace face : new BlockFace[]{
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            Block candidate = woolLoc.getBlock().getRelative(face);
            if (candidate.getState() instanceof Sign sign) {
                String color = on ? ChatColor.GREEN.toString() : ChatColor.DARK_GRAY.toString();
                String text = on ? "ON" : "OFF";
                sign.setLine(3, color + text);
                sign.update(true);
                return;
            }
        }
    }

}
