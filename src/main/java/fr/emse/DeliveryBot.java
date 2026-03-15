package fr.emse;

import java.awt.Color;
import java.util.List;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * DeliveryBot - Autonomous warehouse delivery robot using component-based architecture.
 *
 * This robot coordinates multiple specialized components to accomplish package delivery:
 * - PathPlanner: Computes optimal routes using BFS
 * - NavigationController: Executes movement along routes
 * - CollisionManager: Handles obstacle avoidance
 * - DeliveryMission: Manages the multi-phase delivery workflow
 *
 * Architecture Benefits:
 * - Separation of concerns (pathfinding, navigation, collision detection)
 * - Easier testing and maintenance
 * - Reusable components for different robot types
 */
public class DeliveryBot extends ColorInteractionRobot<ColorSimpleCell> {

    // Component subsystems
    private final PathPlanner routePlanner;
    private final NavigationController navigator;
    private final CollisionManager collisionDetector;
    private final DeliveryMission mission;

    // Environment reference for component communication
    private final ColorGridEnvironment warehouseEnvironment;

    /**
     * Constructs a delivery robot with all necessary components initialized.
     *
     * @param name Robot identifier
     * @param field Field of view size
     * @param debug Debug level
     * @param pos Starting position [row, col]
     * @param color Robot display color
     * @param rows Grid row count
     * @param cols Grid column count
     * @param env Warehouse environment
     * @param packageItem Package to deliver
     * @param packagePos Package pickup coordinates
     * @param safepointPos Intermediate waypoint
     * @param deliveryPos Final delivery zone
     * @param exitPos Warehouse exit point
     */
    public DeliveryBot(String name, int field, int debug, int[] pos, Color color,
                       int rows, int cols, ColorGridEnvironment env,
                       PackageItem packageItem, int[] packagePos,
                       int[] safepointPos, int[] deliveryPos, int[] exitPos) {

        super(name, field, pos,
              new int[]{color.getRed(), color.getGreen(), color.getBlue()});

        this.warehouseEnvironment = env;

        // Initialize all component subsystems
        this.routePlanner = new PathPlanner(env, rows, cols);
        this.navigator = new NavigationController(this);
        this.collisionDetector = new CollisionManager(env, rows, cols);
        this.mission = new DeliveryMission(packageItem, packagePos,
                                          safepointPos, deliveryPos, exitPos);

        // Set initial orientation and compute first route
        setCurrentOrientation(Orientation.up);
        replanRoute();
    }

    /**
     * Executes multiple movement steps.
     *
     * @param nb Number of steps to execute
     */
    @Override
    public void move(int nb) {
        for (int i = 0; i < nb; i++) {
            performSingleStep();
        }
    }

    /**
     * Core control loop - executes one step of robot behavior.
     *
     * Decision flow:
     * 1. Check if mission is complete -> stop
     * 2. Check if reached current destination -> advance mission phase
     * 3. Verify route exists -> replan if needed
     * 4. Check for collisions -> wait or escape if blocked
     * 5. Execute movement step -> replan if movement fails
     */
    private void performSingleStep() {
        // Mission complete - no further action
        if (mission.isComplete()) {
            return;
        }

        int[] currentPosition = getLocation();

        // Check if we've arrived at current phase destination
        if (mission.hasReachedDestination(currentPosition)) {
            handleDestinationReached();
            return;
        }

        // Ensure we have a valid route
        if (!navigator.hasActiveRoute()) {
            replanRoute();
            if (!navigator.hasActiveRoute()) {
                return; // No path available
            }
        }

        // Get next step in route
        int[] nextWaypoint = navigator.getNextWaypoint();

        // Check for collision before moving
        if (collisionDetector.isCellBlocked(nextWaypoint[0], nextWaypoint[1])) {
            handleBlockedPath();
            return;
        }

        // Path is clear - reset blockage tracking
        collisionDetector.resetBlockageCounter();

        // Attempt movement
        boolean movementSucceeded = navigator.executeMovementStep();

        // Replan if movement failed (unexpected obstacle)
        if (!movementSucceeded) {
            replanRoute();
        }
    }

    /**
     * Handles arrival at current mission phase destination.
     *
     * Advances to next phase and computes new route.
     */
    private void handleDestinationReached() {
        mission.advanceToNextPhase();
        replanRoute();
    }

    /**
     * Handles blocked movement attempts.
     *
     * Strategy:
     * 1. Record blockage
     * 2. If blocked repeatedly -> attempt escape maneuver
     * 3. Otherwise -> wait for path to clear
     */
    private void handleBlockedPath() {
        boolean shouldEscape = collisionDetector.recordBlockedAttempt();

        if (shouldEscape) {
            attemptEscapeManeuver();
        }
        // else: wait for blockage to clear
    }

    /**
     * Attempts to escape from a blocked position.
     *
     * Finds an alternative cell perpendicular to the target direction,
     * moves there, then replans the route.
     */
    private void attemptEscapeManeuver() {
        int[] currentPos = getLocation();
        int[] targetDest = mission.getCurrentDestination();

        // Find an escape route
        int[] escapeCell = collisionDetector.findEscapeRoute(currentPos, targetDest);

        if (escapeCell != null) {
            // Execute escape move
            boolean escapedSuccessfully = navigator.moveToCell(escapeCell);

            if (escapedSuccessfully) {
                // Replan from new position
                replanRoute();
            }
        } else {
            // No escape available - replan to try different approach
            replanRoute();
        }
    }

    /**
     * Computes a new route to the current mission destination.
     *
     * Uses PathPlanner to find optimal path and updates NavigationController.
     */
    private void replanRoute() {
        int[] origin = getLocation();
        int[] destination = mission.getCurrentDestination();

        if (destination == null) {
            return; // Mission complete - no destination
        }

        List<int[]> newRoute = routePlanner.findRoute(origin, destination);
        navigator.setRoute(newRoute);
    }

    // -------------------------------------------------------------------------
    // Public API for external components
    // -------------------------------------------------------------------------

    /**
     * Gets the current mission phase.
     *
     * @return Current delivery phase
     */
    public DeliveryMission.Phase getMissionPhase() {
        return mission.getCurrentPhase();
    }

    /**
     * Gets the package being delivered.
     *
     * @return Associated package item
     */
    public PackageItem getPackage() {
        return mission.getPackage();
    }

    /**
     * Checks if delivery mission is complete.
     *
     * @return true if all phases finished
     */
    public boolean isMissionComplete() {
        return mission.isComplete();
    }

    /**
     * Gets the full delivery mission object.
     *
     * @return The mission instance
     */
    public DeliveryMission getMission() {
        return mission;
    }

    // -------------------------------------------------------------------------
    // Message handling (not used in current implementation)
    // -------------------------------------------------------------------------

    @Override
    public void handleMessage(Message msg) {
        // Future enhancement: implement inter-robot communication
    }
}
