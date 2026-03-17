package fr.emse;

import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

import java.util.*;

/**
 * PathPlanner - Computes optimal routes through the warehouse grid.
 *
 * This class implements A* (A-Star) algorithm to find the shortest path
 * between two points while respecting:
 * - Static obstacles (walls, barriers)
 * - Zone separators (vertical movement restrictions)
 * - Grid boundaries
 *
 * A* improves upon BFS by using a heuristic (Manhattan distance) to guide
 * the search toward the goal, resulting in 2-3x faster pathfinding while
 * still guaranteeing optimal shortest paths.
 */
public class PathPlanner {

    private final ColorGridEnvironment gridEnvironment;
    private final int totalRows;
    private final int totalColumns;

    // Zone separator configuration - prevents crossing between zones
    private static final int ZONE_BOUNDARY_COLUMN = 18;
    private static final int[] ZONE_SEPARATORS = {2, 5, 8, 11};

    /**
     * Node class for A* algorithm.
     * Represents a cell in the search space with cost tracking.
     */
    private static class AStarNode implements Comparable<AStarNode> {
        final int row;
        final int col;
        final int gCost;  // Actual distance from start
        final int hCost;  // Heuristic estimate to goal (Manhattan distance)
        final int fCost;  // Total cost: f = g + h
        final AStarNode parent;

        AStarNode(int row, int col, int gCost, int hCost, AStarNode parent) {
            this.row = row;
            this.col = col;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(AStarNode other) {
            // Primary: compare by total cost (fCost)
            int fCompare = Integer.compare(this.fCost, other.fCost);
            if (fCompare != 0) {
                return fCompare;
            }
            // Tie-breaker: prefer nodes with lower heuristic (closer to goal)
            return Integer.compare(this.hCost, other.hCost);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof AStarNode)) return false;
            AStarNode other = (AStarNode) obj;
            return this.row == other.row && this.col == other.col;
        }

        @Override
        public int hashCode() {
            return 31 * row + col;
        }
    }

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
     * Calculates Manhattan distance heuristic between two points.
     * This is an admissible heuristic (never overestimates) for grid-based
     * movement with 4-directional connectivity.
     *
     * @param row1 Row coordinate of first point
     * @param col1 Column coordinate of first point
     * @param row2 Row coordinate of second point
     * @param col2 Column coordinate of second point
     * @return Manhattan distance (absolute row difference + absolute column difference)
     */
    private int calculateManhattanDistance(int row1, int col1, int row2, int col2) {
        return Math.abs(row1 - row2) + Math.abs(col1 - col2);
    }

    /**
     * Finds the shortest path from start to destination using A* algorithm.
     *
     * A* uses f(n) = g(n) + h(n) where:
     * - g(n) = actual cost from start to current node
     * - h(n) = heuristic estimate from current node to goal (Manhattan distance)
     * - f(n) = total estimated cost through current node
     *
     * @param startPosition Starting coordinates [row, col]
     * @param destinationPosition Target coordinates [row, col]
     * @return List of waypoints from start to destination (excluding start)
     */
    public List<int[]> findRoute(int[] startPosition, int[] destinationPosition) {
        // Edge case: start and destination are the same
        if (startPosition[0] == destinationPosition[0] &&
            startPosition[1] == destinationPosition[1]) {
            return new ArrayList<>();
        }

        // Priority queue for A* open set (sorted by f-cost)
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>();

        // Track best g-cost found for each cell
        int[][] gCostMap = new int[totalRows][totalColumns];
        for (int[] row : gCostMap) {
            Arrays.fill(row, Integer.MAX_VALUE);
        }

        // Track which cells have been fully evaluated (closed set)
        boolean[][] closedSet = new boolean[totalRows][totalColumns];

        // Four cardinal directions: North, East, South, West
        int[][] movementDirections = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};

        ColorSimpleCell[][] warehouseGrid = (ColorSimpleCell[][]) gridEnvironment.getGrid();

        // Initialize starting node
        int startHeuristic = calculateManhattanDistance(
            startPosition[0], startPosition[1],
            destinationPosition[0], destinationPosition[1]
        );
        AStarNode startNode = new AStarNode(
            startPosition[0], startPosition[1],
            0, startHeuristic, null
        );
        openSet.add(startNode);
        gCostMap[startPosition[0]][startPosition[1]] = 0;

        // A* main loop: process nodes in order of increasing f-cost
        while (!openSet.isEmpty()) {
            AStarNode current = openSet.poll();

            // Skip if this node has been processed with a better cost
            if (closedSet[current.row][current.col]) {
                continue;
            }

            // Mark current node as fully evaluated
            closedSet[current.row][current.col] = true;

            // Goal check: reached destination
            if (current.row == destinationPosition[0] &&
                current.col == destinationPosition[1]) {
                return reconstructPathFromNode(current);
            }

            // Explore all neighboring cells
            for (int[] direction : movementDirections) {
                int nextRow = current.row + direction[0];
                int nextCol = current.col + direction[1];

                // Skip if out of bounds
                if (!isValidCell(nextRow, nextCol)) {
                    continue;
                }

                // Skip if already in closed set
                if (closedSet[nextRow][nextCol]) {
                    continue;
                }

                // Skip if zone separator blocks this movement
                if (crossesZoneSeparator(current.row, current.col, nextRow, nextCol)) {
                    continue;
                }

                // Check if cell is traversable (not a solid obstacle)
                ColorSimpleCell cell = warehouseGrid[nextRow][nextCol];
                if (!isTraversable(cell)) {
                    continue;
                }

                // Calculate costs for this neighbor
                int tentativeGCost = current.gCost + 1;  // Each move costs 1

                // Skip if we've found a better path to this neighbor already
                if (tentativeGCost >= gCostMap[nextRow][nextCol]) {
                    continue;
                }

                // This path to neighbor is better than any previous one
                gCostMap[nextRow][nextCol] = tentativeGCost;

                int heuristic = calculateManhattanDistance(
                    nextRow, nextCol,
                    destinationPosition[0], destinationPosition[1]
                );

                AStarNode neighborNode = new AStarNode(
                    nextRow, nextCol,
                    tentativeGCost, heuristic,
                    current
                );
                openSet.add(neighborNode);
            }
        }

        // No path found - return empty list
        return new ArrayList<>();
    }

    /**
     * Reconstructs the path by backtracking through parent node links.
     *
     * @param goalNode The destination node reached by A*
     * @return Ordered list of waypoints from origin to target (excluding start position)
     */
    private List<int[]> reconstructPathFromNode(AStarNode goalNode) {
        List<int[]> pathWaypoints = new ArrayList<>();
        AStarNode current = goalNode;

        // Backtrack from destination to origin using parent links
        while (current != null) {
            pathWaypoints.add(0, new int[]{current.row, current.col});
            current = current.parent;
        }

        // Remove the starting position (first element) as per original BFS behavior
        if (!pathWaypoints.isEmpty()) {
            pathWaypoints.remove(0);
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
