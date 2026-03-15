package fr.emse;

import java.awt.Color;

/**
 * Represents a package in the warehouse.
 *
 * Each package has:
 *   - An entry cell (where it physically arrives on the right edge, col 19)
 *   - A delivery zone (1 = bottom-left oval, 2 = top-left oval)
 *   - A colour that identifies its zone (green = zone 1, orange = zone 2)
 *   - An arrival step (when it appears in the simulation)
 */
public class Pallet {

    /** Green packages → zone 1 (bottom-left oval), robot entry row 2. */
    public static final Color COLOR_ZONE1 = new Color(60, 180, 80);

    /** Orange packages → zone 2 (top-left oval), robot entry row 12. */
    public static final Color COLOR_ZONE2 = new Color(220, 120, 40);

    public final int id;
    public final int[] entryCell;   // [row, col] where this package physically arrives
    public final int deliveryZone;  // 1 = bottom-left oval, 2 = top-left oval
    public final Color packageColor;
    public final int arrivalStep;

    public Pallet(int id, int[] entryCell, int deliveryZone, int arrivalStep) {
        this.id           = id;
        this.entryCell    = entryCell;
        this.deliveryZone = deliveryZone;
        this.packageColor = (deliveryZone == 1) ? COLOR_ZONE1 : COLOR_ZONE2;
        this.arrivalStep  = arrivalStep;
    }

    @Override
    public String toString() {
        return String.format("Pallet#%d(zone=%d, entry=[%d,%d], arrival=%d)",
                id, deliveryZone, entryCell[0], entryCell[1], arrivalStep);
    }
}
