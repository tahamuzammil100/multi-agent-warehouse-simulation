package fr.emse;

import java.awt.Color;
import java.util.*;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * DeliveryBot - An autonomous warehouse robot that transports packages.
 *
 * This robot uses a four-stage journey approach:
 *   Stage 1: Navigate to package pickup location
 *   Stage 2: Travel through a waypoint (intermediate checkpoint)
 *   Stage 3: Deliver package to the designated delivery zone
 *   Stage 4: Exit the warehouse for cleanup
 *
 * Navigation Strategy: Uses BFS (Breadth-First Search) to find the shortest path,
 * with reactive collision avoidance when blocked by other robots or humans.
 */
public class DeliveryBot extends ColorInteractionRobot<ColorSimpleCell> {

    // Robot journey stages - tracks where the robot is in its delivery process
    public enum RobotRobotState {
        GOING_TO_PACKAGE,      // Heading to pick up the package
        GOING_TO_SAFEPOINT,    // Moving through intermediate waypoint
        GOING_TO_DELIVERY,     // Transporting to delivery zone
        GOING_TO_EXIT,         // Heading to exit after delivery
        DONE                   // Journey complete, ready for removal
    }

    private RobotRobotState currentRobotState = RobotRobotState.GOING_TO_PACKAGE;

    private final int[] packagePos;
    private final int[] safepointPos;
    private final int[] deliveryPos;
    private final int[] exitPos;
    private int[] currentTarget;

    private List<int[]> path = new ArrayList<>();
    private int pathIndex = 0;

    private final ColorGridEnvironment env;
    private final PackageItem assignedPackage;
    private final int rows, cols;

    private int stuckCounter = 0;

    // ---- Right-panel separator constraints ----------------------------------
    // Robots cannot cross these row boundaries while in cols >= RIGHT_PANEL_COL.
    // Separator line is drawn BELOW each listed row, so movement between
    // (sep, c) ↔ (sep+1, c) is forbidden when c >= RIGHT_PANEL_COL.
    private static final int   RIGHT_PANEL_COL  = 18;
    private static final int[] SEPARATOR_ROWS   = {2, 5, 8, 11};

    public DeliveryBot(String name, int field, int debug, int[] pos, Color color,
               int rows, int cols, ColorGridEnvironment env,
               PackageItem packageItem, int[] packagePos, int[] safepointPos, int[] deliveryPos, int[] exitPos) {
        super(name, field, pos,
              new int[]{color.getRed(), color.getGreen(), color.getBlue()});
        this.env              = env;
        this.assignedPackage  = packageItem;
        this.rows          = rows;
        this.cols          = cols;
        this.packagePos    = packagePos;
        this.safepointPos  = safepointPos;
        this.deliveryPos   = deliveryPos;
        this.exitPos       = exitPos;
        this.currentTarget = packagePos;

        setCurrentOrientation(Orientation.up);
        computePath();
    }

    // -------------------------------------------------------------------------

    @Override
    public void move(int nb) {
        for (int i = 0; i < nb; i++) step();
    }

    // -------------------------------------------------------------------------

    private void step() {
        if (currentRobotState == RobotRobotState.DONE) return;

        int[] loc = getLocation();

        // Reached current waypoint — advance to next state
        if (loc[0] == currentTarget[0] && loc[1] == currentTarget[1]) {
            advanceRobotState();
            return;
        }

        if (path.isEmpty() || pathIndex >= path.size()) {
            computePath();
            if (path.isEmpty()) return;
        }

        int[] next = path.get(pathIndex);

        // Reactive collision avoidance: wait if next cell is occupied by another robot
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) env.getGrid();
        ColorSimpleCell nextCell = grid[next[0]][next[1]];
        boolean blocked = (nextCell != null
                && nextCell.getContent() != null
                && !(nextCell.getContent() instanceof ColorObstacle));
        if (blocked) {
            stuckCounter++;
            if (stuckCounter >= 3) {
                attemptEscape(loc, grid);
                stuckCounter = 0;
            }
            return;
        }
        stuckCounter = 0;

        Orientation required = directionOf(loc[0], loc[1], next[0], next[1]);
        turnToFace(required);

