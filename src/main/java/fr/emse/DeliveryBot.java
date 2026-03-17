package fr.emse;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * DeliveryBot - Persistent AMR with mission execution and battery management.
 */
public class DeliveryBot extends ColorInteractionRobot<ColorSimpleCell> {

    private enum Mode {
        IDLE,
        WORKING,
        GOING_TO_CHARGE,
        CHARGING
    }

    private final PathPlanner routePlanner;
    private final NavigationController navigator;
    private final CollisionManager collisionDetector;
    private final ColorGridEnvironment warehouseEnvironment;

    private final int maxBattery;
    private final int rechargeDuration;
    private final int chargeThreshold;

    private DeliveryMission mission;
    private DeliveryMission pausedMission;
    private Mode mode;

    private int batteryLevel;
    private int chargingStepsRemaining;
    private int[] chargingSpot;
    private int[] chargeSpotToRelease;
    private boolean dropForChargeMission;

    // Decentralized communication state
    private static final int BCAST_RECEIVER = -1;
    private static final int TEAMMATE_TTL_STEPS = 6;
    private static final int BCAST_PERIOD_STEPS = 4;
    private final Map<Integer, int[]> teammatePositions;
    private final Map<Integer, Integer> teammateTtls;
    private int localStepCounter;

    public DeliveryBot(String name, int field, int debug, int[] pos, Color color,
                       int rows, int cols, ColorGridEnvironment env,
                       PackageItem packageItem, int[] packagePos,
                       int[] deliveryPos,
                       int batteryAutonomy, int rechargeTime, int chargeThreshold) {

        super(name, field, pos,
              new int[]{color.getRed(), color.getGreen(), color.getBlue()});

        this.warehouseEnvironment = env;
        this.routePlanner = new PathPlanner(env, rows, cols);
        this.navigator = new NavigationController(this);
        this.collisionDetector = new CollisionManager(env, rows, cols);

        this.maxBattery = Math.max(1, batteryAutonomy);
        this.rechargeDuration = Math.max(1, rechargeTime);
        this.chargeThreshold = Math.max(0, Math.min(this.maxBattery, chargeThreshold));
        this.batteryLevel = this.maxBattery;

        this.mission = new DeliveryMission(packageItem, packagePos, deliveryPos);
        this.pausedMission = null;
        this.mode = Mode.WORKING;

        this.chargingStepsRemaining = 0;
        this.chargingSpot = null;
        this.chargeSpotToRelease = null;
        this.dropForChargeMission = false;
        this.teammatePositions = new HashMap<>();
        this.teammateTtls = new HashMap<>();
        this.localStepCounter = 0;

        setCurrentOrientation(Orientation.up);
        replanRoute();
    }

    @Override
    public void move(int nb) {
        for (int i = 0; i < nb; i++) {
            performSingleStep();
        }
    }

    private void performSingleStep() {
        // Handle incoming AMR messages before deciding actions this step.
        readMessages();
        decayTeammateInfo();
        localStepCounter++;
        if (localStepCounter % BCAST_PERIOD_STEPS == 0) {
            broadcastStatus();
        }

        if (mode == Mode.IDLE) {
            return;
        }

        if (mode == Mode.CHARGING) {
            chargingStepsRemaining--;
            if (chargingStepsRemaining <= 0) {
                batteryLevel = maxBattery;
                chargeSpotToRelease = chargingSpot;
                chargingSpot = null;

                if (pausedMission != null) {
                    mission = pausedMission;
                    pausedMission = null;
                    mode = Mode.WORKING;
                    replanRoute();
                } else {
                    mode = Mode.IDLE;
                    navigator.setRoute(null);
                }
            }
            return;
        }

        if (batteryLevel <= 0) {
            return;
        }

        int[] currentPosition = getLocation();
        if (hasReachedCurrentDestination(currentPosition)) {
            handleDestinationReached();
            return;
        }

        if (!navigator.hasActiveRoute()) {
            replanRoute();
            if (!navigator.hasActiveRoute()) {
                return;
            }
        }

        int[] nextWaypoint = navigator.getNextWaypoint();
        if (nextWaypoint == null) {
            return;
        }

        if (collisionDetector.isCellBlocked(nextWaypoint[0], nextWaypoint[1])) {
            handleBlockedPath(nextWaypoint);
            return;
        }

        if (isTeammateReportedAt(nextWaypoint)) {
            requestYieldFrom(nextWaypoint);
            replanRoute();
            return;
        }

        collisionDetector.resetBlockageCounter();
        boolean movementSucceeded = navigator.executeMovementStep();

        if (movementSucceeded) {
            batteryLevel = Math.max(0, batteryLevel - 1);
        } else {
            replanRoute();
        }
    }

