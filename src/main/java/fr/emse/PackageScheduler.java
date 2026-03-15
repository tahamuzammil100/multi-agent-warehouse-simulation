package fr.emse;

import java.util.*;

/**
 * PackageScheduler - Manages package generation and arrival timing.
 *
 * Responsible for:
 * - Pre-generating packages with random arrival times
 * - Distributing arrivals across the simulation timeline
 * - Tracking packages waiting for robot assignment
 * - Managing package release at appropriate steps
 */
public class PackageScheduler {

    private final Queue<PackageItem> scheduledPackages;
    private final List<PackageItem> pendingAssignment;
    private final Random randomizer;
    private final int[][] entryGates;

    /**
     * Creates a package scheduler with specified parameters.
     *
     * @param totalPackages Number of packages to generate
     * @param maxSteps Total simulation steps
     * @param entryGates Available entry coordinates
     * @param seed Random seed for reproducibility
     */
    public PackageScheduler(int totalPackages, int maxSteps, int[][] entryGates, long seed) {
        this.scheduledPackages = new LinkedList<>();
        this.pendingAssignment = new ArrayList<>();
        this.randomizer = new Random(seed);
        this.entryGates = entryGates;

        generatePackages(totalPackages, maxSteps);
    }

    /**
     * Pre-generates all packages with random arrival times and zones.
     *
     * Spreads arrivals across the first half of the simulation to avoid
     * overwhelming the system at the start.
     */
    private void generatePackages(int count, int totalSteps) {
        List<PackageItem> packages = new ArrayList<>();

        // Distribute arrivals across first half of simulation (or max 200 steps)
        int distributionWindow = Math.max(1, Math.min(totalSteps / 2, 200));

        for (int id = 0; id < count; id++) {
            int[] entryPoint = pickRandomEntry();
            int assignedZone = randomizer.nextInt(2) + 1;  // Zone 1 or 2
            int arrivalTime = randomizer.nextInt(distributionWindow);

            packages.add(new PackageItem(id, entryPoint, assignedZone, arrivalTime));
        }

        // Sort by arrival time for sequential processing
        packages.sort(Comparator.comparingInt(pkg -> pkg.spawnStep));
        scheduledPackages.addAll(packages);
    }

    /**
     * Selects a random entry gate from available options.
     * @return Copy of entry coordinates
     */
    private int[] pickRandomEntry() {
        int index = randomizer.nextInt(entryGates.length);
        return entryGates[index].clone();
    }

    /**
     * Releases packages scheduled for the current step.
     * @param currentStep Current simulation step
     * @return List of packages arriving at this step
     */
    public List<PackageItem> releaseScheduledPackages(int currentStep) {
        List<PackageItem> arrivals = new ArrayList<>();

        while (!scheduledPackages.isEmpty() &&
               scheduledPackages.peek().spawnStep <= currentStep) {
            PackageItem pkg = scheduledPackages.poll();
            pendingAssignment.add(pkg);
            arrivals.add(pkg);
        }

        return arrivals;
    }

    /**
     * Gets packages waiting for robot assignment.
     * @return List of pending packages
     */
    public List<PackageItem> getPendingPackages() {
        return pendingAssignment;
    }

    /**
     * Removes a package from pending list after robot assignment.
     * @param pkg Package to remove
     */
    public void markAsAssigned(PackageItem pkg) {
        pendingAssignment.remove(pkg);
    }

    /**
     * Checks if there are packages still scheduled to arrive.
     * @return true if more packages are coming
     */
    public boolean hasScheduledPackages() {
        return !scheduledPackages.isEmpty();
    }

    /**
     * Checks if there are packages waiting for assignment.
     * @return true if packages are pending
     */
    public boolean hasPendingPackages() {
        return !pendingAssignment.isEmpty();
    }

    /**
     * Gets total count of remaining packages (scheduled + pending).
     * @return Total remaining packages
     */
    public int getRemainingCount() {
        return scheduledPackages.size() + pendingAssignment.size();
    }
}
