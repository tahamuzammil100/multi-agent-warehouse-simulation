package fr.emse;

import java.util.*;

/**
 * PackageScheduler - Manages package generation and assignment queues.
 *
 * Arrival times are generated according to a configurable probability
 * distribution:
 *
 *   UNIFORM   – each package is assigned a uniformly-random arrival step
 *               inside the scheduling window (original behaviour).
 *
 *   POISSON   – a Poisson process with rate λ = totalPackages / window.
 *               Per-step arrivals are drawn from Poisson(λ); packages are
 *               spread across steps proportionally to that rate.
 *
 *   GEOMETRIC – inter-arrival times between consecutive packages are drawn
 *               from Geometric(p) where p = totalPackages / window (the
 *               probability that at least one pallet arrives at any given
 *               step). Produces bursty, memoryless arrival patterns.
 *
 *   BINOMIAL  – each package's arrival step is drawn independently from
 *               Binomial(window, 0.5), producing a bell-shaped arrival
 *               curve concentrated around the midpoint of the window.
 */
public class PackageScheduler {

    // ------------------------------------------------------------------ //
    //  Distribution selector                                               //
    // ------------------------------------------------------------------ //

    public enum ArrivalDistribution {
        UNIFORM, POISSON, GEOMETRIC, BINOMIAL;

        /** Case-insensitive parse; falls back to UNIFORM on unknown input. */
        public static ArrivalDistribution fromString(String value) {
            if (value == null) return UNIFORM;
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("[PackageScheduler] Unknown distribution '"
                        + value + "', defaulting to UNIFORM.");
                return UNIFORM;
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Fields                                                              //
    // ------------------------------------------------------------------ //

    private final Queue<PackageItem>  scheduledPackages;
    private final List<PackageItem>   pendingAssignment;
    private final Random              randomizer;
    private final int[][]             entryGates;
    private final ArrivalDistribution distribution;

    // ------------------------------------------------------------------ //
    //  Construction                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Creates a scheduler using the default UNIFORM distribution.
     * Kept for backward compatibility.
     */
    public PackageScheduler(int totalPackages, int maxSteps,
                            int[][] entryGates, long seed) {
        this(totalPackages, maxSteps, entryGates, seed, ArrivalDistribution.UNIFORM);
    }

    /**
     * Creates a scheduler with a specific arrival distribution.
     *
     * @param totalPackages total number of pallets to schedule
     * @param maxSteps      simulation step limit (used to size the window)
     * @param entryGates    available entry-gate coordinates
     * @param seed          RNG seed (reproducible runs)
     * @param distribution  probability model for inter-arrival timing
     */
    public PackageScheduler(int totalPackages, int maxSteps,
                            int[][] entryGates, long seed,
                            ArrivalDistribution distribution) {
        this.scheduledPackages = new LinkedList<>();
        this.pendingAssignment = new ArrayList<>();
        this.randomizer        = new Random(seed);
        this.entryGates        = entryGates;
        this.distribution      = distribution;

        generatePackages(totalPackages, maxSteps);

        System.out.printf("[PackageScheduler] %d packages scheduled using %s distribution%n",
                totalPackages, distribution);
    }

    // ------------------------------------------------------------------ //
    //  Package generation                                                  //
    // ------------------------------------------------------------------ //

    private void generatePackages(int count, int totalSteps) {
        int window = Math.max(1, Math.min(totalSteps / 2, 200));

        List<Integer> arrivalTimes = generateArrivalTimes(count, window);

        List<PackageItem> packages = new ArrayList<>(count);
        for (int id = 0; id < count; id++) {
            int[] entryPoint   = pickRandomEntry();
            int   assignedZone = randomizer.nextInt(2) + 1;
            packages.add(new PackageItem(id, entryPoint, assignedZone, arrivalTimes.get(id)));
        }

        packages.sort(Comparator.comparingInt(pkg -> pkg.spawnStep));
        scheduledPackages.addAll(packages);
    }

    /**
     * Produces exactly {@code count} arrival-step values in [0, window)
     * according to the configured distribution.
     */
    private List<Integer> generateArrivalTimes(int count, int window) {
        switch (distribution) {

            case POISSON:
                return poissonArrivalTimes(count, window);

            case GEOMETRIC:
                return geometricArrivalTimes(count, window);

            case BINOMIAL:
                return binomialArrivalTimes(count, window);

            case UNIFORM:
            default:
                return uniformArrivalTimes(count, window);
        }
    }

    // ---- UNIFORM -------------------------------------------------------

    private List<Integer> uniformArrivalTimes(int count, int window) {
        List<Integer> times = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            times.add(randomizer.nextInt(window));
        }
        return times;
    }

