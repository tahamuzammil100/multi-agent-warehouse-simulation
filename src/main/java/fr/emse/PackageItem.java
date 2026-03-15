package fr.emse;

import java.awt.Color;

/**
 * PackageItem - Represents a deliverable package in the warehouse simulation.
 *
 * Each package contains the following information:
 *   - Unique identifier for tracking purposes
 *   - Entry position where the package appears on the warehouse grid
 *   - Target delivery zone (determines which robot handles it)
 *   - Visual color indicator matching its delivery zone
 *   - Simulation step when this package becomes available for pickup
 *
 * The package color coding system:
 *   - Green packages are delivered to zone 1 (handled by robots from entry point 1)
 *   - Orange packages are delivered to zone 2 (handled by robots from entry point 2)
 */
public class PackageItem {

    /** Color for zone 1 packages (green) - delivered to bottom-left delivery area. */
    public static final Color ZONE1_COLOR = new Color(60, 180, 80);

    /** Color for zone 2 packages (orange) - delivered to top-left delivery area. */
    public static final Color ZONE2_COLOR = new Color(220, 120, 40);

    public final int packageId;
    public final int[] arrivalPosition;    // Grid coordinates [row, col] where package appears
    public final int targetZone;           // 1 = bottom-left delivery, 2 = top-left delivery
    public final Color displayColor;       // Visual color for this package on the grid
    public final int spawnStep;            // Simulation step when package becomes available

    /**
     * Creates a new package item for warehouse delivery.
     *
     * @param id Unique identifier for this package
     * @param entryCell Grid position [row, col] where package appears
     * @param zone Delivery zone number (1 or 2)
     * @param arrivalStep Simulation step when this package arrives
     */
    public PackageItem(int id, int[] entryCell, int zone, int arrivalStep) {
        this.packageId        = id;
        this.arrivalPosition  = entryCell;
        this.targetZone       = zone;
        this.displayColor     = (zone == 1) ? ZONE1_COLOR : ZONE2_COLOR;
        this.spawnStep        = arrivalStep;
    }

    @Override
    public String toString() {
        return String.format("Package#%d(zone=%d, entry=[%d,%d], arrival=%d)",
                packageId, targetZone, arrivalPosition[0], arrivalPosition[1], spawnStep);
    }
}
