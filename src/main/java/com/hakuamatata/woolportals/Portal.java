package com.hakuamatata.woolportals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Portal {

    private final String pairId;
    private final String name;
    private final String woolColor;

    private String worldA;
    private int xA, yA, zA;
    private String ownerA;
    private long createdAtA;

    private String worldB;
    private int xB, yB, zB;
    private String ownerB;
    private long createdAtB;

    private transient Location cachedLocationA;
    private transient Location cachedLocationB;

    public Portal(String name, String woolColor) {
        this.name = name;
        this.woolColor = woolColor;
        this.pairId = name + "_" + woolColor;
    }

    public void setPortalA(Location signLoc, String owner) {
        this.worldA = signLoc.getWorld().getName();
        this.xA = signLoc.getBlockX();
        this.yA = signLoc.getBlockY();
        this.zA = signLoc.getBlockZ();
        this.ownerA = owner;
        this.createdAtA = System.currentTimeMillis();
        this.cachedLocationA = signLoc.clone();
    }

    public void setPortalB(Location signLoc, String owner) {
        this.worldB = signLoc.getWorld().getName();
        this.xB = signLoc.getBlockX();
        this.yB = signLoc.getBlockY();
        this.zB = signLoc.getBlockZ();
        this.ownerB = owner;
        this.createdAtB = System.currentTimeMillis();
        this.cachedLocationB = signLoc.clone();
    }

    public String getPairId() {
        return pairId;
    }

    public String getName() {
        return name;
    }

    public String getWoolColor() {
        return woolColor;
    }

    public Location getSignLocationA() {
        if (cachedLocationA == null && worldA != null) {
            World w = Bukkit.getWorld(worldA);
            if (w != null) {
                cachedLocationA = new Location(w, xA, yA, zA);
            }
        }
        return cachedLocationA;
    }

    public Location getSignLocationB() {
        if (cachedLocationB == null && worldB != null) {
            World w = Bukkit.getWorld(worldB);
            if (w != null) {
                cachedLocationB = new Location(w, xB, yB, zB);
            }
        }
        return cachedLocationB;
    }

    public String getWorldA() { return worldA; }
    public int getXA() { return xA; }
    public int getYA() { return yA; }
    public int getZA() { return zA; }
    public String getOwnerA() { return ownerA; }
    public long getCreatedAtA() { return createdAtA; }

    public String getWorldB() { return worldB; }
    public int getXB() { return xB; }
    public int getYB() { return yB; }
    public int getZB() { return zB; }
    public String getOwnerB() { return ownerB; }
    public long getCreatedAtB() { return createdAtB; }

    public boolean hasPortalA() {
        return worldA != null;
    }

    public boolean hasPortalB() {
        return worldB != null;
    }

    public boolean isComplete() {
        return hasPortalA() && hasPortalB();
    }

    public Location getButtonLocationA() {
        return getButtonLocation(getSignLocationA());
    }

    public Location getButtonLocationB() {
        return getButtonLocation(getSignLocationB());
    }

    private Location getButtonLocation(Location signLoc) {
        if (signLoc == null) return null;

        for (int dx : new int[]{-1, 0, 1}) {
            for (int dz : new int[]{-1, 0, 1}) {
                for (int dy : new int[]{-1, -2}) {
                    Location candidate = new Location(signLoc.getWorld(),
                        signLoc.getBlockX() + dx,
                        signLoc.getBlockY() + dy,
                        signLoc.getBlockZ() + dz);
                    if (candidate.getBlock().getType().name().contains("BUTTON")) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    public boolean isButtonForThisPortal(Location buttonLoc) {
        if (buttonLoc == null) return false;
        Location btnA = getButtonLocationA();
        Location btnB = getButtonLocationB();
        return (btnA != null && btnA.equals(buttonLoc)) ||
               (btnB != null && btnB.equals(buttonLoc));
    }

    public Location getExitLocation(int portalNumber) {
        Location signLoc = portalNumber == 0 ? getSignLocationA() : getSignLocationB();
        if (signLoc == null) return null;

        Location exit = signLoc.clone().add(0.5, -1.5, 0.5);
        exit.setYaw(determineYaw(signLoc));
        exit.setPitch(0);
        return exit;
    }

    private float determineYaw(Location signLoc) {
        return 0f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Portal)) return false;
        return pairId.equals(((Portal) o).pairId);
    }

    @Override
    public int hashCode() {
        return pairId.hashCode();
    }
}
