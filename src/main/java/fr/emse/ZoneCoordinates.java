package fr.emse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.ini4j.Ini;

/**
 * ZoneCoordinates - Loads and manages warehouse spatial zones from configuration.
 *
 * Reads zone layout from configuration.ini file:
 * - Package entry gates
 * - Robot spawn locations
 * - Intermediate waypoints
 * - Delivery targets
 * - Exit zones
 *
 * This makes the warehouse layout fully configurable without code changes.
 */
public class ZoneCoordinates {

    private final int[][] packageGates;
    private final int[] robotSpawnZone1;
    private final int[] robotSpawnZone2;
    private final int[] waypointZone1;
    private final int[] waypointZone2;
    private final int[] targetZone1;
    private final int[] targetZone2;
    private final int[] exitZone1;
    private final int[] exitZone2;

    /**
     * Loads all zone coordinates from configuration file.
     *
     * @param configPath Path to configuration.ini file
     */
    public ZoneCoordinates(String configPath) {
        try {
            Ini config = new Ini(new File(configPath));

            // Load package gates
            this.packageGates = loadPackageGates(config);

            // Load robot spawn points
            this.robotSpawnZone1 = parseCoordinate(config.get("zones", "robot_spawn_zone1"));
            this.robotSpawnZone2 = parseCoordinate(config.get("zones", "robot_spawn_zone2"));

            // Load waypoints
            this.waypointZone1 = parseCoordinate(config.get("zones", "waypoint_zone1"));
            this.waypointZone2 = parseCoordinate(config.get("zones", "waypoint_zone2"));

            // Load delivery targets
            this.targetZone1 = parseCoordinate(config.get("zones", "target_zone1"));
            this.targetZone2 = parseCoordinate(config.get("zones", "target_zone2"));

            // Load exit points
            this.exitZone1 = parseCoordinate(config.get("zones", "exit_zone1"));
            this.exitZone2 = parseCoordinate(config.get("zones", "exit_zone2"));

            System.out.println("Loaded zone configuration from " + configPath);
            System.out.println("  Package gates: " + packageGates.length);
            System.out.println("  Robot spawns: 2 zones");
            System.out.println("  Waypoints: 2 zones");
            System.out.println("  Delivery targets: 2 zones");

        } catch (Exception e) {
            throw new RuntimeException("Failed to load zone configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Loads all package entry gates from configuration.
     * Reads package_gate1, package_gate2, etc. until no more are found.
     */
    private int[][] loadPackageGates(Ini config) {
        List<int[]> gates = new ArrayList<>();
        int index = 1;

        while (true) {
            String key = "package_gate" + index;
            String value = config.get("zones", key);

            if (value == null || value.trim().isEmpty()) {
                break;
            }

            gates.add(parseCoordinate(value));
            index++;
        }

        if (gates.isEmpty()) {
            throw new RuntimeException("No package gates defined in configuration");
        }

        return gates.toArray(new int[0][]);
    }

    /**
     * Parses a coordinate string in "row,column" format.
     *
     * @param coordinateString String to parse (e.g., "3,19")
     * @return Coordinate array [row, col]
     */
    private int[] parseCoordinate(String coordinateString) {
        if (coordinateString == null || coordinateString.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing coordinate in configuration");
        }

        try {
            String[] parts = coordinateString.trim().split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid coordinate format: " + coordinateString);
            }

            int row = Integer.parseInt(parts[0].trim());
            int col = Integer.parseInt(parts[1].trim());
            return new int[]{row, col};

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid coordinate format: " + coordinateString, e);
        }
    }

    // Getters

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
