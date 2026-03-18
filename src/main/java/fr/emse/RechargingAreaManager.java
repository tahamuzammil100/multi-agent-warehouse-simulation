package fr.emse;

import java.util.ArrayList;
import java.util.List;

/**
 * RechargingAreaManager - Manages limited-capacity AMR charging slots.
 *
 * The charging area accepts at most two AMRs at the same time.
 */
public class RechargingAreaManager {

    private static final int MAX_SIMULTANEOUS_CHARGING = 2;

    private final List<int[]> chargingSlots;
    private final boolean[] occupied;

    public RechargingAreaManager(int[] chargingArea) {
        this.chargingSlots = buildSlots(chargingArea);
        this.occupied = new boolean[chargingSlots.size()];

        System.out.printf("Charging area: cells=%d, capacity=%d%n",
                chargingSlots.size(), getCapacity());
    }

    private List<int[]> buildSlots(int[] area) {
        if (area == null || area.length != 4) {
            throw new IllegalArgumentException("Invalid recharge area definition");
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
            throw new IllegalArgumentException("Recharge area has no cells");
        }

        return slots;
    }

    public synchronized int[] reserveSpot() {
        if (getOccupiedCount() >= getCapacity()) {
            return null;
        }

        for (int i = 0; i < chargingSlots.size(); i++) {
            if (!occupied[i]) {
                occupied[i] = true;
                return chargingSlots.get(i).clone();
            }
        }

        return null;
    }

    public synchronized boolean releaseSpot(int[] slot) {
        if (slot == null) {
            return false;
        }

        for (int i = 0; i < chargingSlots.size(); i++) {
            int[] candidate = chargingSlots.get(i);
            if (candidate[0] == slot[0] && candidate[1] == slot[1]) {
                boolean wasOccupied = occupied[i];
                occupied[i] = false;
                return wasOccupied;
            }
        }

        return false;
    }

    public synchronized int getCapacity() {
        return Math.min(MAX_SIMULTANEOUS_CHARGING, chargingSlots.size());
    }

    public synchronized int getOccupiedCount() {
        int count = 0;
        for (boolean value : occupied) {
            if (value) {
                count++;
            }
        }
        return Math.min(count, getCapacity());
    }
}
