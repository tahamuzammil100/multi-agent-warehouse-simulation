package fr.emse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import fr.emse.fayol.maqit.simulator.ColorSimFactory;
import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.components.ColorRobot;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.configuration.IniFile;
import fr.emse.fayol.maqit.simulator.configuration.SimProperties;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * Warehouse simulation where robots transport pallets from entry zones to exit zones.
 * Implements the Reference Model: 1 robot per pallet, no battery management, no intermediate areas.
 */
public class WarehouseSimulator extends ColorSimFactory {

    // Zones
    private int[] entryZonePosition;   // Entry zone (Zone A) position
    private ExitZone exitZone;         // Exit zone (Zone Z)

    // Pallets and tracking
    private List<Pallet> activePallets;      // Pallets currently in system
    private int palletCounter;               // Counter for pallet IDs

    // Statistics (for Phase 6)
    private int totalDeliveryTime;           // Sum of all delivery times
    private int palletsDelivered;            // Number of pallets delivered
    private int currentStep;                 // Current simulation step

    public WarehouseSimulator(SimProperties sp) {
        super(sp);
        this.activePallets = new ArrayList<>();
        this.palletCounter = 0;
        this.totalDeliveryTime = 0;
        this.palletsDelivered = 0;
        this.currentStep = 0;

        // Fixed positions for simple initial setup
        this.entryZonePosition = new int[]{0, 0};  // Top-left corner
    }

    @Override
    public void createEnvironment() {
        this.environment = new ColorGridEnvironment(this.sp.seed);
        System.out.println("Created " + this.sp.rows + "x" + this.sp.columns + " warehouse environment");
    }

    @Override
    public void createObstacle() {
        // Reduce obstacles to ensure clear paths exist
        int numObstacles = Math.min(this.sp.nbobstacle, 5);  // Max 5 obstacles

        int[] obstacleRgb = new int[]{
            this.sp.colorobstacle.getRed(),
            this.sp.colorobstacle.getGreen(),
            this.sp.colorobstacle.getBlue()
        };

        for (int i = 0; i < numObstacles; i++) {
            int[] pos = this.environment.getPlace();

            // Don't place obstacles at entry or exit zones
            while ((pos[0] == entryZonePosition[0] && pos[1] == entryZonePosition[1]) ||
                   (pos[0] == this.sp.rows - 1 && pos[1] == this.sp.columns - 1)) {
                pos = this.environment.getPlace();
            }

            ColorObstacle obstacle = new ColorObstacle(pos, obstacleRgb);
            addNewComponent(obstacle);
        }
        System.out.println("Created " + numObstacles + " obstacles");
    }

    @Override
    public void createGoal() {
        // Create exit zone at bottom-right corner
        int[] exitPos = new int[]{this.sp.rows - 1, this.sp.columns - 1};
        int[] goalRgb = new int[]{
            this.sp.colorgoal.getRed(),
            this.sp.colorgoal.getGreen(),
            this.sp.colorgoal.getBlue()
        };

        this.exitZone = new ExitZone(1, exitPos, goalRgb);
        this.exitZone.setLocation(exitPos);
        addNewComponent(this.exitZone);

        System.out.println("Created exit zone at (" + exitPos[0] + "," + exitPos[1] + ")");
    }

    @Override
    public void createRobot() {
        // Create initial pallet and robot directly
        System.out.println("Creating initial pallet and robot in createRobot()...");

        // Create pallet
        int[] palletRgb = new int[]{255, 255, 0};  // Yellow
        int[] palletPos = new int[]{entryZonePosition[0], entryZonePosition[1]};
        Pallet pallet = new Pallet(palletPos, palletRgb, currentStep, exitZone.getZoneId());
        pallet.setLocation(palletPos);
        addNewComponent(pallet);
        activePallets.add(pallet);
        palletCounter++;
        System.out.println("Created pallet #" + palletCounter + " at entry zone");

        // Create robot
        int[] robotPos = new int[]{entryZonePosition[0], entryZonePosition[1]};
        WarehouseRobot robot = new WarehouseRobot(
            "Robot" + palletCounter,
            this.sp.field,
            this.sp.debug,
            robotPos,
            this.sp.colorrobot,
            this.sp.rows,
            this.sp.columns
        );
        robot.assignPallet(pallet);
        robot.setGoalPosition(exitZone.getPosition());

        System.out.println("DEBUG: Adding robot directly in createRobot()");
        addNewComponent(robot);
        System.out.println("DEBUG: Robot count after add: " + this.environment.getRobot().size());
        System.out.println("Created robot '" + robot.getName() + "' with pallet");
    }

