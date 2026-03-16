package fr.emse;

/**
 * DeliveryMission - Mission workflow for direct package delivery.
 *
 * Stages:
 * 1. PICKUP  - Travel to package location
 * 2. DROPOFF - Transport package to target location
 */
public class DeliveryMission {

    public enum Phase {
        PICKUP,
        DROPOFF,
        COMPLETED
    }

    private final int[] pickupLocation;
    private final int[] dropoffLocation;
    private Phase currentPhase;
    private final PackageItem assignedPackage;

    public DeliveryMission(PackageItem packageItem, int[] pickup, int[] dropoff) {
        this.assignedPackage = packageItem;
        this.pickupLocation = pickup;
        this.dropoffLocation = dropoff;
        this.currentPhase = Phase.PICKUP;
    }

    public boolean advanceToNextPhase() {
        switch (currentPhase) {
            case PICKUP:
                currentPhase = Phase.DROPOFF;
                return true;
            case DROPOFF:
                currentPhase = Phase.COMPLETED;
                return true;
            case COMPLETED:
            default:
                return false;
        }
    }

    public int[] getCurrentDestination() {
        switch (currentPhase) {
            case PICKUP:
                return pickupLocation;
            case DROPOFF:
                return dropoffLocation;
            case COMPLETED:
            default:
                return null;
        }
    }

    public boolean hasReachedDestination(int[] robotPosition) {
        int[] target = getCurrentDestination();
        if (target == null) {
            return true;
        }
        return robotPosition[0] == target[0] && robotPosition[1] == target[1];
    }

    public Phase getCurrentPhase() {
        return currentPhase;
    }

    public boolean isComplete() {
        return currentPhase == Phase.COMPLETED;
    }

    public PackageItem getPackage() {
        return assignedPackage;
    }
}
