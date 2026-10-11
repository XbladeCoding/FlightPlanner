import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.Random;


public class ArrayGen extends JPanel {


   private static int GRID_SIZE = 64;
   private static int PIXEL_SCALE = 10;


   private static double MIN_ELEVATION = -500.0;
   private static double MAX_ELEVATION = 2000.0;
   private double[][] terrainData;


   // Wind matrices derived entirely from the topography array
   private double[][] windSpeed;
   private double[][] windAngleRad;


   // Animation variable tracked by Swing Timer
   private double animationPhase = 0.0;


   // Data-only constructor: builds the wind fields for a given terrain WITHOUT creating the
   // Swing UI or starting the animation Timer.
   // FIX: generate64x64Wind() used to call new ArrayGen(), which (a) generated and threw away an
   // extra random terrain and (b) started a Swing Timer that could keep the JVM from exiting.
   private ArrayGen(double[][] terrain) {
       this.terrainData = terrain;
       this.windSpeed = new double[GRID_SIZE][GRID_SIZE];
       this.windAngleRad = new double[GRID_SIZE][GRID_SIZE];
       calculateWindFields();
   }

   public ArrayGen() {
       GRID_SIZE = 64;

       long seed = new Random().nextLong();
       this.terrainData = generate64x64Terrain(seed);


       this.windSpeed = new double[GRID_SIZE][GRID_SIZE];
       this.windAngleRad = new double[GRID_SIZE][GRID_SIZE];
       calculateWindFields();


       // --- PRINTING SCRIPT: TERMINAL MATRIX VISUALIZER PANEL ---


    //    // 1. Output the reference Elevation Dataset
    //    System.out.println("\n=== DATASET 1 OF 3: REFERENCE ELEVATION TOPOGRAPHY MATRIX (Meters) ===");
    //    for (double[] row : terrainData) {
    //        System.out.print("[");
    //        for (int i = 0; i < row.length; i++) {
    //            System.out.print(
    //                    String.format(java.util.Locale.ROOT, "%4d", (int) row[i]) + (i == row.length - 1 ? "" : ", "));
    //        }
    //        System.out.println("],");
    //    }
    //    System.out.println("============================================================================\n");


    //    // 2. Output the computed Velocity magnitudes
    //    System.out.println("=== DATASET 2 OF 3: DERIVED WIND SPEED VELOCITY MATRIX (m/s) ===");
    //    for (double[] row : windSpeed) {
    //        System.out.print("[");
    //        for (int i = 0; i < row.length; i++) {
    //            System.out.print(
    //                    String.format(java.util.Locale.ROOT, "%4.1f", row[i]) + (i == row.length - 1 ? "" : ", "));
    //        }
    //        System.out.println("],");
    //    }
    //    System.out.println("============================================================================\n");


    //    // 3. Output the raw flow Trajectory Angles
    //    System.out.println("=== DATASET 3 OF 3: DERIVED WIND DIRECTION TRAJECTORY MATRIX (Radians) ===");
    //    for (double[] row : windAngleRad) {
    //        System.out.print("[");
    //        for (int i = 0; i < row.length; i++) {
    //            System.out.print(
    //                    String.format(java.util.Locale.ROOT, "%5.2f", row[i]) + (i == row.length - 1 ? "" : ", "));
    //        }
    //        System.out.println("],");
    //    }
    //    System.out.println("============================================================================\n");


       setPreferredSize(new Dimension(GRID_SIZE * PIXEL_SCALE, GRID_SIZE * PIXEL_SCALE));


       // Runs at roughly 60 FPS to continuously smoothly animate the pulsing arrows
       Timer timer = new Timer(16, e -> {
           animationPhase += 0.05;
           repaint();
       });
       timer.start();
   }


