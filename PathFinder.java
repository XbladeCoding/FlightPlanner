import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.Random;

public class PathFinder {
    private static final int SIZE = 64;

    // FIX: weather, wind and terrain used to be generated from three DIFFERENT random seeds,
    // so the wind did not belong to the terrain being checked. They now share one seed.
    private final long seed;
    private final Weather[][] weatherMap;
    private final Wind[][] windMap;
    private final double[][] terrainMap;
    private double[][] effectiveSpeedMap = new double[SIZE][SIZE];
    private boolean[][] safeMapCache;

    // Grid orientation used everywhere below (matches ArrayGen):
    //   row index grows "down" (+y), column index grows "right" (+x).
    //   Wind direction is in radians, Plane direction is in degrees, both measured as atan2(y, x)
    //   in that same frame, so Plane.travel() (x += cos, y += sin) moves +column / +row.
    private static final int[][] DIRECTIONS = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // Up, Down, Left, Right (row, col)

    public PathFinder() {
        this(new Random().nextLong());
    }

    public PathFinder(long seed) {
        this.seed = seed;
        this.weatherMap = ArrayGen.generate64x64Weather(seed);
        this.windMap = ArrayGen.generate64x64Wind(seed);
        this.terrainMap = ArrayGen.generate64x64Terrain(seed);
    }

    public long getSeed() {
        return seed;
    }

