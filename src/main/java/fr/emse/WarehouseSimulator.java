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
    private List<Pallet> activePallets;           // Pallets currently in system
    private int palletCounter;                    // Counter for pallet IDs
    private List<WarehouseRobot> activeRobots;    // Track robots manually

    // Statistics (for Phase 6)
    private int totalDeliveryTime;           // Sum of all delivery times
    private int palletsDelivered;            // Number of pallets delivered
    private int currentStep;                 // Current simulation step

    public WarehouseSimulator(SimProperties sp) {
        super(sp);
        this.activePallets = new ArrayList<>();
        this.activeRobots = new ArrayList<>();
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
        // Don't create robots here - will be created in schedule()
        System.out.println("Robot creation will be done at start of schedule()");
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

        addNewComponent(robot);
        activeRobots.add(robot);  // Track robot manually

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
     * Removes robot and its pallet from the simulation after successful delivery.
     * @param robot Robot to remove
     */
    private void removeRobotAndPallet(WarehouseRobot robot) {
        Pallet pallet = robot.getCurrentPallet();

        // Remove pallet from tracking (pallet is not on grid, it's carried by robot)
        if (pallet != null) {
            activePallets.remove(pallet);
            System.out.println("Removed pallet #" + pallet.getId() + " from tracking");
        }

        // Remove robot from grid and component list
        this.environment.removeCellContent(robot.getX(), robot.getY());
        System.out.println("Removed robot '" + robot.getName() + "' from grid at (" +
                           robot.getX() + "," + robot.getY() + ")");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void schedule() {
        // Create first pallet and robot at start of simulation
        System.out.println("\n--- Creating first pallet and robot ---");
        Pallet firstPallet = createPallet();
        createRobotWithPallet(firstPallet);
        refreshGW();

        System.out.println("Initial robot count: " + activeRobots.size());

        for (int i = 0; i < this.sp.step; i++) {
            currentStep = i;
            System.out.println("\n========== Step: " + currentStep + " ==========");
            System.out.println("Active robots: " + activeRobots.size());

            // Distribute messages between robots (for future use)
            for (WarehouseRobot r : activeRobots) {
                for (WarehouseRobot rr : activeRobots) {
                    for (Message m : (List<Message>) ((ColorInteractionRobot) rr).popSentMessages()) {
                        if (r.getId() != rr.getId()) {
                            ((ColorInteractionRobot) r).receiveMessage(m);
                        }
                    }
                }
            }

            // Move each robot and check for deliveries
            List<WarehouseRobot> robotsCopy = new ArrayList<>(activeRobots);
            List<WarehouseRobot> robotsToRemove = new ArrayList<>();

            for (WarehouseRobot r : robotsCopy) {
                int[] oldPos = r.getLocation();
                ColorSimpleCell[][] per = this.environment.getNeighbor(r.getX(), r.getY(), r.getField());
                r.updatePerception(per);
                r.move(1);
                updateEnvironment(oldPos, r.getLocation(), r.getId());

                // Check if robot reached goal - handle delivery and remove IMMEDIATELY
                if (r.hasReachedGoal()) {
                    handleDelivery(r);
                    removeRobotAndPallet(r);  // Remove from grid immediately to free the goal
                    robotsToRemove.add(r);
                }
            }

            // Remove delivered robots from tracking list
            for (WarehouseRobot wr : robotsToRemove) {
                activeRobots.remove(wr);  // Remove from our tracking list
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

            // Stop if no robots left
            if (activeRobots.isEmpty()) {
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
