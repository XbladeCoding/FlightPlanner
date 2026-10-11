import java.util.Random;

public class Main {
    public static void main(String[] args) {
        // Constructor order is (model, efficiency, maxSpeed, fuelCapacity); values kept as written.
        Plane plane = new Plane("Boeing 747", 900, 183380, 1000);

        // FIX: terrain, wind and weather now come from ONE seed (inside PathFinder), instead of
        // an unused terrain map here plus three unrelated maps in PathFinder.
        long seed = new Random().nextLong();
        PathFinder pathFinder = new PathFinder(seed);
        System.out.println("Map seed: " + seed);

        // FIX: the route used to go from (0,0) to (0,0), which is a single point with nothing to fly.
        // Now pick random, distinct, safe start and end cells each run.
        Random rand = new Random();
        double[][] route = null;
        int startRow = -1, startCol = -1, endRow = -1, endCol = -1;
        for (int attempt = 0; attempt < 50 && route == null; attempt++) {
            int[] start = randomSafeCell(pathFinder, rand);
            int[] end = randomSafeCell(pathFinder, rand);
            if (start == null || end == null) break;
            if (start[0] == end[0] && start[1] == end[1]) continue;
            startRow = start[0]; startCol = start[1];
            endRow = end[0]; endCol = end[1];
            route = pathFinder.plotRoute(plane, startRow, startCol, endRow, endCol);
        }

        // FIX: the old code printed "Failed" and then looped over the null route anyway (crash).
        if (route == null) {
            System.out.println("Failed to generate route.");
            return;
        }

        System.out.println("Route generated successfully from (" + startRow + ", " + startCol
                + ") to (" + endRow + ", " + endCol + "), " + route.length + " cells. Each line is: row column");
        for (int i = 0; i < route.length; i++) {
            for (int j = 0; j < route[i].length; j++) {
                System.out.print(route[i][j] + " ");
            }
            System.out.println();
        }
    }

    private static int[] randomSafeCell(PathFinder pathFinder, Random rand) {
        for (int tries = 0; tries < 10000; tries++) {
            int r = rand.nextInt(64), c = rand.nextInt(64);
            if (pathFinder.isSafeCell(r, c)) return new int[]{r, c};
        }
        return null;
    }
}
