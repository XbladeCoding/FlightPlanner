import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.Random;

public class PathFinder {
    private static final int GRID_SIZE = 64;

    public enum RouteStrategy {
        FASTEST,
        SAFEST,
        RECOMMENDED
    }

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
        return plotRoute(plane, startX, startY, endX, endY, dangerMap, RouteStrategy.RECOMMENDED);
    }

    public double[][] plotRoute(Plane plane, int startX, int startY, int endX, int endY,
            double[][] dangerMap, RouteStrategy strategy) {

        if (plane == null || startX < 0 || startX >= GRID_SIZE || startY < 0 || startY >= GRID_SIZE
                || endX < 0 || endX >= GRID_SIZE || endY < 0 || endY >= GRID_SIZE || strategy == null) {
            return null;
        }

        if (!isValidMap(dangerMap)) {
            dangerMap = generateDangerMap();
        }
        effectiveSpeedMap = generateEffectiveSpeedMap(plane);

        boolean[][] traversable = new boolean[GRID_SIZE][GRID_SIZE];
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                traversable[y][x] = Double.isFinite(terrainMap[y][x])
                        && terrainMap[y][x] < 2000
                        && Double.isFinite(dangerMap[y][x])
                        && (strategy != RouteStrategy.RECOMMENDED || dangerMap[y][x] <= 0.3)
                        && Double.isFinite(effectiveSpeedMap[y][x])
                        && effectiveSpeedMap[y][x] > 0;
            }
        }

        int[][] directions = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int rows = GRID_SIZE;
        int cols = GRID_SIZE;
        int s = startY * cols + startX;
        int g = endY * cols + endX;

        if (!traversable[startY][startX] || !traversable[endY][endX]) {
            if (strategy == RouteStrategy.RECOMMENDED) {
                return plotRoute(plane, startX, startY, endX, endY, dangerMap, RouteStrategy.SAFEST);
            }
            return null;
        }

        double[] cost = new double[rows * cols];
        int[] prev = new int[rows * cols];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        cost[s] = strategy == RouteStrategy.SAFEST ? dangerMap[startY][startX] : 0;
        prev[s] = -1;

        PriorityQueue<double[]> pq = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        pq.add(new double[]{cost[s], s});

        while (!pq.isEmpty()) {
            double[] top = pq.poll();
            int curr = (int) top[1];
            if (top[0] > cost[curr]) continue;
            if (curr == g) break;

            int r = curr / cols;
            int c = curr % cols;

            for (int[] d : directions) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                if (!traversable[nr][nc]) continue;

                int next = nr * cols + nc;
                double edgeCost;
                if (strategy == RouteStrategy.SAFEST) {
                    edgeCost = dangerMap[nr][nc];
                } else {
                    double distance = (d[0] != 0 && d[1] != 0) ? Math.sqrt(2) : 1;
                    edgeCost = distance / effectiveSpeedMap[nr][nc];
                }
                double nextCost = cost[curr] + edgeCost;

                if (nextCost < cost[next]) {
                    cost[next] = nextCost;
                    prev[next] = curr;
                    pq.add(new double[]{nextCost, next});
                }
            }
        }

        if (cost[g] == Double.POSITIVE_INFINITY) {
            if (strategy == RouteStrategy.RECOMMENDED) {
                return plotRoute(plane, startX, startY, endX, endY, dangerMap, RouteStrategy.SAFEST);
            }
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