   private void calculateWindFields() {
       double baseSpeed = 8.0;
       double baseAngleRad = 0.0; // Global prevailing wind pushing left-to-right (West-to-East)
       double globalWindX = baseSpeed * Math.cos(baseAngleRad);
       double globalWindY = baseSpeed * Math.sin(baseAngleRad);


       for (int y = 0; y < GRID_SIZE; y++) {
           for (int x = 0; x < GRID_SIZE; x++) {
               double slopeX = 0;
               double slopeY = 0;


               // Calculate local slope rise-over-run from the topography array
               if (x > 0 && x < GRID_SIZE - 1) {
                   slopeX = (terrainData[y][x + 1] - terrainData[y][x - 1]) / 2.0;
               }
               if (y > 0 && y < GRID_SIZE - 1) {
                   slopeY = (terrainData[y - 1][x] - terrainData[y + 1][x]) / 2.0;
               }


               double localWindX = globalWindX;
               double localWindY = globalWindY;


               // Deflection steering maps based on slope gradients
               if (slopeX > 10.0) {
                   localWindY += (slopeY * 0.9);
                   localWindX -= (slopeX * 0.4);
               } else if (slopeX < -10.0) {
                   localWindY += (slopeY * 0.7);
                   localWindX += (Math.abs(slopeX) * 0.5);
               }


               localWindY += (slopeY * 0.6);


               if (terrainData[y][x] < 0.0) {
                   double deepFactor = Math.abs(terrainData[y][x]) / Math.abs(MIN_ELEVATION);
                   localWindX *= (1.0 - (deepFactor * 0.7));
                   localWindY += Math.sin(x * 0.5) * 4.0;
               }


               double speed = Math.sqrt((localWindX * localWindX) + (localWindY * localWindY));
               windSpeed[y][x] = Math.clamp(speed, 2.0, 25.0);
               windAngleRad[y][x] = Math.atan2(localWindY, localWindX);
           }
       }
   }


   @Override
   protected void paintComponent(Graphics g) {
       super.paintComponent(g);
       Graphics2D g2 = (Graphics2D) g;


       g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
       g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);


       // 1. Draw SubZero Background Terrain
       for (int y = 0; y < GRID_SIZE; y++) {
           for (int x = 0; x < GRID_SIZE; x++) {
               double elevation = terrainData[y][x];
               g2.setColor(getSubZeroGradientColor(elevation));
               g2.fillRect(x * PIXEL_SCALE, y * PIXEL_SCALE, PIXEL_SCALE, PIXEL_SCALE);
           }
       }


       // 2. Draw Sleek Weather-App Styled Radar Arrows
       int blockSize = 6;


