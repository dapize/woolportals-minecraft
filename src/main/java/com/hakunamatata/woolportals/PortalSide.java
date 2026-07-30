package com.hakunamatata.woolportals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.LinkedHashMap;
import java.util.Map;

public class PortalSide {

    private String worldName;
    private int x, y, z;
    private String ownerName;
    private long createdAt;
    private boolean disabled;
    private transient BlockFace facing;
    private transient Location cachedLocation;

    PortalSide() {}

    void init(Location woolLoc, String owner, BlockFace facing) {
        this.worldName = woolLoc.getWorld().getName();
        this.x = woolLoc.getBlockX();
        this.y = woolLoc.getBlockY();
        this.z = woolLoc.getBlockZ();
        this.ownerName = owner;
        this.facing = facing;
        this.createdAt = System.currentTimeMillis();
        this.cachedLocation = woolLoc.clone();
    }

    public Location getSignLocation() {
        if (cachedLocation == null && worldName != null) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                cachedLocation = new Location(w, x, y, z);
            }
        }
        return cachedLocation;
    }

    public Location getButtonLocation() {
        Location signLoc = getSignLocation();
        if (signLoc == null || facing == null) return null;

        int wx = signLoc.getBlockX();
        int wy = signLoc.getBlockY();
        int wz = signLoc.getBlockZ();
        boolean northSouth = facing == BlockFace.NORTH || facing == BlockFace.SOUTH;

        for (int dy : new int[]{-1, -2}) {
            for (int lateral : new int[]{-1, 0, 1}) {
                int x = wx + (northSouth ? lateral : 0);
                int z = wz + (northSouth ? 0 : lateral);
                Location candidate = new Location(signLoc.getWorld(), x, wy + dy, z);
                if (candidate.getBlock().getType().name().contains("BUTTON")) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public Location getExitLocation() {
        Location signLoc = getSignLocation();
        if (signLoc == null) return null;

        Location exit = signLoc.clone().add(0.5, -2.0, 0.5);
        exit.setYaw(yawFromFacing(facing));
        exit.setPitch(0);
        return exit;
    }

    public boolean isUsable() {
        return worldName != null && !disabled;
    }

    void clear() {
        this.worldName = null;
        this.facing = null;
        this.cachedLocation = null;
        this.ownerName = null;
    }

    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getOwnerName() { return ownerName; }
    public BlockFace getFacing() { return facing; }
    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean d) { this.disabled = d; }
    public long getCreatedAt() { return createdAt; }
    void setFacing(BlockFace f) { this.facing = f; }

    static float yawFromFacing(BlockFace facing) {
        if (facing == null) return 0f;
        switch (facing) {
            case NORTH: return 180f;
            case SOUTH: return 0f;
            case EAST:  return 270f;
            case WEST:  return 90f;
            default:    return 0f;
        }
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", worldName);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("owner", ownerName);
        map.put("disabled", disabled);
        if (facing != null) map.put("facing", facing.name());
        return map;
    }

    static PortalSide fromMap(Map<String, Object> map) {
        PortalSide side = new PortalSide();
        side.worldName = (String) map.get("world");
        side.x = toInt(map.get("x"));
        side.y = toInt(map.get("y"));
        side.z = toInt(map.get("z"));
        side.ownerName = (String) map.get("owner");
        side.disabled = map.get("disabled") instanceof Boolean b && b;
        side.createdAt = System.currentTimeMillis();
        side.facing = parseFacing((String) map.get("facing"));

        if (side.worldName != null) {
            World w = Bukkit.getWorld(side.worldName);
            if (w != null) {
                side.cachedLocation = new Location(w, side.x, side.y, side.z);
            }
        }
        return side;
    }

    private static int toInt(Object obj) {
        if (obj instanceof Number) return ((Number) obj).intValue();
        return 0;
    }

    private static BlockFace parseFacing(String facingStr) {
        if (facingStr == null) return BlockFace.NORTH;
        try {
            return BlockFace.valueOf(facingStr);
        } catch (IllegalArgumentException e) {
            return BlockFace.NORTH;
        }
    }
}
