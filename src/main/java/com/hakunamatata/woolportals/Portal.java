package com.hakunamatata.woolportals;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

public class Portal {

    private final String pairId;
    private final String name;
    private final String woolColor;

    private PortalSide sideA;
    private PortalSide sideB;

    public Portal(String name, String woolColor) {
        this.name = name;
        this.woolColor = woolColor;
        this.pairId = name + "_" + woolColor;
    }

    PortalSide getOrCreateA() {
        if (sideA == null) sideA = new PortalSide();
        return sideA;
    }

    PortalSide getOrCreateB() {
        if (sideB == null) sideB = new PortalSide();
        return sideB;
    }

    public PortalSide getSideA() { return sideA; }
    public void setSideA(PortalSide s) { this.sideA = s; }

    public PortalSide getSideB() { return sideB; }
    public void setSideB(PortalSide s) { this.sideB = s; }

    public boolean hasSideA() { return sideA != null; }
    public boolean hasSideB() { return sideB != null; }

    public boolean isComplete() {
        return sideA != null && sideB != null && sideA.isUsable() && sideB.isUsable();
    }

    public boolean isButtonForThisPortal(Location buttonLoc) {
        if (buttonLoc == null) return false;
        Location btnA = sideA != null ? sideA.getButtonLocation() : null;
        Location btnB = sideB != null ? sideB.getButtonLocation() : null;
        return (btnA != null && btnA.equals(buttonLoc)) ||
               (btnB != null && btnB.equals(buttonLoc));
    }

    public String getPairId() { return pairId; }
    public String getName() { return name; }
    public String getWoolColor() { return woolColor; }

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
