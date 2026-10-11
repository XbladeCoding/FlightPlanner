import java.util.Random;

public class Main {
    public static void main(String[] args) {
        Plane plane = new Plane("Boeing 747", 900, 183380, 1000);

        double[][] terrainMap = ArrayGen.generate64x64Terrain(new Random().nextLong());

        PathFinder pathFinder = new PathFinder();
        double[][] route = pathFinder.plotRoute(plane, 0, 0, 0, 0);

        if (route != null) {
            System.out.println("Route generated successfully.");
        } else {
            System.out.println("Failed to generate route.");
        }

        for (int i = 0; i < route.length; i++) {
            for (int j = 0; j < route[i].length; j++) {
                System.out.print(route[i][j] + " ");
            }
            System.out.println();
        }
    }
}