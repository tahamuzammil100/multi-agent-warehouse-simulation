package fr.emse;

import java.awt.Color;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.ini4j.Ini;

import fr.emse.fayol.maqit.simulator.SimFactory;
import fr.emse.fayol.maqit.simulator.configuration.IniFile;
import fr.emse.fayol.maqit.simulator.configuration.SimProperties;
import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.components.ColorSituatedComponent;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * Warehouse Simulator — Package-Triggered Robot Model
 *
 * Layout (15 rows × 20 cols):
 *   Package entry zones : col 19, rows 3-5 (zone A), 6-8 (zone B), 9-11 (zone C)
 *   Robot entry 1       : (2, 19)  — spawns zone-1 robots
 *   Robot entry 2       : (12, 19) — spawns zone-2 robots
 *   Delivery zone 1     : top-left    rows 1-2,  cols 1-3  (target centre {2,2})
 *   Delivery zone 2     : bottom-left rows 12-13, cols 1-3 (target centre {13,2})
 *   Robot exit zone 1   : rows 0-2,   col 0  (target {1,0})
 *   Robot exit zone 2   : rows 12-14, col 0  (target {13,0})
 *
 * Flow:
 *   1. Package arrives randomly at a package entry cell, assigned green (zone 1)
 *      or orange (zone 2).
 *   2. A robot spawns from the corresponding robot entry point.
 *   3. Robot navigates: robot-entry → package cell → delivery point → exit cell.
 *   4. Robot is removed from the grid after reaching the exit.
 */
public class WarehouseSimulator extends SimFactory<ColorGridEnvironment, ColorSituatedComponent> {

    // ---- Hardcoded zone layout -----------------------------------------------

    private static final int[][] PACKAGE_ENTRIES = {
        {3,19},{4,19},{5,19},        // entry zone A
        {6,19},{7,19},{8,19},        // entry zone B
        {9,19},{10,19},{11,19}       // entry zone C
    };

    private static final int[] ROBOT_ENTRY_1 = {2,  19};  // zone-1 robot spawn
    private static final int[] ROBOT_ENTRY_2 = {12, 19};  // zone-2 robot spawn

    private static final int[] SAFEPOINT_1   = {4,  11};  // centre of safepoint 1 (rows 3-5, cols 11-12) — zone 1
    private static final int[] SAFEPOINT_2   = {10, 11};  // centre of safepoint 2 (rows 9-11, cols 11-12) — zone 2

    private static final int[] DELIVERY_1    = {2,  2};   // centre of top-left zone 1 (rows 1-2, cols 1-3)
    private static final int[] DELIVERY_2    = {13, 2};   // centre of bottom-left zone 2 (rows 12-13, cols 1-3)

    private static final int[] EXIT_1        = {1,  0};   // middle of zone-1 exit column (rows 0-2, col 0)
    private static final int[] EXIT_2        = {13, 0};   // middle of zone-2 exit column (rows 12-14, col 0)

    // ---- Fields --------------------------------------------------------------

    protected WarehouseDisplay warehouseDisplay;

    private final int totalPackageCount;
    private final float lineStroke;
    private final int padding;
    private final boolean showGrid;

    /** All packages pre-generated, sorted by arrival step. */
    private final Queue<PackageItem> upcomingPackages = new LinkedList<>();

    /** Packages that have arrived but not yet assigned a robot. */
    private final List<PackageItem> waitingPackages = new ArrayList<>();

    /** Currently active robots (spawned on demand). */
    private final List<DeliveryBot> activeRobots = new ArrayList<>();

    /** Human agents. */
    private final List<HumanAgent> humans = new ArrayList<>();

    /** Package display overlay: "row,col" → colour. Updated each step. */
    private final Map<String, Color> packageOverlay = new ConcurrentHashMap<>();

    private long totalDeliveryTime = 0;
    private int  deliveredCount    = 0;
    private int  nextRobotId       = 0;

    /**
     * Completed deliveries: each entry is int[]{palletId, zone, deliverySteps}.
     * Passed to the stats panel each step.
     */
    private final List<int[]> deliveredRecords = new CopyOnWriteArrayList<>();

    private Random random;

    // ---- Constructor ---------------------------------------------------------

    public WarehouseSimulator(SimProperties sp, int totalPackages, float lineStroke, int padding, boolean showGrid) {
        super(sp);
        this.totalPackageCount = totalPackages;
        this.lineStroke   = lineStroke;
        this.padding      = padding;
        this.showGrid     = showGrid;
        this.random = new Random(sp.seed);
    }

