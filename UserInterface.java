import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class UserInterface {
    private static final Color NAVY = new Color(22, 43, 68);
    private static final Color BLUE = new Color(41, 111, 180);
    private static final Color MUTED = new Color(95, 110, 126);
    private static final Color BACKGROUND = new Color(244, 247, 250);
    private static final int MAP_SIZE = 64;

    private final JFrame frame = new JFrame("Flight Planner");
    private final JTextField modelField = new JTextField("Boeing 747");
    private final JTextField maxSpeedField = new JTextField("900");
    private final JTextField cruiseSpeedField = new JTextField("850");
    private final JTextField planeDirectionField = new JTextField("0");
    private final JTextField gridXField = new JTextField("0");
    private final JTextField gridYField = new JTextField("0");
    private final JTextField startXField = new JTextField(randomInteriorCoordinate());
    private final JTextField startYField = new JTextField(randomInteriorCoordinate());
    private final JTextField destinationXField = new JTextField(randomInteriorCoordinate());
    private final JTextField destinationYField = new JTextField(randomInteriorCoordinate());
    private final PathFinder pathFinder = new PathFinder();
    private final JLabel windSpeedValue = conditionValue();
    private final JLabel windDirectionValue = conditionValue();
    private final JLabel elevationValue = conditionValue();
    private final JLabel precipitationValue = conditionValue();
    private final JLabel intensityValue = conditionValue();
    private final JLabel temperatureValue = conditionValue();
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel effectiveSpeedResult = resultValue();
    private final JLabel effectiveDirectionResult = resultValue();
    private final JLabel weatherDangerResult = resultValue();
    private JDialog mapsDialog;

    private UserInterface() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new java.awt.Dimension(680, 640));
        frame.setSize(760, 700);
        frame.setLocationByPlatform(true);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 28, 28));
        content.add(createHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 18));
        body.setOpaque(false);
        body.add(createInputs(), BorderLayout.CENTER);
        body.add(createResults(), BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);

        frame.setContentPane(content);
        calculate();
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> new UserInterface().frame.setVisible(true));
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.setOpaque(false);

        JLabel title = new JLabel("FLIGHT PLANNER");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        title.setForeground(NAVY);
        JButton viewMapsButton = new JButton("VIEW MAPS");
        viewMapsButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        viewMapsButton.setForeground(Color.BLACK);
        viewMapsButton.setBackground(BLUE);
        viewMapsButton.setFocusPainted(false);
        viewMapsButton.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        viewMapsButton.addActionListener(event -> showMaps());
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(viewMapsButton, BorderLayout.EAST);

        JLabel subtitle = new JLabel("Wind-adjusted flight performance and weather assessment");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        header.add(titleRow, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        return header;
    }

    private void showMaps() {
        if (mapsDialog != null && mapsDialog.isDisplayable()) {
            mapsDialog.dispose();
        }

        mapsDialog = new JDialog(frame, "Flight Planner Maps", false);
        mapsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        Route route = null;
        String routeDescription = "Route from the selected start to destination, overlaid on the topographic map.";
        try {
            route = calculateRoute();
            if (route == null) {
                routeDescription = "No safe route found for these endpoints and current aircraft settings.";
            }
        } catch (IllegalArgumentException error) {
            routeDescription = error.getMessage();
        }

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Topographic", createMapTab(
                "Elevation map", "Green is low land, yellow/red is higher terrain, and blue is below sea level.",
                new MapCanvas(pathFinder, MapKind.TERRAIN)));
        tabs.addTab("Wind", createMapTab(
                "Wind map", "Arrows show wind direction; longer arrows indicate faster wind.",
                new MapCanvas(pathFinder, MapKind.WIND)));
        tabs.addTab("Precipitation", createMapTab(
                "Precipitation map", "Darker blue indicates a higher chance of precipitation.",
                new MapCanvas(pathFinder, MapKind.PRECIPITATION)));
        tabs.addTab("Flight Path", createMapTab(
                "Safest available flight path", routeDescription,
                new MapCanvas(pathFinder, MapKind.ROUTE, route)));

        mapsDialog.setContentPane(tabs);
        mapsDialog.setSize(850, 790);
        mapsDialog.setLocationRelativeTo(frame);
        mapsDialog.setVisible(true);
    }

    private JPanel createMapTab(String title, String description, MapCanvas mapCanvas) {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBackground(BACKGROUND);
        tab.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));

        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        heading.setForeground(NAVY);
        JLabel help = new JLabel(description);
        help.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        help.setForeground(MUTED);

        JPanel labels = new JPanel(new GridLayout(2, 1, 0, 3));
        labels.setOpaque(false);
        labels.add(heading);
        labels.add(help);
        tab.add(labels, BorderLayout.NORTH);
        tab.add(mapCanvas, BorderLayout.CENTER);
        return tab;
    }

    private JPanel createInputs() {
        JPanel sections = new JPanel(new GridLayout(1, 2, 16, 0));
        sections.setOpaque(false);
        sections.add(createSection("AIRCRAFT", new String[] {
            "Aircraft model",
            "Maximum speed (km/h)",
            "Cruise speed (km/h)",
            "Heading (degrees)"
        }, new JTextField[] {modelField, maxSpeedField, cruiseSpeedField, planeDirectionField}));
        sections.add(createConditionsSection());
        return sections;
    }

    private JPanel createConditionsSection() {
        JPanel section = new JPanel(new BorderLayout(0, 14));
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 229, 236)),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

        JLabel heading = new JLabel("ROUTE & MAP CONDITIONS");
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        heading.setForeground(BLUE);
        section.add(heading, BorderLayout.NORTH);

        JPanel routeFields = new JPanel(new GridLayout(2, 2, 10, 6));
        routeFields.setOpaque(false);
        routeFields.add(createInputRow("Start X (0-63)", startXField));
        routeFields.add(createInputRow("Start Y (0-63)", startYField));
        routeFields.add(createInputRow("Destination X (0-63)", destinationXField));
        routeFields.add(createInputRow("Destination Y (0-63)", destinationYField));

        JPanel details = new JPanel(new GridLayout(8, 1, 0, 6));
        details.setOpaque(false);
        details.add(createInputRow("Selected cell X (0-63)", gridXField));
        details.add(createInputRow("Selected cell Y (0-63)", gridYField));
        details.add(createConditionRow("Elevation", elevationValue));
        details.add(createConditionRow("Wind speed", windSpeedValue));
        details.add(createConditionRow("Wind direction", windDirectionValue));
        details.add(createConditionRow("Precipitation chance", precipitationValue));
        details.add(createConditionRow("Weather intensity", intensityValue));
        details.add(createConditionRow("Temperature", temperatureValue));
        JScrollPane detailsScroll = new JScrollPane(details);
        detailsScroll.setBorder(null);
        detailsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        detailsScroll.getViewport().setOpaque(false);
        detailsScroll.setOpaque(false);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(routeFields, BorderLayout.NORTH);
        center.add(detailsScroll, BorderLayout.CENTER);
        section.add(center, BorderLayout.CENTER);
        return section;
    }

    private static String randomInteriorCoordinate() {
        return Integer.toString(ThreadLocalRandom.current().nextInt(6, MAP_SIZE - 6));
    }

    private JPanel createSection(String title, String[] labels, JTextField[] fields) {
        JPanel section = new JPanel(new BorderLayout(0, 14));
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 229, 236)),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));

        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        heading.setForeground(BLUE);
        section.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(labels.length, 1, 0, 10));
        form.setOpaque(false);
        for (int i = 0; i < labels.length; i++) {
            JPanel row = new JPanel(new BorderLayout(0, 4));
            row.setOpaque(false);
            JLabel label = new JLabel(labels[i]);
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            label.setForeground(MUTED);
            fields[i].setMargin(new Insets(6, 8, 6, 8));
            row.add(label, BorderLayout.NORTH);
            row.add(fields[i], BorderLayout.CENTER);
            form.add(row);
        }
        section.add(form, BorderLayout.CENTER);
        return section;
    }

    private JPanel createInputRow(String labelText, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        label.setForeground(MUTED);
        field.setMargin(new Insets(6, 8, 6, 8));
        row.add(label, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JPanel createConditionRow(String labelText, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(0, 2));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        label.setForeground(MUTED);
        row.add(label, BorderLayout.NORTH);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private JPanel createResults() {
        JPanel results = new JPanel(new BorderLayout(0, 12));
        results.setOpaque(false);

        JLabel heading = new JLabel("FLIGHT SUMMARY");
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        heading.setForeground(BLUE);
        results.add(heading, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 3, 12, 0));
        cards.setOpaque(false);
        cards.add(createResultCard("EFFECTIVE SPEED", effectiveSpeedResult));
        cards.add(createResultCard("EFFECTIVE DIRECTION", effectiveDirectionResult));
        cards.add(createResultCard("WEATHER DANGER", weatherDangerResult));

        JButton calculateButton = new JButton("Calculate");
        calculateButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        calculateButton.setForeground(Color.WHITE);
        calculateButton.setBackground(BLUE);
        calculateButton.setFocusPainted(false);
        calculateButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        calculateButton.addActionListener(event -> calculate());

        JPanel bottom = new JPanel(new BorderLayout(0, 12));
        bottom.setOpaque(false);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        bottom.add(statusLabel, BorderLayout.NORTH);
        bottom.add(cards, BorderLayout.CENTER);
        bottom.add(calculateButton, BorderLayout.SOUTH);
        results.add(bottom, BorderLayout.CENTER);
        return results;
    }

    private JPanel createResultCard(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 229, 236)),
                BorderFactory.createEmptyBorder(14, 12, 14, 12)));

        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setForeground(MUTED);
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JLabel resultValue() {
        JLabel value = new JLabel("—", SwingConstants.CENTER);
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        value.setForeground(NAVY);
        return value;
    }

    private JLabel conditionValue() {
        JLabel value = new JLabel("No map data");
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        value.setForeground(NAVY);
        return value;
    }

    private void calculate() {
        try {
            clearResults();
            statusLabel.setText(" ");
            String model = modelField.getText().trim();
            if (model.isEmpty()) {
                throw new IllegalArgumentException("Enter an aircraft model.");
            }

            double maxSpeed = readNumber(maxSpeedField, "Maximum speed");
            double cruiseSpeed = readNumber(cruiseSpeedField, "Cruise speed");
            double planeDirection = readNumber(planeDirectionField, "Heading");
            int gridX = readGridCoordinate(gridXField, "Map grid X");
            int gridY = readGridCoordinate(gridYField, "Map grid Y");

            if (maxSpeed <= 0 || cruiseSpeed < 0 || cruiseSpeed > maxSpeed) {
                throw new IllegalArgumentException("Cruise speed must be between 0 and the maximum speed.");
            }

            if (gridX < 0 || gridX >= 64 || gridY < 0 || gridY >= 64) {
                throw new IllegalArgumentException("Map grid coordinates must be between 0 and 63.");
            }

            Wind wind = pathFinder.getWindAt(gridX, gridY);
            Weather weather = pathFinder.getWeatherAt(gridX, gridY);
            if (wind == null || weather == null) {
                statusLabel.setText("Wind and weather data are unavailable at map cell (" + gridX + ", " + gridY + ").");
                return;
            }

            DecimalFormat format = new DecimalFormat("0.0");
            elevationValue.setText(format.format(pathFinder.getTerrainAt(gridX, gridY)) + " m");
            windSpeedValue.setText(format.format(wind.getSpeed()) + " km/h");
            windDirectionValue.setText(format.format(wind.getDirection()) + "°");
            precipitationValue.setText(format.format(weather.getCOF()));
            intensityValue.setText(format.format(weather.getIntensity()));
            temperatureValue.setText(format.format(weather.getTemperature()) + " °C");

            Plane plane = new Plane(model, 900, maxSpeed, 1000);
            plane.accelerate(cruiseSpeed);
            plane.changeDirection(planeDirection);
            double[] effectiveSpeedAndDirection = pathFinder.getEffectiveSpeed(plane, wind);

            effectiveSpeedResult.setText(format.format(effectiveSpeedAndDirection[0]) + " km/h");
            effectiveDirectionResult.setText(format.format(effectiveSpeedAndDirection[1]) + "°");
            weatherDangerResult.setText(format.format(weather.getDanger() * 100) + "%");
            statusLabel.setText("Conditions loaded from map cell (" + gridX + ", " + gridY + ").");
        } catch (IllegalArgumentException error) {
            JOptionPane.showMessageDialog(frame, error.getMessage(), "Check flight details", JOptionPane.WARNING_MESSAGE);
        }
    }

    private int readGridCoordinate(JTextField field, String label) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " must be an integer.");
        }
    }

    private void clearResults() {
        effectiveSpeedResult.setText("—");
        effectiveDirectionResult.setText("—");
        weatherDangerResult.setText("—");
        elevationValue.setText("No map data");
        windSpeedValue.setText("No map data");
        windDirectionValue.setText("No map data");
        precipitationValue.setText("No map data");
        intensityValue.setText("No map data");
        temperatureValue.setText("No map data");
    }

    private Route calculateRoute() {
        String model = modelField.getText().trim();
        if (model.isEmpty()) {
            throw new IllegalArgumentException("Enter an aircraft model before viewing the flight path.");
        }
        double maxSpeed = readNumber(maxSpeedField, "Maximum speed");
        double cruiseSpeed = readNumber(cruiseSpeedField, "Cruise speed");
        double heading = readNumber(planeDirectionField, "Heading");
        int startX = readGridCoordinate(startXField, "Route start X");
        int startY = readGridCoordinate(startYField, "Route start Y");
        int destinationX = readGridCoordinate(destinationXField, "Destination X");
        int destinationY = readGridCoordinate(destinationYField, "Destination Y");

        if (maxSpeed <= 0 || cruiseSpeed < 0 || cruiseSpeed > maxSpeed) {
            throw new IllegalArgumentException("Cruise speed must be between 0 and the maximum speed.");
        }
        if (!isMapCoordinate(startX) || !isMapCoordinate(startY)
                || !isMapCoordinate(destinationX) || !isMapCoordinate(destinationY)) {
            throw new IllegalArgumentException("Route coordinates must be between 0 and 63.");
        }

        Plane plane = new Plane(model, 900, maxSpeed, 1000);
        plane.accelerate(cruiseSpeed);
        plane.changeDirection(heading);
        double[][] path = pathFinder.plotRoute(
                plane, startX, startY, destinationX, destinationY, pathFinder.generateDangerMap());
        return path == null ? null : new Route(path);
    }

    private boolean isMapCoordinate(int coordinate) {
        return coordinate >= 0 && coordinate < MAP_SIZE;
    }

    private double readNumber(JTextField field, String label) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private enum MapKind {
        TERRAIN,
        WIND,
        PRECIPITATION,
        ROUTE
    }

    private static final class MapCanvas extends JPanel {
        private static final long serialVersionUID = 1L;

        private final MapKind kind;
        private final double[][] terrain = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] windSpeeds = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] windDirections = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] precipitation = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] routeCoordinates;

        private MapCanvas(PathFinder pathFinder, MapKind kind) {
            this(pathFinder, kind, null);
        }

        private MapCanvas(PathFinder pathFinder, MapKind kind, Route route) {
            this.kind = kind;
            this.routeCoordinates = route == null ? null : route.getRoute();
            setBackground(Color.WHITE);
            setPreferredSize(new java.awt.Dimension(680, 650));
            for (int y = 0; y < MAP_SIZE; y++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    terrain[y][x] = pathFinder.getTerrainAt(x, y);
                    Wind wind = pathFinder.getWindAt(x, y);
                    windSpeeds[y][x] = wind.getSpeed();
                    windDirections[y][x] = wind.getDirection();
                    precipitation[y][x] = pathFinder.getWeatherAt(x, y).getCOF();
                }
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int mapSize = Math.min(getWidth() - 48, getHeight() - 60);
            if (mapSize <= 0) {
                g.dispose();
                return;
            }
            int mapX = (getWidth() - mapSize) / 2;
            int mapY = (getHeight() - mapSize) / 2;

            for (int y = 0; y < MAP_SIZE; y++) {
                int cellTop = mapY + y * mapSize / MAP_SIZE;
                int cellBottom = mapY + (y + 1) * mapSize / MAP_SIZE;
                for (int x = 0; x < MAP_SIZE; x++) {
                    int cellLeft = mapX + x * mapSize / MAP_SIZE;
                    int cellRight = mapX + (x + 1) * mapSize / MAP_SIZE;
                    g.setColor(cellColor(x, y));
                    g.fillRect(cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop);
                }
            }

            if (kind == MapKind.WIND) {
                drawWindArrows(g, mapX, mapY, mapSize);
            } else if (kind == MapKind.ROUTE) {
                drawRoute(g, mapX, mapY, mapSize);
            }

            g.setColor(new Color(45, 61, 77));
            g.drawRect(mapX, mapY, mapSize, mapSize);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString("0", mapX - 10, mapY + 10);
            g.drawString("63", mapX + mapSize - 13, mapY + 10);
            g.drawString("X →", mapX + mapSize - 28, mapY + mapSize + 18);
            g.drawString("Y ↓", mapX - 28, mapY + mapSize + 18);
            g.dispose();
        }

        private Color cellColor(int x, int y) {
            if (kind == MapKind.PRECIPITATION) {
                return interpolate(new Color(239, 247, 255), new Color(12, 74, 145), precipitation[y][x]);
            }
            return terrainColor(terrain[y][x]);
        }

        private void drawRoute(Graphics2D g, int mapX, int mapY, int mapSize) {
            if (routeCoordinates == null || routeCoordinates.length == 0) {
                g.setColor(new Color(22, 43, 68));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.drawString("No safe route to display", mapX + 16, mapY + 24);
                return;
            }

            double[][] points = routeCoordinates;
            int[] xPoints = new int[points.length];
            int[] yPoints = new int[points.length];
            for (int i = 0; i < points.length; i++) {
                xPoints[i] = mapX + (int) ((points[i][0] + 0.5) * mapSize / MAP_SIZE);
                yPoints[i] = mapY + (int) ((points[i][1] + 0.5) * mapSize / MAP_SIZE);
            }

            g.setStroke(new java.awt.BasicStroke(6, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(Color.WHITE);
            g.drawPolyline(xPoints, yPoints, points.length);
            g.setStroke(new java.awt.BasicStroke(3, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(new Color(255, 115, 35));
            g.drawPolyline(xPoints, yPoints, points.length);
            drawEndpoint(g, xPoints[0], yPoints[0], new Color(25, 145, 75), "S");
            drawEndpoint(g, xPoints[points.length - 1], yPoints[points.length - 1],
                    new Color(190, 45, 45), "D");
        }

        private void drawEndpoint(Graphics2D g, int x, int y, Color color, String label) {
            g.setColor(Color.WHITE);
            g.fillOval(x - 9, y - 9, 18, 18);
            g.setColor(color);
            g.fillOval(x - 7, y - 7, 14, 14);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            g.drawString(label, x - 3, y + 3);
        }

        private void drawWindArrows(Graphics2D g, int mapX, int mapY, int mapSize) {
            double cellSize = (double) mapSize / MAP_SIZE;
            for (int y = 2; y < MAP_SIZE; y += 5) {
                for (int x = 2; x < MAP_SIZE; x += 5) {
                    double angle = Math.toRadians(windDirections[y][x]);
                    double length = cellSize * (0.8 + windSpeeds[y][x] / 25.0 * 1.2);
                    double centerX = mapX + (x + 0.5) * cellSize;
                    double centerY = mapY + (y + 0.5) * cellSize;
                    double tipX = centerX + Math.sin(angle) * length;
                    double tipY = centerY - Math.cos(angle) * length;

                    g.setColor(new Color(255, 255, 255, 210));
                    g.setStroke(new java.awt.BasicStroke(3, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    g.drawLine((int) centerX, (int) centerY, (int) tipX, (int) tipY);
                    g.setColor(new Color(22, 54, 86));
                    g.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    g.drawLine((int) centerX, (int) centerY, (int) tipX, (int) tipY);

                    double headLength = Math.max(4, cellSize * 0.6);
                    double leftAngle = angle + Math.toRadians(150);
                    double rightAngle = angle - Math.toRadians(150);
                    g.drawLine((int) tipX, (int) tipY,
                            (int) (tipX + Math.sin(leftAngle) * headLength),
                            (int) (tipY - Math.cos(leftAngle) * headLength));
                    g.drawLine((int) tipX, (int) tipY,
                            (int) (tipX + Math.sin(rightAngle) * headLength),
                            (int) (tipY - Math.cos(rightAngle) * headLength));
                }
            }
        }

        private static Color terrainColor(double elevation) {
            if (elevation < 0) {
                return interpolate(new Color(55, 160, 225), new Color(10, 35, 105),
                        Math.min(1, Math.abs(elevation) / 500.0));
            }
            if (elevation < 1000) {
                return interpolate(new Color(55, 170, 75), new Color(240, 215, 65), elevation / 1000.0);
            }
            return interpolate(new Color(240, 215, 65), new Color(205, 45, 40),
                    Math.min(1, (elevation - 1000) / 1000.0));
        }

        private static Color interpolate(Color low, Color high, double amount) {
            double t = Math.max(0, Math.min(1, amount));
            return new Color(
                    (int) (low.getRed() + t * (high.getRed() - low.getRed())),
                    (int) (low.getGreen() + t * (high.getGreen() - low.getGreen())),
                    (int) (low.getBlue() + t * (high.getBlue() - low.getBlue())));
        }
    }
}
