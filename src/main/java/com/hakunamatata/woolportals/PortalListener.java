package com.hakunamatata.woolportals;

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

        if (!player.hasPermission("woolportals.create")) {
            player.sendMessage(ChatColor.RED + "No tienes permiso para crear portales.");
            event.setCancelled(true);
            return;
        }

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

            case COUNT_EXCEEDED:
                player.sendMessage(ChatColor.RED + "Has alcanzado el limite maximo de portales.");
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
        boolean isB = isPortalSideB(portal, sign);
        PortalSide mySide = isB ? portal.getSideB() : portal.getSideA();
        PortalSide otherSide = isB ? portal.getSideA() : portal.getSideB();

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
        boolean isDisabled = mySide != null && mySide.isDisabled();

        BlockFace currentFacing = getSignFacingSafe(sign);
        if (mySide != null && mySide.getFacing() != currentFacing) {
            mySide.setFacing(currentFacing);
        }

        if (nameChanged) {
            PortalManager.CreateStatus status = portalManager.reassignPortal(portal, mySide, otherSide, line1, player.getName());
            if (status == PortalManager.CreateStatus.DUPLICATE) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Ya existe un enlace activo con ese nombre y color. Rompe uno de los portales existentes para liberar el cupo.");
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Portal renombrado a '" + line1 + "'.");
            return;
        }

        if (isDisabled && mySide != null) {
            mySide.setDisabled(false);

            Bukkit.getScheduler().runTask(plugin,
                () -> {
                    PortalSide sa = portal.getSideA();
                    PortalSide sb = portal.getSideB();
                    boolean on = portal.isComplete();
                    if (sa != null) portalManager.updateSignAtWool(sa.getSignLocation(), on);
                    if (sb != null) portalManager.updateSignAtWool(sb.getSignLocation(), on);
                });

            player.sendMessage(ChatColor.GREEN + "Portal reactivado.");
        }
    }

    private boolean isPortalSideB(Portal portal, Sign sign) {
        Location signWool = getWoolLoc(sign);
        PortalSide sb = portal.getSideB();
        Location locB = sb != null ? sb.getSignLocation() : null;
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

        PortalSide departureSide = findDepartureSide(portal, clickedBlock.getLocation());
        if (!portalManager.isPlayerInsidePortal(player, departureSide)) {
            player.sendMessage(ChatColor.RED + "Debes estar dentro del portal para usarlo.");
            return;
        }

        portalManager.teleportPlayer(player, portal, clickedBlock);
    }

    private PortalSide findDepartureSide(Portal portal, Location buttonLoc) {
        PortalSide sa = portal.getSideA();
        if (sa != null) {
            Location bA = sa.getButtonLocation();
            if (bA != null && bA.equals(buttonLoc)) return sa;
        }
        PortalSide sb = portal.getSideB();
        if (sb != null) {
            Location bB = sb.getButtonLocation();
            if (bB != null && bB.equals(buttonLoc)) return sb;
        }
        return null;
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
                PortalSide sa = portal.getSideA();
                PortalSide sb = portal.getSideB();

                BlockFace faceA = sa != null ? sa.getFacing() : BlockFace.NORTH;
                if (faceA == null) faceA = BlockFace.NORTH;
                BlockFace faceB = sb != null ? sb.getFacing() : BlockFace.NORTH;
                if (faceB == null) faceB = BlockFace.NORTH;

                if (sa != null && isBlockOnPortalPlane(block, sa.getSignLocation(), faceA)) {
                    final Portal p = portal;
                    final PortalSide side = sa;
                    final Player pl = player;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!portalManager.isFrameIntact(side)) {
                            portalManager.disablePortalSide(side, p);
                            pl.sendMessage(ChatColor.RED + "Marco del portal danado. Portal desactivado (OFF).");
                        }
                    });
                    return;
                }
                if (sb != null && isBlockOnPortalPlane(block, sb.getSignLocation(), faceB)) {
                    final Portal p = portal;
                    final PortalSide side = sb;
                    final Player pl = player;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!portalManager.isFrameIntact(side)) {
                            portalManager.disablePortalSide(side, p);
                            pl.sendMessage(ChatColor.RED + "Marco del portal danado. Portal desactivado (OFF).");
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
            handleButtonPlace(block, event.getPlayer());
            return;
        }

        if (block.getType().name().endsWith("_WOOL")) {
            handleWoolPlace(block);
        }
    }

    private void handleButtonPlace(Block block, Player player) {
        if (!player.hasPermission("woolportals.create")) return;

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
            PortalSide sa = portal.getSideA();
            PortalSide sb = portal.getSideB();

            BlockFace faceA = sa != null ? sa.getFacing() : BlockFace.NORTH;
            if (faceA == null) faceA = BlockFace.NORTH;
            BlockFace faceB = sb != null ? sb.getFacing() : BlockFace.NORTH;
            if (faceB == null) faceB = BlockFace.NORTH;

            if (sa != null && sa.isDisabled() && isBlockOnPortalPlane(block, sa.getSignLocation(), faceA)) {
                Location locA = sa.getSignLocation();
                if (portalManager.hasSignNear(locA) && portalManager.isFrameIntact(sa)) {
                    boolean bOk = sb == null || portalManager.isFrameIntact(sb);
                    if (bOk) {
                        sa.setDisabled(false);
                        if (sb != null && sb.isDisabled()) sb.setDisabled(false);
                        syncSigns(portal);
                    }
                    return;
                }
            }

            if (sb != null && sb.isDisabled() && isBlockOnPortalPlane(block, sb.getSignLocation(), faceB)) {
                Location locB = sb.getSignLocation();
                if (portalManager.hasSignNear(locB) && portalManager.isFrameIntact(sb)) {
                    boolean aOk = sa == null || portalManager.isFrameIntact(sa);
                    if (aOk) {
                        sb.setDisabled(false);
                        if (sa != null && sa.isDisabled()) sa.setDisabled(false);
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
                PortalSide sa = portal.getSideA();
                PortalSide sb = portal.getSideB();
                if (sa != null) portalManager.updateSignAtWool(sa.getSignLocation(), on);
                if (sb != null) portalManager.updateSignAtWool(sb.getSignLocation(), on);
            });
    }

}
