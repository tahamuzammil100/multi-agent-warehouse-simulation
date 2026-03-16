package fr.emse;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;

import fr.emse.fayol.maqit.simulator.components.ColorObstacle;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

/**
 * SimulatorGUI - Main graphical user interface for the warehouse simulation.
 *
 * This class handles all visual rendering of the warehouse environment and provides
 * a real-time view of the simulation state. It displays the warehouse grid, all
 * moving entities (robots, workers, packages), static obstacles, and delivery statistics.
 *
 * Rendering Layers (drawn from back to front):
 *   1. Base layer: Grey cell backgrounds for the warehouse floor
 *   2. Zone backgrounds: Color-coded entry zones on the right panel (columns 18-19)
 *   3. Safe zones: Semi-transparent yellow rectangles marking intermediate storage areas
 *   4. Delivery zones: Red hatched oval areas indicating package delivery locations
 *   5. Grid overlay: Optional grid lines showing cell boundaries
 *   6. Zone dividers: Thick separator lines between different warehouse sections
 *   7. Waiting packages: Small colored circles at entry points
 *   8. Active entities: Obstacles (gray squares), robots (colored squares), workers (circles)
 *   9. Border walls: Thick black perimeter with entry/exit openings
 *
 * Statistics Display:
 *   - Right sidebar panel showing real-time delivery metrics
 *   - Per-package tracking (ID, zone, transit time)
 *   - Overall performance statistics (total delivered, average time)
 */
public class SimulatorGUI {

    private final ColorSimpleCell[][] grid;

    /** Each entry: {minRow, minCol, maxRow, maxCol} — drawn as hatched red oval. */
    private final int[][] ovalZones;

    /** Each entry: {minRow, minCol, maxRow, maxCol} — drawn as semi-transparent yellow rect. */
    private final int[][] yellowZones;
    /** Each entry: {minRow, minCol, maxRow, maxCol} — drawn as cyan recharge zone. */
    private final int[][] rechargeZones;

    /** Per-row background colour for the right panel (cols 18-19). Length == grid rows. */
    private final Color[] rowRightColors;

    /** Row indices after which a thick black separator line is drawn on the right panel. */
    private final int[] separatorRows;
    /** Exit cells to highlight. Each entry is {row, col}. */
    private final int[][] exitCells;

    private final int cellSize;
    private final int windowX, windowY;
    private final String title;
    private final float lineStroke;
    private final int padding;
    private final boolean showGrid;

    /** Waiting packages: "row,col" → colour. Updated by simulator each step. */
    private volatile Map<String, Color> packageOverlay = new ConcurrentHashMap<>();

    // --- Stats data updated each step by the simulator ---
    private volatile int  statStep           = 0;
    private volatile int  statDeliveredCount = 0;
    private volatile int  statTotalPallets   = 0;
    private volatile long statTotalTime      = 0;
    /** {palletId, zone, stepsInTransit} for each active robot. */
    private volatile List<int[]> statActiveRobots  = new CopyOnWriteArrayList<>();
    /** {palletId, zone, deliverySteps} for each delivered package. */
    private volatile List<int[]> statDeliveredPkgs = new CopyOnWriteArrayList<>();
    /** {robotId, batteryLevel, maxBattery} for each robot in the fleet. */
    private volatile List<int[]> statRobotBatteries = new CopyOnWriteArrayList<>();

    private JFrame  frame;
    private JPanel  panel;
    private JPanel  statsPanel;
    private BufferedImage humanImage;
    private BufferedImage obstacleImage;
    private BufferedImage packageImage;
    private BufferedImage robotImage;

    private static final Color BG        = new Color(245, 245, 245);
    private static final Color GRID_LINE = new Color(210, 210, 210);

    /** Width of the statistics side-panel in pixels. */
    private static final int STATS_WIDTH = 260;

    /** First column of the right-side coloured panel. */
    private static final int RIGHT_COL = 18;