    private boolean hasReachedCurrentDestination(int[] robotPosition) {
        int[] target = getCurrentDestination();
        if (target == null) {
            return true;
        }
        return robotPosition[0] == target[0] && robotPosition[1] == target[1];
    }

    private void handleDestinationReached() {
        if (mode == Mode.GOING_TO_CHARGE) {
            mode = Mode.CHARGING;
            chargingStepsRemaining = rechargeDuration;
            navigator.setRoute(null);
            return;
        }

        if (mode == Mode.WORKING && mission != null) {
            mission.advanceToNextPhase();
            replanRoute();
        }
    }

    private void handleBlockedPath(int[] blockedCell) {
        requestYieldFrom(blockedCell);

        boolean shouldEscape = collisionDetector.recordBlockedAttempt();
        if (shouldEscape) {
            attemptEscapeManeuver();
        }
    }

    private void attemptEscapeManeuver() {
        int[] currentPos = getLocation();
        int[] targetDest = getCurrentDestination();
        int[] escapeCell = collisionDetector.findEscapeRoute(currentPos, targetDest);

        if (escapeCell != null) {
            boolean escapedSuccessfully = navigator.moveToCell(escapeCell);
            if (escapedSuccessfully) {
                batteryLevel = Math.max(0, batteryLevel - 1);
                replanRoute();
            }
        } else {
            replanRoute();
        }
    }

    private void replanRoute() {
        int[] destination = getCurrentDestination();
        if (destination == null) {
            navigator.setRoute(null);
            return;
        }

        List<int[]> newRoute = routePlanner.findRoute(getLocation(), destination);
        navigator.setRoute(newRoute);
    }

    private int[] getCurrentDestination() {
        if (mode == Mode.GOING_TO_CHARGE) {
            return chargingSpot;
        }
        if (mode == Mode.WORKING && mission != null) {
            return mission.getCurrentDestination();
        }
        return null;
    }

    public DeliveryMission.Phase getMissionPhase() {
        if (mission == null) {
            return DeliveryMission.Phase.COMPLETED;
        }
        return mission.getCurrentPhase();
    }

    public PackageItem getPackage() {
        if (mission != null) {
            return mission.getPackage();
        }
        return pausedMission != null ? pausedMission.getPackage() : null;
    }

    public boolean isMissionComplete() {
        return mission == null || mission.isComplete();
    }

    public DeliveryMission getMission() {
        return mission;
    }

    public boolean isIdle() {
        return mode == Mode.IDLE;
    }

    public boolean isHeadingToCharge() {
        return mode == Mode.GOING_TO_CHARGE;
    }

    public boolean isCharging() {
        return mode == Mode.CHARGING;
    }

    public boolean needsRecharge() {
        return batteryLevel <= chargeThreshold && !isHeadingToCharge() && !isCharging();
    }

    public void assignMission(PackageItem packageItem, int[] pickup, int[] dropoff) {
        this.mission = new DeliveryMission(packageItem, pickup, dropoff);
        this.pausedMission = null;
        this.mode = Mode.WORKING;
        this.dropForChargeMission = false;
        this.navigator.setRoute(null);
        replanRoute();
    }

    public void redirectToCharge(int[] spot) {
        if (spot == null || isHeadingToCharge() || isCharging()) {
            return;
        }

        this.chargingSpot = spot.clone();
        this.navigator.setRoute(null);

        if (mode == Mode.WORKING && mission != null && !mission.isComplete()) {
            this.pausedMission = this.mission;
            this.mission = null;
        } else {
            this.mission = null;
        }

        this.mode = Mode.GOING_TO_CHARGE;
        replanRoute();
    }

    public int[] consumeChargeSpotToRelease() {
        if (chargeSpotToRelease == null) {
            return null;
        }
        int[] result = chargeSpotToRelease.clone();
        chargeSpotToRelease = null;
        return result;
    }

    public void clearMission() {
        this.mission = null;
        this.dropForChargeMission = false;
        if (mode == Mode.WORKING) {
            mode = Mode.IDLE;
        }
        this.navigator.setRoute(null);
    }

    public boolean isCarryingPackage() {
        return mode == Mode.WORKING &&
               mission != null &&
               mission.getCurrentPhase() == DeliveryMission.Phase.DROPOFF;
    }

