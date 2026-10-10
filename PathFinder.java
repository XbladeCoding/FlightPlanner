public class PathFinder {
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