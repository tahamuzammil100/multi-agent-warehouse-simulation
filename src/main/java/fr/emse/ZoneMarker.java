package fr.emse;

import fr.emse.fayol.maqit.simulator.components.ColorObstacle;

/**
 * Visual marker for exit zones Z1 and Z2.
 * Placed in the cell adjacent to the actual delivery target so it is
 * always visible without blocking AMR entry into the delivery cell.
 */
public class ZoneMarker extends ColorObstacle {

    private static final int[] RED = {200, 50, 50};

    public final String zoneName;

    public ZoneMarker(String zoneName, int[] pos) {
        super(pos, RED);
        this.zoneName = zoneName;
    }
}
