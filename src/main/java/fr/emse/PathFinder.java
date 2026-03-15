package fr.emse;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * Utility class for finding shortest paths using Breadth-First Search (BFS).
 * This class is stateless and provides path-finding as a service.
 */
public class PathFinder {

    /**
     * Finds the shortest path between two points using BFS algorithm.
     * BFS guarantees the shortest path in an unweighted grid.
     *
     * @param start Starting position [row, col]
     * @param goal Target position [row, col]
     * @param grid The warehouse grid
     * @param rows Number of rows in grid
     * @param cols Number of columns in grid
     * @return List of positions representing the path (empty if no path found)
     */
    public static List<int[]> findPath(int[] start, int[] goal,
                                       ColorSimpleCell[][] grid,
                                       int rows, int cols) {

        // Track which cells we've already visited
        boolean[][] visited = new boolean[rows][cols];

        // Track parent of each cell to reconstruct path
        int[][][] parent = new int[rows][cols][2];

        // Queue for BFS - processes cells in order of distance from start
        Queue<int[]> queue = new LinkedList<>();
        queue.add(start);
        visited[start[0]][start[1]] = true;

        // Four possible movement directions: up, right, down, left
        int[][] directions = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};

        // BFS main loop
        while (!queue.isEmpty()) {
            int[] current = queue.poll();

            // Check if we reached the goal
            if (current[0] == goal[0] && current[1] == goal[1]) {
                return buildPath(parent, start, goal);
            }

            // Explore all neighboring cells
            for (int[] direction : directions) {
                int newRow = current[0] + direction[0];
                int newCol = current[1] + direction[1];

                // Check if neighbor is valid and not visited
                if (isValidCell(newRow, newCol, rows, cols) &&
                    !visited[newRow][newCol] &&
                    !isObstacle(grid[newRow][newCol])) {

                    visited[newRow][newCol] = true;
                    parent[newRow][newCol] = current;
                    queue.add(new int[]{newRow, newCol});
                }
            }
        }

        // No path found
        return new ArrayList<>();
    }

    /**
     * Checks if a cell position is within grid bounds.
     */
    private static boolean isValidCell(int row, int col, int rows, int cols) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    /**
     * Checks if a cell contains an obstacle.
     * Only ColorObstacle blocks movement - robots and other entities don't.
     */
    private static boolean isObstacle(ColorSimpleCell cell) {
        if (cell == null) return false;
        Object content = cell.getContent();
        return content != null && content instanceof ColorObstacle;
    }

    /**
     * Reconstructs the path from start to goal by following parent pointers.
     */
    private static List<int[]> buildPath(int[][][] parent, int[] start, int[] goal) {
        List<int[]> path = new ArrayList<>();
        int[] current = goal;

        // Work backwards from goal to start
        while (current[0] != start[0] || current[1] != start[1]) {
            path.add(0, current);  // Add to front of list
            current = parent[current[0]][current[1]];
        }

        return path;
    }
}
