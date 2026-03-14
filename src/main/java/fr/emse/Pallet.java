package fr.emse;

import fr.emse.fayol.maqit.simulator.components.ColorSituatedComponent;

/**
 * Represents a pallet that needs to be delivered in the warehouse simulation.
 * Pallets have an entry time, destination zone, and are carried by robots.
 */
public class Pallet extends ColorSituatedComponent {

    private int entryTime;           // Time step when pallet was created
    private int destinationZoneId;   // ID of the exit zone this pallet should go to
    private int associatedRobotId;   // ID of robot carrying this pallet (-1 if not assigned)

    /**
     * Creates a new pallet at the specified position with the given color.
     *
     * @param pos Position [x, y] on the grid
     * @param rgb Color [red, green, blue] values (0-255)
     * @param entryTime Simulation step when pallet was created
     * @param destinationZoneId Target exit zone ID
     */
    public Pallet(int[] pos, int[] rgb, int entryTime, int destinationZoneId) {
        super(pos, rgb);
        this.entryTime = entryTime;
        this.destinationZoneId = destinationZoneId;
        this.associatedRobotId = -1;  // Not assigned initially
    }

    /**
     * Gets the time step when this pallet entered the system.
     * @return Entry time in simulation steps
     */
    public int getEntryTime() {
        return entryTime;
    }

    /**
     * Gets the destination zone ID for this pallet.
     * @return Destination zone ID
     */
    public int getDestinationZoneId() {
        return destinationZoneId;
    }

    /**
     * Gets the ID of the robot carrying this pallet.
     * @return Robot ID, or -1 if not assigned
     */
    public int getAssociatedRobotId() {
        return associatedRobotId;
    }

    /**
     * Assigns a robot to carry this pallet.
     * @param robotId ID of the robot
     */
    public void setAssociatedRobotId(int robotId) {
        this.associatedRobotId = robotId;
    }

    @Override
    public fr.emse.fayol.maqit.simulator.components.ComponentType getComponentType() {
        return fr.emse.fayol.maqit.simulator.components.ComponentType.object;
    }

    @Override
    public String toString() {
        return "Pallet[entryTime=" + entryTime +
               ", destination=" + destinationZoneId +
               ", robot=" + associatedRobotId +
               ", pos=(" + x + "," + y + ")]";
    }
}