       for (int blockY = 0; blockY < GRID_SIZE; blockY += blockSize) {
           for (int blockX = 0; blockX < GRID_SIZE; blockX += blockSize) {


               double sumSpeed = 0;
               double sumCos = 0;
               double sumSin = 0;
               int count = 0;


               for (int y = blockY; y < blockY + blockSize && y < GRID_SIZE; y++) {
                   for (int x = blockX; x < blockX + blockSize && x < GRID_SIZE; x++) {
                       sumSpeed += windSpeed[y][x];
                       sumCos += Math.cos(windAngleRad[y][x]);
                       sumSin += Math.sin(windAngleRad[y][x]);
                       count++;
                   }
               }


               double avgSpeed = sumSpeed / count;
               double avgAngle = Math.atan2(sumSin / count, sumCos / count);


               int centerX = (blockX * PIXEL_SCALE) + ((blockSize * PIXEL_SCALE) / 2);
               int centerY = (blockY * PIXEL_SCALE) + ((blockSize * PIXEL_SCALE) / 2);


               drawArrow(g2, centerX, centerY, avgSpeed, avgAngle);
           }
       }
   }


   private void drawArrow(Graphics2D g2, int cx, int cy, double speed, double angle) {
       double baseLength = 10.0 + (speed * 0.6);
       double pulse = 1.0 + 0.12 * Math.sin(animationPhase * (1.0 + speed * 0.04));
       double length = baseLength * pulse;


       Path2D.Double arrowShape = new Path2D.Double();
       arrowShape.moveTo(length, 0);
       arrowShape.lineTo(0, -3.5);
       arrowShape.quadTo(length * 0.2, 0, 0, 3.5);
       arrowShape.closePath();


       int alpha = Math.clamp((int) (speed * 14), 70, 220);


       java.awt.geom.AffineTransform oldTransform = g2.getTransform();


       g2.translate(cx, cy);
       g2.rotate(angle);


       g2.setColor(new Color(0, 0, 0, Math.clamp(alpha - 40, 0, 150)));
       g2.translate(1, 1);
       g2.fill(arrowShape);
       g2.translate(-1, -1);


       g2.setColor(new Color(255, 255, 255, alpha));
       g2.fill(arrowShape);


       g2.setTransform(oldTransform);
   }


   private Color getSubZeroGradientColor(double elevation) {
       if (elevation < 0.0) {
           double depthT = elevation / MIN_ELEVATION;
           depthT = Math.clamp(depthT, 0.0, 1.0);


           Color brightBlue = new Color(0, 100, 255);
           Color deepBlack = new Color(0, 0, 0);


           return lerpColor(brightBlue, deepBlack, depthT);
       }


       double landT = elevation / MAX_ELEVATION;
       landT = Math.clamp(landT, 0.0, 1.0);


       Color green = new Color(30, 170, 50);
       Color yellow = new Color(240, 210, 40);
       Color red = new Color(220, 30, 30);


       if (landT < 0.5) {
           double localT = landT / 0.5;
           return lerpColor(green, yellow, localT);
       } else {
           double localT = (landT - 0.5) / 0.5;
           return lerpColor(yellow, red, localT);
       }
   }


   private Color lerpColor(Color c1, Color c2, double t) {
       int r = (int) (c1.getRed() + t * (c2.getRed() - c1.getRed()));
       int g = (int) (c1.getGreen() + t * (c2.getGreen() - c1.getGreen()));
       int b = (int) (c1.getBlue() + t * (c2.getBlue() - c1.getBlue()));
       return new Color(r, g, b);
   }


   public static double[][] generate64x64Terrain(long seed) {
       double[][] rawGrid = new double[GRID_SIZE][GRID_SIZE];
       Random rand = new Random(seed);
       double offsetX = rand.nextDouble() * 10000;
       double offsetY = rand.nextDouble() * 10000;


       double absoluteMinNoise = Double.MAX_VALUE;
       double absoluteMaxNoise = -Double.MAX_VALUE;


       for (int y = 0; y < GRID_SIZE; y++) {
           for (int x = 0; x < GRID_SIZE; x++) {
               double nx = (double) x / GRID_SIZE;
               double ny = (double) y / GRID_SIZE;


               double noiseValue = 0.0;
               noiseValue += 1.00 * rawNoise(1 * nx + offsetX, 1 * ny + offsetY);
               noiseValue += 0.50 * rawNoise(2 * nx + offsetX, 2 * ny + offsetY);
               noiseValue += 0.25 * rawNoise(4 * nx + offsetX, 4 * ny + offsetY);
               noiseValue += 0.12 * rawNoise(8 * nx + offsetX, 8 * ny + offsetY);
               noiseValue = noiseValue / (1.00 + 0.50 + 0.25 + 0.12);
               noiseValue = Math.pow(noiseValue, 1.2);
               rawGrid[y][x] = noiseValue;
               if (noiseValue < absoluteMinNoise)
                   absoluteMinNoise = noiseValue;
               if (noiseValue > absoluteMaxNoise)
                   absoluteMaxNoise = noiseValue;
           }
       }
       double[][] Grid = new double[GRID_SIZE][GRID_SIZE];
       for (int y = 0; y < GRID_SIZE; y++) {
           for (int x = 0; x < GRID_SIZE; x++) {
               Grid[y][x] = MIN_ELEVATION + (normalizedNoise(rawGrid[y][x], absoluteMinNoise, absoluteMaxNoise)
                       * (MAX_ELEVATION - MIN_ELEVATION));
           }
       }
       return Grid;
   }


   private static double normalizedNoise(double val, double min, double max) {
       return (val - min) / (max - min);
   }


   private static double rawNoise(double x, double y) {
       int X = (int) Math.floor(x);
       int Y = (int) Math.floor(y);
       double xf = x - X;
       double yf = y - Y;
       double u = (1 - Math.cos(xf * Math.PI)) * 0.5;
       double v = (1 - Math.cos(yf * Math.PI)) * 0.5;
       double n00 = pseudoRandomHash(X, Y);
       double n10 = pseudoRandomHash(X + 1, Y);
       double n01 = pseudoRandomHash(X, Y + 1);
       double n11 = pseudoRandomHash(X + 1, Y + 1);
       return (n00 + u * (n10 - n00)) + v * ((n01 + u * (n11 - n01)) - (n00 + u * (n10 - n00)));
   }


   private static double pseudoRandomHash(int x, int y) {
       int n = x * 331 + y * 8961;
       n = (n << 13) ^ n;
       int t = (n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff;
       return (double) t / Integer.MAX_VALUE;
   }

   // Wind directions are in RADIANS, measured with x = +column (east) and y = +row
   // (the same orientation the visualizer uses when it rotates the arrows on screen).
   public static Wind[][] generate64x64Wind(long seed) {
       ArrayGen generator = new ArrayGen(generate64x64Terrain(seed));
       Wind[][] windData = new Wind[GRID_SIZE][GRID_SIZE];
       for (int y = 0; y < GRID_SIZE; y++) {
           for (int x = 0; x < GRID_SIZE; x++) {
               windData[y][x] = new Wind(generator.windSpeed[y][x], generator.windAngleRad[y][x]);
           }
       }
       return windData;
   }

   public static Weather[][] generate64x64Weather(long seed) {
       Random rand = new Random(seed);
       double offsetX = rand.nextDouble() * 10000;
       double offsetY = rand.nextDouble() * 10000;
       double seasonalTemperature = 10.0 + rand.nextDouble() * 12.0;
       double[][] terrain = generate64x64Terrain(seed);
       Weather[][] weatherData = new Weather[GRID_SIZE][GRID_SIZE];
       for (int y = 0; y < GRID_SIZE; y++) {
           for (int x = 0; x < GRID_SIZE; x++) {
               double nx = (double) x / GRID_SIZE;
               double ny = (double) y / GRID_SIZE;
               double precipitationNoise = 0.70 * rawNoise(nx * 3.0 + offsetX, ny * 3.0 + offsetY)
                       + 0.30 * rawNoise(nx * 9.0 + offsetX, ny * 9.0 + offsetY);
               double chanceOfPrecipitation = Math.clamp(0.05 + precipitationNoise * 0.85, 0.0, 1.0);

               double intensityNoise = rawNoise(nx * 6.0 + offsetY, ny * 6.0 + offsetX);
               double intensity = Math.clamp(chanceOfPrecipitation * (0.25 + intensityNoise * 0.75), 0.0, 1.0);

               double latitudeCooling = Math.abs((ny * 2.0) - 1.0) * 12.0;
               double elevationCooling = Math.max(0.0, terrain[y][x]) * 0.0065;
               double temperatureVariation = (rawNoise(nx * 4.0 + offsetY, ny * 4.0 + offsetX) - 0.5) * 8.0;
               double temperature = Math.clamp(seasonalTemperature - latitudeCooling - elevationCooling
                       + temperatureVariation, -30.0, 40.0);

               weatherData[y][x] = new Weather(chanceOfPrecipitation, intensity, temperature);
           }
       }
       return weatherData;
   }

// Testing code for the ArrayGen class. Uncomment the main method below to run the visualization panel.

//    public static void main(String[] args) {
//        JFrame frame = new JFrame("Minecraft terrain generator rip-off");
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.add(new ArrayGen());
//        frame.pack();
//        frame.setLocationRelativeTo(null);
//        frame.setVisible(true);
//    }
}

