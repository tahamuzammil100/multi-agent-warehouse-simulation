package fr.emse;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

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
    /** {minTime, maxTime, avgTime} - extended metrics. */
    private volatile int[] statExtendedMetrics = new int[3];

    private JFrame  frame;
    private JPanel  panel;
    private JPanel  statsPanel;
    private BufferedImage humanImage;
    private BufferedImage obstacleImage;
    private BufferedImage packageImage;
    private BufferedImage robotImage;
    private BufferedImage robotWithPackageImage;

    // Modern color scheme for warehouse floor
    private static final Color BG        = new Color(240, 242, 245);
    private static final Color GRID_LINE = new Color(200, 205, 210);
    private static final Color FLOOR_TILE_LIGHT = new Color(245, 247, 250);
    private static final Color FLOOR_TILE_DARK = new Color(235, 237, 240);
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 15);

    /** Width of the statistics side-panel in pixels. */
    private static final int STATS_WIDTH = 260;

    /** First column of the right-side coloured panel. */
    private static final int RIGHT_COL = 22;

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
        loadRobotWithPackageImage();
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

    private void loadRobotWithPackageImage() {
        try {
            File f = new File("src/main/resources/robot_package.png");
            if (f.exists()) {
                robotWithPackageImage = ImageIO.read(f);
                System.out.println("[Display] Robot with package image loaded: " + f.getAbsolutePath());
            } else {
                System.out.println("[Display] Robot with package image not found at: " + f.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("[Display] Failed to load robot with package image: " + e.getMessage());
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
     * @param extendedMetrics array of {minTime, maxTime, avgTime}
     */
    public void updateStats(int step, int delivered, int total, long totalTime,
                            List<int[]> activeRobots, List<int[]> deliveredPkgs,
                            List<int[]> robotBatteries, int[] extendedMetrics) {
        this.statStep           = step;
        this.statDeliveredCount = delivered;
        this.statTotalPallets   = total;
        this.statTotalTime      = totalTime;
        this.statActiveRobots   = new CopyOnWriteArrayList<>(activeRobots);
        this.statDeliveredPkgs  = new CopyOnWriteArrayList<>(deliveredPkgs);
        this.statRobotBatteries = new CopyOnWriteArrayList<>(robotBatteries);
        this.statExtendedMetrics = extendedMetrics != null ? extendedMetrics.clone() : new int[3];
    }

    // -------------------------------------------------------------------------
    // Stats panel renderer
    // -------------------------------------------------------------------------

    private void renderStats(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w  = statsPanel.getWidth();
        int h  = statsPanel.getHeight();
        int mx = 12;

        // Background
        g.setColor(new Color(250, 250, 252));
        g.fillRect(0, 0, w, h);

        Font titleFont = new Font("SansSerif", Font.BOLD, 14);
        Font labelFont = new Font("SansSerif", Font.PLAIN, 12);
        Font valueFont = new Font("SansSerif", Font.BOLD, 13);
        Font smallFont = new Font("SansSerif", Font.PLAIN, 10);

        int y = 15;

        // ========== HEADER SECTION ==========
        drawSectionHeader(g, "SIMULATION STATUS", mx, y, w - 24, titleFont);
        y += 30;

        // Current step with icon
        drawMetricRow(g, "⏱", "Step", String.valueOf(statStep), mx, y, labelFont, valueFont);
        y += 25;

        // Delivery progress with icon
        double progress = statTotalPallets > 0 ? (statDeliveredCount * 100.0) / statTotalPallets : 0;
        drawMetricRow(g, "📦", "Delivered", statDeliveredCount + " / " + statTotalPallets +
                      String.format(" (%.0f%%)", progress), mx, y, labelFont, valueFont);
        y += 30;

        // ========== ROBOT FLEET SECTION ==========
        List<int[]> batteries = statRobotBatteries;
        drawSectionHeader(g, "ROBOT FLEET", mx, y, w - 24, titleFont);
        y += 30;

        if (batteries.isEmpty()) {
            g.setFont(labelFont);
            g.setColor(Color.GRAY);
            g.drawString("No robots active", mx + 8, y);
            y += 25;
        } else {
            for (int[] bat : batteries) {
                int robotId = bat[0];
                int battery = bat[1];
                int maxBattery = bat[2];
                int batteryPercent = maxBattery > 0 ? (battery * 100) / maxBattery : 0;

                y = drawRobotCard(g, robotId, battery, maxBattery, batteryPercent, mx, y, w - 24,
                                  labelFont, valueFont, smallFont);
                y += 8; // Spacing between cards
            }
        }
        y += 15;

        // ========== ACTIVE DELIVERIES SECTION ==========
        List<int[]> active = statActiveRobots;
        if (!active.isEmpty()) {
            drawSectionHeader(g, "ACTIVE DELIVERIES", mx, y, w - 24, titleFont);
            y += 28;

            for (int[] r : active) {
                y = drawActiveDeliveryCard(g, r[0], r[1], r[2], mx, y, w - 24, labelFont, smallFont);
                y += 6;
            }
            y += 10;
        }

        // ========== PERFORMANCE METRICS (Bottom) ==========
        int footerY = h - 110;
        drawSectionHeader(g, "PERFORMANCE", mx, footerY, w - 24, titleFont);
        footerY += 28;

        // Extract metrics
        int minTime = statExtendedMetrics[0];
        int maxTime = statExtendedMetrics[1];
        double avgTime = statExtendedMetrics[2] / 10.0; // Stored as integer * 10

        if (statDeliveredCount > 0) {
            // Average delivery time
            drawMetricRow(g, "📊", "Avg", String.format("%.1f steps", avgTime), mx, footerY, labelFont, valueFont);
            footerY += 22;

            // Min/Max delivery times
            g.setFont(labelFont);
            g.setColor(new Color(90, 90, 90));
            g.drawString("⚡ Best:", mx + 25, footerY);
            g.setFont(valueFont);
            g.setColor(new Color(76, 175, 80)); // Green
            g.drawString(minTime + " steps", mx + 90, footerY);
            footerY += 22;

            g.setFont(labelFont);
            g.setColor(new Color(90, 90, 90));
            g.drawString("🐌 Worst:", mx + 25, footerY);
            g.setFont(valueFont);
            g.setColor(new Color(244, 67, 54)); // Red
            g.drawString(maxTime + " steps", mx + 90, footerY);
        } else {
            g.setFont(labelFont);
            g.setColor(Color.GRAY);
            g.drawString("No deliveries yet", mx + 8, footerY);
        }
    }

    /**
     * Draws a section header with underline
     */
    private void drawSectionHeader(Graphics2D g, String title, int x, int y, int width, Font font) {
        g.setFont(font);
        g.setColor(new Color(50, 50, 80));
        g.drawString(title, x, y);

        // Underline
        g.setColor(new Color(100, 100, 180));
        g.fillRect(x, y + 4, width, 2);
    }

    /**
     * Draws a metric row with icon, label, and value
     */
    private void drawMetricRow(Graphics2D g, String icon, String label, String value,
                               int x, int y, Font labelFont, Font valueFont) {
        // Icon
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(new Color(80, 80, 120));
        g.drawString(icon, x, y);

        // Label
        g.setFont(labelFont);
        g.setColor(new Color(90, 90, 90));
        g.drawString(label + ":", x + 25, y);

        // Value
        g.setFont(valueFont);
        g.setColor(new Color(40, 40, 60));
        g.drawString(value, x + 90, y);
    }

    /**
     * Draws a robot status card with battery visualization
     */
    private int drawRobotCard(Graphics2D g, int robotId, int battery, int maxBattery,
                              int batteryPercent, int x, int y, int cardWidth,
                              Font labelFont, Font valueFont, Font smallFont) {
        int cardHeight = 50;

        // Card background with subtle shadow
        g.setColor(new Color(230, 230, 235));
        g.fillRoundRect(x + 2, y + 2, cardWidth, cardHeight, 8, 8);

        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, cardWidth, cardHeight, 8, 8);

        // Card border
        g.setColor(new Color(200, 200, 210));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, cardWidth, cardHeight, 8, 8);
        g.setStroke(new BasicStroke(1));

        // Robot icon (circle with R)
        int iconX = x + 10;
        int iconY = y + 12;
        int iconSize = 26;

        Color robotColor = new Color(70, 130, 200);
        g.setColor(robotColor);
        g.fillOval(iconX, iconY, iconSize, iconSize);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString("R", iconX + 9, iconY + 19);

        // Robot ID
        g.setFont(valueFont);
        g.setColor(new Color(40, 40, 60));
        g.drawString("Robot " + robotId, iconX + iconSize + 8, y + 18);

        // Battery status text
        g.setFont(smallFont);
        g.setColor(new Color(100, 100, 100));
        g.drawString(battery + " / " + maxBattery + " units", iconX + iconSize + 8, y + 32);

        // Battery bar
        int barX = x + 10;
        int barY = y + cardHeight - 12;
        int barWidth = cardWidth - 20;
        int barHeight = 8;

        // Determine battery color
        Color batteryColor;
        if (batteryPercent > 50) {
            batteryColor = new Color(76, 175, 80);  // Green
        } else if (batteryPercent > 25) {
            batteryColor = new Color(255, 152, 0);  // Orange
        } else {
            batteryColor = new Color(244, 67, 54);  // Red
        }

        // Battery bar background
        g.setColor(new Color(230, 230, 230));
        g.fillRoundRect(barX, barY, barWidth, barHeight, 4, 4);

        // Battery fill
        int fillWidth = (battery * barWidth) / Math.max(1, maxBattery);
        g.setColor(batteryColor);
        g.fillRoundRect(barX, barY, fillWidth, barHeight, 4, 4);

        // Battery percentage text on the bar
        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        g.setColor(Color.DARK_GRAY);
        String percentText = batteryPercent + "%";
        int textWidth = g.getFontMetrics().stringWidth(percentText);
        g.drawString(percentText, barX + barWidth - textWidth - 3, barY + 7);

        return y + cardHeight;
    }

    /**
     * Draws an active delivery card
     */
    private int drawActiveDeliveryCard(Graphics2D g, int palletId, int zone, int steps,
                                       int x, int y, int cardWidth, Font labelFont, Font smallFont) {
        int cardHeight = 28;

        // Card background
        g.setColor(new Color(255, 248, 225));
        g.fillRoundRect(x, y, cardWidth, cardHeight, 6, 6);

        // Border
        g.setColor(new Color(255, 193, 7));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, cardWidth, cardHeight, 6, 6);
        g.setStroke(new BasicStroke(1));

        // Icon
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.setColor(new Color(230, 126, 34));
        g.drawString("📦", x + 8, y + 18);

        // Text
        g.setFont(labelFont);
        g.setColor(new Color(60, 60, 60));
        g.drawString("Pallet " + palletId, x + 30, y + 13);

        g.setFont(smallFont);
        g.setColor(new Color(100, 100, 100));
        g.drawString("Zone " + zone + " • " + steps + " steps", x + 30, y + 23);

        return y + cardHeight;
    }

    // -------------------------------------------------------------------------
    // Grid renderer
    // -------------------------------------------------------------------------

    private void render(Graphics2D g, int rows, int cols) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

        // Shift all drawing by padding so space appears around all sides of the grid
        g.translate(padding, padding);

        // --- Pass 1: Modern checkered floor pattern ----------------------------------------
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int px = c * cellSize;
                int py = r * cellSize;

                // Right panel zone colors
                if (c >= RIGHT_COL && r < rowRightColors.length && rowRightColors[r] != null) {
                    // Create gradient effect for zone panels
                    GradientPaint gradient = new GradientPaint(
                        px, py, rowRightColors[r],
                        px + cellSize, py + cellSize, brighten(rowRightColors[r], 0.15f)
                    );
                    g.setPaint(gradient);
                    g.fillRect(px, py, cellSize, cellSize);
                } else {
                    // Checkered warehouse floor pattern
                    boolean isLightTile = (r + c) % 2 == 0;
                    g.setColor(isLightTile ? FLOOR_TILE_LIGHT : FLOOR_TILE_DARK);
                    g.fillRect(px, py, cellSize, cellSize);

                    // Add subtle inner shadow for depth
                    g.setColor(SHADOW_COLOR);
                    g.drawLine(px, py, px + cellSize - 1, py); // Top edge shadow
                    g.drawLine(px, py, px, py + cellSize - 1); // Left edge shadow
                }
            }
        }

        // --- Pass 2: yellow zones (intermediate storage with enhanced styling) -------------------------
        for (int[] z : yellowZones) {
            int px = z[1] * cellSize, py = z[0] * cellSize;
            int w  = (z[3] - z[1] + 1) * cellSize;
            int h  = (z[2] - z[0] + 1) * cellSize;

            // Gradient fill for intermediate storage zones
            GradientPaint yellowGradient = new GradientPaint(
                px, py, new Color(255, 235, 120, 140),
                px + w, py + h, new Color(240, 215, 100, 140)
            );
            g.setPaint(yellowGradient);
            g.fillRoundRect(px + 2, py + 2, w - 4, h - 4, 12, 12);

            // Border with shadow effect
            g.setColor(new Color(220, 180, 50, 200));
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(px + 2, py + 2, w - 4, h - 4, 12, 12);
            g.setStroke(new BasicStroke(1));

            // Label
            g.setColor(new Color(160, 120, 30));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, cellSize / 4)));
            String label = "STORAGE";
            int labelWidth = g.getFontMetrics().stringWidth(label);
            g.drawString(label, px + (w - labelWidth) / 2, py + h / 2 + 4);
        }

        // --- Pass 3: recharge zones (enhanced charging station styling) ------------------
        for (int[] z : rechargeZones) {
            int px = z[1] * cellSize, py = z[0] * cellSize;
            int w  = (z[3] - z[1] + 1) * cellSize;
            int h  = (z[2] - z[0] + 1) * cellSize;

            // Animated charging gradient (cyan to electric blue)
            GradientPaint chargeGradient = new GradientPaint(
                px, py, new Color(100, 220, 255, 160),
                px + w, py + h, new Color(60, 180, 240, 160)
            );
            g.setPaint(chargeGradient);
            g.fillRoundRect(px + 2, py + 2, w - 4, h - 4, 10, 10);

            // Electric border effect
            g.setColor(new Color(20, 140, 200, 220));
            g.setStroke(new BasicStroke(3f));
            g.drawRoundRect(px + 2, py + 2, w - 4, h - 4, 10, 10);

            // Inner glow effect
            g.setColor(new Color(180, 240, 255, 80));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(px + 4, py + 4, w - 8, h - 8, 8, 8);
            g.setStroke(new BasicStroke(1));

            // Charging icon and label
            g.setColor(new Color(10, 80, 120));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(11, cellSize / 3)));
            String label = "⚡ CHARGE";
            int labelWidth = g.getFontMetrics().stringWidth(label);
            g.drawString(label, px + (w - labelWidth) / 2, py + h / 2 + 4);
        }

        // --- Pass 4: hatched red rectangular zones ---------------------------
        for (int[] z : ovalZones) {
            drawHatchedRect(g, z[0], z[1], z[2], z[3]);
        }

        // --- Pass 5: enhanced exit markers --------------------------------------------
        for (int[] exit : exitCells) {
            if (exit == null || exit.length < 2) continue;
            int row = exit[0];
            int col = exit[1];
            if (row < 0 || row >= rows || col < 0 || col >= cols) continue;

            int px = col * cellSize;
            int py = row * cellSize;

            // Gradient exit zone
            GradientPaint exitGradient = new GradientPaint(
                px, py, new Color(150, 240, 230),
                px + cellSize, py + cellSize, new Color(100, 200, 200)
            );
            g.setPaint(exitGradient);
            g.fillRoundRect(px + 2, py + 2, cellSize - 4, cellSize - 4, 8, 8);

            // Border
            g.setColor(new Color(40, 140, 140));
            g.setStroke(new BasicStroke(2.5f));
            g.drawRoundRect(px + 2, py + 2, cellSize - 4, cellSize - 4, 8, 8);
            g.setStroke(new BasicStroke(1));

            // Exit arrow indicator
            g.setColor(new Color(20, 100, 100));
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(14, cellSize / 2)));
            g.drawString("→", px + cellSize / 4, py + 2 * cellSize / 3);
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

        // Left edge (col 0): open for zone-1 exit (row 2) and zone-2 exit (row 16)
        // Draw three segments: top (0 to row 2), middle (row 3 to row 15), bottom (row 17 to end)
        g.drawLine(0, 0,              0, 2  * cellSize);  // Top segment
        g.drawLine(0, 3 * cellSize,   0, 16 * cellSize);  // Middle segment
        g.drawLine(0, 17 * cellSize,  0, H);              // Bottom segment

        // Right edge (col max): open only for package entries (rows 4-12)
        // Rows 3 and 14 (robot spawns) are closed — robots spawn directly onto the grid
        g.drawLine(W, 0,              W, 3  * cellSize);  // Top segment
        g.drawLine(W, 12 * cellSize,  W, H);              // Bottom segment

        g.setStroke(new BasicStroke(1));
    }

    /**
     * Creates a brighter version of the given color
     */
    private Color brighten(Color color, float factor) {
        int r = Math.min(255, (int) (color.getRed() * (1 + factor)));
        int g = Math.min(255, (int) (color.getGreen() * (1 + factor)));
        int b = Math.min(255, (int) (color.getBlue() * (1 + factor)));
        return new Color(r, g, b);
    }

    /** Draws an enhanced delivery target zone with modern styling */
    private void drawHatchedRect(Graphics2D g, int minR, int minC, int maxR, int maxC) {
        int px = minC * cellSize;
        int py = minR * cellSize;
        int w  = (maxC - minC + 1) * cellSize;
        int h  = (maxR - minR + 1) * cellSize;

        // Rounded rectangle for modern look
        RoundRectangle2D rect = new RoundRectangle2D.Float(px + 3, py + 3, Math.max(1, w - 6), Math.max(1, h - 6), 15, 15);
        Shape oldClip = g.getClip();

        // Gradient background (light pink to deeper pink)
        GradientPaint deliveryGradient = new GradientPaint(
            px, py, new Color(255, 230, 230),
            px + w, py + h, new Color(255, 210, 210)
        );
        g.setPaint(deliveryGradient);
        g.fill(rect);

        // Diagonal red hatching with better spacing
        g.setClip(rect);
        g.setColor(new Color(220, 80, 80, 120));
        g.setStroke(new BasicStroke(2f));
        int diag = w + h;
        for (int d = -diag; d < diag; d += 12) {
            g.drawLine(px + d, py + h, px + d + h, py);
        }

        g.setClip(oldClip);

        // Modern border with shadow effect
        g.setColor(new Color(200, 60, 60));
        g.setStroke(new BasicStroke(3f));
        g.draw(rect);

        // Inner highlight border
        g.setColor(new Color(255, 150, 150, 100));
        g.setStroke(new BasicStroke(1.5f));
        RoundRectangle2D innerRect = new RoundRectangle2D.Float(px + 5, py + 5, Math.max(1, w - 10), Math.max(1, h - 10), 12, 12);
        g.draw(innerRect);

        g.setStroke(new BasicStroke(1));

        // Delivery zone label
        g.setColor(new Color(150, 30, 30));
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, cellSize / 3)));
        String label = "DELIVERY";
        int labelWidth = g.getFontMetrics().stringWidth(label);
        g.drawString(label, px + (w - labelWidth) / 2, py + h / 2 + 4);
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
        // Check if robot is carrying a package (DROPOFF phase means it has picked up the package)
        DeliveryMission.Phase phase = robot.getMissionPhase();
        boolean hasPackage = (phase == DeliveryMission.Phase.DROPOFF);

        // Use robot_package.png if carrying, robot.png otherwise
        BufferedImage imageToUse = hasPackage ? robotWithPackageImage : robotImage;

        if (imageToUse != null) {
            // Draw appropriate robot image scaled to cell size
            g.drawImage(imageToUse, px, py, cellSize, cellSize, null);
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