    /**
     * Creates a new pallet at the entry zone.
     * @return The created pallet
     */
    private Pallet createPallet() {
        int[] palletRgb = new int[]{255, 255, 0};  // Yellow color for pallets
        int[] palletPos = new int[]{entryZonePosition[0], entryZonePosition[1]};

        Pallet pallet = new Pallet(palletPos, palletRgb, currentStep, exitZone.getZoneId());
        pallet.setLocation(palletPos);
        addNewComponent(pallet);
        activePallets.add(pallet);

        palletCounter++;
        System.out.println("Created pallet #" + palletCounter + " at entry zone (" +
                           palletPos[0] + "," + palletPos[1] + ")");
        return pallet;
    }

    /**
     * Creates a robot at the entry zone and assigns it a pallet.
     * @param pallet Pallet to assign to the robot
     * @return The created robot
     */
    private WarehouseRobot createRobotWithPallet(Pallet pallet) {
        int[] robotPos = new int[]{entryZonePosition[0], entryZonePosition[1]};

        WarehouseRobot robot = new WarehouseRobot(
            "Robot" + palletCounter,
            this.sp.field,
            this.sp.debug,
            robotPos,
            this.sp.colorrobot,
            this.sp.rows,
            this.sp.columns
        );

        // Assign pallet and goal to robot
        robot.assignPallet(pallet);
        robot.setGoalPosition(exitZone.getPosition());

        System.out.println("DEBUG: About to add robot with ID: " + robot.getId());
        System.out.println("DEBUG: Robot is instance of ColorRobot: " + (robot instanceof fr.emse.fayol.maqit.simulator.components.ColorRobot));
        addNewComponent(robot);
        System.out.println("DEBUG: Robot added. Current robot count from environment: " + this.environment.getRobot().size());

        System.out.println("Created robot '" + robot.getName() +
                           "' at entry zone with pallet #" + palletCounter);
        return robot;
    }

    /**
     * Initializes the first pallet and robot.
     */
    public void initializeFirstDelivery() {
        Pallet firstPallet = createPallet();
        createRobotWithPallet(firstPallet);
    }

    /**
     * Handles a successful delivery when robot reaches goal.
     * Calculates delivery time and updates statistics.
     * @param robot Robot that completed delivery
     */
    private void handleDelivery(WarehouseRobot robot) {
        Pallet pallet = robot.getCurrentPallet();
        if (pallet == null) {
            System.out.println("WARNING: Robot " + robot.getName() + " reached goal without pallet!");
            return;
        }

        // Calculate delivery time
        int deliveryTime = currentStep - pallet.getEntryTime();

        // Update statistics (Phase 6)
        totalDeliveryTime += deliveryTime;
        palletsDelivered++;

        // Record delivery in exit zone
        exitZone.recordDelivery(pallet.getId());

        System.out.println("\n*** DELIVERY COMPLETE! ***");
        System.out.println("Robot: " + robot.getName());
        System.out.println("Pallet: #" + palletCounter + " (ID: " + pallet.getId() + ")");
        System.out.println("Entry time: Step " + pallet.getEntryTime());
        System.out.println("Delivery time: Step " + currentStep);
        System.out.println("Time taken: " + deliveryTime + " steps");
        System.out.println("Total pallets delivered: " + palletsDelivered);
        System.out.println("Average delivery time: " +
                           String.format("%.2f", (double) totalDeliveryTime / palletsDelivered) + " steps");
        System.out.println("***************************\n");
    }

