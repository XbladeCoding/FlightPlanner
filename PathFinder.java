import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.Random;

public class PathFinder {
    private static final int GRID_SIZE = 64;

    private final Weather[][] weatherMap;
    private final Wind[][] windMap;
    private final double[][] terrainMap;
    private double[][] effectiveSpeedMap = new double[GRID_SIZE][GRID_SIZE];

    public PathFinder() {
        this(new Random().nextLong());
    }

    public PathFinder(long seed) {
        terrainMap = ArrayGen.generate64x64Terrain(seed);
        windMap = ArrayGen.generate64x64Wind(terrainMap);
        weatherMap = ArrayGen.generate64x64Weather(seed, terrainMap);
    }

    public double getTerrainAt(int x, int y) {
        checkCoordinates(x, y);
        return terrainMap[y][x];
    }

    public Wind getWindAt(int x, int y) {
        checkCoordinates(x, y);
        return windMap[y][x];
    }

    public Weather getWeatherAt(int x, int y) {
        checkCoordinates(x, y);
        return weatherMap[y][x];
    }

    private void checkCoordinates(int x, int y) {
        if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) {
            throw new IllegalArgumentException("Map coordinates must be between 0 and 63.");
        }
    }

    public double[][] generateDangerMap() {
        double[][] dangerMap = new double[GRID_SIZE][GRID_SIZE];
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                dangerMap[y][x] = weatherMap[y][x].getDanger();
            }
        }
        return dangerMap;
    }

    public double[][] generateEffectiveSpeedMap(Plane plane) {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                double[] effectiveSpeedAndDirection = getEffectiveSpeed(plane, windMap[y][x]);
                effectiveSpeedMap[y][x] = effectiveSpeedAndDirection[0];
            }
        }
        return effectiveSpeedMap;
    }

    public double[][] plotRoute(Plane plane, int startX, int startY, int endX, int endY, double[][] dangerMap) {
        double[][] safePath = new double[GRID_SIZE][GRID_SIZE];

        if (plane == null || startX < 0 || startX >= GRID_SIZE || startY < 0 || startY >= GRID_SIZE
                || endX < 0 || endX >= GRID_SIZE || endY < 0 || endY >= GRID_SIZE) {
            return null;
        }

        // Nested-loop algorithm to find the safest routes from start to end.
        // Safe routes include also terrain below 2000 ft.
        // A safe route is defined as a path with danger levels below 30% danger or 0.3 on the danger map.
        // Then accounts for the plane and wind speed to determine shortest time and eliminates longer routes.
        // Then the route is plotted on the map and returned.

        // Step 1: Find suitable terrain.

        if (!isValidMap(dangerMap)) {
            dangerMap = generateDangerMap();
        }
        effectiveSpeedMap = generateEffectiveSpeedMap(plane);

        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (Double.isFinite(terrainMap[y][x]) && terrainMap[y][x] < 2000
                        && Double.isFinite(dangerMap[y][x])) {
                    safePath[y][x] = dangerMap[y][x];
                } else {
                    safePath[y][x] = 1.0;
                }
            }
        }

        // Step 2: Find the safest path through weather.

        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (safePath[y][x] > 0.3) {
                    safePath[y][x] = 1.0;
                }
            }
        }

        // Step 3: Find the fastest path from the safe routes and wind speed and direction.
        // We find the path by following the highest effective speeds along the safePath found through Step 2. 
        // with Dijkstra's algorithm.
        // We basically want the highest effective speeds.
        // When we are following the path, the plane will be moving in the direction of the highest effetive speed, 
        // and the wind will be pushing the plane in the direction of the wind.


        // For ease of implementation, we will plot the path on a new array with effective speeds onlt on the safe path.

        // The effective speed is calculated using the wind speed and direction and the plane's speed and direction.
        // See Wind.java and Plane.java for the speed and direction calculations.

        double[][] effectiveSpeedOnSafePath = new double[GRID_SIZE][GRID_SIZE];
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (safePath[y][x] < 1.0) {
                    effectiveSpeedOnSafePath[y][x] = effectiveSpeedMap[y][x];
                } else {
                    effectiveSpeedOnSafePath[y][x] = 0.0;
                }
            }
        }

        int[][] directions = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // Up, Down, Left, Right
        int rows = effectiveSpeedOnSafePath.length;
        int cols = effectiveSpeedOnSafePath[0].length;
        int s = startY * cols + startX;
        int g = endY * cols + endX;

        if (!Double.isFinite(effectiveSpeedOnSafePath[startY][startX])
            || !Double.isFinite(effectiveSpeedOnSafePath[endY][endX])
            || effectiveSpeedOnSafePath[startY][startX] <= 0
            || effectiveSpeedOnSafePath[endY][endX] <= 0) {
            System.out.println("No valid path found due to zero effective speed at start or end.");
            return null;
        }

        double[] time = new double[rows * cols];
        int[] prev = new int[rows * cols];
        Arrays.fill(time, Double.POSITIVE_INFINITY);
        time[s] = 0;
        prev[s] = -1;

        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        pq.add(new double[]{0, s});

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            int curr = (int) top[1];
            if (top[0] > time[curr]) continue;
            if (curr == g) break;

            int r = curr / cols;
            int c = curr % cols;

            for (int[] d : directions) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;

                double v = effectiveSpeedOnSafePath[nr][nc];
                if (!Double.isFinite(v) || v <= 0) continue; // Ignore invalid or unsafe speed cells.

                double dist = (d[0] != 0 && d[1] != 0) ? Math.sqrt(2) : 1; // Diagonal distance is √2

                int next = nr * cols + nc;
                double t = time[curr] + dist / v;

                if (t < time[next]) {
                    time[next] = t;
                    prev[next] = curr;
                    pq.add(new double[]{t, next});
                }
            }
        }

        if (time[g] == Double.POSITIVE_INFINITY) {
            System.out.println("No path found.");
            return null;
        }

        LinkedList<int[]> path = new LinkedList<>();
        for (int at = g; at != -1; at = prev[at]) {
            path.addFirst(new int[]{at % cols, at / cols});
        }

        double[][] route = new double[path.size()][2];
        for (int i = 0; i < path.size(); i++) {
            route[i][0] = path.get(i)[0];
            route[i][1] = path.get(i)[1];
        }
        return route;
    }

    public double[] getEffectiveSpeed(Plane plane, Wind wind) {
        if (plane == null || wind == null) {
            return new double[] {0.0, 0.0};
        }
        double windSpeed = wind.getSpeed();
        double windDirection = wind.getDirection();
        double planeDirection = plane.getDirection();
        double planeSpeed = plane.getCurrentSpeed();

        // Calculate the effective speed of the plane considering wind
        double planeX = planeSpeed * Math.sin(Math.toRadians(planeDirection));
        double planeY = planeSpeed * Math.cos(Math.toRadians(planeDirection));
        double windX = windSpeed * Math.sin(Math.toRadians(windDirection));
        double windY = windSpeed * Math.cos(Math.toRadians(windDirection));
        double effectiveX = planeX + windX;
        double effectiveY = planeY + windY;
        double effectiveSpeed = Math.hypot(effectiveX, effectiveY);
        double effectiveDirection = (Math.toDegrees(Math.atan2(
                effectiveX, effectiveY)) + 360) % 360;

        return new double[] {effectiveSpeed, effectiveDirection};
    }

    private boolean isValidMap(double[][] map) {
        if (map == null || map.length != GRID_SIZE) {
            return false;
        }
        for (double[] row : map) {
            if (row == null || row.length != GRID_SIZE) {
                return false;
            }
        }
        return true;
    }
}