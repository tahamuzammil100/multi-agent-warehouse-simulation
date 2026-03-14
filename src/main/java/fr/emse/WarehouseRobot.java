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
    private int wallFollowCounter;     // Steps remaining in wall-follow mode
    private static final int WALL_FOLLOW_STEPS = 5; // How long to follow wall before retrying goal

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
        this.wallFollowCounter = 0;
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
     * Uses wall-following when stuck: picks a direction and commits to it.
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

            boolean moved = false;

            // Wall-following mode: keep moving in current direction
            if (wallFollowCounter > 0) {
                if (freeForward()) {
                    moveForward();
                    wallFollowCounter--;
                    moved = true;
                } else {
                    // Hit obstacle while wall-following, find new direction
                    wallFollowCounter = 0;
                    moved = findAndCommitToFreeDirection();
                }
            } else {
                // Normal mode: try to move toward goal
                int dx = goalPosition[0] - this.x;
                int dy = goalPosition[1] - this.y;
                moved = tryMoveTowardGoal(dx, dy);

                // If blocked, enter wall-following mode
                if (!moved) {
                    moved = findAndCommitToFreeDirection();
                }
            }

            if (!moved && SimFactory.DEBUG == 1) {
                System.out.println("Robot " + name + " completely stuck at (" + x + "," + y + ")");
            }
        }

        if (SimFactory.DEBUG == 1) {
            System.out.println("Robot " + name + " at (" + x + "," + y +
                               "), goal: (" + goalPosition[0] + "," + goalPosition[1] +
                               "), distance: " + (Math.abs(goalPosition[0] - x) + Math.abs(goalPosition[1] - y)) + ")" +
                               ", wallFollow: " + wallFollowCounter);
        }
    }

    /**
     * Finds a free direction and commits to moving in it for several steps.
     * @return true if found a direction to move
     */
    private boolean findAndCommitToFreeDirection() {
        // Try all four directions to find a free one
        for (int turns = 0; turns < 4; turns++) {
            if (freeForward()) {
                moveForward();
                wallFollowCounter = WALL_FOLLOW_STEPS;  // Commit to this direction
                return true;
            }
            turnRight();  // Try next direction
        }
        return false;  // Completely surrounded
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
     * Override freeForward to check the correct cell based on current orientation.
     * The base class only checks "up" direction, but we need to check based on actual orientation.
     * Also treats the goal position as valid even if it contains the ExitZone component.
     * @return true if forward cell is free or is the goal
     */
    @Override
    protected boolean freeForward() {
        if (grid == null) {
            return false;
        }

        // Determine which cell to check based on current orientation
        int gridX = field;  // Center of perception grid
        int gridY = field;  // Center of perception grid

        switch (this.orientation) {
            case up:    gridX = field - 1; gridY = field; break;     // Up is row - 1
            case down:  gridX = field + 1; gridY = field; break;     // Down is row + 1
            case left:  gridX = field; gridY = field - 1; break;     // Left is col - 1
            case right: gridX = field; gridY = field + 1; break;     // Right is col + 1
        }

        if (SimFactory.DEBUG == 1) {
            System.out.println("      freeForward: orientation=" + this.orientation + ", checking grid[" + gridX + "][" + gridY + "]");
        }

        // Check if the cell exists
        ColorSimpleCell cell = grid[gridX][gridY];

        if (cell == null) {
            if (SimFactory.DEBUG == 1) {
                System.out.println("      cell: NULL (out of bounds)");
            }
            return false;
        }

        // Calculate what world position this cell represents
        int targetWorldX = x;
        int targetWorldY = y;
        switch (this.orientation) {
            case up:    targetWorldX = x - 1; break;
            case down:  targetWorldX = x + 1; break;
            case left:  targetWorldY = y - 1; break;
            case right: targetWorldY = y + 1; break;
        }

        // Check if this is the goal position - if so, it's always valid
        if (goalPosition != null && targetWorldX == goalPosition[0] && targetWorldY == goalPosition[1]) {
            if (SimFactory.DEBUG == 1) {
                System.out.println("      cell: GOAL position - valid!");
            }
            return true;
        }

        // Otherwise check if cell is empty
        boolean isFree = cell.getContent() == null;

        if (SimFactory.DEBUG == 1) {
            System.out.println("      cell content: " + (cell.getContent() != null ? cell.getContent().getClass().getSimpleName() : "null (FREE)"));
        }

        return isFree;
    }

    /**
     * Attempts to move toward the goal position with distance-based greedy algorithm.
     * ONLY accepts moves that DECREASE the distance from current position (strict improvement).
     * Checks all 4 directions and picks the one with smallest resulting distance.
     * @param dx Horizontal distance to goal (positive = right)
     * @param dy Vertical distance to goal (positive = down)
     * @return true if robot moved or turned
     */
    private boolean tryMoveTowardGoal(int dx, int dy) {
        int currentDistance = Math.abs(dx) + Math.abs(dy);

        // Check all 4 directions to find the one that DECREASES distance most
        Orientation[] directions = {Orientation.up, Orientation.down, Orientation.left, Orientation.right};
        Orientation bestDirection = null;
        int bestDistance = currentDistance;  // Start with current distance as threshold

        if (SimFactory.DEBUG == 1) {
            System.out.println("  Current distance: " + currentDistance);
            System.out.println("  Checking all 4 directions from (" + x + "," + y + "):");
        }

        for (Orientation dir : directions) {
            // Temporarily orient to this direction to check if it's free
            Orientation originalOrientation = this.orientation;
            while (this.orientation != dir) {
                turnLeft();
            }

            boolean isFree = freeForward();

            if (isFree) {
                // Calculate where we'd be if we moved forward
                int newX = x;
                int newY = y;
                switch (this.orientation) {
                    case up:    newX = x - 1; break;
                    case down:  newX = x + 1; break;
                    case left:  newY = y - 1; break;
                    case right: newY = y + 1; break;
                }

                // Calculate distance from that new position to goal
                int newDx = goalPosition[0] - newX;
                int newDy = goalPosition[1] - newY;
                int newDistance = Math.abs(newDx) + Math.abs(newDy);

                if (SimFactory.DEBUG == 1) {
                    System.out.println("    " + dir + " -> (" + newX + "," + newY + ") free=true, distance=" + newDistance);
                }

                // ONLY accept moves that DECREASE distance (strict improvement)
                if (newDistance < bestDistance) {
                    bestDistance = newDistance;
                    bestDirection = dir;
                }
            } else {
                if (SimFactory.DEBUG == 1) {
                    System.out.println("    " + dir + " -> blocked");
                }
            }

            // Restore original orientation
            while (this.orientation != originalOrientation) {
                turnLeft();
            }
        }

        if (SimFactory.DEBUG == 1) {
            System.out.println("  Best direction: " + bestDirection + " (new distance: " + bestDistance + ")");
        }

        // Move in the best direction found
        if (bestDirection != null) {
            while (this.orientation != bestDirection) {
                turnLeft();
            }
            moveForward();
            return true;
        }

        // No improving move found - try any free direction to avoid being stuck
        if (SimFactory.DEBUG == 1) {
            System.out.println("  No improving move, trying any free direction...");
        }

        for (Orientation dir : directions) {
            Orientation originalOrientation = this.orientation;
            while (this.orientation != dir) {
                turnLeft();
            }

            if (freeForward()) {
                if (SimFactory.DEBUG == 1) {
                    System.out.println("  Taking random move: " + dir);
                }
                moveForward();
                return true;
            }

            while (this.orientation != originalOrientation) {
                turnLeft();
            }
        }

        return false;
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