    public boolean isDropForChargeMission() {
        return dropForChargeMission;
    }

    public boolean consumeDropForChargeFlag() {
        boolean result = dropForChargeMission;
        dropForChargeMission = false;
        return result;
    }

    public boolean scheduleIntermediateDropForCharging(int[] slot) {
        if (slot == null || !isCarryingPackage()) {
            return false;
        }

        PackageItem pkg = mission.getPackage();
        this.mission = new DeliveryMission(pkg, getLocation(), slot);
        this.dropForChargeMission = true;
        this.navigator.setRoute(null);
        replanRoute();
        return true;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public int getMaxBattery() {
        return maxBattery;
    }

    /**
     * Moves the robot one cell down to clear the exit lane for other robots.
     * Called immediately after transitioning to IDLE so the exit position is freed.
     *
     * @return true if the robot successfully moved one step down
     */
    public boolean stepToIdleCell() {
        int[] pos = getLocation();
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) warehouseEnvironment.getGrid();
        int targetRow = pos[0] + 1;
        if (targetRow >= grid.length) {
            return false;
        }
        ColorSimpleCell cell = grid[targetRow][pos[1]];
        if (cell != null && cell.getContent() != null) {
            return false;
        }
        return navigator.moveToCell(new int[]{targetRow, pos[1]});
    }

    private void broadcastStatus() {
        int[] pos = getLocation();
        String content = String.format(
                "BCAST|POS=%d,%d|BAT=%d|MODE=%s",
                pos[0], pos[1], batteryLevel, mode.name()
        );
        Message message = new Message(getId(), content);
        message.setReceiver(BCAST_RECEIVER);
        sendMessage(message);
    }

    private void requestYieldFrom(int[] blockedCell) {
        if (blockedCell == null) {
            return;
        }

        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) warehouseEnvironment.getGrid();
        if (blockedCell[0] < 0 || blockedCell[0] >= grid.length ||
            blockedCell[1] < 0 || blockedCell[1] >= grid[0].length) {
            return;
        }

        ColorSimpleCell cell = grid[blockedCell[0]][blockedCell[1]];
        if (cell == null || !(cell.getContent() instanceof DeliveryBot)) {
            return;
        }

        DeliveryBot other = (DeliveryBot) cell.getContent();
        if (other.getId() == getId()) {
            return;
        }

        Message request = new Message(
                getId(),
                "DYADIC|YIELD_REQUEST|CELL=" + blockedCell[0] + "," + blockedCell[1]
        );
        request.setReceiver(other.getId());
        sendMessage(request);
    }

    private boolean isTeammateReportedAt(int[] cell) {
        if (cell == null) {
            return false;
        }
        for (Map.Entry<Integer, int[]> entry : teammatePositions.entrySet()) {
            int[] pos = entry.getValue();
            if (pos[0] == cell[0] && pos[1] == cell[1]) {
                return true;
            }
        }
        return false;
    }

    private void decayTeammateInfo() {
        Iterator<Map.Entry<Integer, Integer>> it = teammateTtls.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
                teammatePositions.remove(entry.getKey());
                it.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg == null || msg.getEmitter() == getId() || msg.getContent() == null) {
            return;
        }

        String content = msg.getContent();
        if (content.startsWith("BCAST|")) {
            // Example: BCAST|POS=r,c|BAT=x|MODE=STATE
            String[] parts = content.split("\\|");
            for (String part : parts) {
                if (part.startsWith("POS=")) {
                    String[] rc = part.substring(4).split(",");
                    if (rc.length == 2) {
                        try {
                            int r = Integer.parseInt(rc[0]);
                            int c = Integer.parseInt(rc[1]);
                            teammatePositions.put(msg.getEmitter(), new int[]{r, c});
                            teammateTtls.put(msg.getEmitter(), TEAMMATE_TTL_STEPS);
                        } catch (NumberFormatException ignored) {
                            // Ignore malformed data.
                        }
                    }
                }
            }
            return;
        }

        if (content.startsWith("DYADIC|YIELD_REQUEST")) {
            // Local decentralized reaction: try to move aside/replan immediately.
            if (mode == Mode.WORKING || mode == Mode.GOING_TO_CHARGE) {
                attemptEscapeManeuver();
            } else {
                replanRoute();
            }

            Message ack = new Message(getId(), "DYADIC|YIELD_ACK");
            ack.setReceiver(msg.getEmitter());
            sendMessage(ack);
        }
    }
}
