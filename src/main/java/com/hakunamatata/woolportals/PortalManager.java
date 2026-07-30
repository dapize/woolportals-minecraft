package com.hakunamatata.woolportals;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortalManager {

    private final WoolPortals plugin;
    private final ConfigManager config;
    private final Map<String, Portal> portals;
    private final File dataFile;

    private final Map<UUID, Long> cooldowns;

    public PortalManager(WoolPortals plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.portals = new ConcurrentHashMap<>();
        this.cooldowns = new ConcurrentHashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "portals.yml");

        Bukkit.getScheduler().runTaskTimer(plugin, this::savePortals,
            config.getAutoSaveIntervalTicks(), config.getAutoSaveIntervalTicks());
    }

    public enum CreateStatus {
        CREATED, LINKED, FRAME_DETECTED, INVALID_USER, INVALID_NAME, DUPLICATE, NO_WOOL, REPAIRED, COUNT_EXCEEDED
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
            int maxPortals = config.getMaxPortalsPerPlayer();
            if (maxPortals > 0 && countPortalsOwnedBy(playerName) >= maxPortals) {
                return new CreateResult(CreateStatus.COUNT_EXCEEDED, null);
            }

            Portal portal = new Portal(portalName, woolColor);
            portal.getOrCreateA().init(woolBlock.getLocation(), playerName, signFacing);
            portals.put(pairId, portal);

            Bukkit.getScheduler().runTask(plugin, () -> setSignStatus(sign.getBlock(), false));

            return new CreateResult(CreateStatus.CREATED, portal);
        }

        if (existing.isComplete()) {
            return new CreateResult(CreateStatus.DUPLICATE, null);
        }

        if (!existing.hasSideB()) {
            existing.getOrCreateB().init(woolBlock.getLocation(), playerName, signFacing);
            portals.put(pairId, existing);

            Bukkit.getScheduler().runTask(plugin, () -> {
                setSignStatus(sign.getBlock(), true);
                PortalSide sa = existing.getSideA();
                if (sa != null) updateSignAtWool(sa.getSignLocation(), true);
            });

            return new CreateResult(CreateStatus.LINKED, existing);
        }

        PortalSide sideA = existing.getSideA();
        if (sideA != null && sideA.isDisabled()) {
            sideA.setDisabled(false);
            sideA.init(woolBlock.getLocation(), playerName, signFacing);
            portals.put(pairId, existing);

            Bukkit.getScheduler().runTask(plugin, () -> {
                setSignStatus(sign.getBlock(), true);
                PortalSide sb = existing.getSideB();
                if (sb != null && sb.isUsable()) {
                    updateSignAtWool(sb.getSignLocation(), true);
                }
            });

            return new CreateResult(CreateStatus.REPAIRED, existing);
        }

        PortalSide sideB = existing.getSideB();
        if (sideB != null && sideB.isDisabled()) {
            sideB.setDisabled(false);
            sideB.init(woolBlock.getLocation(), playerName, signFacing);
            portals.put(pairId, existing);

            Bukkit.getScheduler().runTask(plugin, () -> {
                setSignStatus(sign.getBlock(), true);
                PortalSide sa = existing.getSideA();
                if (sa != null && sa.isUsable()) {
                    updateSignAtWool(sa.getSignLocation(), true);
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
            player.sendMessage(ChatColor.RED + "Este portal no esta enlazado.");
            return false;
        }

        PortalSide departureSide = whichSideForButton(portal, clickedButton.getLocation());
        if (departureSide == null) {
            player.sendMessage(ChatColor.RED + "El portal no esta disponible.");
            return false;
        }

        if (!isFrameIntact(departureSide)) {
            disablePortalSide(departureSide, portal);
            player.sendMessage(ChatColor.RED + "El marco del portal esta danado. Reparalo y edita el letrero para reactivarlo.");
            return false;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        int cooldownSecs = config.getCooldownSeconds();
        if (cooldownSecs > 0) {
            Long lastUsed = cooldowns.get(playerId);
            if (lastUsed != null && (now - lastUsed) < cooldownSecs * 1000L) {
                player.sendMessage(ChatColor.RED + "Espera " + cooldownSecs + " segundos entre usos.");
                return false;
            }
        }

        PortalSide destinationSide = (departureSide == portal.getSideA()) ? portal.getSideB() : portal.getSideA();
        Location target = destinationSide != null ? destinationSide.getExitLocation() : null;

        if (target == null) {
            player.sendMessage(ChatColor.RED + "El portal de destino no esta disponible.");
            return false;
        }

        player.teleport(target);
        cooldowns.put(playerId, now);

        if (config.isParticleEnabled()) {
            player.getWorld().spawnParticle(config.getTeleportParticle(), player.getLocation(), 50, 0.5, 1.0, 0.5, 0.1);
        }
        if (config.isSoundEnabled()) {
            player.getWorld().playSound(player.getLocation(), config.getTeleportSound(), 0.5f, 1.0f);
        }

        player.sendMessage(ChatColor.LIGHT_PURPLE + "Woosh!");

        return true;
    }

    private PortalSide whichSideForButton(Portal portal, Location buttonLoc) {
        PortalSide sa = portal.getSideA();
        if (sa != null) {
            Location btnA = sa.getButtonLocation();
            if (btnA != null && btnA.equals(buttonLoc)) return sa;
        }
        PortalSide sb = portal.getSideB();
        if (sb != null) {
            Location btnB = sb.getButtonLocation();
            if (btnB != null && btnB.equals(buttonLoc)) return sb;
        }
        return null;
    }

    public Portal removePortalAtSign(Block signBlock, org.bukkit.entity.Player destroyer) {
        if (!(signBlock.getState() instanceof Sign)) return null;
        Sign sign = (Sign) signBlock.getState();
        BlockFace signFacing = getSignFacing(sign);
        Block woolBlock = signBlock.getRelative(signFacing.getOppositeFace());

        Portal found = null;
        String foundKey = null;
        boolean isSideA = false;

        for (Map.Entry<String, Portal> entry : portals.entrySet()) {
            Portal p = entry.getValue();
            PortalSide sa = p.getSideA();
            PortalSide sb = p.getSideB();

            Location locA = sa != null ? sa.getSignLocation() : null;
            if (locA != null && locA.equals(woolBlock.getLocation())) {
                found = p;
                foundKey = entry.getKey();
                isSideA = true;
                break;
            }
            Location locB = sb != null ? sb.getSignLocation() : null;
            if (locB != null && locB.equals(woolBlock.getLocation())) {
                found = p;
                foundKey = entry.getKey();
                isSideA = false;
                break;
            }
        }

        if (found == null) return null;

        final Portal portal = found;
        final PortalSide matchedSide = isSideA ? portal.getSideA() : portal.getSideB();
        final PortalSide otherSide = isSideA ? portal.getSideB() : portal.getSideA();
        final boolean hasOther = otherSide != null;

        if (!hasOther) {
            portals.remove(foundKey);
            destroyer.sendMessage(ChatColor.GREEN + "Portal '" + portal.getName() + "' eliminado.");
            return portal;
        }

        final Location matchedLoc = matchedSide != null ? matchedSide.getSignLocation() : null;
        final Location otherLoc = otherSide != null ? otherSide.getSignLocation() : null;

        if (matchedSide != null) {
            matchedSide.setDisabled(true);
            matchedSide.clear();
        }

        if (!portal.hasSideA() && !portal.hasSideB()) {
            portals.remove(foundKey);
            destroyer.sendMessage(ChatColor.GREEN + "Portal '" + portal.getName() + "' eliminado completamente.");
            return portal;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            updateSignAtWool(matchedLoc, false);
            if (matchedLoc != null && otherLoc != null) {
                updateSignAtWool(otherLoc, false);
            }
        });

        destroyer.sendMessage(ChatColor.GREEN + "Portal '" + portal.getName() + "' destruido.");
        return portal;
    }

    private Sign findSignNear(Location woolLoc) {
        if (woolLoc == null || woolLoc.getWorld() == null) return null;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP}) {
            Block candidate = woolLoc.getBlock().getRelative(face);
            if (candidate.getState() instanceof Sign sign) {
                return sign;
            }
        }
        return null;
    }

    public boolean hasSignNear(Location woolLoc) {
        return findSignNear(woolLoc) != null;
    }

    public void updateSignAtWool(Location woolLoc, boolean on) {
        Sign sign = findSignNear(woolLoc);
        if (sign != null) {
            setSignStatus(sign.getBlock(), on);
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

    private int countPortalsOwnedBy(String playerName) {
        int count = 0;
        for (Portal portal : portals.values()) {
            PortalSide sa = portal.getSideA();
            PortalSide sb = portal.getSideB();
            if ((sa != null && playerName.equalsIgnoreCase(sa.getOwnerName()))
                    || (sb != null && playerName.equalsIgnoreCase(sb.getOwnerName()))) {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    public void loadPortals() {
        portals.clear();

        if (!dataFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        Object rawList = config.get("portals");
        if (rawList == null) return;

        if (!(rawList instanceof List<?>)) {
            plugin.getLogger().warning("portals.yml: 'portals' key is not a list, skipping portal loading.");
            return;
        }

        List<?> portalList = (List<?>) rawList;
        int skipped = 0;

        for (Object item : portalList) {
            if (!(item instanceof Map<?, ?>)) {
                skipped++;
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;

            try {
                String name = (String) map.get("name");
                String color = (String) map.get("color");

                if (name == null || color == null) {
                    skipped++;
                    continue;
                }

                Portal portal = new Portal(name, color);

                Map<String, Object> a = (Map<String, Object>) map.get("portalA");
                if (a != null) {
                    PortalSide sideA = PortalSide.fromMap(a);
                    if (sideA.getWorldName() != null) {
                        portal.setSideA(sideA);
                    }
                }

                Map<String, Object> b = (Map<String, Object>) map.get("portalB");
                if (b != null) {
                    PortalSide sideB = PortalSide.fromMap(b);
                    if (sideB.getWorldName() != null) {
                        portal.setSideB(sideB);
                    }
                }

                portals.put(portal.getPairId(), portal);
            } catch (Exception e) {
                skipped++;
                plugin.getLogger().warning("Failed to load portal entry from portals.yml, skipping: " + e.getMessage());
            }
        }

        if (skipped > 0) {
            plugin.getLogger().warning("Skipped " + skipped + " malformed portal entries from portals.yml.");
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

            PortalSide sideA = portal.getSideA();
            if (sideA != null) {
                map.put("portalA", sideA.toMap());
            }

            PortalSide sideB = portal.getSideB();
            if (sideB != null) {
                map.put("portalB", sideB.toMap());
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
            PortalSide sa = portal.getSideA();
            PortalSide sb = portal.getSideB();
            Location locA = sa != null ? sa.getSignLocation() : null;
            Location locB = sb != null ? sb.getSignLocation() : null;
            if (locA != null && locA.equals(woolBlock.getLocation())) return portal;
            if (locB != null && locB.equals(woolBlock.getLocation())) return portal;
        }
        return null;
    }

    public boolean isFrameIntact(PortalSide side) {
        if (side == null) return false;
        Location woolLoc = side.getSignLocation();
        if (woolLoc == null || woolLoc.getWorld() == null) return false;
        if (!isWool(woolLoc.getBlock().getType())) return false;

        Material woolType = woolLoc.getBlock().getType();
        BlockFace facing = side.getFacing();
        if (facing == null) facing = BlockFace.NORTH;

        return tryDetectFrame(woolLoc.getBlock(), facing, woolType) != null;
    }

    public void disablePortalSide(PortalSide side, Portal portal) {
        if (side == null) return;
        side.setDisabled(true);

        PortalSide sa = portal.getSideA();
        PortalSide sb = portal.getSideB();

        Location locA = sa != null ? sa.getSignLocation() : null;
        Location locB = sb != null ? sb.getSignLocation() : null;

        Bukkit.getScheduler().runTask(plugin, () -> {
            updateSignAtWool(locA, false);
            updateSignAtWool(locB, false);
        });
    }

    public boolean isPlayerInsidePortal(org.bukkit.entity.Player player, PortalSide side) {
        if (side == null) return false;
        Location woolLoc = side.getSignLocation();
        BlockFace facing = side.getFacing();
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

    public CreateStatus reassignPortal(Portal portal, PortalSide mySide, PortalSide orphanSide, String newName, String playerName) {
        String woolColor = portal.getWoolColor();
        String oldPairId = portal.getPairId();
        String newPairId = newName + "_" + woolColor;

        Portal targetPair = portals.get(newPairId);

        if (targetPair != null && targetPair.isComplete()) {
            return CreateStatus.DUPLICATE;
        }

        Location orphanLoc = orphanSide != null ? orphanSide.getSignLocation() : null;

        portals.remove(oldPairId);

        BlockFace facing = mySide != null ? mySide.getFacing() : null;
        Location myLoc = mySide != null ? mySide.getSignLocation() : null;

        if (orphanLoc != null) {
            final Location orphan = orphanLoc.clone();
            Bukkit.getScheduler().runTask(plugin, () -> updateSignAtWool(orphan, false));
        }

        if (targetPair != null) {
            PortalSide tsa = targetPair.getSideA();
            PortalSide tsb = targetPair.getSideB();

            if (tsa != null && tsa.isDisabled()) {
                tsa.setDisabled(false);
            }
            if (tsb != null && tsb.isDisabled()) {
                tsb.setDisabled(false);
            }

            if (targetPair.getSideA() == null) {
                targetPair.getOrCreateA().init(myLoc, playerName, facing);
            } else if (targetPair.getSideB() == null) {
                targetPair.getOrCreateB().init(myLoc, playerName, facing);
            }

            portals.put(newPairId, targetPair);

            if (targetPair.isComplete()) {
                final Portal tp = targetPair;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    PortalSide ta = tp.getSideA();
                    PortalSide tb = tp.getSideB();
                    if (ta != null) updateSignAtWool(ta.getSignLocation(), true);
                    if (tb != null) updateSignAtWool(tb.getSignLocation(), true);
                });
                return CreateStatus.LINKED;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                PortalSide ta = targetPair.getSideA();
                PortalSide tb = targetPair.getSideB();
                Location loc = ta != null ? ta.getSignLocation() : (tb != null ? tb.getSignLocation() : null);
                if (loc != null) updateSignAtWool(loc, false);
            });
            return CreateStatus.CREATED;
        }

        Portal newPortal = new Portal(newName, woolColor);
        newPortal.getOrCreateA().init(myLoc, playerName, facing);
        portals.put(newPairId, newPortal);

        Bukkit.getScheduler().runTask(plugin, () -> {
            PortalSide nsa = newPortal.getSideA();
            if (nsa != null) updateSignAtWool(nsa.getSignLocation(), false);
        });
        return CreateStatus.CREATED;
    }
}
