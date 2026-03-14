package fr.emse;

import java.awt.Color;

import fr.emse.fayol.maqit.simulator.SimFactory;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * A warehouse robot that can carry pallets and navigate to goal positions.
 * Extends BasicRobot with pallet-handling and goal-seeking capabilities.
 */
public class WarehouseRobot extends BasicRobot {

    private Pallet currentPallet;      // Pallet being carried (null if none)
    private int[] goalPosition;        // Target position [x, y] to reach
    private int targetZoneId;          // ID of target exit zone

    /**
     * Creates a new warehouse robot.
     *
     * @param name Robot name
     * @param field Perception field size
     * @param debug Debug level
     * @param pos Initial position [x, y]
     * @param co Color
     * @param rows Grid rows
     * @param columns Grid columns
     */
    public WarehouseRobot(String name, int field, int debug, int[] pos, Color co, int rows, int columns) {
        super(name, field, debug, pos, co, rows, columns);
        this.currentPallet = null;
        this.goalPosition = null;
        this.targetZoneId = -1;
    }

    /**
     * Assigns a pallet to this robot.
     * @param pallet Pallet to carry
     */
    public void assignPallet(Pallet pallet) {
        this.currentPallet = pallet;
        if (pallet != null) {
            pallet.setAssociatedRobotId(this.getId());
            this.targetZoneId = pallet.getDestinationZoneId();
        }
    }

    /**
     * Gets the pallet currently being carried.
     * @return Current pallet, or null if not carrying any
     */
    public Pallet getCurrentPallet() {
        return currentPallet;
    }

    /**
     * Checks if robot is carrying a pallet.
     * @return true if carrying a pallet
     */
    public boolean hasPallet() {
        return currentPallet != null;
    }

    /**
     * Sets the goal position this robot should navigate to.
     * @param goalPos Target position [x, y]
     */
    public void setGoalPosition(int[] goalPos) {
        this.goalPosition = goalPos;
    }

    /**
     * Gets the goal position.
     * @return Goal position [x, y]
     */
    public int[] getGoalPosition() {
        return goalPosition;
    }

    /**
     * Checks if robot has reached its goal position.
     * @return true if at goal position
     */
    public boolean hasReachedGoal() {
        if (goalPosition == null) {
            return false;
        }
        return (this.x == goalPosition[0] && this.y == goalPosition[1]);
    }

    /**
     * Enhanced movement that navigates toward the goal.
     * Uses simple navigation: move in direction of goal, avoid obstacles.
     */
    @Override
    public void move(int nb) {
        for (int i = 0; i < nb; i++) {
            if (goalPosition == null) {
                // No goal set, use default random movement
                super.move(1);
                return;
            }

            // Check if already at goal
            if (hasReachedGoal()) {
                setGoalReached(true);
                if (SimFactory.DEBUG == 1) {
                    System.out.println("Robot " + name + " REACHED GOAL at (" + x + "," + y + ")");
                }
                return;
            }

            // Calculate direction to goal
            int dx = goalPosition[0] - this.x;  // Positive = goal is to the right
            int dy = goalPosition[1] - this.y;  // Positive = goal is below

            // Try to move toward goal
            boolean moved = tryMoveTowardGoal(dx, dy);

            if (!moved && SimFactory.DEBUG == 1) {
                System.out.println("Robot " + name + " stuck at (" + x + "," + y +
                                   "), orientation: " + orientation);
            }
        }

        if (SimFactory.DEBUG == 1) {
            System.out.println("Robot " + name + " at (" + x + "," + y +
                               "), goal: (" + goalPosition[0] + "," + goalPosition[1] +
                               "), distance: " + (Math.abs(goalPosition[0] - x) + Math.abs(goalPosition[1] - y)) + ")");
        }
    }

    /**
     * Checks if the cell to the left is free.
     * @return true if left cell is free
     */
    private boolean freeLeft() {
        ColorSimpleCell cell = grid[field][field - 1];
        return cell != null && cell.getContent() == null;
    }

    /**
     * Checks if the cell to the right is free.
     * @return true if right cell is free
     */
    private boolean freeRight() {
        ColorSimpleCell cell = grid[field][field + 1];
        return cell != null && cell.getContent() == null;
    }

    /**
     * Attempts to move toward the goal position with enhanced obstacle avoidance.
     * Checks both left AND right when blocked and chooses the better direction.
     * @param dx Horizontal distance to goal (positive = right)
     * @param dy Vertical distance to goal (positive = down)
     * @return true if robot moved or turned
     */
    private boolean tryMoveTowardGoal(int dx, int dy) {
        // Determine desired direction based on larger distance component
        Orientation desiredOrientation;
        if (Math.abs(dx) > Math.abs(dy)) {
            // Horizontal movement more important
            desiredOrientation = (dx > 0) ? Orientation.right : Orientation.left;
        } else if (Math.abs(dy) > Math.abs(dx)) {
            // Vertical movement more important
            desiredOrientation = (dy > 0) ? Orientation.down : Orientation.up;
        } else if (dx != 0) {
            // Equal distance, prefer horizontal
            desiredOrientation = (dx > 0) ? Orientation.right : Orientation.left;
        } else if (dy != 0) {
            // Only vertical movement needed
            desiredOrientation = (dy > 0) ? Orientation.down : Orientation.up;
        } else {
            // Already at goal
            return false;
        }

        // Turn to face desired direction
        while (this.orientation != desiredOrientation) {
            turnLeft();
        }

        // Try to move forward in desired direction
        if (freeForward()) {
            moveForward();
            return true;
        } else {
            // Path blocked - Enhanced obstacle avoidance: check BOTH left AND right
            boolean leftFree = freeLeft();
            boolean rightFree = freeRight();

            if (rightFree && leftFree) {
                // Both free - choose based on which direction helps reach goal
                // Turn right if it helps, otherwise turn left
                if (Math.random() < 0.5) {
                    turnRight();
                } else {
                    turnLeft();
                }
                moveForward();
                return true;
            } else if (rightFree) {
                // Only right is free
                turnRight();
                moveForward();
                return true;
            } else if (leftFree) {
                // Only left is free
                turnLeft();
                moveForward();
                return true;
            } else {
                // Both blocked, turn around
                turnRight();
                turnRight();
                if (freeForward()) {
                    moveForward();
                    return true;
                } else {
                    // Completely surrounded
                    return false;
                }
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        if (SimFactory.DEBUG == 1) {
            System.out.println("WarehouseRobot " + name + " received message from " +
                               msg.getEmitter() + ": " + msg.getContent());
        }
    }

    @Override
    public String toString() {
        return "WarehouseRobot[" + name +
               ", pos=(" + x + "," + y + ")" +
               ", hasPallet=" + hasPallet() +
               ", goal=(" + (goalPosition != null ? goalPosition[0] + "," + goalPosition[1] : "none") + ")" +
               ", goalReached=" + isGoalReached() + "]";
    }
}
