package fr.emse;

import java.util.ArrayList;
import java.util.List;

import fr.emse.fayol.maqit.simulator.components.ColorSituatedComponent;

/**
 * Represents an exit zone (Zone Z) where pallets are delivered.
 * This is a colored component that marks the goal position for robots.
 */
public class ExitZone extends ColorSituatedComponent {

    private int zoneId;                    // Unique identifier for this exit zone
    private int[] position;                // Position [x, y] on the grid
    private List<Integer> deliveredPallets; // Track pallets delivered to this zone

    /**
     * Creates a new exit zone with the specified ID and color.
     *
     * @param zoneId Unique identifier for this exit zone
     * @param position Position [x, y] on the grid
     * @param rgb Color [red, green, blue] values (0-255)
     */
    public ExitZone(int zoneId, int[] position, int[] rgb) {
        super(position, rgb);
        this.zoneId = zoneId;
        this.position = position;
        this.deliveredPallets = new ArrayList<>();
    }

    /**
     * Creates a new exit zone with default green color.
     *
     * @param zoneId Unique identifier for this exit zone
     * @param position Position [x, y] on the grid
     */
    public ExitZone(int zoneId, int[] position) {
        this(zoneId, position, new int[]{0, 255, 0}); // Green color by default
    }

    /**
     * Gets the zone ID.
     * @return Zone ID
     */
    public int getZoneId() {
        return zoneId;
    }

    /**
     * Gets the position of this exit zone.
     * @return Position [x, y]
     */
    public int[] getPosition() {
        return position;
    }

    /**
     * Records a pallet delivery to this zone.
     * @param palletId ID of the delivered pallet
     */
    public void recordDelivery(int palletId) {
        deliveredPallets.add(palletId);
    }

    /**
     * Gets the number of pallets delivered to this zone.
     * @return Number of deliveries
     */
    public int getDeliveryCount() {
        return deliveredPallets.size();
    }

    /**
     * Gets the list of delivered pallet IDs.
     * @return List of pallet IDs
     */
    public List<Integer> getDeliveredPallets() {
        return new ArrayList<>(deliveredPallets);
    }

    @Override
    public fr.emse.fayol.maqit.simulator.components.ComponentType getComponentType() {
        return fr.emse.fayol.maqit.simulator.components.ComponentType.object;
    }

    @Override
    public String toString() {
        return "ExitZone[id=" + zoneId +
               ", pos=(" + position[0] + "," + position[1] + ")" +
               ", deliveries=" + deliveredPallets.size() + "]";
    }
}
