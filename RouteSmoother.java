/** Creates a smooth display curve from the grid cells returned by the pathfinder. */
public final class RouteSmoother {
    private RouteSmoother() {}

    /**
     * Samples a Catmull-Rom curve through the route points for rendering only.
     * The input route is not changed and should still be used for all route calculations.
     *
     * @param route grid coordinates in {@code [x, y]} order
     * @param samplesPerSegment number of visual samples between each pair of cells
     * @return a new array of smooth display coordinates
     */
    public static double[][] smoothPath(double[][] route, int samplesPerSegment) {
        if (route == null || route.length == 0) {
            return new double[0][2];
        }
        validate(route);
        if (route.length == 1) {
            return new double[][] {route[0].clone()};
        }

        int samples = Math.max(1, samplesPerSegment);
        double[][] smooth = new double[(route.length - 1) * samples + 1][2];
        int outputIndex = 0;

        for (int segment = 0; segment < route.length - 1; segment++) {
            double[] p0 = route[Math.max(0, segment - 1)];
            double[] p1 = route[segment];
            double[] p2 = route[segment + 1];
            double[] p3 = route[Math.min(route.length - 1, segment + 2)];

            for (int sample = 0; sample < samples; sample++) {
                double t = sample / (double) samples;
                double t2 = t * t;
                double t3 = t2 * t;
                smooth[outputIndex][0] = catmullRom(p0[0], p1[0], p2[0], p3[0], t, t2, t3);
                smooth[outputIndex][1] = catmullRom(p0[1], p1[1], p2[1], p3[1], t, t2, t3);
                outputIndex++;
            }
        }
        smooth[outputIndex] = route[route.length - 1].clone();
        return smooth;
    }

    private static double catmullRom(double p0, double p1, double p2, double p3,
                                     double t, double t2, double t3) {
        return 0.5 * ((2 * p1)
                + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    private static void validate(double[][] route) {
        for (int i = 0; i < route.length; i++) {
            if (route[i] == null || route[i].length < 2
                    || !Double.isFinite(route[i][0]) || !Double.isFinite(route[i][1])) {
                throw new IllegalArgumentException("Each route point must contain finite x and y coordinates.");
            }
        }
    }
}
