package fr.emse;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

import java.util.List;

/**
 * NavigationController - Manages robot movement and orientation along paths.
 *
 * This controller handles:
 * - Following computed waypoint paths
 * - Turning to face the next waypoint
 * - Executing forward movement
 * - Tracking progress along the route
 */
public class NavigationController {

    private final ColorInteractionRobot<ColorSimpleCell> controlledRobot;

    private List<int[]> currentRoute;
    private int waypointIndex;

    /**
     * Constructs a navigation controller for a specific robot.
     *
     * @param robot The robot instance to control
     */
    public NavigationController(ColorInteractionRobot<ColorSimpleCell> robot) {
        this.controlledRobot = robot;
        this.currentRoute = null;
        this.waypointIndex = 0;
    }

    /**
     * Sets a new route for the robot to follow.
     *
     * @param route List of waypoint coordinates to traverse
     */
    public void setRoute(List<int[]> route) {
        this.currentRoute = route;
        this.waypointIndex = 0;
    }

    /**
     * Checks if the robot has a valid route to follow.
     *
     * @return true if a route exists and hasn't been completed
     */
    public boolean hasActiveRoute() {
        return currentRoute != null &&
               !currentRoute.isEmpty() &&
               waypointIndex < currentRoute.size();
    }

    /**
     * Gets the next waypoint the robot should move toward.
     *
     * @return Coordinates of next waypoint, or null if route is complete
     */
    public int[] getNextWaypoint() {
        if (!hasActiveRoute()) {
            return null;
        }
        return currentRoute.get(waypointIndex);
    }

    /**
     * Executes one movement step toward the next waypoint.
     *
     * This method:
     * 1. Calculates required orientation
     * 2. Rotates robot to face waypoint
     * 3. Attempts forward movement
     * 4. Advances waypoint index on success
     *
     * @return true if robot successfully moved forward
     */
    public boolean executeMovementStep() {
        if (!hasActiveRoute()) {
            return false;
        }

        int[] targetWaypoint = currentRoute.get(waypointIndex);
        int[] robotPosition = controlledRobot.getLocation();

        // Calculate required direction
        Orientation requiredOrientation = calculateDirection(
            robotPosition[0], robotPosition[1],
            targetWaypoint[0], targetWaypoint[1]
        );

        // Rotate to face the waypoint
        rotateToOrientation(requiredOrientation);

        // Attempt to move forward
        boolean movementSuccessful = controlledRobot.moveForward();

        if (movementSuccessful) {
            waypointIndex++;
        }

        return movementSuccessful;
    }

    /**
     * Moves robot to a specific cell if it's adjacent.
     *
     * @param targetCell Coordinates to move to
     * @return true if movement was successful
     */
    public boolean moveToCell(int[] targetCell) {
        int[] currentPos = controlledRobot.getLocation();

        Orientation direction = calculateDirection(
            currentPos[0], currentPos[1],
            targetCell[0], targetCell[1]
        );

        rotateToOrientation(direction);
        return controlledRobot.moveForward();
    }

    /**
     * Calculates the orientation needed to move from one cell to another.
     *
     * @param fromRow Source row
     * @param fromCol Source column
     * @param toRow Destination row
     * @param toCol Destination column
     * @return The cardinal direction for this movement
     */
    private Orientation calculateDirection(int fromRow, int fromCol, int toRow, int toCol) {
        if (toRow < fromRow) {
            return Orientation.up;
        }
        if (toRow > fromRow) {
            return Orientation.down;
        }
        if (toCol > fromCol) {
            return Orientation.right;
        }
        return Orientation.left;
    }

    /**
     * Rotates the robot to face a specific direction.
     *
     * Calculates the minimum number of left turns needed and executes them.
     *
     * @param targetOrientation The desired facing direction
     */
    private void rotateToOrientation(Orientation targetOrientation) {
        Orientation currentOrientation = controlledRobot.getCurrentOrientation();

        // Handle uninitialized orientation
        if (currentOrientation == null || currentOrientation == Orientation.unknown) {
            controlledRobot.setCurrentOrientation(Orientation.up);
            currentOrientation = Orientation.up;
        }

        // Calculate turns needed (counter-clockwise)
        int turnsRequired = (getOrientationIndex(targetOrientation) -
                            getOrientationIndex(currentOrientation) + 4) % 4;

        // Execute left turns
        for (int i = 0; i < turnsRequired; i++) {
            controlledRobot.turnLeft();
        }
    }

    /**
     * Maps orientation to a numeric index for rotation calculation.
     *
     * Index progression represents counter-clockwise rotation:
     * UP(0) -> LEFT(1) -> DOWN(2) -> RIGHT(3) -> UP(0)
     *
     * @param orientation The direction to map
     * @return Numeric index 0-3
     */
    private int getOrientationIndex(Orientation orientation) {
        switch (orientation) {
            case up:    return 0;
            case left:  return 1;
            case down:  return 2;
            case right: return 3;
            default:    return 0;
        }
    }

    /**
     * Gets the current waypoint index for debugging/monitoring.
     *
     * @return Current position in the route
     */
    public int getCurrentWaypointIndex() {
        return waypointIndex;
    }

    /**
     * Gets the total number of waypoints in the current route.
     *
     * @return Route length, or 0 if no route exists
     */
    public int getRouteLength() {
        return (currentRoute != null) ? currentRoute.size() : 0;
    }
}