    // ---- SimFactory overrides ------------------------------------------------

    @Override
    public void createEnvironment() {
        this.environment = new ColorGridEnvironment(this.sp.seed);
    }

    @Override
    public void createObstacle() {
        int[] rgb = {
            this.sp.colorobstacle.getRed(),
            this.sp.colorobstacle.getGreen(),
            this.sp.colorobstacle.getBlue()
        };

        int[][] positions = {
            {0,  5},                                                                                                                                                                           
            {2, 11}, {2, 14},                                                                                                                                                                  
            {5,  2}, {5,  6},                                                                                                                                                                  
            {6, 15},                                                                                                                                                                           
            {7,  1},                                                                                                                                                                           
            {8,  7},                                                                                                                                                                           
            {9,  4},                                                                                                                                                                           
            {11, 10},                                                                                                                                                                          
            {13,  7}, {13, 16}                                                                                                                                                                 
        }; 

        for (int[] pos : positions) addNewComponent(new ColorObstacle(pos, rgb));
    }

    @Override
    public void createRobot() {
        // Pre-generate all packages; spread arrivals randomly across first half of simulation
        int spreadSteps = Math.max(1, Math.min(this.sp.step / 2, 200));
        for (int i = 0; i < totalPackageCount; i++) {
            int[] entryCell  = PACKAGE_ENTRIES[random.nextInt(PACKAGE_ENTRIES.length)].clone();
            int deliveryZone = random.nextInt(2) + 1;
            int arrivalStep  = random.nextInt(spreadSteps);
            upcomingPackages.add(new PackageItem(i, entryCell, deliveryZone, arrivalStep));
        }
        // Sort by arrival step
        List<PackageItem> sorted = new ArrayList<>(upcomingPackages);
        sorted.sort(Comparator.comparingInt(p -> p.spawnStep));
        upcomingPackages.clear();
        upcomingPackages.addAll(sorted);

        // Create human agents
        int[][] humanPositions = {{4,8},{7,4},{8,11},{8,17},{11,8},{13,13}};
        Color humanColor = new Color(200, 160, 100);
        for (int i = 0; i < humanPositions.length; i++) {
            HumanAgent h = new HumanAgent(
                "Human" + i, this.sp.field, humanPositions[i],
                humanColor, this.sp.rows, this.sp.columns,
                this.environment, (long) this.sp.seed + i + 1
            );
            addNewComponent(h);
            humans.add(h);
        }
    }

    @Override
    public void createGoal() {
        // Visual zones are defined in initializeGW(). No grid objects needed.
    }

    @Override
    public void addNewComponent(ColorSituatedComponent sc) {
        int[] pos = sc.getLocation();
        this.environment.setCellContent(pos[0], pos[1], sc);
    }

    @Override
    public void updateEnvironment(int[] from, int[] to, int id) {
        this.environment.moveComponent(from, to);
    }

    // ---- Simulation loop -----------------------------------------------------

