package fr.emse;

import java.awt.Color;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.ini4j.Ini;

import fr.emse.fayol.maqit.simulator.SimFactory;
import fr.emse.fayol.maqit.simulator.configuration.IniFile;
import fr.emse.fayol.maqit.simulator.configuration.SimProperties;
import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.components.ColorSituatedComponent;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * AutonomousLogisticsEngine - Orchestrates automated package delivery operations.
 *
 * Grid layout (15×20):
 * - Package arrival gates on right edge (col 19)
 * - Robot spawn zones at (2,19) and (12,19)
 * - Delivery targets in top-left and bottom-left corners
 * - Exit lanes on left edge (col 0)
 *
 * Operational workflow:
 * 1. Packages arrive at scheduled times
 * 2. Robots spawn on-demand from designated points
 * 3. Each robot: collects package → navigates waypoint → delivers → exits
 * 4. System tracks performance metrics throughout
 */
public class AutonomousLogisticsEngine extends SimFactory<ColorGridEnvironment, ColorSituatedComponent> {

    // Core subsystems
    private final ZoneCoordinates zoneLayout;
    private final PackageScheduler packageManager;
    private final RobotFactory robotManager;
    private final DeliveryMetrics performanceTracker;

    // Human agents (warehouse workers)
    private final List<WarehouseWorker> humanWorkers;

    // Visual display
    private SimulatorGUI displayWindow;
    private final Map<String, Color> packageVisuals;

    // Configuration
    private final int totalPackages;
    private final float gridLineWidth;
    private final int cellPadding;
    private final boolean gridVisible;

    /**
     * Constructs the logistics engine with specified configuration.
     *
     * @param properties Simulation parameters
     * @param packageCount Number of packages to process
     * @param lineWidth Grid line thickness
     * @param padding Cell padding in pixels
     * @param showGrid Whether to display grid lines
     */
    public AutonomousLogisticsEngine(SimProperties properties, int packageCount,
                                     float lineWidth, int padding, boolean showGrid) {
        super(properties);
        this.totalPackages = packageCount;
        this.gridLineWidth = lineWidth;
        this.cellPadding = padding;
        this.gridVisible = showGrid;

        // Initialize subsystems
        this.zoneLayout = new ZoneCoordinates();
        this.packageManager = new PackageScheduler(
            packageCount, properties.step, zoneLayout.getPackageGates(), properties.seed
        );
        this.robotManager = new RobotFactory(
            null,  // Will be set after environment creation
            zoneLayout, properties.field, properties.debug,
            properties.rows, properties.columns, properties.colorrobot
        );
        this.performanceTracker = new DeliveryMetrics();

        this.humanWorkers = new ArrayList<>();
        this.packageVisuals = new ConcurrentHashMap<>();
    }

    @Override
    public void createEnvironment() {
        this.environment = new ColorGridEnvironment(this.sp.seed);
        // Update robot factory with environment reference
        java.lang.reflect.Field envField;
        try {
            envField = RobotFactory.class.getDeclaredField("environment");
            envField.setAccessible(true);
            envField.set(robotManager, this.environment);
        } catch (ReflectiveOperationException e) {
            System.err.println("Warning: Could not set environment in RobotFactory");
        }
    }

    @Override
    public void createObstacle() {
        // Load obstacle positions from configuration
        List<int[]> positions = ObstacleLoader.loadFromConfig("configuration.ini");

        // Create visual representation for each obstacle
        int[] colorRGB = {
            this.sp.colorobstacle.getRed(),
            this.sp.colorobstacle.getGreen(),
            this.sp.colorobstacle.getBlue()
        };

        for (int[] position : positions) {
            placeComponent(new ColorObstacle(position, colorRGB));
        }
    }

    @Override
    public void createRobot() {
        // Deploy warehouse workers (human agents with patrol behavior)
        int[][] workerLocations = {
            {4, 8}, {7, 4}, {8, 11}, {8, 17}, {11, 8}, {13, 13}
        };
        Color workerVisual = new Color(200, 160, 100);

        for (int i = 0; i < workerLocations.length; i++) {
            WarehouseWorker worker = new WarehouseWorker(
                "Worker" + i,
                this.sp.field,
                workerLocations[i],
                workerVisual,
                this.sp.rows,
                this.sp.columns,
                this.environment,
                (long) this.sp.seed + i + 1
            );
            placeComponent(worker);
            humanWorkers.add(worker);
        }
    }

    @Override
    public void createGoal() {
        // Visual zones drawn in setupDisplay() - no grid objects required
    }

    @Override
    public void addNewComponent(ColorSituatedComponent component) {
        placeComponent(component);
    }

    @Override
    public void updateEnvironment(int[] fromPos, int[] toPos, int id) {
        this.environment.moveComponent(fromPos, toPos);
    }