    // ---- POISSON -------------------------------------------------------

    /**
     * Simulates a discrete Poisson arrival process.
     *
     * At each step t the number of new arrivals is drawn from Poisson(λ)
     * where λ = count / window (average pallets per step). Packages are
     * assigned to those steps sequentially until all {@code count} are
     * placed.
     */
    private List<Integer> poissonArrivalTimes(int count, int window) {
        double lambda = (double) count / window;
        List<Integer> times = new ArrayList<>(count);
        int assigned = 0;

        for (int step = 0; step < window && assigned < count; step++) {
            int arrivals = samplePoisson(lambda);
            for (int k = 0; k < arrivals && assigned < count; k++) {
                times.add(step);
                assigned++;
            }
        }
        // Fill any remainder at the last step (rare edge case)
        while (times.size() < count) {
            times.add(window - 1);
        }
        return times;
    }

    // ---- GEOMETRIC -----------------------------------------------------

    /**
     * Uses geometric inter-arrival times to produce a memoryless arrival
     * process (discrete-time analog of a Poisson process).
     *
     * p = count / window is the per-step arrival probability, giving an
     * expected inter-arrival gap of window / count steps.
     */
    private List<Integer> geometricArrivalTimes(int count, int window) {
        double p = Math.min(0.99, Math.max(0.01, (double) count / window));
        List<Integer> times = new ArrayList<>(count);
        int currentTime = 0;

        for (int i = 0; i < count; i++) {
            int gap = sampleGeometric(p);
            currentTime += gap;
            // Clamp to window; multiple packages may share the last step
            times.add(Math.min(currentTime, window - 1));
        }
        return times;
    }

    // ---- BINOMIAL ------------------------------------------------------

    /**
     * Each pallet's arrival step is drawn independently from
     * Binomial(window - 1, 0.5), producing a symmetric bell-curve
     * distribution of arrivals centred on the midpoint of the window.
     */
    private List<Integer> binomialArrivalTimes(int count, int window) {
        List<Integer> times = new ArrayList<>(count);
        int trials = window - 1;
        for (int i = 0; i < count; i++) {
            times.add(sampleBinomial(trials, 0.5));
        }
        return times;
    }

    // ------------------------------------------------------------------ //
    //  Statistical samplers                                               //
    // ------------------------------------------------------------------ //

    /**
     * Samples from Poisson(λ) using Knuth's algorithm (exact for small λ).
     * For large λ (> 30) a normal approximation avoids deep loops.
     */
    private int samplePoisson(double lambda) {
        if (lambda <= 0) return 0;

        if (lambda > 30) {
            // Normal approximation: X ~ N(λ, λ)
            int sample = (int) Math.round(lambda + Math.sqrt(lambda) * randomizer.nextGaussian());
            return Math.max(0, sample);
        }

        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= randomizer.nextDouble();
        } while (p > L);
        return k - 1;
    }

    /**
     * Samples from Geometric(p): number of Bernoulli trials until the
     * first success (minimum value = 1).
     */
    private int sampleGeometric(double p) {
        double u = randomizer.nextDouble();
        // Avoid log(0)
        if (u == 0.0) u = 1e-10;
        return (int) Math.ceil(Math.log(u) / Math.log(1.0 - p));
    }

    /**
     * Samples from Binomial(n, p) by summing n Bernoulli trials.
     * Exact for small-to-moderate n.
     */
    private int sampleBinomial(int n, double p) {
        int successes = 0;
        for (int i = 0; i < n; i++) {
            if (randomizer.nextDouble() < p) successes++;
        }
        return successes;
    }

    // ------------------------------------------------------------------ //
    //  Entry-gate selection                                               //
    // ------------------------------------------------------------------ //

    private int[] pickRandomEntry() {
        return entryGates[randomizer.nextInt(entryGates.length)].clone();
    }

    // ------------------------------------------------------------------ //
    //  Runtime API                                                         //
    // ------------------------------------------------------------------ //

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

    public ArrivalDistribution getDistribution() {
        return distribution;
    }
}
