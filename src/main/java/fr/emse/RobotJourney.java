package fr.emse;

/**
 * Represents a robot's complete journey through the warehouse.
 * Encapsulates the multi-waypoint navigation logic.
 *
 * Journey stages:
 * 1. Move to package pickup location
 * 2. Move to intermediate waypoint (traffic management)
 * 3. Move to delivery zone
 * 4. Move to exit point
 */
public class RobotJourney {

    /**
     * Enumeration of journey stages.
     * Uses clear, descriptive names for readability.
     */
    public enum Stage {
        PICKING_UP,      // Going to collect package
        AT_WAYPOINT,     // Moving through intermediate point
        DELIVERING,      // Heading to delivery zone
        EXITING,         // Moving to exit
        COMPLETED        // Journey finished
    }

    // Journey waypoints
    private final int[] pickupPoint;
    private final int[] waypointLocation;
    private final int[] deliveryPoint;
    private final int[] exitLocation;

    // Current state
    private Stage currentStage;
    private int[] currentDestination;

    /**
     * Creates a new robot journey with all waypoints defined.
     *
     * @param pickup Where to collect the package
     * @param waypoint Intermediate traffic point
     * @param delivery Final delivery location
     * @param exit Where robot leaves the warehouse
     */
    public RobotJourney(int[] pickup, int[] waypoint, int[] delivery, int[] exit) {
        this.pickupPoint = pickup.clone();
        this.waypointLocation = waypoint.clone();
        this.deliveryPoint = delivery.clone();
        this.exitLocation = exit.clone();

        // Start at first stage
        this.currentStage = Stage.PICKING_UP;
        this.currentDestination = this.pickupPoint;
    }

    /**
     * Advances to the next stage of the journey.
     * Returns true if there are more stages, false if completed.
     */
    public boolean advanceToNextStage() {
        switch (currentStage) {
            case PICKING_UP:
                currentStage = Stage.AT_WAYPOINT;
                currentDestination = waypointLocation;
                return true;

            case AT_WAYPOINT:
                currentStage = Stage.DELIVERING;
                currentDestination = deliveryPoint;
                return true;

            case DELIVERING:
                currentStage = Stage.EXITING;
                currentDestination = exitLocation;
                return true;

            case EXITING:
                currentStage = Stage.COMPLETED;
                currentDestination = null;
                return false;

            case COMPLETED:
                return false;
        }
        return false;
    }

    // Getters with clear names
    public Stage getCurrentStage() { return currentStage; }
    public int[] getCurrentDestination() { return currentDestination; }
    public boolean isCompleted() { return currentStage == Stage.COMPLETED; }

    /**
     * Checks if robot has reached current destination.
     */
    public boolean hasReachedCurrentDestination(int[] position) {
        if (currentDestination == null) return true;
        return position[0] == currentDestination[0] &&
               position[1] == currentDestination[1];
    }

    @Override
    public String toString() {
        return String.format("Journey[stage=%s, destination=(%d,%d)]",
                           currentStage,
                           currentDestination != null ? currentDestination[0] : -1,
                           currentDestination != null ? currentDestination[1] : -1);
    }
}
