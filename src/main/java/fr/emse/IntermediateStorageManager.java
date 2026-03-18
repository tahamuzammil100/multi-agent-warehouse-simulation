package fr.emse;

import java.util.ArrayList;
import java.util.List;

/**
 * IntermediateStorageManager - Tracks intermediate-area storage capacity and slots.
 *
 * Each zone has a rectangular area with a logical storage capacity lp,
 * computed as a ratio of area size.
 */
public class IntermediateStorageManager {

    private final List<int[]> slotsZone1;
    private final List<int[]> slotsZone2;
    private final int capacityZone1;
    private final int capacityZone2;
    private final boolean[] occupiedZone1;
    private final boolean[] occupiedZone2;

    public IntermediateStorageManager(int[] areaZone1, int[] areaZone2, double ratio) {
        this.slotsZone1 = buildSlots(areaZone1);
        this.slotsZone2 = buildSlots(areaZone2);

        this.capacityZone1 = computeCapacity(slotsZone1.size(), ratio);
        this.capacityZone2 = computeCapacity(slotsZone2.size(), ratio);

        this.occupiedZone1 = new boolean[slotsZone1.size()];
        this.occupiedZone2 = new boolean[slotsZone2.size()];

        System.out.printf("Intermediate storage: Z1 area=%d lp=%d | Z2 area=%d lp=%d%n",
                slotsZone1.size(), capacityZone1,
                slotsZone2.size(), capacityZone2);
    }

    private List<int[]> buildSlots(int[] area) {
        if (area == null || area.length != 4) {
            throw new IllegalArgumentException("Invalid intermediate area definition");
        }

        int minRow = Math.min(area[0], area[2]);
        int minCol = Math.min(area[1], area[3]);
        int maxRow = Math.max(area[0], area[2]);
        int maxCol = Math.max(area[1], area[3]);

        List<int[]> slots = new ArrayList<>();
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                slots.add(new int[]{r, c});
            }
        }

        if (slots.isEmpty()) {
            throw new IllegalArgumentException("Intermediate area has no cells");
        }

        return slots;
    }

    private int computeCapacity(int areaSize, double ratio) {
        double boundedRatio = Math.max(0.0, Math.min(1.0, ratio));
        return Math.max(1, (int) Math.round(areaSize * boundedRatio));
    }

    public synchronized int[] reserveSlot(int zone) {
        List<int[]> slots = (zone == 1) ? slotsZone1 : slotsZone2;
        boolean[] occupied = (zone == 1) ? occupiedZone1 : occupiedZone2;
        int capacity = (zone == 1) ? capacityZone1 : capacityZone2;

        if (getOccupiedCount(zone) >= capacity) {
            return null;
        }

        for (int i = 0; i < slots.size(); i++) {
            if (!occupied[i]) {
                occupied[i] = true;
                return slots.get(i).clone();
            }
        }

        return null;
    }

    public synchronized boolean releaseSlot(int zone, int[] slot) {
        if (slot == null) {
            return false;
        }

        List<int[]> slots = (zone == 1) ? slotsZone1 : slotsZone2;
        boolean[] occupied = (zone == 1) ? occupiedZone1 : occupiedZone2;

        for (int i = 0; i < slots.size(); i++) {
            int[] candidate = slots.get(i);
            if (candidate[0] == slot[0] && candidate[1] == slot[1]) {
                boolean wasOccupied = occupied[i];
                occupied[i] = false;
                return wasOccupied;
            }
        }

        return false;
    }

    public synchronized int getCapacity(int zone) {
        return (zone == 1) ? capacityZone1 : capacityZone2;
    }

    public synchronized int getOccupiedCount(int zone) {
        boolean[] occupied = (zone == 1) ? occupiedZone1 : occupiedZone2;
        int count = 0;
        for (boolean value : occupied) {
            if (value) {
                count++;
            }
        }
        return count;
    }
}