    /**
     * Removes robot and its pallet from the simulation.
     * @param robot Robot to remove
     */
    private void removeRobotAndPallet(WarehouseRobot robot) {
        Pallet pallet = robot.getCurrentPallet();

        // Remove pallet from grid
        if (pallet != null) {
            this.environment.removeCellContent(pallet.getX(), pallet.getY());
            activePallets.remove(pallet);
            System.out.println("Removed pallet from (" + pallet.getX() + "," + pallet.getY() + ")");
        }

        // Remove robot from grid
        this.environment.removeCellContent(robot.getX(), robot.getY());
        System.out.println("Removed robot '" + robot.getName() + "' from (" +
                           robot.getX() + "," + robot.getY() + ")");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void schedule() {
        List<ColorRobot<ColorSimpleCell>> robots = this.environment.getRobot();
        System.out.println("DEBUG: Initial robot count: " + robots.size());

        for (int i = 0; i < this.sp.step; i++) {
            currentStep = i;
            System.out.println("\n========== Step: " + currentStep + " ==========");
            System.out.println("Active robots: " + robots.size());

            // Distribute messages between robots (for future use)
            for (ColorRobot<ColorSimpleCell> r : robots) {
                for (ColorRobot<ColorSimpleCell> rr : robots) {
                    for (Message m : (List<Message>) ((ColorInteractionRobot) rr).popSentMessages()) {
                        if (r.getId() != rr.getId()) {
                            ((ColorInteractionRobot) r).receiveMessage(m);
                        }
                    }
                }
            }

            // Move each robot and check for deliveries
            List<ColorRobot<ColorSimpleCell>> robotsCopy = new ArrayList<>(robots);
            List<WarehouseRobot> robotsToRemove = new ArrayList<>();

            for (ColorRobot<ColorSimpleCell> r : robotsCopy) {
                int[] oldPos = r.getLocation();
                ColorSimpleCell[][] per = this.environment.getNeighbor(r.getX(), r.getY(), r.getField());
                r.updatePerception(per);
                r.move(1);
                updateEnvironment(oldPos, r.getLocation(), r.getId());

                // Check if robot reached goal - handle delivery
                if (r instanceof WarehouseRobot) {
                    WarehouseRobot wr = (WarehouseRobot) r;
                    if (wr.hasReachedGoal()) {
                        handleDelivery(wr);
                        robotsToRemove.add(wr);
                    }
                }
            }

            // Remove delivered robots and their pallets
            for (WarehouseRobot wr : robotsToRemove) {
                removeRobotAndPallet(wr);
            }

            // Dynamic respawning: Create new pallets and robots after deliveries (Phase 7)
            for (int j = 0; j < robotsToRemove.size(); j++) {
                System.out.println("\n--- Spawning new pallet and robot ---");
                Pallet newPallet = createPallet();
                createRobotWithPallet(newPallet);
            }

            // Refresh display
            refreshGW();

            // Wait between steps
            try {
                Thread.sleep(this.sp.waittime);
            } catch (InterruptedException ex) {
                System.out.println(ex);
            }

            // Get updated robot list for next iteration
            robots = this.environment.getRobot();

            // Stop if no robots left
            if (robots.isEmpty()) {
                System.out.println("\nAll robots have completed deliveries!");
                break;
            }
        }

        // Print final statistics
        printStatistics();
    }

    /**
     * Prints simulation statistics.
     */
    private void printStatistics() {
        System.out.println("\n========================================");
        System.out.println("SIMULATION COMPLETE");
        System.out.println("========================================");
        System.out.println("Pallets delivered: " + palletsDelivered);
        System.out.println("Total delivery time: " + totalDeliveryTime + " steps");
        if (palletsDelivered > 0) {
            double avgTime = (double) totalDeliveryTime / palletsDelivered;
            System.out.println("Average delivery time: " + String.format("%.2f", avgTime) + " steps");
        }
        System.out.println("========================================");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("===========================================");
        System.out.println("WAREHOUSE ROBOT SIMULATOR - Reference Model");
        System.out.println("===========================================\n");

        IniFile ifile = new IniFile("configuration.ini");
        SimProperties sp = new SimProperties(ifile);
        sp.simulationParams();
        sp.displayParams();

        System.out.println("Simulation parameters:");
        System.out.println("  Grid: " + sp.rows + "x" + sp.columns);
        System.out.println("  Entry zone: (0, 0)");
        System.out.println("  Exit zone: (" + (sp.rows - 1) + ", " + (sp.columns - 1) + ")");
        System.out.println("  Steps: " + sp.step);
        System.out.println("  Seed: " + sp.seed);
        System.out.println("  Field: " + sp.field);
        System.out.println("  Wait time: " + sp.waittime + "ms\n");

        WarehouseSimulator sim = new WarehouseSimulator(sp);
        sim.createEnvironment();
        sim.createObstacle();
        sim.createGoal();
        sim.createRobot();  // This creates the first pallet and robot
        sim.initializeGW();
        sim.refreshGW();

        // Start simulation
        sim.schedule();
    }
}