    /**
     * Main simulation loop - orchestrates all system operations.
     */
    @Override
    public void schedule() {
        System.out.println("=== Autonomous Logistics Simulation ===");
        System.out.println("Processing " + totalPackages + " packages");

        for (int step = 0; step < this.sp.step; step++) {
            logStepStatus(step);

            // Phase 1: Process scheduled package arrivals
            handlePackageArrivals(step);

            // Phase 2: Attempt robot spawning for pending packages
            spawnRobotsForPendingPackages();

            // Phase 3: Update warehouse worker positions
            moveHumanWorkers();

            // Phase 4: Execute robot deliveries and handle transitions
            processDeliveryRobots(step);

            // Phase 5: Update visual display
            refreshDisplay(step);

            // Pause for visualization
            pauseExecution();

            // Check completion criteria
            if (isOperationComplete()) {
                break;
            }
        }

        performanceTracker.printSummary(totalPackages);
    }

    /**
     * Processes package arrivals scheduled for current step.
     */
    private void handlePackageArrivals(int step) {
        List<PackageItem> arrivals = packageManager.releaseScheduledPackages(step);

        for (PackageItem pkg : arrivals) {
            packageVisuals.put(positionKey(pkg.arrivalPosition), pkg.displayColor);
            System.out.println("  [ARRIVAL] " + pkg);
        }
    }

    /**
     * Attempts to spawn robots for packages waiting assignment.
     */
    private void spawnRobotsForPendingPackages() {
        List<PackageItem> pending = new ArrayList<>(packageManager.getPendingPackages());

        for (PackageItem pkg : pending) {
            DeliveryBot robot = robotManager.trySpawnRobot(pkg);

            if (robot != null) {
                placeComponent(robot);
                packageManager.markAsAssigned(pkg);
                System.out.println("  [SPAWN] Robot#" + robot.getId() + " for " + pkg);
            }
        }
    }

    /**
     * Updates positions of human warehouse workers.
     */
    private void moveHumanWorkers() {
        for (WarehouseWorker worker : humanWorkers) {
            int[] oldPos = worker.getLocation();
            worker.updatePerception(
                this.environment.getNeighbor(worker.getX(), worker.getY(), worker.getField())
            );
            worker.move(1);

            int[] newPos = worker.getLocation();
            if (!Arrays.equals(oldPos, newPos)) {
                updateEnvironment(oldPos, newPos, worker.getId());
            }
        }
    }

    /**
     * Processes all active delivery robots and handles mission transitions.
     */
    private void processDeliveryRobots(int step) {
        List<DeliveryBot> robots = new ArrayList<>(robotManager.getActiveFleet());

        for (DeliveryBot robot : robots) {
            DeliveryMission.Phase phaseBefore = robot.getMissionPhase();
            int[] previousPos = robot.getLocation();

            // Update robot
            robot.updatePerception(
                this.environment.getNeighbor(robot.getX(), robot.getY(), robot.getField())
            );
            robot.move(1);

            int[] currentPos = robot.getLocation();
            if (!Arrays.equals(previousPos, currentPos)) {
                updateEnvironment(previousPos, currentPos, robot.getId());
            }

            DeliveryMission.Phase phaseAfter = robot.getMissionPhase();

            // Handle phase transitions
            handlePhaseTransition(robot, phaseBefore, phaseAfter, step);
        }
    }

    /**
     * Handles robot mission phase changes.
     */
    private void handlePhaseTransition(DeliveryBot robot,
                                      DeliveryMission.Phase before,
                                      DeliveryMission.Phase after,
                                      int step) {
        // Package picked up
        if (before == DeliveryMission.Phase.PICKUP &&
            after == DeliveryMission.Phase.CHECKPOINT) {
            packageVisuals.remove(positionKey(robot.getPackage().arrivalPosition));
            System.out.println("  [PICKUP] " + robot.getPackage() +
                             " by Robot#" + robot.getId());
        }

        // Package delivered
        if (before == DeliveryMission.Phase.DROPOFF &&
            after == DeliveryMission.Phase.EXIT) {
            int deliveryTime = step - robot.getPackage().spawnStep;
            performanceTracker.recordDelivery(
                robot.getPackage().packageId,
                robot.getPackage().targetZone,
                deliveryTime
            );
            System.out.printf("  [DELIVERY] %s by Robot#%d in %d steps%n",
                            robot.getPackage(), robot.getId(), deliveryTime);
        }

        // Robot exited
        if (after == DeliveryMission.Phase.COMPLETED) {
            removeRobot(robot);
            System.out.println("  [EXIT] Robot#" + robot.getId());
        }
    }

    /**
     * Refreshes the visual display with current state.
     */
    private void refreshDisplay(int step) {
        displayWindow.setPackageOverlay(packageVisuals);

        // Prepare active robot statistics
        List<int[]> activeStats = new ArrayList<>();
        for (DeliveryBot robot : robotManager.getActiveFleet()) {
            activeStats.add(new int[]{
                robot.getPackage().packageId,
                robot.getPackage().targetZone,
                step - robot.getPackage().spawnStep
            });
        }

        displayWindow.updateStats(
            step,
            performanceTracker.getCompletedCount(),
            totalPackages,
            performanceTracker.getTotalDeliveryTime(),
            activeStats,
            performanceTracker.getHistoryAsArrays()
        );

        displayWindow.refresh();
    }