    // -------------------------------------------------------------------------

    public SimulatorGUI(ColorSimpleCell[][] grid,
                        int windowX, int windowY,
                        int cellSize, String title,
                        int[][] ovalZones,
                        int[][] yellowZones,
                        int[][] rechargeZones,
                        Color[] rowRightColors,
                        int[] separatorRows,
                        int[][] exitCells,
                        float lineStroke,
                        int padding,
                        boolean showGrid) {
        this.grid           = grid;
        this.ovalZones      = ovalZones      != null ? ovalZones      : new int[0][];
        this.yellowZones    = yellowZones    != null ? yellowZones    : new int[0][];
        this.rechargeZones  = rechargeZones  != null ? rechargeZones  : new int[0][];
        this.rowRightColors = rowRightColors != null ? rowRightColors : new Color[0];
        this.separatorRows  = separatorRows  != null ? separatorRows  : new int[0];
        this.exitCells      = exitCells      != null ? exitCells      : new int[0][];
        this.windowX  = windowX;
        this.windowY  = windowY;
        this.cellSize    = cellSize;
        this.title       = title;
        this.lineStroke  = lineStroke;
        this.padding    = padding;
        this.showGrid    = showGrid;
        loadHumanImage();
        loadObstacleImage();
        loadPackageImage();
        loadRobotImage();
    }

    private void loadHumanImage() {
        try {
            // Try loading from resources first
            File f = new File("src/main/resources/people.png");
            if (f.exists()) {
                humanImage = ImageIO.read(f);
                System.out.println("[Display] People image loaded: " + f.getAbsolutePath());
                return;
            }

            // Fallback to old assets paths
            String[] candidates = {
                "assets/human_icon.png", "assets/human_icon.jpg",
                "assets/human.png",      "assets/human.jpg"
            };
            for (String name : candidates) {
                f = new File(name);
                if (f.exists()) {
                    humanImage = ImageIO.read(f);
                    System.out.println("[Display] Human icon loaded: " + f.getAbsolutePath());
                    return;
                }
            }
        } catch (IOException e) {
            System.out.println("[Display] Failed to load people image: " + e.getMessage());
        }
        System.out.println("[Display] No people icon found — using default circle.");
    }

