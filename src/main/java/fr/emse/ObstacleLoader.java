package fr.emse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.ini4j.Ini;

/**
 * ObstacleLoader - Utility for loading obstacle configurations from file.
 *
 * Handles:
 * - Reading obstacle positions from configuration file
 * - Parsing coordinate data
 * - Providing fallback defaults on errors
 * - Validation of coordinate format
 */
public class ObstacleLoader {

    private static final int DEFAULT_OBSTACLE_COUNT = 10;

    /**
     * Loads obstacle positions from the configuration file.
     *
     * Reads entries formatted as "row,column" from the [obstacles] section.
     * Falls back to default positions if file is missing or malformed.
     *
     * @param configPath Path to configuration file
     * @param maxCount Maximum number of obstacles to load
     * @return List of obstacle coordinates
     */
    public static List<int[]> loadFromConfig(String configPath, int maxCount) {
        List<int[]> positions = new ArrayList<>();

        try {
            Ini configFile = new Ini(new File(configPath));

            for (int i = 1; i <= maxCount; i++) {
                String key = "obstacle" + i;
                String value = configFile.get("obstacles", key);

                if (value != null && !value.trim().isEmpty()) {
                    int[] coords = parseCoordinates(value);
                    if (coords != null) {
                        positions.add(coords);
                    }
                }
            }

            System.out.println("Loaded " + positions.size() +
                             " obstacle positions from configuration");

        } catch (Exception e) {
            System.err.println("Failed to read obstacles from config: " + e.getMessage());
            System.err.println("Falling back to default obstacle layout");
            positions = getDefaultPositions();
        }

        return positions;
    }

    /**
     * Parses a coordinate string in "row,column" format.
     *
     * @param coordinateString String to parse
     * @return Coordinate array [row, col], or null if invalid
     */
    private static int[] parseCoordinates(String coordinateString) {
        try {
            String[] parts = coordinateString.trim().split(",");
            if (parts.length == 2) {
                int row = Integer.parseInt(parts[0].trim());
                int col = Integer.parseInt(parts[1].trim());
                return new int[]{row, col};
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid coordinate format: " + coordinateString);
        }
        return null;
    }

    /**
     * Provides default obstacle positions as fallback.
     *
     * @return List of 10 predefined obstacle coordinates
     */
    private static List<int[]> getDefaultPositions() {
        List<int[]> defaults = new ArrayList<>();
        defaults.add(new int[]{1, 8});
        defaults.add(new int[]{3, 12});
        defaults.add(new int[]{4, 5});
        defaults.add(new int[]{6, 16});
        defaults.add(new int[]{7, 3});
        defaults.add(new int[]{8, 9});
        defaults.add(new int[]{10, 6});
        defaults.add(new int[]{11, 14});
        defaults.add(new int[]{12, 2});
        defaults.add(new int[]{13, 11});
        return defaults;
    }

    /**
     * Loads obstacles with default count (10).
     *
     * @param configPath Path to configuration file
     * @return List of obstacle coordinates
     */
    public static List<int[]> loadFromConfig(String configPath) {
        return loadFromConfig(configPath, DEFAULT_OBSTACLE_COUNT);
    }
}
