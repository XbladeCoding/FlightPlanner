import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.Random;

public class PathFinder {
    private Weather[][] weatherMap = ArrayGen.generate64x64Weather(new Random().nextLong());
    private Wind[][] windMap = ArrayGen.generate64x64Wind(new Random().nextLong());
    private double[][] terrainMap = ArrayGen.generate64x64Terrain(new Random().nextLong());
    private double[][] effectiveSpeedMap = new double[64][64];

    public double[][] generateDangerMap() {
        double[][] dangerMap = new double[64][64];
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                dangerMap[i][j] = weatherMap[i][j] == null ? 1.0 : weatherMap[i][j].getDanger();
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

    private double computeOptimalTravelSpeed(Plane plane, double routeDistance) {
        if (plane == null || !Double.isFinite(routeDistance) || routeDistance <= 0.0) {
            return 0.0;
        }

        double currentSpeed = plane.getCurrentSpeed();
        if (!Double.isFinite(currentSpeed) || currentSpeed <= 0.0) {
            return 0.0;
        }

        // Do not mutate the plane while evaluating the optimal speed.
        // We only estimate the best possible cruising speed from the current state.
        double acceleratedSpeed = currentSpeed;
        try {
            java.lang.reflect.Method getMaxSpeedMethod = plane.getClass().getMethod("getMaxSpeed");
            Object maxSpeedValue = getMaxSpeedMethod.invoke(plane);
            if (maxSpeedValue instanceof Number) {
                double maxSpeed = ((Number) maxSpeedValue).doubleValue();
                if (Double.isFinite(maxSpeed) && maxSpeed > currentSpeed) {
                    acceleratedSpeed = maxSpeed;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // No max-speed accessor is available; keep the current speed.
        }

        if (currentSpeed > 0.0 && acceleratedSpeed > currentSpeed) {
            double fuelSavedAtCruise = routeDistance * (1.0 / currentSpeed - 1.0 / acceleratedSpeed);
            if (fuelSavedAtCruise > 0.0) {
                return acceleratedSpeed;
            }
        }
        return currentSpeed;
    }

    private void acceleratePlaneToOptimalSpeed(Plane plane, double optimalSpeed) {
        if (plane == null || !Double.isFinite(optimalSpeed) || optimalSpeed <= 0.0) {
            return;
        }

        double currentSpeed = plane.getCurrentSpeed();
        if (!Double.isFinite(currentSpeed) || currentSpeed <= 0.0 || optimalSpeed <= currentSpeed) {
            return;
        }

        int safetyLimit = 0;
        while (plane.getCurrentSpeed() < optimalSpeed && safetyLimit < 100) {
            plane.accelerate(optimalSpeed);
            if (!Double.isFinite(plane.getCurrentSpeed()) || plane.getCurrentSpeed() <= currentSpeed) {
                break;
            }
            currentSpeed = plane.getCurrentSpeed();
            safetyLimit++;
        }
    }

    public double[][] plotRoute(Plane plane, int startX, int startY, int endX, int endY) {
        double[][] safePath = new double[64][64];
        double[][] dangerMap = generateDangerMap();

        if (plane == null || startX < 0 || startX >= 64 || startY < 0 || startY >= 64
                || endX < 0 || endX >= 64 || endY < 0 || endY >= 64) {
            return null;
        }

        // Nested-loop algorithm to find the safest routes from start to end.
        // Safe routes include also terrain below 2000 ft.
        // A safe route is defined as a path with danger levels below 30% danger or 0.3 on the danger map.
        // Then accounts for the plane and wind speed to determine shortest time and eliminates longer routes.
        // Then the route is plotted on the map and returned.

        // Step 1: Find suitable terrain.

        if (dangerMap == null || dangerMap.length != 64) {
            dangerMap = generateDangerMap();
        }
        effectiveSpeedMap = generateEffectiveSpeedMap(plane);

        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (dangerMap[i] != null && dangerMap[i].length == 64
                        && Double.isFinite(terrainMap[i][j]) && terrainMap[i][j] < 2000
                        && Double.isFinite(dangerMap[i][j])) {
                    safePath[i][j] = dangerMap[i][j];
                } else {
                    safePath[i][j] = (1.0); // Mark as unsafe
                }
            }
        }

        // Step 2: Find the safest path through weather.

        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (safePath[i][j] > 0.3) {
                    safePath[i][j] = (1.0); // Mark as unsafe
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

        double[][] effectiveSpeedOnSafePath = new double[64][64];
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 64; j++) {
                if (safePath[i][j] < 1.0) { 
                    effectiveSpeedOnSafePath[i][j] = effectiveSpeedMap[i][j];
                } else {
                    effectiveSpeedOnSafePath[i][j] = 0.0; // Mark as unsafe
                }
            }
        }

        int[][] directions = new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // Up, Down, Left, Right
        int rows = effectiveSpeedOnSafePath.length;
        int cols = effectiveSpeedOnSafePath[0].length;
        int s = startX * cols + startY;
        int g = endX * cols + endY;

        if (!Double.isFinite(effectiveSpeedOnSafePath[startX][startY])
            || !Double.isFinite(effectiveSpeedOnSafePath[endX][endY])
            || effectiveSpeedOnSafePath[startX][startY] <= 0
            || effectiveSpeedOnSafePath[endX][endY] <= 0) {
            System.out.println("No valid path found due to zero effective speed at start or end.");
            return null;
        }

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

        if (!Double.isFinite(time[g])) {
            System.out.println("No path found.");
            return null;
        }

        LinkedList<int[]> path = new LinkedList<>();
        int at = g;
        while (at != -1) {
            path.addFirst(new int[]{at / cols, at % cols});
            if (at == s) {
                break;
            }
            at = prev[at];
        }
        if (path.isEmpty() || path.getFirst()[0] != startX || path.getFirst()[1] != startY) {
            System.out.println("No valid path found.");
            return null;
        }

        double[][] route = new double[path.size()][2];
        for (int i = 0; i < path.size(); i++) {
            route[i][0] = path.get(i)[0];
            route[i][1] = path.get(i)[1];
        }

        // Account for the route length when deciding whether acceleration pays
        // for itself. In the travel model, fuel use per distance is proportional
        // to the time required (1 / speed).
        double routeDistance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            int[] previous = path.get(i - 1);
            int[] current = path.get(i);
            routeDistance += Math.hypot(current[0] - previous[0], current[1] - previous[1]);
        }



        double optimalTravelSpeed = computeOptimalTravelSpeed(plane, routeDistance);
        double speedBeforeAcceleration = plane.getCurrentSpeed();
        acceleratePlaneToOptimalSpeed(plane, optimalTravelSpeed);

        double currentSpeed = plane.getCurrentSpeed();
        if (currentSpeed > speedBeforeAcceleration) {
            System.out.println("Route is long enough to justify acceleration: optimal speed = " + optimalTravelSpeed);
        } else {
            System.out.println("Optimal travel speed for this route: " + currentSpeed);
        }

        for (int i = 1; i < path.size(); i++) {
            int[] previous = path.get(i - 1);
            int[] current = path.get(i);
            double distance = Math.hypot(current[0] - previous[0], current[1] - previous[1]);
            plane.travel(distance);
            if (plane.getFuelCapacity() <= 0) {
                System.out.println("Path failed: Aircraft ran out of fuel");
                return null;
            }
        }
        System.out.println("Fuel remaining: " + plane.getFuelCapacity());
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