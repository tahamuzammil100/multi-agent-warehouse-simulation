package fr.emse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.ini4j.Ini;

/**
 * ZoneCoordinates - Loads and manages warehouse spatial zones from configuration.
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
    private final int[] intermediateAreaZone1;
    private final int[] intermediateAreaZone2;
    private final int[] rechargeArea;
    private final double intermediateCapacityRatio;

    public ZoneCoordinates(String configPath) {
        try {
            Ini config = new Ini(new File(configPath));

            this.packageGates = loadPackageGates(config);
            this.robotSpawnZone1 = parseCoordinate(config.get("zones", "robot_spawn_zone1"));
            this.robotSpawnZone2 = parseCoordinate(config.get("zones", "robot_spawn_zone2"));
            this.waypointZone1 = parseCoordinate(config.get("zones", "waypoint_zone1"));
            this.waypointZone2 = parseCoordinate(config.get("zones", "waypoint_zone2"));
            this.targetZone1 = parseCoordinate(config.get("zones", "target_zone1"));
            this.targetZone2 = parseCoordinate(config.get("zones", "target_zone2"));
            this.exitZone1 = parseCoordinate(config.get("zones", "exit_zone1"));
            this.exitZone2 = parseCoordinate(config.get("zones", "exit_zone2"));

            this.intermediateAreaZone1 = parseArea(config.get("zones", "intermediate_area_zone1"), waypointZone1);
            this.intermediateAreaZone2 = parseArea(config.get("zones", "intermediate_area_zone2"), waypointZone2);
            this.rechargeArea = parseArea(config.get("zones", "recharge_area"), robotSpawnZone1);

            String ratioValue = config.get("warehouse", "intermediate_capacity_ratio");
            this.intermediateCapacityRatio = (ratioValue == null || ratioValue.trim().isEmpty())
                    ? 0.5
                    : Double.parseDouble(ratioValue.trim());

            System.out.println("Loaded zone configuration from " + configPath);
            System.out.println("  Package gates: " + packageGates.length);
            System.out.println("  Robot spawns: 2 zones");
            System.out.println("  Waypoints: 2 zones");
            System.out.println("  Delivery targets: 2 zones");

        } catch (Exception e) {
            throw new RuntimeException("Failed to load zone configuration: " + e.getMessage(), e);
        }
    }

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

    private int[] parseArea(String areaString, int[] fallbackCenter) {
        if (areaString == null || areaString.trim().isEmpty()) {
            int row = fallbackCenter[0];
            int col = fallbackCenter[1];
            return new int[]{row, col, row, col};
        }

        String[] parts = areaString.trim().split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid area format: " + areaString);
        }

        int minRow = Integer.parseInt(parts[0].trim());
        int minCol = Integer.parseInt(parts[1].trim());
        int maxRow = Integer.parseInt(parts[2].trim());
        int maxCol = Integer.parseInt(parts[3].trim());
        return new int[]{minRow, minCol, maxRow, maxCol};
    }

    public int[][] getPackageGates() {
        return packageGates;
    }

    public int[] getRobotSpawn(int zoneNumber) {
        return (zoneNumber == 1) ? robotSpawnZone1 : robotSpawnZone2;
    }

    public int[] getWaypoint(int zoneNumber) {
        return (zoneNumber == 1) ? waypointZone1 : waypointZone2;
    }

    public int[] getDeliveryTarget(int zoneNumber) {
        return (zoneNumber == 1) ? targetZone1 : targetZone2;
    }

    public int[] getExit(int zoneNumber) {
        return (zoneNumber == 1) ? exitZone1 : exitZone2;
    }

    public int[] getIntermediateArea(int zoneNumber) {
        int[] area = (zoneNumber == 1) ? intermediateAreaZone1 : intermediateAreaZone2;
        return area.clone();
    }

    public int[] getRechargeArea() {
        return rechargeArea.clone();
    }

    public double getIntermediateCapacityRatio() {
        return intermediateCapacityRatio;
    }
}
