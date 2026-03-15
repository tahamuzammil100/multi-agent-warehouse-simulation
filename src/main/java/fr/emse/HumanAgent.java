package fr.emse;

import java.awt.Color;
import java.util.Random;

import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.components.Orientation;
import fr.emse.fayol.maqit.simulator.environment.ColorGridEnvironment;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * A randomly-walking human agent that acts as a movable obstacle.
 * Human agents wander the warehouse floor and temporarily block AMR paths.
 */
public class HumanAgent extends ColorInteractionRobot<ColorSimpleCell> {

    private final ColorGridEnvironment env;
    private final int rows, cols;
    private final Random rnd;

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public HumanAgent(String name, int field, int[] pos, Color color,
                      int rows, int cols, ColorGridEnvironment env, long seed) {
        super(name, field, pos, new int[]{color.getRed(), color.getGreen(), color.getBlue()});
        this.env = env;
        this.rows = rows;
        this.cols = cols;
        this.rnd = new Random(seed);
        setCurrentOrientation(Orientation.up);
    }

    @Override
    public void move(int nb) {
        for (int i = 0; i < nb; i++) step();
    }

    private void step() {
        int[] loc = getLocation();
        ColorSimpleCell[][] grid = (ColorSimpleCell[][]) env.getGrid();

        // Try a random direction; fall back to others if blocked
        int start = rnd.nextInt(4);
        for (int i = 0; i < 4; i++) {
            int[] d = DIRS[(start + i) % 4];
            int nr = loc[0] + d[0], nc = loc[1] + d[1];
            if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
            ColorSimpleCell cell = grid[nr][nc];
            if (cell != null && cell.getContent() != null) continue; // occupied
            turnToFace(directionOf(d[0], d[1]));
            moveForward();
            return;
        }
        // All directions blocked — stay put this step
    }

    private Orientation directionOf(int dr, int dc) {
        if (dr < 0) return Orientation.up;
        if (dr > 0) return Orientation.down;
        if (dc > 0) return Orientation.right;
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

    @Override
    public void handleMessage(Message msg) {
        // Human agents don't communicate
    }
}
