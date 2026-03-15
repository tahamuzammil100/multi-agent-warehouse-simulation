package fr.emse;

/**
 * DeliveryMission - Manages the multi-stage delivery workflow for a robot.
 *
 * Each delivery mission consists of four sequential phases:
 * 1. PICKUP - Travel to package location
 * 2. CHECKPOINT - Navigate through intermediate waypoint
 * 3. DROPOFF - Transport package to delivery zone
 * 4. EXIT - Return to warehouse exit point
 *
 * The mission tracks current phase and provides destination coordinates
 * for each stage of the journey.
 */
public class DeliveryMission {

    /**
     * Mission phases representing the delivery lifecycle.
     */
    public enum Phase {
        PICKUP,      // Moving to collect the package
        CHECKPOINT,  // Passing through safety waypoint
        DROPOFF,     // Delivering to target zone
        EXIT,        // Returning to exit location
        COMPLETED    // Mission finished
    }

    // Mission waypoints
    private final int[] pickupLocation;
    private final int[] checkpointLocation;
    private final int[] dropoffLocation;
    private final int[] exitLocation;

    // Current mission state
    private Phase currentPhase;

    // Associated package information
    private final PackageItem assignedPackage;

    /**
     * Creates a new delivery mission with all waypoints defined.
     *
     * @param packageItem The package to be delivered
     * @param pickup Coordinates where package is located
     * @param checkpoint Intermediate waypoint coordinates
     * @param dropoff Final delivery zone coordinates
     * @param exit Warehouse exit coordinates
     */
    public DeliveryMission(PackageItem packageItem,
                          int[] pickup,
                          int[] checkpoint,
                          int[] dropoff,
                          int[] exit) {
        this.assignedPackage = packageItem;
        this.pickupLocation = pickup;
        this.checkpointLocation = checkpoint;
        this.dropoffLocation = dropoff;
        this.exitLocation = exit;
        this.currentPhase = Phase.PICKUP;
    }

    /**
     * Advances to the next phase of the mission.
     *
     * Transitions follow this sequence:
     * PICKUP -> CHECKPOINT -> DROPOFF -> EXIT -> COMPLETED
     *
     * @return true if phase was advanced, false if already completed
     */
    public boolean advanceToNextPhase() {
        switch (currentPhase) {
            case PICKUP:
                currentPhase = Phase.CHECKPOINT;
                return true;

            case CHECKPOINT:
                currentPhase = Phase.DROPOFF;
                return true;

            case DROPOFF:
                currentPhase = Phase.EXIT;
                return true;

            case EXIT:
                currentPhase = Phase.COMPLETED;
                return true;

            case COMPLETED:
                return false; // Already at final phase

            default:
                return false;
        }
    }

    /**
     * Gets the destination coordinates for the current phase.
     *
     * @return Target coordinates [row, col] for current mission phase
     */
    public int[] getCurrentDestination() {
        switch (currentPhase) {
            case PICKUP:
                return pickupLocation;

            case CHECKPOINT:
                return checkpointLocation;

            case DROPOFF:
                return dropoffLocation;

            case EXIT:
                return exitLocation;

            case COMPLETED:
            default:
                return null; // No destination when complete
        }
    }

    /**
     * Checks if the robot has reached the current destination.
     *
     * @param robotPosition Current robot coordinates [row, col]
     * @return true if robot is at the current phase destination
     */
    public boolean hasReachedDestination(int[] robotPosition) {
        int[] target = getCurrentDestination();

        if (target == null) {
            return true; // Completed mission - no more destinations
        }

        return robotPosition[0] == target[0] && robotPosition[1] == target[1];
    }

    /**
     * Gets the current phase of the mission.
     *
     * @return Current mission phase
     */
    public Phase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Checks if the entire mission is complete.
     *
     * @return true if robot has finished all phases
     */
    public boolean isComplete() {
        return currentPhase == Phase.COMPLETED;
    }

    /**
     * Gets the package associated with this mission.
     *
     * @return The package being delivered
     */
    public PackageItem getPackage() {
        return assignedPackage;
    }

    /**
     * Gets coordinates for the pickup location.
     *
     * @return Package pickup coordinates
     */
    public int[] getPickupLocation() {
        return pickupLocation;
    }

    /**
     * Gets coordinates for the checkpoint waypoint.
     *
     * @return Checkpoint coordinates
     */
    public int[] getCheckpointLocation() {
        return checkpointLocation;
    }

    /**
     * Gets coordinates for the dropoff zone.
     *
     * @return Delivery zone coordinates
     */
    public int[] getDropoffLocation() {
        return dropoffLocation;
    }

    /**
     * Gets coordinates for the warehouse exit.
     *
     * @return Exit point coordinates
     */
    public int[] getExitLocation() {
        return exitLocation;
    }

    /**
     * Provides a human-readable description of the mission state.
     *
     * @return String describing current phase and destination
     */
    @Override
    public String toString() {
        return String.format("DeliveryMission[phase=%s, package=%s]",
                           currentPhase, assignedPackage);
    }
}
