package fr.emse;

import java.awt.Color;
import java.util.List;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * A transport robot that delivers packages through the warehouse.
 * Uses composition pattern - delegates pathfinding to PathFinder,
 * and journey management to RobotJourney.
 */
public class TransportRobot extends ColorInteractionRobot<ColorSimpleCell> {

    // What this robot is carrying (Encapsulation)
    private final DeliveryPackage assignedPackage;

    // Where this robot needs to go (Composition)
    private final RobotJourney journey;

    // Navigation state
    private List<int[]> plannedRoute;
    private int routeStep;
    private int stuckCounter;  // Tracks how long we've been stuck

    // Environment reference for pathfinding
    private final ColorGridEnvironment environment;
    private final int gridRows;
    private final int gridCols;

    /**
     * Creates a new transport robot.
     *
     * @param name Robot identifier
     * @param fieldSize Perception range
     * @param debugLevel Debug verbosity
     * @param startPos Initial position [row, col]
     * @param color Robot display color
     * @param rows Grid row count
     * @param cols Grid column count
     * @param env Warehouse environment
     * @param pkg Package to deliver
     * @param pickup Package collection point
     * @param waypoint Intermediate navigation point
     * @param delivery Final delivery location
     * @param exit Where robot exits after delivery
     */
    public TransportRobot(String name, int fieldSize, int debugLevel,
                          int[] startPos, Color color,
                          int rows, int cols, ColorGridEnvironment env,
                          DeliveryPackage pkg,
                          int[] pickup, int[] waypoint, int[] delivery, int[] exit) {
        super(name, fieldSize, startPos,
              new int[]{color.getRed(), color.getGreen(), color.getBlue()});

        this.assignedPackage = pkg;
        this.journey = new RobotJourney(pickup, waypoint, delivery, exit);
        this.environment = env;
        this.gridRows = rows;
        this.gridCols = cols;

        this.plannedRoute = null;
        this.routeStep = 0;
        this.stuckCounter = 0;

        // Calculate initial path
        computeNewRoute();
    }

    /**
     * Main movement logic - called once per simulation step.
     * Demonstrates polymorphism by overriding parent's move method.
     */
    @Override
    public void move(int steps) {
        // Process each movement step
        for (int i = 0; i < steps; i++) {
            // Don't move if journey is complete
            if (journey.isCompleted()) {
                return;
            }

            int[] currentPos = getLocation();

            // Check if we reached current destination
            if (journey.hasReachedCurrentDestination(currentPos)) {
                journey.advanceToNextStage();
                computeNewRoute();  // Plan route to next destination
                return;
            }

            // Make sure we have a valid route
            if (plannedRoute == null || routeStep >= plannedRoute.size()) {
                computeNewRoute();
                if (plannedRoute == null || plannedRoute.isEmpty()) {
                    return;  // Can't find path
                }
            }

            // Get next position in route
            int[] nextPos = plannedRoute.get(routeStep);

            // Check if path is blocked by another robot
            if (isPositionBlocked(nextPos)) {
                handleBlockage();
                return;
            }

            // Path is clear - move forward
            stuckCounter = 0;
            moveTowardsPosition(nextPos);
            routeStep++;
        }
    }

    /**
     * Computes a new route to current destination using PathFinder.
     * Demonstrates Separation of Concerns - delegates to utility class.
     */
    private void computeNewRoute() {
        int[] destination = journey.getCurrentDestination();
        if (destination == null) {
            plannedRoute = null;
            return;
        }

        int[] start = getLocation();
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) environment.getGrid();

        // Delegate pathfinding to specialized utility class
        plannedRoute = PathFinder.findPath(start, destination, grid, gridRows, gridCols);
        routeStep = 0;
    }

    /**
     * Checks if a position is blocked by another robot.
     */
    private boolean isPositionBlocked(int[] pos) {
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) environment.getGrid();
        ColorSimpleCell cell = grid[pos[0]][pos[1]];

        if (cell == null) return false;

        Object content = cell.getContent();
        // Only other robots block us, not obstacles (those are avoided in pathfinding)
        return content != null && content instanceof ColorInteractionRobot;
    }

    /**
     * Handles situation where path is blocked.
     * If stuck too long, recompute the route.
     */
    private void handleBlockage() {
        stuckCounter++;

        // If stuck for 3 steps, try finding alternate route
        if (stuckCounter >= 3) {
            computeNewRoute();
            stuckCounter = 0;
        }
    }

    /**
     * Physically moves the robot towards target position.
     * Handles orientation and forward movement.
     */
    private void moveTowardsPosition(int[] target) {
        int[] current = getLocation();

        // Determine required orientation
        Orientation requiredOrientation = calculateOrientation(current, target);

        // Turn to face the target
        while (this.orientation != requiredOrientation) {
            turnLeft();
        }

        // Move forward
        moveForward();
    }

    /**
     * Calculates which direction to face to move from 'from' to 'to'.
     */
    private Orientation calculateOrientation(int[] from, int[] to) {
        int rowDiff = to[0] - from[0];
        int colDiff = to[1] - from[1];

        if (rowDiff < 0) return Orientation.up;
        if (rowDiff > 0) return Orientation.down;
        if (colDiff < 0) return Orientation.left;
        if (colDiff > 0) return Orientation.right;

        return this.orientation;  // Same position
    }

    // Getters for external access (Encapsulation)
    public DeliveryPackage getPackage() { return assignedPackage; }
    public RobotJourney getJourney() { return journey; }
    public boolean hasCompletedJourney() { return journey.isCompleted(); }

    /**
     * Handles inter-robot messages (required by ColorInteractionRobot).
     * Currently not used, but required by the framework.
     */
    @Override
    public void handleMessage(fr.emse.fayol.maqit.simulator.components.Message msg) {
        // Message handling can be added here for robot communication
    }

    @Override
    public String toString() {
        return String.format("TransportRobot[%s, pos=(%d,%d), %s]",
                           name, x, y, journey);
    }
}
