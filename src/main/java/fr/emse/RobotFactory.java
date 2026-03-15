package fr.emse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * RobotFactory - Handles creation and lifecycle management of delivery robots.
 *
 * Manages:
 * - Robot instantiation with proper configuration
 * - Active robot tracking
 * - Robot ID assignment
 * - Spawn point availability checking
 */
public class RobotFactory {

    private final ColorGridEnvironment environment;
    private final ZoneCoordinates zones;
    private final int fieldOfView;
    private final int debugLevel;
    private final int gridRows;
    private final int gridColumns;
    private final Color robotColor;

    private final List<DeliveryBot> activeFleet;
    private int nextRobotId;

    /**
     * Constructs a robot factory with environment configuration.
     *
     * @param env Grid environment
     * @param zones Zone coordinate registry
     * @param fieldOfView Robot perception range
     * @param debug Debug output level
     * @param rows Grid row count
     * @param cols Grid column count
     * @param color Robot display color
     */
    public RobotFactory(ColorGridEnvironment env, ZoneCoordinates zones,
                       int fieldOfView, int debug, int rows, int cols, Color color) {
        this.environment = env;
        this.zones = zones;
        this.fieldOfView = fieldOfView;
        this.debugLevel = debug;
        this.gridRows = rows;
        this.gridColumns = cols;
        this.robotColor = color;
        this.activeFleet = new ArrayList<>();
        this.nextRobotId = 0;
    }

    /**
     * Attempts to spawn a robot for a package if spawn point is available.
     *
     * @param pkg Package requiring robot assignment
     * @return Newly created robot, or null if spawn blocked
     */
    public DeliveryBot trySpawnRobot(PackageItem pkg) {
        int zoneNumber = pkg.targetZone;
        int[] spawnPoint = zones.getRobotSpawn(zoneNumber);

        // Check if spawn location is available
        if (!isLocationAvailable(spawnPoint)) {
            return null;
        }

        // Create robot with full mission parameters
        DeliveryBot robot = buildRobot(pkg, zoneNumber, spawnPoint);

        // Register in active fleet
        activeFleet.add(robot);

        return robot;
    }

    /**
     * Constructs a fully configured delivery robot.
     *
     * @param pkg Package to deliver
     * @param zone Target zone number
     * @param spawn Spawn coordinates
     * @return Configured robot instance
     */
    private DeliveryBot buildRobot(PackageItem pkg, int zone, int[] spawn) {
        int[] waypoint = zones.getWaypoint(zone);
        int[] target = zones.getDeliveryTarget(zone);
        int[] exit = zones.getExit(zone);

        return new DeliveryBot(
            generateRobotName(),
            fieldOfView,
            debugLevel,
            spawn,
            robotColor,
            gridRows,
            gridColumns,
            environment,
            pkg,
            pkg.arrivalPosition,
            waypoint,
            target,
            exit
        );
    }

    /**
     * Generates unique robot identifier.
     * @return Robot name string
     */
    private String generateRobotName() {
        return "Robot" + nextRobotId++;
    }

    /**
     * Checks if a grid location is unoccupied.
     *
     * @param coordinates Position to check
     * @return true if cell is empty
     */
    private boolean isLocationAvailable(int[] coordinates) {
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) environment.getGrid();
        ColorSimpleCell cell = grid[coordinates[0]][coordinates[1]];
        return cell == null || cell.getContent() == null;
    }

    /**
     * Removes a robot from the active fleet.
     * @param robot Robot to remove
     */
    public void decommissionRobot(DeliveryBot robot) {
        activeFleet.remove(robot);
    }

    /**
     * Gets all currently active robots.
     * @return List of active robots
     */
    public List<DeliveryBot> getActiveFleet() {
        return activeFleet;
    }

    /**
     * Gets count of active robots.
     * @return Number of robots currently operating
     */
    public int getActiveCount() {
        return activeFleet.size();
    }

    /**
     * Checks if any robots are still active.
     * @return true if fleet has active robots
     */
    public boolean hasActiveRobots() {
        return !activeFleet.isEmpty();
    }
}
