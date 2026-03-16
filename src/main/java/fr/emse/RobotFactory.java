package fr.emse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * RobotFactory - Handles creation and lifecycle management of delivery robots.
 */
public class RobotFactory {

    private final ColorGridEnvironment environment;
    private final ZoneCoordinates zones;
    private final int fieldOfView;
    private final int debugLevel;
    private final int gridRows;
    private final int gridColumns;
    private final Color robotColor;
    private final int maxRobots;
    private final int batteryAutonomy;
    private final int rechargeTime;
    private final int chargeThreshold;

    private final List<DeliveryBot> activeFleet;
    private int nextRobotId;

    public RobotFactory(ColorGridEnvironment env, ZoneCoordinates zones,
                       int fieldOfView, int debug, int rows, int cols, Color color,
                       int maxRobots, int batteryAutonomy, int rechargeTime,
                       int chargeThreshold) {
        this.environment = env;
        this.zones = zones;
        this.fieldOfView = fieldOfView;
        this.debugLevel = debug;
        this.gridRows = rows;
        this.gridColumns = cols;
        this.robotColor = color;
        this.maxRobots = Math.max(1, maxRobots);
        this.batteryAutonomy = Math.max(1, batteryAutonomy);
        this.rechargeTime = Math.max(1, rechargeTime);
        this.chargeThreshold = Math.max(0, chargeThreshold);
        this.activeFleet = new ArrayList<>();
        this.nextRobotId = 0;
    }

    public DeliveryBot trySpawnRobot(PackageItem pkg, int[] pickup, int[] dropoff) {
        int zoneNumber = pkg.targetZone;

        // Reuse an idle robot first (persistent AMR behavior).
        for (DeliveryBot robot : activeFleet) {
            if (robot.isIdle()) {
                robot.assignMission(pkg, pickup, dropoff);
                return robot;
            }
        }

        int[] spawnPoint = zones.getRobotSpawn(zoneNumber);
        if (activeFleet.size() >= maxRobots) {
            return null;
        }
        if (!isLocationAvailable(spawnPoint)) {
            return null;
        }

        DeliveryBot robot = new DeliveryBot(
            generateRobotName(),
            fieldOfView,
            debugLevel,
            spawnPoint,
            robotColor,
            gridRows,
            gridColumns,
            environment,
            pkg,
            pickup,
            dropoff,
            batteryAutonomy,
            rechargeTime,
            chargeThreshold
        );

        activeFleet.add(robot);
        return robot;
    }

    private String generateRobotName() {
        return "Robot" + nextRobotId++;
    }

    private boolean isLocationAvailable(int[] coordinates) {
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) environment.getGrid();
        ColorSimpleCell cell = grid[coordinates[0]][coordinates[1]];
        return cell == null || cell.getContent() == null;
    }

    public List<DeliveryBot> getActiveFleet() {
        return activeFleet;
    }

    public int getActiveCount() {
        return activeFleet.size();
    }

    public int getBusyCount() {
        int busy = 0;
        for (DeliveryBot robot : activeFleet) {
            if (!robot.isIdle()) {
                busy++;
            }
        }
        return busy;
    }

    public boolean hasBusyRobots() {
        return getBusyCount() > 0;
    }

    public int getMaxRobots() {
        return maxRobots;
    }
}
