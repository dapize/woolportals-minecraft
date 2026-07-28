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
    }

    public enum CreateStatus {
        CREATED, LINKED, FRAME_DETECTED, INVALID_USER, INVALID_NAME, DUPLICATE, NO_WOOL
    }

    public static class CreateResult {
        public CreateStatus status;
        public Portal portal;
        public CreateResult(CreateStatus status, Portal portal) {
            this.status = status;
            this.portal = portal;
        }
    }

    public CreateResult validateAndCreatePortal(Sign sign, String playerName, String line0, String line1) {
        if (sign == null) return new CreateResult(CreateStatus.NO_WOOL, null);

        String rawLine1 = line0 != null ? line0.trim() : "";
        String portalName = line1 != null ? line1.trim() : "";

        if (!rawLine1.equalsIgnoreCase("@" + playerName)) {
            return new CreateResult(CreateStatus.INVALID_USER, null);
        }
        if (portalName.isEmpty()) {
            return new CreateResult(CreateStatus.INVALID_NAME, null);
        }

        BlockFace signFacing = getSignFacing(sign);
        Block woolBlock = sign.getBlock().getRelative(signFacing.getOppositeFace());

        if (!isWool(woolBlock.getType())) return new CreateResult(CreateStatus.NO_WOOL, null);

        Material woolType = woolBlock.getType();

        List<Block> frameBlocks = detectPortalFrame(woolBlock, signFacing, woolType);
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
            portal.setPortalA(woolBlock.getLocation(), playerName);
            registerPortalBlocks(portal, 0, frameBlocks, buttonBlock);
            portals.put(pairId, portal);

            String msg = ChatColor.GREEN + "¡Portal '" + portalName + "' creado! " +
                         ChatColor.GRAY + "Ahora construye otro igual para enlazarlo.";
            woolBlock.getWorld().getNearbyEntities(woolBlock.getLocation(), 10, 10, 10).stream()
                .filter(e -> e instanceof org.bukkit.entity.Player)
                .forEach(e -> ((org.bukkit.entity.Player) e).sendMessage(msg));

            return new CreateResult(CreateStatus.CREATED, portal);
        }

        if (existing.isComplete()) {
            return new CreateResult(CreateStatus.DUPLICATE, null);
        }

        if (isPortalTooClose(existing.getSignLocationA(), woolBlock.getLocation())) {
            return new CreateResult(CreateStatus.DUPLICATE, null);
        }

        existing.setPortalB(woolBlock.getLocation(), playerName);
        registerPortalBlocks(existing, 1, frameBlocks, buttonBlock);
        portals.put(pairId, existing);

        String msg = ChatColor.GREEN + "¡Portales '" + portalName + "' enlazados! " +
                     ChatColor.LIGHT_PURPLE + "¡Ya puedes teletransportarte!";
        broadcastToPortal(existing, msg);

        return new CreateResult(CreateStatus.LINKED, existing);
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
            player.sendMessage(ChatColor.RED + "Este portal aún no tiene contraparte.");
            return false;
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

        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());

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

        portals.remove(foundKey);

        final String portalName = found.getName();

        if (isPortalA) {
            Location other = found.getSignLocationB();
            if (other != null && other.getWorld() != null) {
                other.getWorld().getNearbyEntities(other, 10, 10, 10).stream()
                    .filter(e -> e instanceof org.bukkit.entity.Player)
                    .forEach(e -> ((org.bukkit.entity.Player) e).sendMessage(
                        ChatColor.RED + "¡El portal '" + portalName + "' ha sido destruido!"));
            }
        } else {
            Location other = found.getSignLocationA();
            if (other != null && other.getWorld() != null) {
                other.getWorld().getNearbyEntities(other, 10, 10, 10).stream()
                    .filter(e -> e instanceof org.bukkit.entity.Player)
                    .forEach(e -> ((org.bukkit.entity.Player) e).sendMessage(
                        ChatColor.RED + "¡El portal '" + portalName + "' ha sido destruido!"));
            }
        }

        destroyer.sendMessage(ChatColor.GREEN + "Portal '" + portalName + "' destruido.");
        return found;
    }

    private List<Block> detectPortalFrame(Block signBlock, BlockFace facing, Material woolType) {
        return tryDetectFrame(signBlock, facing, woolType);
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

    private void registerPortalBlocks(Portal portal, int portalNum, List<Block> frame, Block button) {
    }

    private void broadcastToPortal(Portal portal, String msg) {
        Location locA = portal.getSignLocationA();
        Location locB = portal.getSignLocationB();

        if (locA != null && locA.getWorld() != null) {
            locA.getWorld().getNearbyEntities(locA, 15, 15, 15).stream()
                .filter(e -> e instanceof org.bukkit.entity.Player)
                .forEach(e -> ((org.bukkit.entity.Player) e).sendMessage(msg));
        }

        if (locB != null && locB.getWorld() != null) {
            locB.getWorld().getNearbyEntities(locB, 15, 15, 15).stream()
                .filter(e -> e instanceof org.bukkit.entity.Player)
                .forEach(e -> ((org.bukkit.entity.Player) e).sendMessage(msg));
        }
    }

    @SuppressWarnings("unchecked")
    public void loadPortals() {
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
                    portal.setPortalA(new Location(world, x, y, z), (String) a.get("owner"));
                }
            }

            Map<String, Object> b = (Map<String, Object>) map.get("portalB");
            if (b != null) {
                World world = Bukkit.getWorld((String) b.get("world"));
                if (world != null) {
                    int x = (Integer) b.get("x");
                    int y = (Integer) b.get("y");
                    int z = (Integer) b.get("z");
                    portal.setPortalB(new Location(world, x, y, z), (String) b.get("owner"));
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
            map.put("portalA", a);

            if (portal.hasPortalB()) {
                Map<String, Object> b = new LinkedHashMap<>();
                b.put("world", portal.getWorldB());
                b.put("x", portal.getXB());
                b.put("y", portal.getYB());
                b.put("z", portal.getZB());
                b.put("owner", portal.getOwnerB());
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
}
