package fr.emse;

/**
 * ZoneCoordinates - Central registry for all warehouse spatial zones.
 *
 * Organizes the warehouse layout into functional areas:
 * - Entry points for packages and robots
 * - Intermediate waypoints (safepoints)
 * - Delivery zones
 * - Exit zones
 */
public class ZoneCoordinates {

    // Entry gates where packages arrive (column 19, various rows)
    private final int[][] packageGates;

    // Spawn locations for delivery robots (2 zones)
    private final int[] robotSpawnZone1;
    private final int[] robotSpawnZone2;

    // Intermediate waypoints robots pass through
    private final int[] waypointZone1;
    private final int[] waypointZone2;

    // Target locations for package delivery
    private final int[] targetZone1;
    private final int[] targetZone2;

    // Exit points where robots leave after delivery
    private final int[] exitZone1;
    private final int[] exitZone2;

    /**
     * Constructs the warehouse zone layout with all predefined coordinates.
     */
    public ZoneCoordinates() {
        // Package arrival gates (9 entry points across 3 sections)
        this.packageGates = new int[][]{
            {3, 19}, {4, 19}, {5, 19},  // Section A
            {6, 19}, {7, 19}, {8, 19},  // Section B
            {9, 19}, {10, 19}, {11, 19} // Section C
        };

        // Robot spawn points for each zone
        this.robotSpawnZone1 = new int[]{2, 19};
        this.robotSpawnZone2 = new int[]{12, 19};

        // Intermediate waypoints (safety checkpoints)
        this.waypointZone1 = new int[]{4, 11};
        this.waypointZone2 = new int[]{10, 11};

        // Final delivery targets
        this.targetZone1 = new int[]{2, 2};
        this.targetZone2 = new int[]{13, 2};

        // Exit gates
        this.exitZone1 = new int[]{1, 0};
        this.exitZone2 = new int[]{13, 0};
    }

    /**
     * Gets all package entry gates.
     * @return Array of entry coordinates
     */
    public int[][] getPackageGates() {
        return packageGates;
    }

    /**
     * Gets robot spawn point for a specific zone.
     * @param zoneNumber Zone identifier (1 or 2)
     * @return Spawn coordinates
     */
    public int[] getRobotSpawn(int zoneNumber) {
        return (zoneNumber == 1) ? robotSpawnZone1 : robotSpawnZone2;
    }

    /**
     * Gets waypoint coordinates for a specific zone.
     * @param zoneNumber Zone identifier (1 or 2)
     * @return Waypoint coordinates
     */
    public int[] getWaypoint(int zoneNumber) {
        return (zoneNumber == 1) ? waypointZone1 : waypointZone2;
    }

    /**
     * Gets delivery target coordinates for a specific zone.
     * @param zoneNumber Zone identifier (1 or 2)
     * @return Target coordinates
     */
    public int[] getDeliveryTarget(int zoneNumber) {
        return (zoneNumber == 1) ? targetZone1 : targetZone2;
    }

    /**
     * Gets exit coordinates for a specific zone.
     * @param zoneNumber Zone identifier (1 or 2)
     * @return Exit coordinates
     */
    public int[] getExit(int zoneNumber) {
        return (zoneNumber == 1) ? exitZone1 : exitZone2;
    }
}
