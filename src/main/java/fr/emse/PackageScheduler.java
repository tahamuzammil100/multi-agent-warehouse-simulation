package fr.emse;

import java.util.*;

/**
 * PackageScheduler - Manages package generation and assignment queues.
 */
public class PackageScheduler {

    private final Queue<PackageItem> scheduledPackages;
    private final List<PackageItem> pendingAssignment;
    private final Random randomizer;
    private final int[][] entryGates;

    public PackageScheduler(int totalPackages, int maxSteps, int[][] entryGates, long seed) {
        this.scheduledPackages = new LinkedList<>();
        this.pendingAssignment = new ArrayList<>();
        this.randomizer = new Random(seed);
        this.entryGates = entryGates;

        generatePackages(totalPackages, maxSteps);
    }

    private void generatePackages(int count, int totalSteps) {
        List<PackageItem> packages = new ArrayList<>();
        int distributionWindow = Math.max(1, Math.min(totalSteps / 2, 200));

        for (int id = 0; id < count; id++) {
            int[] entryPoint = pickRandomEntry();
            int assignedZone = randomizer.nextInt(2) + 1;
            int arrivalTime = randomizer.nextInt(distributionWindow);
            packages.add(new PackageItem(id, entryPoint, assignedZone, arrivalTime));
        }

        packages.sort(Comparator.comparingInt(pkg -> pkg.spawnStep));
        scheduledPackages.addAll(packages);
    }

    private int[] pickRandomEntry() {
        int index = randomizer.nextInt(entryGates.length);
        return entryGates[index].clone();
    }

    public List<PackageItem> releaseScheduledPackages(int currentStep) {
        List<PackageItem> arrivals = new ArrayList<>();

        while (!scheduledPackages.isEmpty() &&
               scheduledPackages.peek().spawnStep <= currentStep) {
            PackageItem pkg = scheduledPackages.poll();
            enqueueForAssignment(pkg);
            arrivals.add(pkg);
        }

        return arrivals;
    }

    public void enqueueForAssignment(PackageItem pkg) {
        if (!pendingAssignment.contains(pkg)) {
            pendingAssignment.add(pkg);
        }
    }

    public List<PackageItem> getPendingPackages() {
        return pendingAssignment;
    }

    public void markAsAssigned(PackageItem pkg) {
        pendingAssignment.remove(pkg);
    }

    public boolean hasScheduledPackages() {
        return !scheduledPackages.isEmpty();
    }

    public boolean hasPendingPackages() {
        return !pendingAssignment.isEmpty();
    }

    public int getRemainingCount() {
        return scheduledPackages.size() + pendingAssignment.size();
    }
}
