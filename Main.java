public class Main {
    public static void main(String[] args) {
        Plane plane = new Plane("Boeing 747", 900, 183380, 1000);
        Wind wind = new Wind(50, 45);
        Weather weather = new Weather(0.5, 0.8, 20);

        PathFinder pathFinder = new PathFinder();
        double[] effectiveSpeedAndDirection = pathFinder.getEffectiveSpeed(plane, wind);

        System.out.println("Effective Speed: " + effectiveSpeedAndDirection[0]);
        System.out.println("Effective Direction: " + effectiveSpeedAndDirection[1] + " degrees east of north");
        System.out.println("Weather Danger Level: " + weather.getDanger());
    }
}