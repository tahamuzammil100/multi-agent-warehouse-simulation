package fr.emse;

import java.awt.Color;

/**
 * PackageItem - Represents a deliverable package in the warehouse simulation.
 */
public class PackageItem {

    public static final Color ZONE1_COLOR = new Color(60, 180, 80);
    public static final Color ZONE2_COLOR = new Color(220, 120, 40);

    public enum Stage {
        WAITING_AT_GATE,
        STORED_IN_INTERMEDIATE,
        DELIVERED
    }

    public final int packageId;
    public final int[] arrivalPosition;
    public final int targetZone;
    public final Color displayColor;
    public final int spawnStep;

    private Stage stage;
    private int[] intermediateSlot;

    public PackageItem(int id, int[] entryCell, int zone, int arrivalStep) {
        this.packageId = id;
        this.arrivalPosition = entryCell;
        this.targetZone = zone;
        this.displayColor = (zone == 1) ? ZONE1_COLOR : ZONE2_COLOR;
        this.spawnStep = arrivalStep;
        this.stage = Stage.WAITING_AT_GATE;
        this.intermediateSlot = null;
    }

    public Stage getStage() {
        return stage;
    }

    public void setIntermediateSlot(int[] slot) {
        this.intermediateSlot = (slot == null) ? null : slot.clone();
    }

    public int[] getIntermediateSlot() {
        return intermediateSlot == null ? null : intermediateSlot.clone();
    }

    public void markStoredInIntermediate() {
        this.stage = Stage.STORED_IN_INTERMEDIATE;
    }

    public void markDelivered() {
        this.stage = Stage.DELIVERED;
    }

    @Override
    public String toString() {
        return String.format("Package#%d(zone=%d, stage=%s, entry=[%d,%d], arrival=%d)",
                packageId, targetZone, stage, arrivalPosition[0], arrivalPosition[1], spawnStep);
    }
}
