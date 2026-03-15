# Multi-Agent Warehouse Delivery Simulation System

A sophisticated autonomous warehouse logistics simulation implementing multi-agent pathfinding, collision avoidance, and task scheduling. This system demonstrates autonomous delivery robots navigating a warehouse grid while avoiding dynamic obstacles (human workers) and static barriers.

## Table of Contents

- [Overview](#overview)
- [System Architecture](#system-architecture)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Running the Simulation](#running-the-simulation)
- [Configuration](#configuration)
- [How It Works](#how-it-works)
- [Project Structure](#project-structure)
- [Key Components](#key-components)
- [Customization Guide](#customization-guide)
- [Troubleshooting](#troubleshooting)
- [License](#license)

---

## Overview

This project simulates an automated warehouse where delivery robots autonomously:

1. **Spawn on-demand** at designated zones when packages arrive
2. **Navigate** to package pickup locations using BFS pathfinding
3. **Avoid collisions** with warehouse workers and static obstacles
4. **Route through waypoints** for optimized traffic flow
5. **Deliver packages** to target zones
6. **Exit** the warehouse after completing delivery missions

The simulation includes:
- **Delivery Robots**: Autonomous agents executing multi-phase delivery missions
- **Warehouse Workers**: Human agents acting as dynamic obstacles with random patrol behavior
- **Package Scheduling**: Time-based package arrivals distributed across simulation timeline
- **Performance Metrics**: Real-time tracking of delivery times and throughput
- **Visual GUI**: Live display showing warehouse state, robot positions, and delivery progress

---

## System Architecture

### Component-Based Design

The system uses a **component-based architecture** where each robot consists of specialized subsystems:

```
DeliveryBot
├── PathPlanner         → BFS-based route computation
├── NavigationController → Movement execution and orientation control
├── CollisionManager    → Obstacle detection and escape maneuvers
└── DeliveryMission     → Multi-phase mission state management
```

### Multi-Agent Coordination

- **Reactive collision avoidance**: Robots detect and navigate around dynamic obstacles
- **Zone-based spawning**: Robots spawn only when their zone's spawn point is available
- **Escape maneuvers**: When blocked for multiple steps, robots attempt perpendicular movement
- **BFS pathfinding**: Optimal routes computed while respecting zone separators and obstacles

---

## Prerequisites

### Required

- **Java Development Kit (JDK)**: Version 8 or higher
  - Check your version: `java -version`
  - Download: [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)

### Included

- **Gradle**: Build automation (wrapper included, no separate installation needed)
- **MAQiT Simulator Library**: Pre-packaged in `lib/maqit-simulator-1.0.jar`
- **ini4j**: Configuration file parsing (auto-downloaded by Gradle)

---

## Installation

### Step 1: Clone/Download the Project

If you received this as a ZIP file, extract it to your desired location:

```bash
unzip basic_simulator_s2.zip
cd basic_simulator_s2
```

Or if using Git:

```bash
git clone <repository-url>
cd basic_simulator_s2
```

### Step 2: Verify Project Structure

Ensure the following structure exists:

```
basic_simulator_s2/
├── build.gradle              # Gradle build configuration
├── settings.gradle            # Project settings
├── configuration.ini          # Simulation parameters
├── gradlew                    # Gradle wrapper (Unix/Mac)
├── gradlew.bat               # Gradle wrapper (Windows)
├── lib/
│   └── maqit-simulator-1.0.jar  # Required simulator library
└── src/
    └── main/
        └── java/
            └── fr/
                └── emse/
                    ├── AutonomousLogisticsEngine.java
                    ├── DeliveryBot.java
                    ├── WarehouseWorker.java
                    └── ... (other source files)
```

### Step 3: Verify Java Installation

```bash
java -version
```

Expected output (version 8 or higher):
```
java version "1.8.0_XXX"
```

---

## Running the Simulation

### Quick Start

**On macOS/Linux:**
```bash
./gradlew run
```

**On Windows:**
```cmd
gradlew.bat run
```

### Build First (Optional)

If you want to compile without running:

```bash
./gradlew build
```

### Expected Output

When you run the simulation, you'll see:

1. **Console output** showing:
   - Configuration parameters
   - Package arrivals
   - Robot spawning events
   - Pickup/delivery confirmations
   - Performance metrics

2. **GUI window** displaying:
   - 15×20 warehouse grid
   - Delivery robots (blue agents)
   - Warehouse workers (tan/brown agents moving randomly)
   - Static obstacles (white cells)
   - Packages (green for zone 1, orange for zone 2)
   - Color-coded zones (delivery areas, waypoints, spawn points)
   - Real-time statistics panel

### Sample Console Output

```
=== Configuration ===
Grid dimensions: 15 × 20
Packages to process: 9
Grid line width: 4.0px

=== Autonomous Logistics Simulation ===
Processing 9 packages

Step 0 | Delivered: 0/9 | Active: 0 | Pending: 0
  [ARRIVAL] Package#0(zone=1, entry=[5,19], arrival=0)
  [SPAWN] Robot#0 for Package#0(zone=1, entry=[5,19], arrival=0)

Step 5 | Delivered: 0/9 | Active: 1 | Pending: 0
  [PICKUP] Package#0(zone=1, entry=[5,19], arrival=0) by Robot#0

Step 42 | Delivered: 0/9 | Active: 1 | Pending: 0
  [DELIVERY] Package#0(zone=1, entry=[5,19], arrival=0) by Robot#0 in 42 steps
  [EXIT] Robot#0

...

=== Delivery Performance Summary ===
Completed: 9/9
Total time: 378 steps
Average time: 42.0 steps/package
```

---

## Configuration

All simulation parameters are controlled via `configuration.ini`:

### Main Configuration Section

```ini
[configuration]
display = 1        # Enable GUI (1) or headless mode (0)
simulation = 1     # Enable simulation
mqtt = 0          # MQTT communication (not implemented)
robot = 0         # Legacy parameter
obstacle = 0      # Legacy parameter
seed = 150        # Random seed for reproducibility
field = 3         # Robot field of view radius
debug = 0         # Debug level (0-3)
waittime = 300    # Milliseconds between steps (visualization speed)
step = 500        # Maximum simulation steps
```

### Environment Settings

```ini
[environment]
rows = 15         # Grid height
columns = 20      # Grid width
```

### Warehouse Parameters

```ini
[warehouse]
total_pallets = 9      # Number of packages to process
line_stroke = 4        # Grid line thickness (pixels)
padding = 2           # Cell padding (pixels)
show_grid = 1         # Show grid lines (1) or hide (0)
```

### Zone Definitions

The warehouse is divided into functional zones:

```ini
[zones]
# Package entry gates (right edge, column 19)
package_gate1 = 3,19
package_gate2 = 4,19
...

# Robot spawn points
robot_spawn_zone1 = 2,19   # Top zone robots
robot_spawn_zone2 = 12,19  # Bottom zone robots

# Waypoints (intermediate checkpoints)
waypoint_zone1 = 4,11
waypoint_zone2 = 10,11

# Delivery targets
target_zone1 = 2,2    # Top-left corner
target_zone2 = 13,2   # Bottom-left corner

# Exit points (left edge)
exit_zone1 = 1,0
exit_zone2 = 13,0
```

### Obstacles

```ini
[obstacles]
obstacle1 = 1,8
obstacle2 = 3,12
obstacle3 = 4,5
...
```

---

## How It Works

### Simulation Lifecycle

#### 1. Initialization Phase (AutonomousLogisticsEngine.java:435-462)

```java
main() {
  1. Load configuration from configuration.ini
  2. Create grid environment (15×20)
  3. Place static obstacles from config
  4. Deploy warehouse workers at fixed positions
  5. Initialize zone coordinates
  6. Pre-generate package arrival schedule
  7. Setup GUI window
  8. Start main simulation loop
}
```

#### 2. Simulation Loop (AutonomousLogisticsEngine.java:160-193)

Each step executes the following phases:

**Phase 1: Package Arrivals**
- Check if any packages are scheduled for current step
- Add them to pending queue
- Display packages on grid at entry gates
- Color: Green (zone 1) or Orange (zone 2)

**Phase 2: Robot Spawning**
- For each pending package:
  - Determine target zone (1 or 2)
  - Check if spawn point is available
  - Create new DeliveryBot if spawn is clear
  - Place robot on grid
  - Mark package as assigned

**Phase 3: Warehouse Worker Movement**
- For each WarehouseWorker:
  - Choose random direction (up/down/left/right)
  - If blocked, try alternative directions
  - Move to first available adjacent cell
  - Update grid position

**Phase 4: Robot Delivery Execution**
- For each active DeliveryBot:
  - Execute one movement step
  - Update mission phase if destination reached
  - Track phase transitions (pickup, checkpoint, dropoff, exit)
  - Remove robot when mission complete

**Phase 5: GUI Update**
- Refresh visual display
- Update statistics panel
- Show package overlays
- Display active robot count and delivery progress

### Robot Behavior (DeliveryBot.java)

Each robot executes a **4-phase delivery mission**:

#### Phase 1: PICKUP
- **Destination**: Package arrival position
- **Path**: Robot spawn → Package location
- **Action on arrival**: Pick up package, advance to CHECKPOINT phase

#### Phase 2: CHECKPOINT
- **Destination**: Intermediate waypoint
- **Purpose**: Route through traffic management waypoint
- **Action on arrival**: Advance to DROPOFF phase

#### Phase 3: DROPOFF
- **Destination**: Delivery target zone
- **Path**: Waypoint → Target zone (2,2) or (13,2)
- **Action on arrival**: Deliver package, record metrics, advance to EXIT

#### Phase 4: EXIT
- **Destination**: Warehouse exit point
- **Path**: Delivery zone → Exit lane (column 0)
- **Action on arrival**: Remove robot from grid, mark mission COMPLETED

### Pathfinding Algorithm (PathPlanner.java)

**Breadth-First Search (BFS)** with constraints:

```
Input: Start position [r1, c1], Target position [r2, c2]

1. Initialize:
   - visited[][] = false
   - parent[][] = null
   - queue = [start]

2. While queue not empty:
   - current = queue.dequeue()

   - If current == target:
       return reconstructPath(parent, start, target)

   - For each neighbor (up, down, left, right):
       - Skip if out of bounds
       - Skip if already visited
       - Skip if crosses zone separator (rows 2,5,8,11 in col≥18)
       - Skip if cell contains obstacle

       - Mark as visited
       - Set parent[neighbor] = current
       - queue.enqueue(neighbor)

3. Return empty path (no route found)
```

**Zone Separator Rules:**
- Vertical barriers exist at rows {2, 5, 8, 11} in columns ≥18
- Prevent robots from crossing between package gate sections
- Example: Cannot move from (2,19) to (3,19)

### Collision Avoidance (CollisionManager.java)

**Reactive Strategy:**

```
On each movement step:

1. Get next waypoint from current route

2. Check if waypoint cell is occupied:
   - If occupied by dynamic agent (robot/worker):
       → Increment blocked counter
       → If blocked ≥ 3 consecutive steps:
           → Execute escape maneuver
       → Else: Wait

   - If cell is free:
       → Reset blocked counter
       → Execute movement

Escape Maneuver Algorithm:
1. Calculate direction to target (horizontal vs vertical bias)
2. If target is mostly horizontal → try vertical moves first
3. If target is mostly vertical → try horizontal moves first
4. Return first free perpendicular cell found
5. If all blocked → return null (replan route)
```

### Warehouse Worker Behavior (WarehouseWorker.java)

**Random Walk Pattern:**

```
Each step:
1. Choose random direction (0-3: up, down, left, right)
2. For attempt in 0 to 3:
   - Try direction = (random_start + attempt) % 4
   - If cell is:
       ✓ Within bounds
       ✓ Not occupied
       → Move there and return
3. If all 4 directions blocked → stay in place
```

Workers create dynamic obstacles that robots must navigate around.

---

## Project Structure

### Source Files Overview

| File | Purpose | Key Responsibilities |
|------|---------|---------------------|
| **AutonomousLogisticsEngine.java** | Main orchestrator | Simulation loop, package/robot lifecycle, metrics |
| **DeliveryBot.java** | Autonomous delivery robot | Component coordination, mission execution |
| **WarehouseWorker.java** | Human agent | Random patrol movement, dynamic obstacles |
| **PathPlanner.java** | Route computation | BFS pathfinding, zone separator enforcement |
| **NavigationController.java** | Movement execution | Orientation control, waypoint following |
| **CollisionManager.java** | Obstacle avoidance | Blockage detection, escape maneuvers |
| **DeliveryMission.java** | Mission state | Phase transitions, destination tracking |
| **PackageScheduler.java** | Package generation | Arrival timing, pending queue management |
| **PackageItem.java** | Package data | ID, zone, color, arrival time |
| **RobotFactory.java** | Robot lifecycle | Spawning, ID assignment, fleet tracking |
| **DeliveryMetrics.java** | Performance tracking | Delivery times, completion count, statistics |
| **ZoneCoordinates.java** | Configuration loader | Zone positions, spawn points, waypoints |
| **ObstacleLoader.java** | Static obstacle loader | Load obstacle positions from config |
| **SimulatorGUI.java** | Visual display | Grid rendering, statistics panel, package overlay |

### Class Relationships

```
AutonomousLogisticsEngine (main orchestrator)
│
├─→ PackageScheduler (manages package arrivals)
│   └─→ PackageItem[] (package data)
│
├─→ RobotFactory (creates and tracks robots)
│   └─→ DeliveryBot[] (active fleet)
│       ├─→ PathPlanner (BFS routing)
│       ├─→ NavigationController (movement)
│       ├─→ CollisionManager (avoidance)
│       └─→ DeliveryMission (phase tracking)
│           └─→ PackageItem (assigned package)
│
├─→ WarehouseWorker[] (dynamic obstacles)
│
├─→ DeliveryMetrics (performance tracking)
│
└─→ SimulatorGUI (visualization)
```

---

## Key Components

### 1. DeliveryBot Architecture

**Component-Based Design:**

```java
public class DeliveryBot {
    private final PathPlanner routePlanner;
    private final NavigationController navigator;
    private final CollisionManager collisionDetector;
    private final DeliveryMission mission;

    private void performSingleStep() {
        if (mission.isComplete()) return;

        if (mission.hasReachedDestination(getLocation())) {
            mission.advanceToNextPhase();
            replanRoute();
            return;
        }

        if (collisionDetector.isCellBlocked(nextWaypoint)) {
            handleBlockedPath();  // Wait or escape
        } else {
            navigator.executeMovementStep();  // Move forward
        }
    }
}
```

**Benefits:**
- Separation of concerns (pathfinding, navigation, collision)
- Easy testing of individual components
- Reusable subsystems for different agent types

### 2. BFS Pathfinding

**Why BFS?**
- Guarantees shortest path in unweighted grid
- Explores level-by-level (optimal for grid navigation)
- Time complexity: O(rows × columns)

**Path Reconstruction:**
```
After BFS finds target:
1. Start at target cell
2. Follow parent pointers backward to start
3. Reverse the path to get start → target
4. Return waypoint list
```

### 3. Collision Detection

**Two-Level Strategy:**

1. **Static Obstacles**: Handled during pathfinding (BFS avoids them)
2. **Dynamic Obstacles**: Handled during movement (reactive avoidance)

**Why separate?**
- Static obstacles never move → compute path once
- Dynamic obstacles change → check before each step

### 4. Package Scheduling

**Time-Based Arrivals:**

```java
// Packages arrive in first half of simulation
int distributionWindow = min(totalSteps / 2, 200);

for each package:
    arrivalTime = random(0, distributionWindow)

// Sort by arrival time
packages.sort(by arrivalTime)
```

This prevents overwhelming the system with simultaneous arrivals.

---

## Customization Guide

### Modify Number of Packages

Edit `configuration.ini`:
```ini
[warehouse]
total_pallets = 15  # Change from 9 to 15
```

### Change Simulation Speed

Adjust wait time between steps (milliseconds):
```ini
[configuration]
waittime = 100  # Faster (default: 300)
waittime = 1000 # Slower for detailed observation
```

### Add More Obstacles

Edit `configuration.ini`:
```ini
[obstacles]
obstacle11 = 5,10
obstacle12 = 8,15
# Add as many as needed
```

Or modify the source at AutonomousLogisticsEngine.java:102-116

### Modify Warehouse Workers

**Change worker count/positions** at AutonomousLogisticsEngine.java:120-139:

```java
int[][] workerLocations = {
    {4, 8}, {7, 4}, {8, 11}, {8, 17}, {11, 8}, {13, 13}
    // Add more positions here
};
```

### Adjust Robot Field of View

```ini
[configuration]
field = 5  # Increase from 3 (larger perception range)
```

### Change Grid Size

```ini
[environment]
rows = 20     # Increase from 15
columns = 30  # Increase from 20
```

**Important:** You must also adjust zone coordinates in `[zones]` section to match new grid dimensions.

### Modify Escape Threshold

Change how many blocked attempts trigger escape maneuver:

Edit CollisionManager.java:23:
```java
private static final int ESCAPE_THRESHOLD = 5;  // Default: 3
```

### Run Headless (No GUI)

For automated testing or faster simulation:
```ini
[configuration]
display = 0
```

### Change Random Seed

For different package/worker behavior patterns:
```ini
[configuration]
seed = 42  # Any integer value
```

Same seed = reproducible results (useful for testing)

---

## Troubleshooting

### Issue: "java: command not found"

**Solution:**
1. Install JDK 8 or higher
2. Verify installation: `java -version`
3. Add Java to PATH (if needed)

### Issue: "Permission denied: ./gradlew"

**Solution (macOS/Linux):**
```bash
chmod +x gradlew
./gradlew run
```

### Issue: "Could not find maqit-simulator-1.0.jar"

**Solution:**
Verify `lib/maqit-simulator-1.0.jar` exists in the project root. If missing, the library file is required (should be included with the project).

### Issue: GUI window doesn't appear

**Check:**
1. `display = 1` in configuration.ini
2. Running in graphical environment (not SSH without X forwarding)
3. Java can create GUI windows

**Test:**
```bash
java -version  # Should show desktop Java, not headless version
```

### Issue: Robots get stuck and don't move

**Possible causes:**
1. All paths blocked by obstacles → reduce obstacles or verify they don't create impassable barriers
2. Zone separators misconfigured → check `[zones]` section
3. Too many workers blocking paths → reduce worker count

**Debug:**
Enable debug output by editing AutonomousLogisticsEngine.java:79:
```java
this.robotManager = new RobotFactory(
    null, zoneLayout, properties.field, 2,  // Change 0 to 2 for debug
    properties.rows, properties.columns, properties.colorrobot
);
```

### Issue: Simulation runs too fast/slow

**Adjust visualization speed:**
```ini
[configuration]
waittime = 500  # Increase for slower, decrease for faster
```

### Issue: OutOfMemoryError

**Solution:**
Increase Java heap size:
```bash
# Unix/Mac
JAVA_OPTS="-Xmx2g" ./gradlew run

# Windows
set JAVA_OPTS=-Xmx2g
gradlew.bat run
```

---

## Understanding the Output

### Console Symbols

- `[ARRIVAL]` - Package appeared at entry gate
- `[SPAWN]` - Robot created for package
- `[PICKUP]` - Robot collected package
- `[DELIVERY]` - Package delivered to target zone
- `[EXIT]` - Robot removed from warehouse

### Step Statistics

```
Step 42 | Delivered: 3/9 | Active: 2 | Pending: 1
```

- **Step**: Current simulation step number
- **Delivered**: Completed deliveries / Total packages
- **Active**: Robots currently on missions
- **Pending**: Packages waiting for robot assignment

### GUI Color Coding

- **Blue agents**: Delivery robots
- **Tan/brown agents**: Warehouse workers
- **White cells**: Static obstacles
- **Green overlays**: Zone 1 packages (deliver to bottom-left)
- **Orange overlays**: Zone 2 packages (deliver to top-left)
- **Light blue cells** (column 18-19): Robot spawn zones
- **Light green cells** (column 18-19): Package entry gates

---

## Advanced Topics

### Multi-Agent Coordination

This system demonstrates:

1. **Decentralized control**: Each robot makes independent decisions
2. **Reactive coordination**: Robots avoid each other through collision detection
3. **Resource allocation**: Spawn points act as shared resources
4. **Task assignment**: First-come-first-served package allocation

### Pathfinding Optimizations

Potential improvements (not implemented):
- **A* algorithm**: Faster pathfinding with heuristics
- **Dynamic replanning**: Recompute paths when workers block routes
- **Path smoothing**: Reduce unnecessary turns
- **Corridor detection**: Recognize and optimize for narrow passages

### Scalability Considerations

Current limitations:
- BFS runs on every replan (could cache partial paths)
- No inter-robot communication (could share obstacle information)
- Sequential robot updates (could parallelize for large fleets)

For large-scale simulations (100+ robots):
- Consider space-time A* for collision-free path planning
- Implement priority-based traffic management
- Use spatial hashing for faster neighbor queries

---

## License

This project is provided for educational purposes. Check `LICENSE` file for details.

---

## Additional Resources

### Understanding BFS Pathfinding
- [BFS Algorithm Visualization](https://www.cs.usfca.edu/~galles/visualization/BFS.html)
- Grid pathfinding tutorial: [Red Blob Games](https://www.redblobgames.com/pathfinding/a-star/introduction.html)

### Multi-Agent Systems
- Collision avoidance strategies
- Decentralized coordination algorithms
- Task allocation in robotics

### Java Swing GUI
- [Java Swing Tutorial](https://docs.oracle.com/javase/tutorial/uiswing/)

---

## Contact & Support

For questions or issues:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review console output for error messages
3. Verify `configuration.ini` settings match expected format

---

**Happy Simulating!**
