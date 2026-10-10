import java.util.LinkedList;
import java.util.Queue;

public class PathFinder {
    Weather[][] weatherMap = new Weather[64][64];
    Wind[][] windMap = new Wind[64][64];
    double[][] terrainMap = new double[64][64];
    double[][] effectiveSpeedMap = new double[64][64];

    public double[][] generateDangerMap() {
        double[][] dangerMap = new double[64][64];
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                dangerMap[i][j] = weatherMap[i][j].getDanger();
            }
        }
        return dangerMap;
    }

    public double[][] generateEffectiveSpeedMap(Plane plane) {
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                double[] effectiveSpeedAndDirection = getEffectiveSpeed(plane, windMap[i][j]);
                effectiveSpeedMap[i][j] = effectiveSpeedAndDirection[0];
            }
        }
        return effectiveSpeedMap;
    }

    public double[][] plotRoute(Plane plane, int startX, int startY, int endX, int endY, double[][] dangerMap) {
        double[][] safePath = new double[64][64];

        // BFS algorithm to find the safest routes from start to end.
        // Safe routes include also terrain below 2000 ft.
        // A safe route is defined as a path with danger levels below 30% danger or 0.3 on the danger map.
        // Then accounts for the plane and wind speed to determine shortest time and eliminates longer routes.
        // Then the route is plotted on the map and returned.

        // Step 1: Find suitable terrain.

        dangerMap = generateDangerMap();
        effectiveSpeedMap = generateEffectiveSpeedMap(plane);

        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (terrainMap[i][j] < 2000) {
                    safePath[i][j] = dangerMap[i][j];
                } else {
                    safePath[i][j] = (1.0); // Mark as unsafe
                }
            }
        }

        // Step 2: Find the safest route through weather using BFS.

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // Up, Down, Left, Right
        boolean[][] visited = new boolean[64][64];
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{startX, startY});
        visited[startX][startY] = true;

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int x = current[0];
            int y = current[1];

            // PROBLEM: Must prevent going off the map and limit directions to the ones that are valid.
            int[] availableDirections = new int[4];
            for (int i = 0; i < directions.length; i++) {
                int newX = x + directions[i][0];
                int newY = y + directions[i][1];
                if (newX >= 0 && newX < 64 && newY >= 0 && newY < 64) {
                    availableDirections[i] = 1; // available
                } else {
                    availableDirections[i] = 0; // unavailable
                }
            }

            for (int[] dir : directions) {
                int newX = x + dir[0];
                int newY = y + dir[1];

                if (newX >= 0 && newX < 64 && newY >= 0 && newY < 64 && !visited[newX][newY] && safePath[newX][newY] < 0.3) {
                    visited[newX][newY] = true;
                    queue.add(new int[]{newX, newY});
                    safePath[newX][newY] = safePath[x][y] + dangerMap[newX][newY]; // Accumulate danger level
                }
            }
        }

        // Step 3: Find the fastest path from the safe routes and wind speed and direction.
        // We find the path by following the highest effective speeds along the safePath found through BFS 
        // with Dijkstra's algorithm.
        // We basically want the highest effective speeds.
        // The effective speed is calculated using the wind speed and direction and the plane's speed and direction.
        // See Wind.java and Plane.java for the speed and direction calculations.

        

        return safePath;
    }

    public double[] getEffectiveSpeed(Plane plane, Wind wind) {
        double windSpeed = wind.getSpeed();
        double windDirection = wind.getDirection();
        double planeDirection = plane.getDirection();
        double planeSpeed = plane.getCurrentSpeed();

        // Calculate the effective speed of the plane considering wind
        double effectiveSpeed = Math.sqrt((planeSpeed * Math.cos(Math.toRadians(planeDirection)) + windSpeed * Math.cos(Math.toRadians(windDirection))) *
                (planeSpeed * Math.cos(Math.toRadians(planeDirection)) + windSpeed * Math.cos(Math.toRadians(windDirection))) +
                (planeSpeed * Math.sin(Math.toRadians(planeDirection)) + windSpeed * Math.sin(Math.toRadians(windDirection))) *
                (planeSpeed * Math.sin(Math.toRadians(planeDirection)) + windSpeed * Math.sin(Math.toRadians(windDirection))));
        // Compass bearing: 0° = north, 90° = east, 180° = south.
        double effectiveDirection = (Math.toDegrees(Math.atan2(
                windSpeed * Math.cos(Math.toRadians(windDirection)) + planeSpeed * Math.cos(Math.toRadians(planeDirection)),
                windSpeed * Math.sin(Math.toRadians(windDirection)) + planeSpeed * Math.sin(Math.toRadians(planeDirection)))) + 360) % 360;

        return new double[] {effectiveSpeed, effectiveDirection};
    }
}