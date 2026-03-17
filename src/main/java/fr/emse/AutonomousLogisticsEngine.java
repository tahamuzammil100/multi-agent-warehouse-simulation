package fr.emse;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.ini4j.Ini;

import fr.emse.fayol.maqit.simulator.SimFactory;
import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.components.ColorSituatedComponent;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.configuration.IniFile;
import fr.emse.fayol.maqit.simulator.configuration.SimProperties;
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
    private final IntermediateStorageManager intermediateStorage;
    private final RechargingAreaManager rechargingArea;
    private final DeliveryMetrics performanceTracker;

    // Human agents (warehouse workers)
    private final List<WarehouseWorker> humanWorkers;

    // Visual display
    private SimulatorGUI displayWindow;
    private final Map<String, Color> packageVisuals;

    // Configuration
    private final int totalPackages;
    private final int maxRobots;
    private final int batteryAutonomy;
    private final int rechargeTimeSteps;
    private final int chargeThreshold;
    private final float gridLineWidth;
    private final int cellPadding;
    private final boolean gridVisible;

    /**
     * Constructs the logistics engine with specified configuration.
     *
     * @param properties Simulation parameters
     * @param packageCount Number of packages to process
     * @param maxRobotCount Maximum number of delivery robots in the fleet
     * @param batteryAutonomy Maximum battery autonomy in movement steps
     * @param rechargeTime Recharge duration (steps) once in charging area
     * @param chargeThreshold Battery threshold to trigger charging diversion
     * @param lineWidth Grid line thickness
     * @param padding Cell padding in pixels
     * @param showGrid Whether to display grid lines
     */
    public AutonomousLogisticsEngine(SimProperties properties, int packageCount,
                                     int maxRobotCount,
                                     int batteryAutonomy, int rechargeTime, int chargeThreshold,
                                     float lineWidth, int padding, boolean showGrid) {
        super(properties);
        this.totalPackages = packageCount;
        this.maxRobots = Math.max(1, maxRobotCount);
        this.batteryAutonomy = Math.max(1, batteryAutonomy);
        this.rechargeTimeSteps = Math.max(1, rechargeTime);
        this.chargeThreshold = Math.max(0, chargeThreshold);
        this.gridLineWidth = lineWidth;
        this.cellPadding = padding;
        this.gridVisible = showGrid;

        // Initialize subsystems
        this.zoneLayout = new ZoneCoordinates("configuration.ini");
        this.intermediateStorage = new IntermediateStorageManager(
            zoneLayout.getIntermediateArea(1),
            zoneLayout.getIntermediateArea(2),
            zoneLayout.getIntermediateCapacityRatio()
        );
        this.rechargingArea = new RechargingAreaManager(zoneLayout.getRechargeArea());
        this.packageManager = new PackageScheduler(
            packageCount, properties.step, zoneLayout.getPackageGates(), properties.seed
        );
        this.robotManager = new RobotFactory(
            null,  // Will be set after environment creation
            zoneLayout, properties.field, properties.debug,
            properties.rows, properties.columns, properties.colorrobot,
            this.maxRobots, this.batteryAutonomy, this.rechargeTimeSteps, this.chargeThreshold
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
        int[][] workerLocations;
        Color workerVisual;
        try {
            Ini ini = new Ini(new File("configuration.ini"));
            int[] wc = parseInts(ini.get("color", "worker").trim());
            workerVisual = new Color(wc[0], wc[1], wc[2]);
            List<int[]> locs = new ArrayList<>();
            int idx = 1;
            while (true) {
                String val = ini.get("workers", "worker" + idx);
                if (val == null) break;
                locs.add(parseInts(val.trim()));
                idx++;
            }
            workerLocations = locs.toArray(int[][]::new);
        } catch (java.io.IOException | NumberFormatException e) {
            throw new RuntimeException("Failed to load worker config", e);
        }

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

            // Phase 5: Relay AMR-to-AMR messages (dyadic and broadcast)
            relayRobotMessages();

            // Phase 6: Update visual display
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
            int[] pickup = null;
            int[] dropoff = null;

            if (pkg.getStage() == PackageItem.Stage.WAITING_AT_GATE) {
                pickup = pkg.arrivalPosition;
                dropoff = zoneLayout.getDeliveryTarget(pkg.targetZone);
            } else if (pkg.getStage() == PackageItem.Stage.STORED_IN_INTERMEDIATE) {
                pickup = pkg.getIntermediateSlot();
                if (pickup == null) {
                    continue;
                }
                dropoff = zoneLayout.getDeliveryTarget(pkg.targetZone);
            } else {
                continue;
            }

            DeliveryBot robot = robotManager.trySpawnRobot(pkg, pickup, dropoff);

            if (robot != null) {
                if (!isRobotAlreadyPlaced(robot)) {
                    placeComponent(robot);
                }
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
            if (robot.needsRecharge()) {
                if (robot.isCarryingPackage() && !robot.isDropForChargeMission()) {
                    PackageItem pkg = robot.getPackage();
                    if (pkg != null && pkg.getStage() == PackageItem.Stage.WAITING_AT_GATE) {
                        int[] dropSlot = pkg.getIntermediateSlot();
                        if (dropSlot == null) {
                            dropSlot = intermediateStorage.reserveSlot(pkg.targetZone);
                            if (dropSlot != null) {
                                pkg.setIntermediateSlot(dropSlot);
                            }
                        }
                        if (dropSlot != null && robot.scheduleIntermediateDropForCharging(dropSlot)) {
                            System.out.printf("  [DROP-FOR-CHARGE] Robot#%d -> [%d,%d] for %s%n",
                                    robot.getId(), dropSlot[0], dropSlot[1], pkg);
                        }
                    }
                } else if (!robot.isDropForChargeMission()) {
                    int[] chargeSpot = rechargingArea.reserveSpot();
                    if (chargeSpot != null) {
                        robot.redirectToCharge(chargeSpot);
                        System.out.printf("  [CHARGE-START] Robot#%d -> [%d,%d]%n",
                                robot.getId(), chargeSpot[0], chargeSpot[1]);
                    }
                }
            }

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

            int[] releasedSpot = robot.consumeChargeSpotToRelease();
            if (releasedSpot != null) {
                rechargingArea.releaseSpot(releasedSpot);
                System.out.printf("  [CHARGE-DONE] Robot#%d at [%d,%d]%n",
                        robot.getId(), releasedSpot[0], releasedSpot[1]);
            }
        }
    }

    /**
     * Relays messages sent by robots to other robots.
     *
     * This method is only a communication medium. Decision logic remains
     * fully decentralized inside each robot.
     */
    private void relayRobotMessages() {
        List<DeliveryBot> robots = new ArrayList<>(robotManager.getActiveFleet());
        Map<Integer, DeliveryBot> byId = new HashMap<>();
        for (DeliveryBot robot : robots) {
            byId.put(robot.getId(), robot);
        }

        for (DeliveryBot sender : robots) {
            List<Message> outgoing = sender.popSentMessages();
            for (Message msg : outgoing) {
                int receiver = msg.getReceiver();
                if (receiver < 0) {
                    for (DeliveryBot target : robots) {
                        if (target.getId() == sender.getId()) {
                            continue;
                        }
                        Message copy = new Message(msg.getEmitter(), msg.getContent());
                        copy.setReceiver(target.getId());
                        target.receiveMessage(copy);
                    }
                } else {
                    DeliveryBot target = byId.get(receiver);
                    if (target != null && target.getId() != sender.getId()) {
                        Message copy = new Message(msg.getEmitter(), msg.getContent());
                        copy.setReceiver(target.getId());
                        target.receiveMessage(copy);
                    }
                }
            }
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
            after == DeliveryMission.Phase.DROPOFF) {
            PackageItem pkg = robot.getPackage();
            if (pkg.getStage() == PackageItem.Stage.WAITING_AT_GATE) {
                packageVisuals.remove(positionKey(pkg.arrivalPosition));
            } else if (pkg.getStage() == PackageItem.Stage.STORED_IN_INTERMEDIATE) {
                int[] slot = pkg.getIntermediateSlot();
                if (slot != null) {
                    packageVisuals.remove(positionKey(slot));
                    intermediateStorage.releaseSlot(pkg.targetZone, slot);
                    pkg.setIntermediateSlot(null);
                }
            }
            System.out.println("  [PICKUP] " + robot.getPackage() +
                             " by Robot#" + robot.getId());
        }

        // Package delivered
        if (before == DeliveryMission.Phase.DROPOFF &&
            after == DeliveryMission.Phase.COMPLETED) {
            PackageItem pkg = robot.getPackage();
            boolean droppedForCharge = robot.consumeDropForChargeFlag();

            if (droppedForCharge) {
                pkg.markStoredInIntermediate();
                int[] slot = pkg.getIntermediateSlot();
                if (slot != null) {
                    packageVisuals.put(positionKey(slot), pkg.displayColor);
                    packageManager.enqueueForAssignment(pkg);
                    System.out.printf("  [STORE-FOR-CHARGE] %s by Robot#%d at [%d,%d]%n",
                            pkg, robot.getId(), slot[0], slot[1]);
                }

                robot.clearMission();
                int[] chargeSpot = rechargingArea.reserveSpot();
                if (chargeSpot != null) {
                    robot.redirectToCharge(chargeSpot);
                    System.out.printf("  [CHARGE-START] Robot#%d -> [%d,%d]%n",
                            robot.getId(), chargeSpot[0], chargeSpot[1]);
                }
            } else {
                pkg.markDelivered();
                int deliveryTime = step - pkg.spawnStep;
                performanceTracker.recordDelivery(
                    pkg.packageId,
                    pkg.targetZone,
                    deliveryTime
                );
                System.out.printf("  [DELIVERY] %s by Robot#%d in %d steps%n",
                        pkg, robot.getId(), deliveryTime);
            }
        }

        // Robot completed mission and becomes idle (persistent AMR)
        if (before != DeliveryMission.Phase.COMPLETED &&
            after == DeliveryMission.Phase.COMPLETED &&
            !robot.isHeadingToCharge() && !robot.isCharging()) {
            robot.clearMission();
            System.out.println("  [IDLE] Robot#" + robot.getId());
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
            if (robot.isIdle() || robot.getPackage() == null) {
                continue;
            }
            activeStats.add(new int[]{
                robot.getPackage().packageId,
                robot.getPackage().targetZone,
                step - robot.getPackage().spawnStep
            });
        }

        // Prepare battery statistics for all robots
        List<int[]> batteryStats = new ArrayList<>();
        for (DeliveryBot robot : robotManager.getActiveFleet()) {
            batteryStats.add(new int[]{
                robot.getId(),
                robot.getBatteryLevel(),
                robot.getMaxBattery()
            });
        }

        // Prepare extended metrics {minTime, maxTime, avgTime * 10}
        int minTime = performanceTracker.getMinDeliveryTime();
        int maxTime = performanceTracker.getMaxDeliveryTime();
        int avgTimeX10 = (int) (performanceTracker.getAverageDeliveryTime() * 10);
        int[] extendedMetrics = {minTime, maxTime, avgTimeX10};

        displayWindow.updateStats(
            step,
            performanceTracker.getCompletedCount(),
            totalPackages,
            performanceTracker.getTotalDeliveryTime(),
            activeStats,
            performanceTracker.getHistoryAsArrays(),
            batteryStats,
            extendedMetrics
        );

        displayWindow.refresh();
    }

    /**
     * Initializes the graphical display window.
     */
    public void setupDisplay() {
        int cellSize = this.sp.display_width / this.sp.columns;

        // Load visual configuration from ini
        int[][] deliveryZones;
        Color packageGateColor;
        int gateRowsStart, gateRowsEnd;
        int[] separators;
        try {
            Ini ini = new Ini(new File("configuration.ini"));
            deliveryZones = new int[][] {
                parseInts(ini.get("zones", "delivery_zone1_visual").trim()),
                parseInts(ini.get("zones", "delivery_zone2_visual").trim())
            };
            int[] pgc = parseInts(ini.get("color", "package_gate").trim());
            packageGateColor = new Color(pgc[0], pgc[1], pgc[2]);
            gateRowsStart = Integer.parseInt(ini.get("display", "gate_rows_start").trim());
            gateRowsEnd   = Integer.parseInt(ini.get("display", "gate_rows_end").trim());
            separators    = parseInts(ini.get("display", "separator_rows").trim());
        } catch (java.io.IOException | NumberFormatException e) {
            throw new RuntimeException("Failed to load display config", e);
        }

        // Define waypoint zone visual areas
        int[][] waypointZones = {
            zoneLayout.getIntermediateArea(1),
            zoneLayout.getIntermediateArea(2)
        };
        int[][] rechargeZones = {
            zoneLayout.getRechargeArea()
        };

        Color[] rightPanelColors = new Color[this.sp.rows];

        // Package gates and robot spawn areas - unified color
        for (int r = gateRowsStart; r <= gateRowsEnd; r++) {
            rightPanelColors[r] = packageGateColor;
        }

        int[][] exits = {
            zoneLayout.getExit(1),
            zoneLayout.getExit(2)
        };

        displayWindow = new SimulatorGUI(
            (ColorSimpleCell[][]) this.environment.getGrid(),
            this.sp.display_x, this.sp.display_y,
            cellSize, this.sp.display_title,
            deliveryZones, waypointZones, rechargeZones,
            rightPanelColors, separators,
            exits,
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

    private boolean isRobotAlreadyPlaced(DeliveryBot robot) {
        int[] pos = robot.getLocation();
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) this.environment.getGrid();
        ColorSimpleCell cell = grid[pos[0]][pos[1]];
        return cell != null && cell.getContent() == robot;
    }

    private String positionKey(int[] pos) {
        return pos[0] + "," + pos[1];
    }

    private static int[] parseInts(String csv) {
        String[] parts = csv.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    private void logStepStatus(int step) {
        System.out.printf(
                "Step %d | Delivered: %d/%d | Fleet: %d/%d | Busy: %d | Pending: %d | Storage Z1: %d/%d | Z2: %d/%d | Charging: %d/%d%n",
                        step,
                        performanceTracker.getCompletedCount(),
                        totalPackages,
                        robotManager.getActiveCount(),
                        robotManager.getMaxRobots(),
                        robotManager.getBusyCount(),
                        packageManager.getPendingPackages().size(),
                        intermediateStorage.getOccupiedCount(1),
                        intermediateStorage.getCapacity(1),
                        intermediateStorage.getOccupiedCount(2),
                        intermediateStorage.getCapacity(2),
                        rechargingArea.getOccupiedCount(),
                        rechargingArea.getCapacity());
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
               !robotManager.hasBusyRobots() &&
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
        String maxAmrValue = ini.get("warehouse", "max_amrs");
        int maxRobotCount = (maxAmrValue == null || maxAmrValue.trim().isEmpty())
                ? packageCount
                : Integer.parseInt(maxAmrValue.trim());
        int batteryAutonomy = Integer.parseInt(ini.get("warehouse", "battery_autonomy").trim());
        int rechargeTime = Integer.parseInt(ini.get("warehouse", "recharge_time").trim());
        int chargeThreshold = Integer.parseInt(ini.get("warehouse", "charge_threshold").trim());
        float lineWidth = Float.parseFloat(ini.get("warehouse", "line_stroke").trim());
        int padding = Integer.parseInt(ini.get("warehouse", "padding").trim());
        boolean showGrid = Integer.parseInt(ini.get("warehouse", "show_grid").trim()) != 0;

        System.out.println("=== Configuration ===");
        System.out.println("Grid dimensions: " + properties.rows + " × " + properties.columns);
        System.out.println("Packages to process: " + packageCount);
        System.out.println("Max AMRs: " + maxRobotCount);
        System.out.println("Battery autonomy: " + batteryAutonomy + " moves");
        System.out.println("Recharge time: " + rechargeTime + " steps");
        System.out.println("Charge threshold: " + chargeThreshold + " moves");
        System.out.println("Grid line width: " + lineWidth + "px");

        AutonomousLogisticsEngine engine = new AutonomousLogisticsEngine(
            properties, packageCount, maxRobotCount,
            batteryAutonomy, rechargeTime, chargeThreshold,
            lineWidth, padding, showGrid
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
