package fr.emse;

import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

import java.util.*;

/**
 * PathPlanner - Computes optimal routes through the warehouse grid.
 *
 * This class implements BFS (Breadth-First Search) algorithm to find
 * the shortest path between two points while respecting:
 * - Static obstacles (walls, barriers)
 * - Zone separators (vertical movement restrictions)
 * - Grid boundaries
 */
public class PathPlanner {

    private final ColorGridEnvironment gridEnvironment;
    private final int totalRows;
    private final int totalColumns;

    // Zone separator configuration - prevents crossing between zones
    private static final int ZONE_BOUNDARY_COLUMN = 22;
    private static final int[] ZONE_SEPARATORS = {3, 6, 9, 12};

    /**
     * Constructs a path planner for the given warehouse environment.
     *
     * @param environment The warehouse grid environment
     * @param rows Number of rows in the grid
     * @param columns Number of columns in the grid
     */
    public PathPlanner(ColorGridEnvironment environment, int rows, int columns) {
        this.gridEnvironment = environment;
        this.totalRows = rows;
        this.totalColumns = columns;
    }

    /**
     * Finds the shortest path from start to destination using BFS.
     *
     * @param startPosition Starting coordinates [row, col]
     * @param destinationPosition Target coordinates [row, col]
     * @return List of waypoints from start to destination (excluding start)
     */
    public List<int[]> findRoute(int[] startPosition, int[] destinationPosition) {
        // Track visited cells to avoid cycles
        boolean[][] exploredCells = new boolean[totalRows][totalColumns];

        // Store parent cell for each position to reconstruct path
        int[][][] parentMap = new int[totalRows][totalColumns][2];
        initializeParentMap(parentMap);

        // BFS queue for frontier exploration
        Queue<int[]> frontier = new LinkedList<>();
        frontier.add(startPosition);
        exploredCells[startPosition[0]][startPosition[1]] = true;

        // Four cardinal directions: North, East, South, West
        int[][] movementDirections = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};

        ColorSimpleCell[][] warehouseGrid = (ColorSimpleCell[][]) gridEnvironment.getGrid();

        // Explore the grid level by level
        while (!frontier.isEmpty()) {
            int[] currentCell = frontier.poll();

            // Check if we've reached the destination
            if (currentCell[0] == destinationPosition[0] &&
                currentCell[1] == destinationPosition[1]) {
                return reconstructPath(parentMap, startPosition, destinationPosition);
            }

            // Explore all neighboring cells
            for (int[] direction : movementDirections) {
                int nextRow = currentCell[0] + direction[0];
                int nextCol = currentCell[1] + direction[1];

                // Skip if out of bounds or already visited
                if (!isValidCell(nextRow, nextCol) || exploredCells[nextRow][nextCol]) {
                    continue;
                }

                // Skip if zone separator blocks this movement
                if (crossesZoneSeparator(currentCell[0], currentCell[1], nextRow, nextCol)) {
                    continue;
                }

                // Check if cell is traversable (not a solid obstacle)
                ColorSimpleCell cell = warehouseGrid[nextRow][nextCol];
                if (isTraversable(cell)) {
                    exploredCells[nextRow][nextCol] = true;
                    parentMap[nextRow][nextCol] = currentCell;
                    frontier.add(new int[]{nextRow, nextCol});
                }
            }
        }

        // No path found - return empty list
        return new ArrayList<>();
    }

    /**
     * Initializes the parent map with sentinel values.
     */
    private void initializeParentMap(int[][][] parentMap) {
        for (int[][] rowArray : parentMap) {
            for (int[] cell : rowArray) {
                Arrays.fill(cell, -1);
            }
        }
    }

    /**
     * Reconstructs the path by backtracking through parent pointers.
     *
     * @param parentMap The parent relationship map from BFS
     * @param origin Starting position
     * @param target Destination position
     * @return Ordered list of waypoints from origin to target
     */
    private List<int[]> reconstructPath(int[][][] parentMap, int[] origin, int[] target) {
        List<int[]> pathWaypoints = new ArrayList<>();
        int[] currentPosition = target;

        // Backtrack from destination to origin
        while (currentPosition[0] != origin[0] || currentPosition[1] != origin[1]) {
            pathWaypoints.add(0, currentPosition);
            int[] parent = parentMap[currentPosition[0]][currentPosition[1]];

            // Safety check: break if no valid parent
            if (parent[0] == -1) {
                break;
            }

            currentPosition = parent;
        }

        return pathWaypoints;
    }

    /**
     * Checks if a cell coordinate is within grid boundaries.
     *
     * @param row Row index
     * @param col Column index
     * @return true if the cell is within valid bounds
     */
    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < totalRows && col >= 0 && col < totalColumns;
    }

    /**
     * Determines if a cell can be traversed by a robot.
     *
     * @param cell The grid cell to check
     * @return true if the cell is walkable
     */
    private boolean isTraversable(ColorSimpleCell cell) {
        if (cell == null) {
            return true;
        }

        Object cellContent = cell.getContent();
        if (cellContent == null) {
            return true;
        }

        // Block all obstacles
        boolean isObstacle = cellContent instanceof ColorObstacle;

        return !isObstacle;
    }

    /**
     * Checks if movement between two cells would illegally cross a zone separator.
     *
     * Zone separators are horizontal barriers in the right panel (column >= 18)
     * that prevent vertical movement between specific row pairs.
     *
     * @param fromRow Source row
     * @param fromCol Source column
     * @param toRow Destination row
     * @param toCol Destination column
     * @return true if the movement crosses a forbidden separator
     */
    private boolean crossesZoneSeparator(int fromRow, int fromCol, int toRow, int toCol) {
        // Separators only exist in the right panel
        if (Math.max(fromCol, toCol) < ZONE_BOUNDARY_COLUMN) {
            return false;
        }

        // Only vertical movement (same column) can cross a separator
        if (fromCol != toCol) {
            return false;
        }

        // Check if movement crosses any separator line
        int lowerRow = Math.min(fromRow, toRow);
        for (int separatorRow : ZONE_SEPARATORS) {
            if (lowerRow == separatorRow) {
                return true; // Movement between separatorRow and separatorRow+1 is blocked
            }
        }

        return false;
    }
}