    @Override
    public void schedule() {
        System.out.println("=== Warehouse Simulation Start ===");
        System.out.println("Total packages: " + totalPackageCount);

        for (int step = 0; step < this.sp.step; step++) {
            System.out.printf("Step %d | Delivered: %d/%d | Active: %d | Waiting: %d%n",
                    step, deliveredCount, totalPackageCount,
                    activeRobots.size(), waitingPackages.size());

            // 1. Release packages arriving at this step
            while (!upcomingPackages.isEmpty()
                    && upcomingPackages.peek().spawnStep <= step) {
                PackageItem pkg = upcomingPackages.poll();
                waitingPackages.add(pkg);
                packageOverlay.put(key(pkg.arrivalPosition), pkg.displayColor);
                System.out.println("  [ARRIVED]  " + pkg);
            }

            // 2. Try to spawn a robot for each waiting package (if entry cell is free)
            for (PackageItem pkg : new ArrayList<>(waitingPackages)) {
                int[] robotEntry = (pkg.targetZone == 1) ? ROBOT_ENTRY_1 : ROBOT_ENTRY_2;
                if (isCellFree(robotEntry)) {
                    spawnRobot(pkg);
                    waitingPackages.remove(pkg);
                }
            }

            // 3. Move humans
            for (HumanAgent h : humans) {
                int[] hOld = h.getLocation();
                h.updatePerception(this.environment.getNeighbor(h.getX(), h.getY(), h.getField()));
                h.move(1);
                int[] hNew = h.getLocation();
                if (hNew[0] != hOld[0] || hNew[1] != hOld[1])
                    updateEnvironment(hOld, hNew, h.getId());
            }

            // 4. Move active delivery robots and handle state transitions
            List<DeliveryBot> toRemove = new ArrayList<>();
            for (DeliveryBot robot : activeRobots) {
                DeliveryBot.RobotRobotState before = robot.getRobotState();
                int[] oldPos = robot.getLocation();

                robot.updatePerception(
                        this.environment.getNeighbor(robot.getX(), robot.getY(), robot.getField()));
                robot.move(1);

                int[] newPos = robot.getLocation();
                if (newPos[0] != oldPos[0] || newPos[1] != oldPos[1])
                    updateEnvironment(oldPos, newPos, robot.getId());

                DeliveryBot.RobotRobotState after = robot.getRobotState();

                if (before == DeliveryBot.RobotRobotState.GOING_TO_PACKAGE
                        && after == DeliveryBot.RobotRobotState.GOING_TO_SAFEPOINT) {
                    packageOverlay.remove(key(robot.getPackage().arrivalPosition));
                    System.out.println("  [PICKED UP] " + robot.getPackage()
                            + " by Robot#" + robot.getId());
                }

                if (before == DeliveryBot.RobotRobotState.GOING_TO_DELIVERY
                        && after == DeliveryBot.RobotRobotState.GOING_TO_EXIT) {
                    int tp = step - robot.getPackage().spawnStep;
                    totalDeliveryTime += tp;
                    deliveredCount++;
                    deliveredRecords.add(new int[]{
                        robot.getPackage().packageId, robot.getPackage().targetZone, tp
                    });
                    System.out.printf("  [DELIVERED] %s by Robot#%d in %d steps%n",
                            robot.getPackage(), robot.getId(), tp);
                }

                if (after == DeliveryBot.RobotRobotState.DONE) {
                    clearCell(robot.getLocation());
                    toRemove.add(robot);
                    System.out.println("  [EXITED]   Robot#" + robot.getId());
                }
            }
            activeRobots.removeAll(toRemove);

            // 5. Refresh display
            warehouseDisplay.setPackageOverlay(packageOverlay);

            // Build active-robot stats: {packageId, zone, stepsInTransit}
            List<int[]> activeStats = new ArrayList<>();
            for (DeliveryBot robot : activeRobots) {
                activeStats.add(new int[]{
                    robot.getPackage().packageId,
                    robot.getPackage().targetZone,
                    step - robot.getPackage().spawnStep
                });
            }
            warehouseDisplay.updateStats(step, deliveredCount, totalPackageCount,
                                         totalDeliveryTime, activeStats, deliveredRecords);

            refreshGW();

            try {
                Thread.sleep(this.sp.waittime);
            } catch (InterruptedException e) {
                System.out.println(e);
            }

            if (deliveredCount >= totalPackageCount
                    && activeRobots.isEmpty()
                    && waitingPackages.isEmpty()) break;
        }

        printResults();
    }

    // ---- Graphical window ----------------------------------------------------

    public void initializeGW() {
        int cellSize = this.sp.display_width / this.sp.columns;

        // Oval zones: hatched red (zone 1 = top-left, zone 2 = bottom-left)
        int[][] ovalZones = {
            {1, 1, 2,  3},   // zone 1 — top-left    (rows 1-2,  cols 1-3)
            {12, 1, 13, 3}   // zone 2 — bottom-left (rows 12-13, cols 1-3)
        };

        // Right-panel colours (cols 18-19)
        Color blue       = new Color(120, 185, 225);   // robot entry cells
        Color lightGreen = new Color(150, 215, 150);   // package entry cells
        Color[] rowRightColors = new Color[this.sp.rows];
        rowRightColors[2]  = blue;         // robot entry 1
        rowRightColors[12] = blue;         // robot entry 2
        for (int r = 3; r <= 11; r++)
            rowRightColors[r] = lightGreen; // package entry zone

        // Separator lines: after robot entry 1 (row 2), between package sub-zones (5,8), after zone (11)
        int[] separatorRows = {2, 5, 8, 11};

        warehouseDisplay = new WarehouseDisplay(
            (ColorSimpleCell[][]) this.environment.getGrid(),
            this.sp.display_x, this.sp.display_y,
            cellSize, this.sp.display_title,
            ovalZones,
            new int[][]{{3, 11, 5, 12}, {9, 11, 11, 12}},  // safepoint zones
            rowRightColors,
            separatorRows,
            this.lineStroke,
            this.padding,
            this.showGrid
        );
        warehouseDisplay.init();
    }