    private void loadObstacleImage() {
        try {
            File f = new File("src/main/resources/obstacle.png");
            if (f.exists()) {
                obstacleImage = ImageIO.read(f);
                System.out.println("[Display] Obstacle image loaded: " + f.getAbsolutePath());
            } else {
                System.out.println("[Display] Obstacle image not found at: " + f.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("[Display] Failed to load obstacle image: " + e.getMessage());
        }
    }

    private void loadPackageImage() {
        try {
            File f = new File("src/main/resources/package.png");
            if (f.exists()) {
                packageImage = ImageIO.read(f);
                System.out.println("[Display] Package image loaded: " + f.getAbsolutePath());
            } else {
                System.out.println("[Display] Package image not found at: " + f.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("[Display] Failed to load package image: " + e.getMessage());
        }
    }

    private void loadRobotImage() {
        try {
            File f = new File("src/main/resources/robot.png");
            if (f.exists()) {
                robotImage = ImageIO.read(f);
                System.out.println("[Display] Robot image loaded: " + f.getAbsolutePath());
            } else {
                System.out.println("[Display] Robot image not found at: " + f.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("[Display] Failed to load robot image: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------

    public void init() {
        int rows = grid.length, cols = grid[0].length;

        panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                render((Graphics2D) g, rows, cols);
            }
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(padding * 2 + cols * cellSize, padding * 2 + rows * cellSize);
            }
        };

        statsPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                renderStats((Graphics2D) g);
            }
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(STATS_WIDTH, padding * 2 + rows * cellSize);
            }
        };
        statsPanel.setBackground(new Color(245, 245, 245));

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel,      BorderLayout.CENTER);
        frame.add(statsPanel, BorderLayout.EAST);
        frame.pack();
        frame.setLocation(windowX, windowY);
        frame.setVisible(true);
    }

    public void refresh() {
        if (frame != null) {
            frame.validate();
            frame.repaint();
        }
    }

    public void setPackageOverlay(Map<String, Color> overlay) {
        this.packageOverlay = new ConcurrentHashMap<>(overlay);
    }

    /**
     * Push fresh stats into the side panel. Called once per simulation step.
     *
     * @param step          current step number
     * @param delivered     packages delivered so far
     * @param total         total packages in this run
     * @param totalTime     cumulative delivery time (sum of steps per package)
     * @param activeRobots  list of int[]{palletId, zone, stepsInTransit}
     * @param deliveredPkgs list of int[]{palletId, zone, deliverySteps} (all delivered so far)
     * @param robotBatteries list of int[]{robotId, batteryLevel, maxBattery} for all robots
     */
    public void updateStats(int step, int delivered, int total, long totalTime,
                            List<int[]> activeRobots, List<int[]> deliveredPkgs,
                            List<int[]> robotBatteries) {
        this.statStep           = step;
        this.statDeliveredCount = delivered;
        this.statTotalPallets   = total;
        this.statTotalTime      = totalTime;
        this.statActiveRobots   = new CopyOnWriteArrayList<>(activeRobots);
        this.statDeliveredPkgs  = new CopyOnWriteArrayList<>(deliveredPkgs);
        this.statRobotBatteries = new CopyOnWriteArrayList<>(robotBatteries);
    }

    // -------------------------------------------------------------------------
    // Stats panel renderer
    // -------------------------------------------------------------------------

    private void renderStats(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w  = statsPanel.getWidth();
        int h  = statsPanel.getHeight();
        int lh = 18;
        int mx = 10;

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        Font plain = new Font("SansSerif", Font.PLAIN, 12);
        Font bold  = new Font("SansSerif", Font.BOLD,  12);
        Font small = new Font("SansSerif", Font.PLAIN, 11);

        int y = 20;

        // Step & delivered
        g.setFont(bold);
        g.setColor(Color.DARK_GRAY);
        g.drawString("Step: " + statStep, mx, y);
        y += lh;
        g.drawString("Delivered: " + statDeliveredCount + " / " + statTotalPallets, mx, y);
        y += lh + 10;

        // Robot Battery Status Section
        List<int[]> batteries = statRobotBatteries;
        g.setFont(bold);
        g.setColor(Color.DARK_GRAY);
        g.drawString("Robot Fleet (" + batteries.size() + "):", mx, y);
        y += lh;

        g.setFont(small);
        if (batteries.isEmpty()) {
            g.setColor(Color.GRAY);
            g.drawString("  no robots yet", mx, y);
            y += lh;
        } else {
            for (int[] bat : batteries) {
                int robotId = bat[0];
                int battery = bat[1];
                int maxBattery = bat[2];
                int batteryPercent = maxBattery > 0 ? (battery * 100) / maxBattery : 0;

                // Determine battery color based on level
                Color batteryColor;
                if (batteryPercent > 50) {
                    batteryColor = new Color(40, 160, 40);  // Green
                } else if (batteryPercent > 25) {
                    batteryColor = new Color(220, 160, 20); // Orange
                } else {
                    batteryColor = new Color(200, 40, 40);  // Red
                }

                // Draw robot name
                g.setColor(Color.DARK_GRAY);
                g.drawString("  Robot #" + robotId + ":", mx, y);

                // Draw battery bar
                int barX = mx + 90;
                int barY = y - 10;
                int barWidth = 100;
                int barHeight = 12;

                // Background (empty battery)
                g.setColor(new Color(220, 220, 220));
                g.fillRect(barX, barY, barWidth, barHeight);

                // Battery fill
                int fillWidth = (battery * barWidth) / Math.max(1, maxBattery);
                g.setColor(batteryColor);
                g.fillRect(barX, barY, fillWidth, barHeight);

                // Border
                g.setColor(Color.GRAY);
                g.drawRect(barX, barY, barWidth, barHeight);

                // Percentage text
                g.setColor(Color.DARK_GRAY);
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                String batteryText = battery + "/" + maxBattery + " (" + batteryPercent + "%)";
                g.drawString(batteryText, barX + barWidth + 5, y);
                g.setFont(small);

                y += lh;
            }
        }
        y += 10;

        // Active robots
        List<int[]> active = statActiveRobots;
        g.setFont(bold);
        g.setColor(Color.DARK_GRAY);
        g.drawString("Active (" + active.size() + "):", mx, y);
        y += lh;

        g.setFont(plain);
        if (active.isEmpty()) {
            g.setColor(Color.GRAY);
            g.drawString("  none", mx, y);
            y += lh;
        } else {
            for (int[] r : active) {
                g.setColor(Color.DARK_GRAY);
                g.drawString("  Pallet #" + r[0] + " (Z" + r[1] + "): " + r[2] + " steps", mx, y);
                y += lh;
            }
        }
        y += 10;

        // Delivered packages
        List<int[]> delivered = statDeliveredPkgs;
        g.setFont(bold);
        g.setColor(Color.DARK_GRAY);
        g.drawString("Delivered (" + delivered.size() + "):", mx, y);
        y += lh;

        g.setFont(plain);
        if (delivered.isEmpty()) {
            g.setColor(Color.GRAY);
            g.drawString("  none yet", mx, y);
            y += lh;
        } else {
            int footerH = 50;
            int maxRows = Math.max(1, (h - y - footerH) / lh);
            int start   = Math.max(0, delivered.size() - maxRows);
            if (start > 0) {
                g.setColor(Color.GRAY);
                g.drawString("  +" + start + " earlier…", mx, y);
                y += lh;
            }
            for (int i = start; i < delivered.size(); i++) {
                int[] d = delivered.get(i);
                g.setColor(Color.DARK_GRAY);
                g.drawString("  Pallet #" + d[0] + " (Z" + d[1] + "): " + d[2] + " steps", mx, y);
                y += lh;
            }
        }

        // Overall (pinned to bottom)
        int oy = h - 40;
        double avg = statDeliveredCount > 0 ? (double) statTotalTime / statDeliveredCount : 0.0;
        g.setFont(bold);
        g.setColor(Color.DARK_GRAY);
        g.drawString("Total: " + statTotalTime + " steps", mx, oy);
        g.drawString(String.format("Avg: %.1f steps/pkg", avg), mx, oy + lh);
    }

    // -------------------------------------------------------------------------
    // Grid renderer
    // -------------------------------------------------------------------------

    private void render(Graphics2D g, int rows, int cols) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        // Shift all drawing by padding so space appears around all sides of the grid
        g.translate(padding, padding);

        // --- Pass 1: cell backgrounds ----------------------------------------
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c >= RIGHT_COL && r < rowRightColors.length && rowRightColors[r] != null) {
                    g.setColor(rowRightColors[r]);
                } else {
                    g.setColor(BG);
                }
                g.fillRect(c * cellSize, r * cellSize, cellSize, cellSize);
            }
        }

        // --- Pass 2: yellow zones (semi-transparent) -------------------------
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        g.setColor(new Color(240, 215, 100));
        for (int[] z : yellowZones) {
            int px = z[1] * cellSize, py = z[0] * cellSize;
            int w  = (z[3] - z[1] + 1) * cellSize;
            int h  = (z[2] - z[0] + 1) * cellSize;
            g.fillRect(px, py, w, h);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        // --- Pass 3: recharge zones (semi-transparent cyan) ------------------
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.60f));
        g.setColor(new Color(120, 220, 240));
        for (int[] z : rechargeZones) {
            int px = z[1] * cellSize, py = z[0] * cellSize;
            int w  = (z[3] - z[1] + 1) * cellSize;
            int h  = (z[2] - z[0] + 1) * cellSize;
            g.fillRect(px, py, w, h);
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g.setColor(new Color(20, 110, 130));
        g.setStroke(new BasicStroke(2f));
        for (int[] z : rechargeZones) {
            int px = z[1] * cellSize, py = z[0] * cellSize;
            int w  = (z[3] - z[1] + 1) * cellSize;
            int h  = (z[2] - z[0] + 1) * cellSize;
            g.drawRect(px + 1, py + 1, Math.max(1, w - 2), Math.max(1, h - 2));
            g.setColor(new Color(15, 90, 110));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, cellSize / 3)));
            g.drawString("CHG", px + 4, py + Math.max(14, cellSize / 2));
            g.setColor(new Color(20, 110, 130));
        }
        g.setStroke(new BasicStroke(1));

        // --- Pass 4: hatched red rectangular zones ---------------------------
        for (int[] z : ovalZones) {
            drawHatchedRect(g, z[0], z[1], z[2], z[3]);
        }

        // --- Pass 5: exit markers --------------------------------------------
        for (int[] exit : exitCells) {
            if (exit == null || exit.length < 2) continue;
            int row = exit[0];
            int col = exit[1];
            if (row < 0 || row >= rows || col < 0 || col >= cols) continue;

            int px = col * cellSize;
            int py = row * cellSize;

            g.setColor(new Color(140, 230, 230));
            g.fillRect(px, py, cellSize, cellSize);
            g.setColor(new Color(25, 120, 120));
            g.setStroke(new BasicStroke(2));
            g.drawRect(px + 1, py + 1, cellSize - 2, cellSize - 2);
            g.setStroke(new BasicStroke(1));
        }

        // --- Pass 6: grid lines ----------------------------------------------
        if (showGrid) {
            g.setColor(GRID_LINE);
            g.setStroke(new BasicStroke(1));
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    g.drawRect(c * cellSize, r * cellSize, cellSize, cellSize);
                }
            }
        }

        // --- Pass 7: thick separator lines on right panel --------------------
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(lineStroke));
        for (int sep : separatorRows) {
            int y = (sep + 1) * cellSize;
            g.drawLine(RIGHT_COL * cellSize, y, cols * cellSize, y);
        }
        g.setStroke(new BasicStroke(1));

        // --- Pass 8: waiting packages ----------------------------------------
        Map<String, Color> overlay = packageOverlay;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Color pkgColor = overlay.get(r + "," + c);
                if (pkgColor == null) continue;
                int px = c * cellSize, py = r * cellSize;

                if (packageImage != null) {
                    // Draw package image scaled to cell size
                    g.drawImage(packageImage, px, py, cellSize, cellSize, null);
                } else {
                    // Fallback: draw as colored rectangle if image not loaded
                    int m = cellSize / 4;
                    g.setColor(pkgColor);
                    g.fillRect(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m);
                    g.setColor(pkgColor.darker());
                    g.setStroke(new BasicStroke(2));
                    g.drawRect(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m);
                    g.setStroke(new BasicStroke(1));
                }
            }
        }

        // --- Pass 9: content (obstacles, robots, humans) ---------------------
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int px = c * cellSize, py = r * cellSize;
                ColorSimpleCell cell = grid[r][c];
                if (cell == null || cell.getContent() == null) continue;
                Object content = cell.getContent();
                if      (content instanceof WarehouseWorker) drawHuman   (g, px, py);
                else if (content instanceof DeliveryBot)   drawRobot   (g, px, py, (DeliveryBot) content);
                else if (content instanceof ColorObstacle) drawObstacle(g, px, py, (ColorObstacle) content);
            }
        }

        // --- Pass 10: border wall (thick black outline with entry/exit gaps) -
        int W = cols * cellSize;
        int H = rows * cellSize;
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(lineStroke));

        // Top and bottom: solid full-width lines
        g.drawLine(0, 0, W, 0);
        g.drawLine(0, H, W, H);

        // Left edge (col 0): open for zone-1 exit (rows 0-2) and zone-2 exit (rows 12-14)
        // Draw only the middle segment: from bottom of row 2 to top of row 12
        g.drawLine(0, 3 * cellSize, 0, 12 * cellSize);

        // Right edge (col max): open only for package entries (rows 3-11)
        // Rows 2 and 12 (robot entries) are closed — robots spawn directly onto the grid
        g.drawLine(W, 0,             W, 3  * cellSize);
        g.drawLine(W, 12 * cellSize, W, H);

        g.setStroke(new BasicStroke(1));
    }

    /** Draws a hatched red rectangle spanning the given grid row/col bounds. */
    private void drawHatchedRect(Graphics2D g, int minR, int minC, int maxR, int maxC) {
        int px = minC * cellSize;
        int py = minR * cellSize;
        int w  = (maxC - minC + 1) * cellSize;
        int h  = (maxR - minR + 1) * cellSize;

        Shape rect    = new Rectangle(px + 3, py + 3, Math.max(1, w - 6), Math.max(1, h - 6));
        Shape oldClip = g.getClip();

        // Light pink fill
        g.setClip(rect);
        g.setColor(new Color(255, 220, 220));
        g.fill(rect);

        // Diagonal red hatching
        g.setColor(new Color(200, 50, 50));
        g.setStroke(new BasicStroke(1.5f));
        int diag = w + h;
        for (int d = -diag; d < diag; d += 10) {
            g.drawLine(px + d, py + h, px + d + h, py);
        }

        g.setClip(oldClip);
        g.setStroke(new BasicStroke(1));

        // Rectangle border
        g.setColor(new Color(180, 40, 40));
        g.setStroke(new BasicStroke(2));
        g.draw(rect);
        g.setStroke(new BasicStroke(1));
    }

    private void drawHuman(Graphics2D g, int px, int py) {
        if (humanImage != null) {
            // Draw people image scaled to cell size (same as obstacle)
            g.drawImage(humanImage, px, py, cellSize, cellSize, null);
        } else {
            // Fallback: draw as circle if image not loaded
            int m = cellSize / 8;
            g.setColor(new Color(200, 160, 100));
            g.fillOval(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m);
            g.setColor(Color.DARK_GRAY);
            g.drawOval(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m);
        }
    }

    private void drawRobot(Graphics2D g, int px, int py, DeliveryBot robot) {
        if (robotImage != null) {
            // Draw robot image scaled to cell size
            g.drawImage(robotImage, px, py, cellSize, cellSize, null);
        } else {
            // Fallback: draw as rounded rectangle if image not loaded
            int[] rgb = robot.getColor();
            int m = cellSize / 3;
            g.setColor(new Color(rgb[0], rgb[1], rgb[2]));
            g.fillRoundRect(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m, 6, 6);
            g.setColor(Color.DARK_GRAY);
            g.drawRoundRect(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m, 6, 6);
        }
    }

    private void drawObstacle(Graphics2D g, int px, int py, ColorObstacle obs) {
        if (obstacleImage != null) {
            // Draw obstacle image scaled to cell size
            g.drawImage(obstacleImage, px, py, cellSize, cellSize, null);
        } else {
            // Fallback: draw as rectangle if image not loaded
            int[] rgb = obs.getColor();
            int m = 3;
            g.setColor(new Color(rgb[0], rgb[1], rgb[2]));
            g.fillRect(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(lineStroke));
            g.drawRect(px + m, py + m, cellSize - 2 * m, cellSize - 2 * m);
            g.setStroke(new BasicStroke(1));
        }
    }
}
