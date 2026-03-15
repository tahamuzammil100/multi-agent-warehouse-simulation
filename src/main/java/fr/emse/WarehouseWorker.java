package fr.emse;

import java.awt.Color;
import java.util.Random;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * WarehouseWorker - Represents a human worker moving through the warehouse.
 *
 * These workers act as dynamic obstacles in the warehouse environment.
 * They move randomly across the warehouse floor, and delivery robots must
 * navigate around them using collision avoidance strategies.
 *
 * Movement Behavior:
 *   - Random walk pattern: chooses a random direction each step
 *   - If blocked, tries alternative directions
 *   - Stays in place if all directions are occupied
 *   - Never leaves the warehouse grid boundaries
 */
public class WarehouseWorker extends ColorInteractionRobot<ColorSimpleCell> {

    private final ColorGridEnvironment environment;
    private final int gridRows, gridCols;
    private final Random randomGenerator;

    /** All possible movement directions: up, down, left, right */
    private static final int[][] MOVEMENT_DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /**
     * Creates a new warehouse worker that moves randomly through the warehouse.
     *
     * @param name Worker identifier
     * @param fieldSize Perception range
     * @param startPos Initial position [row, col]
     * @param color Display color for this worker
     * @param rows Grid row count
     * @param cols Grid column count
     * @param env Warehouse environment
     * @param seed Random seed for movement decisions
     */
    public WarehouseWorker(String name, int fieldSize, int[] startPos, Color color,
                           int rows, int cols, ColorGridEnvironment env, long seed) {
        super(name, fieldSize, startPos, new int[]{color.getRed(), color.getGreen(), color.getBlue()});
        this.environment = env;
        this.gridRows = rows;
        this.gridCols = cols;
        this.randomGenerator = new Random(seed);
        setCurrentOrientation(Orientation.up);
    }

    /**
     * Executes movement steps for this worker.
     * Each step attempts to move in a random direction.
     */
    @Override
    public void move(int stepCount) {
        for (int i = 0; i < stepCount; i++) {
            performMovementStep();
        }
    }

    /**
     * Attempts to move the worker in a random direction.
     * Tries alternative directions if the first choice is blocked.
     */
    private void performMovementStep() {
        int[] currentPos = getLocation();
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) environment.getGrid();

        // Pick a random starting direction and try all four if needed
        int startDirection = randomGenerator.nextInt(4);
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] direction = MOVEMENT_DIRECTIONS[(startDirection + attempt) % 4];
            int newRow = currentPos[0] + direction[0];
            int newCol = currentPos[1] + direction[1];

            // Check if new position is within grid bounds
            if (newRow < 0 || newRow >= gridRows || newCol < 0 || newCol >= gridCols) {
                continue;
            }

            // Check if target cell is free
            ColorSimpleCell targetCell = grid[newRow][newCol];
            if (targetCell != null && targetCell.getContent() != null) {
                continue; // Cell is occupied
            }

            // Move to this free cell
            turnToFace(getOrientationFromDirection(direction[0], direction[1]));
            moveForward();
            return;
        }
        // All four directions are blocked - stay in current position
    }

    /**
     * Converts a direction delta to an Orientation enum value.
     *
     * @param rowDelta Change in row (-1 = up, +1 = down)
     * @param colDelta Change in column (-1 = left, +1 = right)
     * @return The corresponding Orientation
     */
    private Orientation getOrientationFromDirection(int rowDelta, int colDelta) {
        if (rowDelta < 0) return Orientation.up;
        if (rowDelta > 0) return Orientation.down;
        if (colDelta > 0) return Orientation.right;
        return Orientation.left;
    }

    /**
     * Turns the worker to face the specified orientation.
     * Calculates the minimum number of left turns needed.
     */
    private void turnToFace(Orientation targetOrientation) {
        Orientation currentOrientation = getCurrentOrientation();
        if (currentOrientation == null || currentOrientation == Orientation.unknown) {
            setCurrentOrientation(Orientation.up);
            currentOrientation = Orientation.up;
        }
        int turnsNeeded = (getCounterClockwiseIndex(targetOrientation)
                          - getCounterClockwiseIndex(currentOrientation) + 4) % 4;
        for (int i = 0; i < turnsNeeded; i++) {
            turnLeft();
        }
    }

    /**
     * Maps orientations to counter-clockwise indices for turn calculations.
     * up=0, left=1, down=2, right=3
     */
    private int getCounterClockwiseIndex(Orientation orientation) {
        switch (orientation) {
            case up:    return 0;
            case left:  return 1;
            case down:  return 2;
            case right: return 3;
            default:    return 0;
        }
    }

    /**
     * Warehouse workers do not respond to messages.
     * This method is required by the ColorInteractionRobot interface.
     */
    @Override
    public void handleMessage(Message msg) {
        // Workers operate independently and do not communicate
    }
}
