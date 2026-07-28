package com.hakuamatata.woolportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortalManager {

    private final WoolPortals plugin;
    private final Map<String, Portal> portals;
    private final File dataFile;

    private static final int COOLDOWN_SECONDS = 3;
    private final Map<UUID, Long> cooldowns;

    public PortalManager(WoolPortals plugin) {
        this.plugin = plugin;
        this.portals = new ConcurrentHashMap<>();
        this.cooldowns = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "portals.yml");

        Bukkit.getScheduler().runTaskTimer(plugin, this::savePortals, 6000L, 6000L);
    }

    public enum CreateStatus {
        CREATED, LINKED, FRAME_DETECTED, INVALID_USER, INVALID_NAME, DUPLICATE, NO_WOOL, REPAIRED
    }

    public static class CreateResult {
        public CreateStatus status;
        public Portal portal;
        public CreateResult(CreateStatus status, Portal portal) {
            this.status = status;
            this.portal = portal;
        }
    }

    public CreateResult validateAndCreatePortal(Sign sign, String playerName, String line0, String line1, String line2) {
        if (sign == null) return new CreateResult(CreateStatus.NO_WOOL, null);

        String rawLine1 = line0 != null ? line0.trim() : "";
        String portalName  = line1 != null ? line1.trim() : "";
        String optionLine  = line2 != null ? line2.trim().toLowerCase() : "";

        boolean isPrivate = optionLine.equals("privado");

        if (!rawLine1.equalsIgnoreCase("#" + playerName)) {
            return new CreateResult(CreateStatus.INVALID_USER, null);
        }
        if (portalName.isEmpty()) {
            return new CreateResult(CreateStatus.INVALID_NAME, null);
        }

        BlockFace signFacing = getSignFacing(sign);
        Block woolBlock = sign.getBlock().getRelative(signFacing.getOppositeFace());

        if (!isWool(woolBlock.getType())) return new CreateResult(CreateStatus.NO_WOOL, null);

        Material woolType = woolBlock.getType();

        List<Block> frameBlocks = tryDetectFrame(woolBlock, signFacing, woolType);
        if (frameBlocks == null) return new CreateResult(CreateStatus.NO_WOOL, null);

        Block buttonBlock = findButtonInside(woolBlock, signFacing);
        if (buttonBlock == null) {
            return new CreateResult(CreateStatus.FRAME_DETECTED, null);
        }

        String woolColor = woolType.name();
        String pairId = portalName + "_" + woolColor;

        Portal existing = portals.get(pairId);

        if (existing == null) {
            Portal portal = new Portal(portalName, woolColor);
            portal.setPortalA(woolBlock.getLocation(), playerName, isPrivate, signFacing);
            portals.put(pairId, portal);

            Bukkit.getScheduler().runTask(plugin, () -> setSignStatus(sign.getBlock(), false));

            return new CreateResult(CreateStatus.CREATED, portal);
        }

        if (existing.isComplete()) {
            return new CreateResult(CreateStatus.DUPLICATE, null);
        }

        if (!existing.hasPortalB()) {
            existing.setPortalB(woolBlock.getLocation(), playerName, isPrivate, signFacing);
            portals.put(pairId, existing);

            Bukkit.getScheduler().runTask(plugin, () -> {
                setSignStatus(sign.getBlock(), true);
                updateOtherSign(existing, true);
            });

            return new CreateResult(CreateStatus.LINKED, existing);
        }

        if (existing.isDisabledA()) {
            existing.setDisabledA(false);
            existing.setPortalA(woolBlock.getLocation(), playerName, isPrivate, signFacing);
            portals.put(pairId, existing);

            Bukkit.getScheduler().runTask(plugin, () -> {
                setSignStatus(sign.getBlock(), true);
                if (existing.isUsableB()) {
                    updateOtherSignA(existing, true);
                }
            });

            return new CreateResult(CreateStatus.REPAIRED, existing);
        }

        if (existing.isDisabledB()) {
            existing.setDisabledB(false);
            existing.setPortalB(woolBlock.getLocation(), playerName, isPrivate, signFacing);
            portals.put(pairId, existing);

            Bukkit.getScheduler().runTask(plugin, () -> {
                setSignStatus(sign.getBlock(), true);
                if (existing.isUsableA()) {
                    updateOtherSign(existing, true);
                }
            });

            return new CreateResult(CreateStatus.REPAIRED, existing);
        }

        return new CreateResult(CreateStatus.DUPLICATE, null);
    }

    private void setSignStatus(Block signBlock, boolean on) {
        if (signBlock.getState() instanceof Sign sign) {
            String color = on ? ChatColor.GREEN.toString() : ChatColor.DARK_GRAY.toString();
            String text = on ? "ON" : "OFF";
            sign.setLine(3, color + text);
            sign.update(true);
        }
    }

    private void updateOtherSign(Portal portal, boolean on) {
        Location otherLoc = portal.getSignLocationA();
        if (otherLoc != null && otherLoc.getWorld() != null) {
            findAndSetSign(otherLoc, on);
        }
    }

    private void updateOtherSignA(Portal portal, boolean on) {
        Location otherLoc = portal.getSignLocationB();
        if (otherLoc != null && otherLoc.getWorld() != null) {
            findAndSetSign(otherLoc, on);
        }
    }

    public Portal getPortalAtButton(Block buttonBlock) {
        for (Portal portal : portals.values()) {
            if (portal.isButtonForThisPortal(buttonBlock.getLocation())) {
                return portal;
            }
        }
        return null;
    }

    public boolean teleportPlayer(org.bukkit.entity.Player player, Portal portal, Block clickedButton) {
        if (!portal.isComplete()) {
            player.sendMessage(ChatColor.RED + "Este portal no está enlazado.");
            return false;
        }

        boolean isPortalA = isPortalAButton(portal, clickedButton.getLocation());
        if (!isFrameIntact(portal, isPortalA)) {
            disablePortal(portal, isPortalA);
            player.sendMessage(ChatColor.RED + "El marco del portal está dañado. Repáralo y edita el letrero para reactivarlo.");
            return false;
        }

        if (isPortalA) {
            if (portal.isPrivateA() && !player.getName().equalsIgnoreCase(portal.getOwnerA())) {
                player.sendMessage(ChatColor.RED + "Este portal es privado. Solo " + portal.getOwnerA() + " puede usarlo.");
                return false;
            }
        } else {
            if (portal.isPrivateB() && !player.getName().equalsIgnoreCase(portal.getOwnerB())) {
                player.sendMessage(ChatColor.RED + "Este portal es privado. Solo " + portal.getOwnerB() + " puede usarlo.");
                return false;
            }
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUsed = cooldowns.get(playerId);
        if (lastUsed != null && (now - lastUsed) < COOLDOWN_SECONDS * 1000) {
            player.sendMessage(ChatColor.RED + "Espera " + COOLDOWN_SECONDS + " segundos entre usos.");
            return false;
        }

        Location target;
        if (isPortalAButton(portal, clickedButton.getLocation())) {
            target = portal.getExitLocation(1);
        } else {
            target = portal.getExitLocation(0);
        }

        if (target == null) {
            player.sendMessage(ChatColor.RED + "El portal de destino no está disponible.");
            return false;
        }

        player.teleport(target);
        cooldowns.put(playerId, now);

        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 50, 0.5, 1.0, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);

        player.sendMessage(ChatColor.LIGHT_PURPLE + "¡Woosh!");

        return true;
    }

    public Portal removePortalAtSign(Block signBlock, org.bukkit.entity.Player destroyer) {
        if (!(signBlock.getState() instanceof Sign)) return null;
        Sign sign = (Sign) signBlock.getState();
        BlockFace signFacing = getSignFacing(sign);
        Block woolBlock = signBlock.getRelative(signFacing.getOppositeFace());

        Portal found = null;
        String foundKey = null;
        boolean isPortalA = false;

        for (Map.Entry<String, Portal> entry : portals.entrySet()) {
            Portal p = entry.getValue();
            Location signA = p.getSignLocationA();
            Location signB = p.getSignLocationB();

            if (signA != null && signA.equals(woolBlock.getLocation())) {
                found = p;
                foundKey = entry.getKey();
                isPortalA = true;
                break;
            }
            if (signB != null && signB.equals(woolBlock.getLocation())) {
                found = p;
                foundKey = entry.getKey();
                isPortalA = false;
                break;
            }
        }

        if (found == null) return null;

        String owner = isPortalA ? found.getOwnerA() : found.getOwnerB();
        if (!destroyer.getName().equals(owner) && !destroyer.hasPermission("woolportals.admin")) {
            destroyer.sendMessage(ChatColor.RED + "Solo " + owner + " puede destruir este portal.");
            return null;
        }

        final Portal portal = found;
        final boolean isA = isPortalA;
        final boolean hasOther = isA ? portal.hasPortalB() : portal.hasPortalA();

        if (!hasOther) {
            portals.remove(foundKey);
            destroyer.sendMessage(ChatColor.GREEN + "Portal '" + portal.getName() + "' eliminado.");
            return portal;
        }

        final Location locA = portal.getSignLocationA();
        final Location locB = portal.getSignLocationB();

        if (isA) {
            portal.setDisabledA(true);
            portal.clearA();
        } else {
            portal.setDisabledB(true);
            portal.clearB();
        }

        if (!portal.hasPortalA() && !portal.hasPortalB()) {
            portals.remove(foundKey);
            destroyer.sendMessage(ChatColor.GREEN + "Portal '" + portal.getName() + "' eliminado completamente.");
            return portal;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isA) {
                findAndSetSign(locA, false);
            } else {
                findAndSetSign(locB, false);
            }
            if (locA != null && locB != null) {
                if (isA && portal.isUsableB()) {
                    findAndSetSign(locB, false);
                } else if (!isA && portal.isUsableA()) {
                    findAndSetSign(locA, false);
                }
            }
        });

        destroyer.sendMessage(ChatColor.GREEN + "Portal '" + portal.getName() + "' destruido.");
        return portal;
    }

    private void findAndSetSign(Location woolLoc, boolean on) {
        if (woolLoc == null || woolLoc.getWorld() == null) return;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            Block candidate = woolLoc.getBlock().getRelative(face);
            if (candidate.getState() instanceof Sign) {
                setSignStatus(candidate, on);
                return;
            }
        }
    }

    private List<Block> tryDetectFrame(Block signBlock, BlockFace facing, Material woolType) {
        BlockFace right = getRightFace(facing);
        BlockFace left = right.getOppositeFace();
        BlockFace down = BlockFace.DOWN;

        List<Block> frame = new ArrayList<>();
        frame.add(signBlock);

        Block topLeft = signBlock.getRelative(left, 1);
        if (topLeft.getType() != woolType) return null;
        frame.add(topLeft);

        Block topRight = signBlock.getRelative(right, 1);
        if (topRight.getType() != woolType) return null;
        frame.add(topRight);

        for (int dy : new int[]{-1, -2}) {
            Block lCol = signBlock.getRelative(left, 1).getRelative(down, -dy);
            if (lCol.getType() != woolType) return null;
            frame.add(lCol);

            Block rCol = signBlock.getRelative(right, 1).getRelative(down, -dy);
            if (rCol.getType() != woolType) return null;
            frame.add(rCol);
        }

        Block bottomCenter = signBlock.getRelative(down, 3);
        if (bottomCenter.getType() != woolType) return null;
        frame.add(bottomCenter);

        Block bottomLeft = signBlock.getRelative(left, 1).getRelative(down, 3);
        if (bottomLeft.getType() != woolType) return null;
        frame.add(bottomLeft);

        Block bottomRight = signBlock.getRelative(right, 1).getRelative(down, 3);
        if (bottomRight.getType() != woolType) return null;
        frame.add(bottomRight);

        for (int dy : new int[]{-1, -2}) {
            Block interior = signBlock.getRelative(down, -dy);
            if (!interior.getType().isAir() && !isButton(interior.getType())) return null;
        }

        return frame;
    }

    private Block findButtonInside(Block signBlock, BlockFace facing) {
        BlockFace right = getRightFace(facing);
        BlockFace left = right.getOppositeFace();

        for (int dy : new int[]{-1, -2}) {
            Block interior = signBlock.getRelative(BlockFace.DOWN, -dy);
            if (isButton(interior.getType())) {
                return interior;
            }
        }

        for (int side : new int[]{-1, 1}) {
            for (int dy : new int[]{-1, -2}) {
                Block column = signBlock.getRelative(side == -1 ? left : right, 1)
                    .getRelative(BlockFace.DOWN, -dy);
                if (isButton(column.getType())) {
                    return column;
                }
            }
        }

        return null;
    }

    private BlockFace getRightFace(BlockFace facing) {
        switch (facing) {
            case NORTH: return BlockFace.EAST;
            case SOUTH: return BlockFace.WEST;
            case EAST:  return BlockFace.SOUTH;
            case WEST:  return BlockFace.NORTH;
            default:    return BlockFace.EAST;
        }
    }

    private BlockFace getSignFacing(Sign sign) {
        BlockData data = sign.getBlock().getBlockData();
        if (data instanceof WallSign) {
            return ((WallSign) data).getFacing();
        }
        return BlockFace.NORTH;
    }

    private boolean isWool(Material mat) {
        return mat.name().endsWith("_WOOL");
    }

    private boolean isButton(Material mat) {
        return mat.name().contains("BUTTON");
    }

    private boolean isPortalAButton(Portal portal, Location buttonLoc) {
        Location btnA = portal.getButtonLocationA();
        return btnA != null && btnA.equals(buttonLoc);
    }

    private boolean isPortalTooClose(Location existing, Location newLoc) {
        if (existing == null || newLoc == null) return false;
        if (!existing.getWorld().equals(newLoc.getWorld())) return false;
        return existing.distance(newLoc) < 5;
    }

    @SuppressWarnings("unchecked")
    public void loadPortals() {
        portals.clear();

        if (!dataFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<Map<String, Object>> portalList = (List<Map<String, Object>>) config.getList("portals");
        if (portalList == null) return;

        for (Map<String, Object> map : portalList) {
            String name = (String) map.get("name");
            String color = (String) map.get("color");

            Portal portal = new Portal(name, color);

            Map<String, Object> a = (Map<String, Object>) map.get("portalA");
            if (a != null) {
                World world = Bukkit.getWorld((String) a.get("world"));
                if (world != null) {
                    int x = (Integer) a.get("x");
                    int y = (Integer) a.get("y");
                    int z = (Integer) a.get("z");
                    boolean priv = a.get("private") instanceof Boolean b && b;
                    boolean dis = a.get("disabled") instanceof Boolean b && b;
                    String facingStr = (String) a.get("facing");
                    portal.setPortalA(new Location(world, x, y, z), (String) a.get("owner"), priv,
                        facingStr != null ? BlockFace.valueOf(facingStr) : BlockFace.NORTH);
                    if (dis) portal.setDisabledA(true);
                }
            }

            Map<String, Object> b = (Map<String, Object>) map.get("portalB");
            if (b != null) {
                World world = Bukkit.getWorld((String) b.get("world"));
                if (world != null) {
                    int x = (Integer) b.get("x");
                    int y = (Integer) b.get("y");
                    int z = (Integer) b.get("z");
                    boolean priv = b.get("private") instanceof Boolean bool && bool;
                    boolean dis = b.get("disabled") instanceof Boolean bool && bool;
                    String facingStr = (String) b.get("facing");
                    portal.setPortalB(new Location(world, x, y, z), (String) b.get("owner"), priv,
                        facingStr != null ? BlockFace.valueOf(facingStr) : BlockFace.NORTH);
                    if (dis) portal.setDisabledB(true);
                }
            }

            portals.put(portal.getPairId(), portal);
        }

        plugin.getLogger().info("Loaded " + portals.size() + " portal pairs from disk.");
    }

    public void savePortals() {
        FileConfiguration config = new YamlConfiguration();
        List<Map<String, Object>> portalList = new ArrayList<>();

        for (Portal portal : portals.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", portal.getName());
            map.put("color", portal.getWoolColor());

            Map<String, Object> a = new LinkedHashMap<>();
            a.put("world", portal.getWorldA());
            a.put("x", portal.getXA());
            a.put("y", portal.getYA());
            a.put("z", portal.getZA());
            a.put("owner", portal.getOwnerA());
            a.put("private", portal.isPrivateA());
            a.put("disabled", portal.isDisabledA());
            if (portal.getFacingA() != null) a.put("facing", portal.getFacingA().name());
            map.put("portalA", a);

            if (portal.hasPortalB()) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("world", portal.getWorldB());
                b.put("x", portal.getXB());
                b.put("y", portal.getYB());
                b.put("z", portal.getZB());
                b.put("owner", portal.getOwnerB());
                b.put("private", portal.isPrivateB());
                b.put("disabled", portal.isDisabledB());
                if (portal.getFacingB() != null) b.put("facing", portal.getFacingB().name());
                map.put("portalB", b);
            }

            portalList.add(map);
        }

        config.set("portals", portalList);

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save portals: " + e.getMessage());
        }
    }

    public int getPortalCount() {
        return portals.size();
    }

    public Collection<Portal> getAllPortals() {
        return Collections.unmodifiableCollection(portals.values());
    }

    public Portal getPortal(String pairId) {
        return portals.get(pairId);
    }

    public Portal findPortalBySignBlock(Block signBlock) {
        if (!(signBlock.getState() instanceof Sign sign)) return null;
        BlockFace signFacing = getSignFacing(sign);
        Block woolBlock = signBlock.getRelative(signFacing.getOppositeFace());
        if (!isWool(woolBlock.getType())) return null;

        for (Portal portal : portals.values()) {
            Location locA = portal.getSignLocationA();
            Location locB = portal.getSignLocationB();
            if (locA != null && locA.equals(woolBlock.getLocation())) return portal;
            if (locB != null && locB.equals(woolBlock.getLocation())) return portal;
        }
        return null;
    }

    public boolean isFrameIntact(Portal portal, boolean portalB) {
        Location woolLoc = portalB ? portal.getSignLocationB() : portal.getSignLocationA();
        if (woolLoc == null || woolLoc.getWorld() == null) return false;
        if (!isWool(woolLoc.getBlock().getType())) return false;

        Material woolType = woolLoc.getBlock().getType();
        BlockFace facing = portalB ? portal.getFacingB() : portal.getFacingA();
        if (facing == null) facing = BlockFace.NORTH;

        return tryDetectFrame(woolLoc.getBlock(), facing, woolType) != null;
    }

    public void disablePortal(Portal portal, boolean portalB) {
        if (portalB) {
            portal.setDisabledB(true);
        } else {
            portal.setDisabledA(true);
        }

        Location locA = portal.getSignLocationA();
        Location locB = portal.getSignLocationB();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (locA != null) findAndSetSign(locA, false);
            if (locB != null) findAndSetSign(locB, false);
        });
    }

    public boolean isPlayerInsidePortal(org.bukkit.entity.Player player, Portal portal, boolean sideB) {
        Location woolLoc = sideB ? portal.getSignLocationB() : portal.getSignLocationA();
        BlockFace facing = sideB ? portal.getFacingB() : portal.getFacingA();
        if (woolLoc == null || facing == null) return false;
        if (!player.getWorld().equals(woolLoc.getWorld())) return false;

        Location pl = player.getLocation();
        double cx = woolLoc.getX() + 0.5;
        double cz = woolLoc.getZ() + 0.5;
        double wy = woolLoc.getY();

        double lateralDist, forwardDist;

        switch (facing) {
            case NORTH: case SOUTH:
                forwardDist = Math.abs(pl.getZ() - cz);
                lateralDist  = Math.abs(pl.getX() - cx);
                break;
            case EAST: case WEST:
                forwardDist = Math.abs(pl.getX() - cx);
                lateralDist  = Math.abs(pl.getZ() - cz);
                break;
            default:
                return false;
        }

        if (forwardDist > 0.9) return false;
        if (lateralDist > 0.75) return false;

        double feetY = pl.getY();
        return feetY >= wy - 4.0 && feetY <= wy;
    }

    public CreateStatus reassignPortal(Portal portal, boolean portalB, String newName, String playerName) {
        String woolColor = portal.getWoolColor();
        String oldPairId = portal.getPairId();
        String newPairId = newName + "_" + woolColor;

        Portal targetPair = portals.get(newPairId);

        if (targetPair != null && targetPair.isComplete()) {
            return CreateStatus.DUPLICATE;
        }

        Location orphanLoc = portalB ? portal.getSignLocationA() : portal.getSignLocationB();

        portals.remove(oldPairId);

        boolean isPrivate = portalB ? portal.isPrivateB() : portal.isPrivateA();
        BlockFace facing = portalB ? portal.getFacingB() : portal.getFacingA();
        Location myLoc = portalB ? portal.getSignLocationB() : portal.getSignLocationA();

        if (orphanLoc != null) {
            final Location orphan = orphanLoc.clone();
            Bukkit.getScheduler().runTask(plugin, () -> findAndSetSign(orphan, false));
        }

        if (targetPair != null) {
            if (targetPair.hasPortalA() && targetPair.isDisabledA()) {
                targetPair.setDisabledA(false);
            }
            if (targetPair.hasPortalB() && targetPair.isDisabledB()) {
                targetPair.setDisabledB(false);
            }

            if (!targetPair.hasPortalA()) {
                targetPair.setPortalA(myLoc, playerName, isPrivate, facing);
            } else if (!targetPair.hasPortalB()) {
                targetPair.setPortalB(myLoc, playerName, isPrivate, facing);
            }

            portals.put(newPairId, targetPair);

            if (targetPair.isComplete()) {
                final Portal tp = targetPair;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    updateOtherSign(tp, true);
                    updateOtherSignA(tp, true);
                });
                return CreateStatus.LINKED;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                Location loc = targetPair.getSignLocationA() != null ? targetPair.getSignLocationA() : targetPair.getSignLocationB();
                if (loc != null) findAndSetSign(loc, false);
            });
            return CreateStatus.CREATED;
        }

        Portal newPortal = new Portal(newName, woolColor);
        newPortal.setPortalA(myLoc, playerName, isPrivate, facing);
        portals.put(newPairId, newPortal);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Location loc = newPortal.getSignLocationA();
            if (loc != null) findAndSetSign(loc, false);
        });
        return CreateStatus.CREATED;
    }
}
