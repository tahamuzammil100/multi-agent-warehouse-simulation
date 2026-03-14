package fr.emse;

import java.awt.Color;

import fr.emse.fayol.maqit.simulator.SimFactory;
import fr.emse.fayol.maqit.simulator.components.ColorInteractionRobot;
import fr.emse.fayol.maqit.simulator.components.Message;
import fr.emse.fayol.maqit.simulator.environment.ColorSimpleCell;

public class BasicRobot extends ColorInteractionRobot<ColorSimpleCell> {

    public BasicRobot(String name, int field, int debug, int[] pos, Color co, int rows, int columns) {
        super(name, field, pos, new int[]{co.getRed(), co.getGreen(), co.getBlue()});
    }

    protected boolean freeForward() {
        ColorSimpleCell cell = grid[field - 1][field];
        return cell != null && cell.getContent() == null;
    }

    public void move(int nb) {
        for (int i = 0; i < nb; i++) {
            if (freeForward()) {
                moveForward();
            } else {
                turnLeft();
            }
        }
        if (SimFactory.DEBUG == 1) {
            System.out.println("Robot " + name + " at (" + x + "," + y + ") orientation: " + orientation);
        }
    }

    public void handleMessage(Message msg) {
        if (SimFactory.DEBUG == 1) {
            System.out.println("Robot " + name + " received message from " + msg.getEmitter() + ": " + msg.getContent());
        }
    }
}
