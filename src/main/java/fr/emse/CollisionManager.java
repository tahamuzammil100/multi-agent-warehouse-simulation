package fr.emse;

import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * CollisionManager - Handles obstacle detection and avoidance strategies.
 *
 * This class provides reactive collision avoidance by:
 * - Detecting occupied cells before movement
 * - Finding alternative escape routes when blocked
 * - Prioritizing movement toward the target destination
 */
public class CollisionManager {

    private final ColorGridEnvironment environment;
    private final int gridHeight;
    private final int gridWidth;

    // Blockage tracking
    private int consecutiveBlockedAttempts = 0;
    private static final int ESCAPE_THRESHOLD = 3;

    // Zone separator enforcement
    private static final int RIGHT_ZONE_COLUMN = 18;
    private static final int[] FORBIDDEN_CROSSINGS = {2, 5, 8, 11};

    /**
     * Constructs a collision manager for the warehouse.
     *
     * @param env The grid environment
     * @param rows Grid row count
     * @param cols Grid column count
     */
    public CollisionManager(ColorGridEnvironment env, int rows, int cols) {
        this.environment = env;
        this.gridHeight = rows;
        this.gridWidth = cols;
    }

    /**
     * Checks if a specific cell is currently occupied by a dynamic entity.
     *
     * Static obstacles (ColorObstacle) are not considered blocking for this check,
     * as they are handled during pathfinding. This focuses on dynamic blockages
     * like other robots or workers.
     *
     * @param targetRow Row coordinate
     * @param targetCol Column coordinate
     * @return true if a robot or worker occupies the cell
     */
    public boolean isCellBlocked(int targetRow, int targetCol) {
        ColorSimpleCell[][] warehouseGrid = (ColorSimpleCell[][]) environment.getGrid();
        ColorSimpleCell targetCell = warehouseGrid[targetRow][targetCol];

        if (targetCell == null || targetCell.getContent() == null) {
            return false;
        }

        // Only consider dynamic entities as blocking
        // Static obstacles are handled by pathfinding
        return !(targetCell.getContent() instanceof ColorObstacle);
    }

    /**
     * Records a blocked movement attempt and determines if escape is needed.
     *
     * @return true if robot should attempt an escape maneuver
     */
    public boolean recordBlockedAttempt() {
        consecutiveBlockedAttempts++;
        return consecutiveBlockedAttempts >= ESCAPE_THRESHOLD;
    }

    /**
     * Resets the blockage counter after successful movement.
     */
    public void resetBlockageCounter() {
        consecutiveBlockedAttempts = 0;
    }

    /**
     * Attempts to find an escape route when stuck.
     *
     * Prioritizes movement perpendicular to the target direction first,
     * then tries parallel movement. Returns the first free cell found.
     *
     * @param currentPosition Current robot position [row, col]
     * @param targetPosition Destination position [row, col]
     * @return First available escape cell, or null if all directions blocked
     */
    public int[] findEscapeRoute(int[] currentPosition, int[] targetPosition) {
        consecutiveBlockedAttempts = 0; // Reset after attempting escape

        int rowDifference = targetPosition[0] - currentPosition[0];
        int colDifference = targetPosition[1] - currentPosition[1];

        // Prioritize perpendicular movement to avoid direct blockage
        int[][] escapeDirections;
        if (Math.abs(colDifference) >= Math.abs(rowDifference)) {
            // Target is mostly horizontal - try vertical movement first
            escapeDirections = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        } else {
            // Target is mostly vertical - try horizontal movement first
            escapeDirections = new int[][]{{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        }

        ColorSimpleCell[][] warehouseGrid = (ColorSimpleCell[][]) environment.getGrid();

        // Try each escape direction
        for (int[] direction : escapeDirections) {
            int candidateRow = currentPosition[0] + direction[0];
            int candidateCol = currentPosition[1] + direction[1];

            // Check if candidate cell is valid and free
            if (!isWithinBounds(candidateRow, candidateCol)) {
                continue;
            }

            if (violatesZoneSeparator(currentPosition[0], currentPosition[1],
                                      candidateRow, candidateCol)) {
                continue;
            }

            ColorSimpleCell candidateCell = warehouseGrid[candidateRow][candidateCol];
            if (candidateCell != null && candidateCell.getContent() != null) {
                continue; // Cell is occupied
            }

            // Found a free escape cell
            return new int[]{candidateRow, candidateCol};
        }

        // No escape route available
        return null;
    }

    /**
     * Checks if coordinates are within grid boundaries.
     */
    private boolean isWithinBounds(int row, int col) {
        return row >= 0 && row < gridHeight && col >= 0 && col < gridWidth;
    }

    /**
     * Determines if a movement violates zone separator rules.
     *
     * @param startRow Origin row
     * @param startCol Origin column
     * @param endRow Destination row
     * @param endCol Destination column
     * @return true if movement crosses a forbidden separator
     */
    private boolean violatesZoneSeparator(int startRow, int startCol, int endRow, int endCol) {
        // Separators only apply in right zone
        if (Math.max(startCol, endCol) < RIGHT_ZONE_COLUMN) {
            return false;
        }

        // Only vertical movement can violate separators
        if (startCol != endCol) {
            return false;
        }

        int transitionRow = Math.min(startRow, endRow);
        for (int separator : FORBIDDEN_CROSSINGS) {
            if (transitionRow == separator) {
                return true;
            }
        }

        return false;
    }
}