    public double[][] generateDangerMap() {
        double[][] dangerMap = new double[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                dangerMap[i][j] = weatherMap[i][j] == null ? 1.0 : weatherMap[i][j].getDanger();
            }
        }
        return dangerMap;
    }

    public double[][] generateEffectiveSpeedMap(Plane plane) {
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                double[] effectiveSpeedAndDirection = getEffectiveSpeed(plane, windMap[i][j]);
                effectiveSpeedMap[i][j] = effectiveSpeedAndDirection[0];
            }
        }
        return effectiveSpeedMap;
    }

    // Steps 1 + 2: a cell is safe if its terrain is below 2000 and its weather danger is <= 0.3.
    private boolean[][] buildSafeMap() {
        double[][] dangerMap = generateDangerMap();
        boolean[][] safe = new boolean[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                safe[i][j] = Double.isFinite(terrainMap[i][j]) && terrainMap[i][j] < 2000
                        && Double.isFinite(dangerMap[i][j]) && dangerMap[i][j] <= 0.3;
            }
        }
        return safe;
    }

    /** True if (row, col) is inside the grid and safe to fly through. */
    public boolean isSafeCell(int row, int col) {
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) {
            return false;
        }
        if (safeMapCache == null) {
            safeMapCache = buildSafeMap();
        }
        return safeMapCache[row][col];
    }

    /**
     * Ground speed achieved when the plane holds a track along (dRow, dCol) through a cell
     * with the given wind, flying at the given airspeed. The plane crabs into any crosswind,
     * so only the along-track wind component speeds it up or slows it down.
     * Returns 0 if the plane cannot make progress in that direction.
     * FIX: previously every cell used one speed computed for a plane always pointing at
     * direction 0, regardless of which way it was actually moving.
     */
    private double groundSpeedAlong(double airspeed, Wind wind, int dRow, int dCol) {
        if (airspeed <= 0) return 0.0;
        double len = Math.hypot(dRow, dCol);
        double ux = dCol / len, uy = dRow / len;                 // unit vector of the track
        double wx = 0, wy = 0;
        if (wind != null) {
            wx = wind.getSpeed() * Math.cos(wind.getDirection()); // direction already in radians
            wy = wind.getSpeed() * Math.sin(wind.getDirection());
        }
        double along = wx * ux + wy * uy;
        double cross = -wx * uy + wy * ux;
        if (Math.abs(cross) >= airspeed) return 0.0;              // crosswind stronger than the plane
        double gs = along + Math.sqrt(airspeed * airspeed - cross * cross);
        return gs > 0 ? gs : 0.0;
    }

    /** Dijkstra over safe cells, minimising flight time at the given airspeed. Returns [row, col] cells or null. */
    private LinkedList<int[]> fastestPath(boolean[][] safe, double airspeed,
                                          int startRow, int startCol, int endRow, int endCol) {
        int rows = SIZE, cols = SIZE;
        int s = startRow * cols + startCol;
        int g = endRow * cols + endCol;

        double[] time = new double[rows * cols];
        int[] prev = new int[rows * cols];
        Arrays.fill(time, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        time[s] = 0;

        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        pq.add(new double[]{0, s});

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            int curr = (int) top[1];
            if (Double.compare(top[0], time[curr]) != 0) continue;
            if (curr == g) break;

            int r = curr / cols;
            int c = curr % cols;

            for (int[] d : DIRECTIONS) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (!safe[nr][nc]) continue; // Ignore unsafe cells.

                double v = groundSpeedAlong(airspeed, windMap[nr][nc], d[0], d[1]);
                if (!Double.isFinite(v) || v <= 0) continue; // Cannot make headway this way.

                double dist = Math.hypot(d[0], d[1]);
                int next = nr * cols + nc;
                double t = time[curr] + dist / v;

                if (t < time[next]) {
                    time[next] = t;
                    prev[next] = curr;
                    pq.add(new double[]{t, next});
                }
            }
        }

        if (!Double.isFinite(time[g])) {
            return null;
        }

        LinkedList<int[]> path = new LinkedList<>();
        int at = g;
        while (at != -1) {
            path.addFirst(new int[]{at / cols, at % cols});
            if (at == s) break;
            at = prev[at];
        }
        if (path.isEmpty() || path.getFirst()[0] != startRow || path.getFirst()[1] != startCol) {
            return null;
        }
        return path;
    }

    private static double pathDistance(LinkedList<int[]> path) {
        double d = 0.0;
        for (int i = 1; i < path.size(); i++) {
            int[] a = path.get(i - 1), b = path.get(i);
            d += Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        return d;
    }

    /**
     * Optimal cruise speed, accounting for acceleration.
     * Fuel model (from Plane): accelerating to speed v costs v / efficiency, and flying a
     * distance D costs D / efficiency. The plane must finish the route with at least as much
     * fuel as it took to accelerate (a reserve equal to the acceleration cost):
     *     fuel - v/eff - D/eff >= v/eff   =>   v <= (fuel * eff - D) / 2
     * The optimal speed is the fastest speed allowed by that rule, capped at the max speed.
     * FIX: the old version returned 0 whenever the plane started at 0 speed (it always does),
     * never subtracted the fuel spent accelerating, and found max speed via reflection.
     * Returns 0 if no positive speed satisfies the rule.
     */
    private double computeOptimalTravelSpeed(Plane plane, double routeDistance) {
        if (plane == null || !Double.isFinite(routeDistance) || routeDistance < 0.0) {
            return 0.0;
        }
        double eff = plane.getEfficiency();
        double fuel = plane.getFuelCapacity();
        if (!(eff > 0) || !(fuel > 0)) {
            return 0.0;
        }
        double limitFromFuel = (fuel * eff - routeDistance) / 2.0;
        double v = Math.min(plane.getMaxSpeed(), limitFromFuel);
        return v > 0 ? v : 0.0;
    }

    private static double reserveFor(Plane plane, double speed) {
        return speed / plane.getEfficiency(); // same as the fuel Plane.accelerate(speed) burns
    }

    private void acceleratePlaneToOptimalSpeed(Plane plane, double optimalSpeed) {
        if (plane == null || !Double.isFinite(optimalSpeed) || optimalSpeed <= 0.0) {
            return;
        }
        if (plane.getCurrentSpeed() == optimalSpeed) {
            return;
        }
        // Plane.accelerate() sets the speed in one call, so a single call is enough.
        plane.accelerate(optimalSpeed);
    }

    // Arguments are grid indices: first index = row, second = column (same as the returned route).
    public double[][] plotRoute(Plane plane, int startRow, int startCol, int endRow, int endCol) {
        if (plane == null || startRow < 0 || startRow >= SIZE || startCol < 0 || startCol >= SIZE
                || endRow < 0 || endRow >= SIZE || endCol < 0 || endCol >= SIZE) {
            return null;
        }

        // Steps 1 + 2: safe cells (terrain and weather).
        boolean[][] safe = buildSafeMap();
        if (!safe[startRow][startCol] || !safe[endRow][endCol]) {
            System.out.println("No valid path: start or end cell is unsafe.");
            return null;
        }

        // Step 3: fastest safe path given the plane's airspeed and the wind.
        // FIX: the route used to be planned with the plane's speed BEFORE it accelerated (0), so only
        // the wind was moving it. Now the speed is chosen first and the route is planned at that speed.
        // The optimal speed depends on the route length and the route depends on the speed, so
        // alternate the two until the speed stops changing.
        double speed = plane.getMaxSpeed();
        LinkedList<int[]> path = null;
        for (int iter = 0; iter < 10; iter++) {
            path = fastestPath(safe, speed, startRow, startCol, endRow, endCol);
            if (path == null) {
                System.out.println("No path found.");
                return null;
            }
            double newSpeed = computeOptimalTravelSpeed(plane, pathDistance(path));
            if (newSpeed <= 0) {
                System.out.println("Path failed: not enough fuel to accelerate, fly the route and keep the reserve.");
                return null;
            }
            if (Math.abs(newSpeed - speed) < 1e-9) break;
            speed = newSpeed;
        }
        path = fastestPath(safe, speed, startRow, startCol, endRow, endCol);
        if (path == null) {
            System.out.println("No path found.");
            return null;
        }
        double routeDistance = pathDistance(path);
        double reserve = reserveFor(plane, speed);
        double predictedFuelLeft = plane.getFuelCapacity() - reserve - routeDistance / plane.getEfficiency();
        if (predictedFuelLeft < reserve - 1e-9) {
            System.out.println("Path failed: not enough fuel to keep the acceleration reserve.");
            return null;
        }

        double[][] route = new double[path.size()][2];
        for (int i = 0; i < path.size(); i++) {
            route[i][0] = path.get(i)[0];
            route[i][1] = path.get(i)[1];
        }

        System.out.println("Optimal cruise speed: " + speed
                + (speed >= plane.getMaxSpeed() ? " (max speed)" : " (limited by fuel reserve)"));
        acceleratePlaneToOptimalSpeed(plane, speed);
        System.out.println("Fuel after accelerating: " + plane.getFuelCapacity());

        // Fly the route: turn to face each move, then travel it.
        // FIX: the plane never changed direction, so its x/y position was wrong.
        plane.setX(startCol);
        plane.setY(startRow);
        for (int i = 1; i < path.size(); i++) {
            int[] previous = path.get(i - 1);
            int[] current = path.get(i);
            int dRow = current[0] - previous[0];
            int dCol = current[1] - previous[1];
            plane.changeDirection((Math.toDegrees(Math.atan2(dRow, dCol)) + 360) % 360);
            plane.travel(Math.hypot(dRow, dCol));
            if (plane.getFuelCapacity() <= 0) {
                System.out.println("Path failed: Aircraft ran out of fuel");
                return null;
            }
        }
        System.out.println("Fuel remaining: " + plane.getFuelCapacity()
                + " (reserve required = acceleration cost = " + reserve + ")");
        return route;
    }

    /**
     * Ground velocity of the plane (at its current heading and speed) plus the wind.
     * Returns {speed, direction in degrees}, direction measured as atan2(y, x) in the grid frame
     * described at the top of this class (same convention as Plane.getDirection()).
     * FIX: wind direction is already in radians and was being converted with Math.toRadians again.
     */
    public double[] getEffectiveSpeed(Plane plane, Wind wind) {
        if (plane == null || wind == null) {
            return new double[] {0.0, 0.0};
        }
        double windRad = wind.getDirection();
        double planeRad = Math.toRadians(plane.getDirection());
        double vx = plane.getCurrentSpeed() * Math.cos(planeRad) + wind.getSpeed() * Math.cos(windRad);
        double vy = plane.getCurrentSpeed() * Math.sin(planeRad) + wind.getSpeed() * Math.sin(windRad);
        double effectiveSpeed = Math.hypot(vx, vy);
        double effectiveDirection = (Math.toDegrees(Math.atan2(vy, vx)) + 360) % 360;
        return new double[] {effectiveSpeed, effectiveDirection};
    }
}