        boolean moved = moveForward();
        if (moved) {
            pathIndex++;
        } else {
            computePath();
        }
    }

    private void advanceRobotState() {
        switch (currentRobotState) {
            case GOING_TO_PACKAGE:
                currentRobotState = RobotRobotState.GOING_TO_SAFEPOINT;
                currentTarget = safepointPos;
                computePath();
                break;
            case GOING_TO_SAFEPOINT:
                currentRobotState = RobotRobotState.GOING_TO_DELIVERY;
                currentTarget = deliveryPos;
                computePath();
                break;
            case GOING_TO_DELIVERY:
                currentRobotState = RobotRobotState.GOING_TO_EXIT;
                currentTarget = exitPos;
                computePath();
                break;
            case GOING_TO_EXIT:
                currentRobotState = RobotRobotState.DONE;
                break;
            default:
                break;
        }
    }

    private void computePath() {
        path = bfs(getLocation(), currentTarget);
        pathIndex = 0;
    }

    private void attemptEscape(int[] loc, ColorSimpleCell[][] grid) {
        int dr = currentTarget[0] - loc[0];
        int dc = currentTarget[1] - loc[1];
        int[][] escapeDirs = (Math.abs(dc) >= Math.abs(dr))
                ? new int[][]{{-1,0},{1,0},{0,-1},{0,1}}
                : new int[][]{{0,-1},{0,1},{-1,0},{1,0}};

        for (int[] d : escapeDirs) {
            int nr = loc[0] + d[0], nc = loc[1] + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            if (isForbiddenTransition(loc[0], loc[1], nr, nc)) continue;
            ColorSimpleCell cell = grid[nr][nc];
            if (cell != null && cell.getContent() != null) continue;
            turnToFace(directionOf(loc[0], loc[1], nr, nc));
            if (moveForward()) {
                computePath();
                return;
            }
        }
        computePath();
    }

    // -------------------------------------------------------------------------
    // BFS
    // -------------------------------------------------------------------------

    private List<int[]> bfs(int[] start, int[] goal) {
        boolean[][] visited = new boolean[rows][cols];
        int[][][] prev = new int[rows][cols][2];
        for (int[][] row : prev)
            for (int[] cell : row)
                Arrays.fill(cell, -1);

        Queue<int[]> q = new LinkedList<>();
        q.add(start);
        visited[start[0]][start[1]] = true;

        int[][] dirs = {{-1,0},{0,1},{1,0},{0,-1}};
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) env.getGrid();

        while (!q.isEmpty()) {
            int[] cur = q.poll();
            if (cur[0] == goal[0] && cur[1] == goal[1]) {
                return reconstruct(prev, start, goal);
            }
            for (int[] d : dirs) {
                int nr = cur[0] + d[0], nc = cur[1] + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols || visited[nr][nc]) continue;
                if (isForbiddenTransition(cur[0], cur[1], nr, nc)) continue;
                ColorSimpleCell cell = grid[nr][nc];
                boolean isObstacle = (cell != null
                        && cell.getContent() != null
                        && cell.getContent() instanceof ColorObstacle
                        && !(cell.getContent() instanceof ZoneMarker));
                if (!isObstacle) {
                    visited[nr][nc] = true;
                    prev[nr][nc] = cur;
                    q.add(new int[]{nr, nc});
                }
            }
        }
        return new ArrayList<>();
    }

    private List<int[]> reconstruct(int[][][] prev, int[] start, int[] goal) {
        List<int[]> result = new ArrayList<>();
        int[] cur = goal;
        while (cur[0] != start[0] || cur[1] != start[1]) {
            result.add(0, cur);
            int[] p = prev[cur[0]][cur[1]];
            if (p[0] == -1) break;
            cur = p;
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Right-panel wall enforcement
    // -------------------------------------------------------------------------

    /**
     * Returns true if moving from (r1,c1) to (r2,c2) would cross a separator
     * line in the right panel (cols >= 18).
     * Only vertical moves (same column) are restricted; horizontal moves are free.
     */
    private boolean isForbiddenTransition(int r1, int c1, int r2, int c2) {
        // Only applies in the right panel
        if (Math.max(c1, c2) < RIGHT_PANEL_COL) return false;
        // Only vertical movement can cross a separator line
        if (c1 != c2) return false;
        int minR = Math.min(r1, r2);
        for (int sep : SEPARATOR_ROWS) {
            if (minR == sep) return true; // line is below row sep → sep↔sep+1 blocked
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Orientation helpers
    // -------------------------------------------------------------------------

    private Orientation directionOf(int fromR, int fromC, int toR, int toC) {
        if (toR < fromR) return Orientation.up;
        if (toR > fromR) return Orientation.down;
        if (toC > fromC) return Orientation.right;
        return Orientation.left;
    }

    private void turnToFace(Orientation target) {
        Orientation cur = getCurrentOrientation();
        if (cur == null || cur == Orientation.unknown) {
            setCurrentOrientation(Orientation.up);
            cur = Orientation.up;
        }
        int leftTurns = (ccwIndex(target) - ccwIndex(cur) + 4) % 4;
        for (int i = 0; i < leftTurns; i++) turnLeft();
    }

    private int ccwIndex(Orientation o) {
        switch (o) {
            case up:    return 0;
            case left:  return 1;
            case down:  return 2;
            case right: return 3;
            default:    return 0;
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public RobotRobotState  getRobotState()  { return currentRobotState; }
    public PackageItem getPackage() { return assignedPackage; }

    // -------------------------------------------------------------------------

    @Override
    public void handleMessage(Message msg) {
        // No inter-robot communication
    }
}
