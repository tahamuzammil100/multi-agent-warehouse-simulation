package fr.emse;

import java.awt.Color;

/**
 * Represents a package that needs to be delivered in the warehouse.
 * Encapsulates all package-related data.
 */
public class DeliveryPackage {

    // Package identification
    private final int packageId;

    // Location information
    private final int[] pickupLocation;  // Where package appears
    private final int targetZone;        // Which delivery zone (1 or 2)

    // Timing information
    private final int arrivalTime;       // When package arrives in simulation

    // Visual representation
    private final Color displayColor;

    /**
     * Creates a new delivery package.
     *
     * @param id Unique package identifier
     * @param pickup Location where package appears [row, col]
     * @param zone Target delivery zone (1 or 2)
     * @param arrival Simulation step when package arrives
     */
    public DeliveryPackage(int id, int[] pickup, int zone, int arrival) {
        this.packageId = id;
        this.pickupLocation = pickup.clone();  // Defensive copy
        this.targetZone = zone;
        this.arrivalTime = arrival;

        // Zone 1 = Green, Zone 2 = Orange
        this.displayColor = (zone == 1) ?
            new Color(60, 180, 80) :
            new Color(220, 120, 40);
    }

    // Getters with clear names
    public int getId() { return packageId; }
    public int[] getPickupLocation() { return pickupLocation.clone(); }
    public int getTargetZone() { return targetZone; }
    public int getArrivalTime() { return arrivalTime; }
    public Color getColor() { return displayColor; }

    @Override
    public String toString() {
        return String.format("Package#%d (Zone %d, arrives at step %d)",
                           packageId, targetZone, arrivalTime);
    }
}
