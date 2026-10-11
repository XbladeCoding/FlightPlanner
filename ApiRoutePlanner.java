import java.util.Arrays;
import java.util.LinkedList;
import java.util.PriorityQueue;

/** Applies the existing four-neighbor route cost rules to live API weather grids. */
public final class ApiRoutePlanner {
    private ApiRoutePlanner() {}

    public static double[][] plotRoute(ApiWeatherGrid.Snapshot data, PathFinder speedCalculator,
            Plane plane, int startX, int startY, int endX, int endY,
            PathFinder.RouteStrategy strategy) {
        if (data == null || speedCalculator == null || plane == null || strategy == null
                || !validCoordinate(startX, startY) || !validCoordinate(endX, endY)) return null;
        double[][] route = search(data, speedCalculator, plane, startX, startY, endX, endY, strategy);
        if (route == null && strategy == PathFinder.RouteStrategy.RECOMMENDED) {
            return search(data, speedCalculator, plane, startX, startY, endX, endY,
                    PathFinder.RouteStrategy.SAFEST);
        }
        return route;
    }

    private static double[][] search(ApiWeatherGrid.Snapshot data, PathFinder speedCalculator,
            Plane plane, int startX, int startY, int endX, int endY,
            PathFinder.RouteStrategy strategy) {
        double[][] terrain = data.elevationMeters();
        double[][] danger = data.danger();
        double[][] wind = data.windSpeedKmh();
        double[][] direction = data.windDirectionDeg();
        double[][] effectiveSpeed = new double[ApiWeatherGrid.SIZE][ApiWeatherGrid.SIZE];
        boolean[][] traversable = new boolean[ApiWeatherGrid.SIZE][ApiWeatherGrid.SIZE];

        for (int row = 0; row < ApiWeatherGrid.SIZE; row++) {
            for (int col = 0; col < ApiWeatherGrid.SIZE; col++) {
                if (!ApiWeatherGrid.insideUs(row, col) || !Double.isFinite(terrain[row][col])
                        || !Double.isFinite(danger[row][col]) || !Double.isFinite(wind[row][col])
                        || !Double.isFinite(direction[row][col])) continue;
                Wind liveWind = new Wind(wind[row][col], direction[row][col]);
                effectiveSpeed[row][col] = speedCalculator.getEffectiveSpeed(plane, liveWind)[0];
                traversable[row][col] = terrain[row][col] < 2000
                        && (strategy != PathFinder.RouteStrategy.RECOMMENDED || danger[row][col] <= 0.3)
                        && effectiveSpeed[row][col] > 0;
            }
        }

        if (!traversable[startY][startX] || !traversable[endY][endX]) return null;

        int cols = ApiWeatherGrid.SIZE;
        int start = startY * cols + startX;
        int goal = endY * cols + endX;
        double[] costs = new double[cols * ApiWeatherGrid.SIZE];
        int[] previous = new int[costs.length];
        Arrays.fill(costs, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);
        costs[start] = strategy == PathFinder.RouteStrategy.SAFEST ? danger[startY][startX] : 0;

        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{costs[start], start});
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        while (!queue.isEmpty()) {
            double[] node = queue.poll();
            int current = (int) node[1];
            if (node[0] > costs[current]) continue;
            if (current == goal) break;

            int row = current / cols;
            int col = current % cols;
            for (int[] step : directions) {
                int nextRow = row + step[0], nextCol = col + step[1];
                if (nextRow < 0 || nextRow >= ApiWeatherGrid.SIZE
                        || nextCol < 0 || nextCol >= cols || !traversable[nextRow][nextCol]) continue;
                int next = nextRow * cols + nextCol;
                double edge = strategy == PathFinder.RouteStrategy.SAFEST
                        ? danger[nextRow][nextCol] : 1.0 / effectiveSpeed[nextRow][nextCol];
                double nextCost = costs[current] + edge;
                if (nextCost < costs[next]) {
                    costs[next] = nextCost;
                    previous[next] = current;
                    queue.add(new double[]{nextCost, next});
                }
            }
        }

        if (!Double.isFinite(costs[goal])) return null;
        LinkedList<int[]> path = new LinkedList<>();
        for (int at = goal; at != -1; at = previous[at]) path.addFirst(new int[]{at % cols, at / cols});
        double[][] result = new double[path.size()][2];
        for (int i = 0; i < path.size(); i++) {
            result[i][0] = path.get(i)[0];
            result[i][1] = path.get(i)[1];
        }
        return result;
    }

    private static boolean validCoordinate(int x, int y) {
        return x >= 0 && x < ApiWeatherGrid.SIZE && y >= 0 && y < ApiWeatherGrid.SIZE;
    }
}