    /**
     * Initializes the graphical display window.
     */
    public void setupDisplay() {
        int cellSize = this.sp.display_width / this.sp.columns;

        // Define delivery zone visual areas
        int[][] deliveryZones = {
            {1, 1, 2, 3},     // Zone 1 (top-left)
            {12, 1, 13, 3}    // Zone 2 (bottom-left)
        };

        // Define waypoint zone visual areas
        int[][] waypointZones = {
            {3, 11, 5, 12},   // Zone 1 waypoint
            {9, 11, 11, 12}   // Zone 2 waypoint
        };

        // Right panel coloring (columns 18-19)
        Color robotSpawnColor = new Color(120, 185, 225);
        Color packageGateColor = new Color(150, 215, 150);

        Color[] rightPanelColors = new Color[this.sp.rows];
        rightPanelColors[2] = robotSpawnColor;   // Robot spawn 1
        rightPanelColors[12] = robotSpawnColor;  // Robot spawn 2

        for (int r = 3; r <= 11; r++) {
            rightPanelColors[r] = packageGateColor;  // Package gates
        }

        // Separator lines
        int[] separators = {2, 5, 8, 11};

        displayWindow = new SimulatorGUI(
            (ColorSimpleCell[][]) this.environment.getGrid(),
            this.sp.display_x, this.sp.display_y,
            cellSize, this.sp.display_title,
            deliveryZones, waypointZones,
            rightPanelColors, separators,
            this.gridLineWidth, this.cellPadding, this.gridVisible
        );
        displayWindow.init();
    }

    public void refreshWindow() {
        displayWindow.refresh();
    }

    // Helper methods

    private void placeComponent(ColorSituatedComponent component) {
        int[] pos = component.getLocation();
        this.environment.setCellContent(pos[0], pos[1], component);
    }

    private void removeRobot(DeliveryBot robot) {
        clearGridCell(robot.getLocation());
        robotManager.decommissionRobot(robot);
    }

    private void clearGridCell(int[] position) {
        try {
            ColorSimpleCell[][] grid = (ColorSimpleCell[][]) this.environment.getGrid();
            ColorSimpleCell cell = grid[position[0]][position[1]];
            if (cell != null) {
                java.lang.reflect.Field field = cell.getClass().getDeclaredField("content");
                field.setAccessible(true);
                field.set(cell, null);
            }
        } catch (ReflectiveOperationException ignored) {}
        this.environment.removeCellContent(position[0], position[1]);
    }

    private String positionKey(int[] pos) {
        return pos[0] + "," + pos[1];
    }

    private void logStepStatus(int step) {
        System.out.printf("Step %d | Delivered: %d/%d | Active: %d | Pending: %d%n",
                        step,
                        performanceTracker.getCompletedCount(),
                        totalPackages,
                        robotManager.getActiveCount(),
                        packageManager.getPendingPackages().size());
    }

    private void pauseExecution() {
        try {
            Thread.sleep(this.sp.waittime);
        } catch (InterruptedException e) {
            System.err.println("Simulation interrupted: " + e.getMessage());
        }
    }

    private boolean isOperationComplete() {
        return performanceTracker.getCompletedCount() >= totalPackages &&
               !robotManager.hasActiveRobots() &&
               !packageManager.hasPendingPackages();
    }

    // Entry point

    public static void main(String[] args) throws Exception {
        IniFile configFile = new IniFile("configuration.ini");
        SimProperties properties = new SimProperties(configFile);
        properties.simulationParams();
        properties.displayParams();

        Ini ini = new Ini(new File("configuration.ini"));
        int packageCount = Integer.parseInt(ini.get("warehouse", "total_pallets").trim());
        float lineWidth = Float.parseFloat(ini.get("warehouse", "line_stroke").trim());
        int padding = Integer.parseInt(ini.get("warehouse", "padding").trim());
        boolean showGrid = Integer.parseInt(ini.get("warehouse", "show_grid").trim()) != 0;

        System.out.println("=== Configuration ===");
        System.out.println("Grid dimensions: " + properties.rows + " × " + properties.columns);
        System.out.println("Packages to process: " + packageCount);
        System.out.println("Grid line width: " + lineWidth + "px");

        AutonomousLogisticsEngine engine = new AutonomousLogisticsEngine(
            properties, packageCount, lineWidth, padding, showGrid
        );

        engine.createEnvironment();
        engine.createObstacle();
        engine.createRobot();
        engine.createGoal();
        engine.setupDisplay();
        engine.refreshWindow();
        engine.schedule();
    }
}