    public void refreshGW() {
        warehouseDisplay.refresh();
    }

    // ---- Helpers -------------------------------------------------------------

    private void spawnRobot(PackageItem pkg) {
        int[] robotEntry    = (pkg.targetZone == 1) ? ROBOT_ENTRY_1 : ROBOT_ENTRY_2;
        int[] safepointPos  = (pkg.targetZone == 1) ? SAFEPOINT_1   : SAFEPOINT_2;
        int[] deliveryPos   = (pkg.targetZone == 1) ? DELIVERY_1    : DELIVERY_2;
        int[] exitPos       = (pkg.targetZone == 1) ? EXIT_1        : EXIT_2;

        DeliveryBot robot = new DeliveryBot(
            "Robot" + nextRobotId++,
            this.sp.field, this.sp.debug,
            robotEntry,
            this.sp.colorrobot,
            this.sp.rows, this.sp.columns,
            this.environment,
            pkg,
            pkg.arrivalPosition,
            safepointPos,
            deliveryPos,
            exitPos
        );
        addNewComponent(robot);
        activeRobots.add(robot);
        System.out.println("  [SPAWNED]  Robot#" + robot.getId() + " for " + pkg);
    }

    private boolean isCellFree(int[] pos) {
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) this.environment.getGrid();
        ColorSimpleCell cell = grid[pos[0]][pos[1]];
        return cell == null || cell.getContent() == null;
    }

    private void clearCell(int[] pos) {
        // removeCellContent() only clears SimpleCell.content (SituatedComponent),
        // but the display reads ColorSimpleCell.content (ColorSituatedComponent) — a separate
        // shadowed field. We must null it directly via reflection.
        try {
            ColorSimpleCell[][] grid = (ColorSimpleCell[][]) this.environment.getGrid();
            ColorSimpleCell cell = grid[pos[0]][pos[1]];
            if (cell != null) {
                java.lang.reflect.Field f = cell.getClass().getDeclaredField("content");
                f.setAccessible(true);
                f.set(cell, null);
            }
        } catch (ReflectiveOperationException ignored) {}
        this.environment.removeCellContent(pos[0], pos[1]);
    }

    private String key(int[] pos) {
        return pos[0] + "," + pos[1];
    }

    private void printResults() {
        System.out.println("\n=== RESULTS ===");
        System.out.println("Delivered: " + deliveredCount + "/" + totalPackageCount);
        System.out.println("Total delivery time:   " + totalDeliveryTime + " steps");
        System.out.printf ("Average delivery time: %.1f steps/package%n",
                deliveredCount > 0 ? (double) totalDeliveryTime / deliveredCount : 0.0);
    }

    // ---- Entry point ---------------------------------------------------------

    public static void main(String[] args) throws Exception {
        IniFile ifile = new IniFile("configuration.ini");
        SimProperties sp = new SimProperties(ifile);
        sp.simulationParams();
        sp.displayParams();

        Ini ini = new Ini(new File("configuration.ini"));
        int     totalPackages = Integer.parseInt(ini.get("warehouse", "total_pallets").trim());
        float   lineStroke    = Float.parseFloat(ini.get("warehouse", "line_stroke").trim());
        int     padding       = Integer.parseInt(ini.get("warehouse", "padding").trim());
        boolean showGrid      = Integer.parseInt(ini.get("warehouse", "show_grid").trim()) != 0;

        System.out.println("=== Configuration ===");
        System.out.println("Grid:           " + sp.rows + " x " + sp.columns);
        System.out.println("Total packages: " + totalPackages);
        System.out.println("Line stroke:    " + lineStroke + "px");

        WarehouseSimulator sim = new WarehouseSimulator(sp, totalPackages, lineStroke, padding, showGrid);
        sim.createEnvironment();
        sim.createObstacle();
        sim.createRobot();
        sim.createGoal();
        sim.initializeGW();
        sim.refreshGW();
        sim.schedule();
    }
}
