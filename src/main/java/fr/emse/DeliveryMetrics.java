package fr.emse;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * DeliveryMetrics - Tracks performance statistics for the delivery system.
 *
 * Monitors:
 * - Total deliveries completed
 * - Cumulative delivery time
 * - Individual delivery records
 * - Performance calculations
 */
public class DeliveryMetrics {

    private int completedDeliveries;
    private long cumulativeDeliveryTime;
    private final List<DeliveryRecord> completionHistory;

    /**
     * Creates a new metrics tracker.
     */
    public DeliveryMetrics() {
        this.completedDeliveries = 0;
        this.cumulativeDeliveryTime = 0;
        this.completionHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * Records a completed delivery.
     *
     * @param packageId Identifier of delivered package
     * @param zone Target zone number
     * @param elapsedTime Time from arrival to delivery
     */
    public void recordDelivery(int packageId, int zone, int elapsedTime) {
        completedDeliveries++;
        cumulativeDeliveryTime += elapsedTime;

        DeliveryRecord record = new DeliveryRecord(packageId, zone, elapsedTime);
        completionHistory.add(record);
    }

    /**
     * Gets the total number of completed deliveries.
     * @return Delivery count
     */
    public int getCompletedCount() {
        return completedDeliveries;
    }

    /**
     * Gets the total time spent on all deliveries.
     * @return Cumulative time in steps
     */
    public long getTotalDeliveryTime() {
        return cumulativeDeliveryTime;
    }

    /**
     * Calculates average delivery time per package.
     * @return Average time, or 0 if no deliveries completed
     */
    public double getAverageDeliveryTime() {
        if (completedDeliveries == 0) {
            return 0.0;
        }
        return (double) cumulativeDeliveryTime / completedDeliveries;
    }

    /**
     * Gets all delivery completion records.
     * @return List of delivery records
     */
    public List<DeliveryRecord> getCompletionHistory() {
        return completionHistory;
    }

    /**
     * Converts delivery history to array format for display.
     * @return Array of [packageId, zone, time] entries
     */
    public List<int[]> getHistoryAsArrays() {
        List<int[]> arrays = new java.util.ArrayList<>();
        for (DeliveryRecord record : completionHistory) {
            arrays.add(new int[]{
                record.packageId,
                record.zone,
                record.elapsedTime
            });
        }
        return arrays;
    }

    /**
     * Prints summary statistics to console.
     * @param totalExpected Total number of packages expected
     */
    public void printSummary(int totalExpected) {
        System.out.println("\n=== Delivery Performance Summary ===");
        System.out.println("Completed: " + completedDeliveries + "/" + totalExpected);
        System.out.println("Total time: " + cumulativeDeliveryTime + " steps");
        System.out.printf("Average time: %.1f steps/package%n", getAverageDeliveryTime());
    }

    /**
     * Record of a single delivery completion.
     */
    public static class DeliveryRecord {
        public final int packageId;
        public final int zone;
        public final int elapsedTime;

        public DeliveryRecord(int packageId, int zone, int elapsedTime) {
            this.packageId = packageId;
            this.zone = zone;
            this.elapsedTime = elapsedTime;
        }

        @Override
        public String toString() {
            return String.format("Package#%d (zone=%d, time=%d steps)",
                               packageId, zone, elapsedTime);
        }
    }
}
