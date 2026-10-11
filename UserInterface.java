import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class UserInterface {
    private static final Color NAVY = new Color(35, 49, 68);
    private static final Color DEEP_INDIGO = new Color(57, 43, 83);
    private static final Color BLUE = new Color(224, 112, 77);
    private static final Color MUTED = new Color(116, 105, 120);
    private static final Color BACKGROUND = new Color(249, 244, 241);
    private static final Color SURFACE_BORDER = new Color(232, 218, 211);
    private static final Color MAPS_DARK = new Color(48, 39, 56);
    private static final Color MAPS_PANEL = new Color(67, 51, 63);
    private static final Color MAPS_TEXT = new Color(255, 239, 226);
    private static final Color MAPS_MUTED = new Color(207, 184, 180);
    private static final int MAP_SIZE = 64;

    private final JFrame frame = new JFrame("Flight Planner");
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private HomeBackgroundPanel homeBackgroundPanel;
    private final BufferedImage coverImage = loadCoverImage();
    private final BufferedImage homeBackgroundImage = loadImage("horizonbackground.png");
    private final BufferedImage homePlaneImage = loadImage("startscreenplanepng.png");
    private final BufferedImage mapsCardIcon = loadImage("mapicon.png");
    private final BufferedImage statsCardIcon = loadImage("info.png");
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
    private boolean routeDragActive;
    private final Timer routeDragUpdateTimer = new Timer(60, event -> {
        if (routeDragActive) {
            refreshFlightPath();
            calculate(false);
        } else {
            ((Timer) event.getSource()).stop();
        }
    });
    private final Map<PathFinder.RouteStrategy, JButton> mainRouteButtons =
            new EnumMap<>(PathFinder.RouteStrategy.class);
    private final Map<PathFinder.RouteStrategy, List<JButton>> mapRouteButtons =
            new EnumMap<>(PathFinder.RouteStrategy.class);
    private final List<MapCanvas> mapCanvases = new ArrayList<>();
    private PathFinder.RouteStrategy selectedRouteStrategy = PathFinder.RouteStrategy.RECOMMENDED;
    private JPanel mapsPage;

    private UserInterface() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new java.awt.Dimension(680, 640));
        frame.setSize(900, 760);
        frame.setLocationByPlatform(true);

        pages.add(createHomePage(), "home");
        pages.add(createStatsPage(), "stats");
        frame.setContentPane(pages);
        pageLayout.show(pages, "home");
        calculate();
    }

    private int readEndpointCoordinate(JTextField field) {
        try {
            int coordinate = Integer.parseInt(field.getText().trim());
            return isMapCoordinate(coordinate) ? coordinate : -1;
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private JPanel createStatsPage() {
        JPanel content = new PageBackgroundPanel(coverImage);
        content.setLayout(new BorderLayout(0, 18));
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 28, 28));
        content.add(createHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 18));
        body.setOpaque(false);
        body.add(createInputs(), BorderLayout.CENTER);
        body.add(createResults(), BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);
        return content;
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("Label.font", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            UIManager.put("TextField.font", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            UIManager.put("Button.font", new Font(Font.SANS_SERIF, Font.BOLD, 11));
            UIManager.put("TabbedPane.font", new Font(Font.SANS_SERIF, Font.BOLD, 12));
            new UserInterface().frame.setVisible(true);
        });
    }

    private static BufferedImage loadCoverImage() {
        return loadImage("planecoverimage.png");
    }

    private static BufferedImage loadImage(String fileName) {
        try {
            var imageUrl = UserInterface.class.getResource("/" + fileName);
            if (imageUrl == null) {
                throw new IllegalStateException("Could not find " + fileName + " on the application classpath.");
            }
            BufferedImage image = ImageIO.read(imageUrl);
            if (image == null) {
                throw new IllegalStateException(fileName + " is not a supported image.");
            }
            return image;
        } catch (IOException error) {
            throw new IllegalStateException("Could not load " + fileName + ".", error);
        }
    }

    private JPanel createHomePage() {
        homeBackgroundPanel = new HomeBackgroundPanel(homeBackgroundImage, homePlaneImage);
        HomeBackgroundPanel home = homeBackgroundPanel;
        home.setLayout(new BorderLayout(0, 24));
        home.setBorder(BorderFactory.createEmptyBorder(34, 44, 38, 44));

        JLabel eyebrow = new JLabel("FLIGHT PLANNER");
        eyebrow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        eyebrow.setForeground(new Color(235, 237, 255));
        JLabel title = new JLabel("Plan your next journey");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Explore your maps or review flight conditions and stats.");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        subtitle.setForeground(new Color(230, 231, 247));

        JPanel intro = new JPanel(new GridLayout(3, 1, 0, 8));
        intro.setOpaque(false);
        intro.add(eyebrow);
        intro.add(title);
        intro.add(subtitle);
        home.add(intro, BorderLayout.NORTH);

        JPanel choices = new JPanel(new GridLayout(1, 2, 18, 0));
        choices.setOpaque(false);
        choices.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        choices.add(createHomeChoice("MAPS", "View topography, wind, and precipitation.",
                () -> showMaps()));
        choices.add(createHomeChoice("STATS", "Review aircraft details and selected-cell conditions.",
                () -> pageLayout.show(pages, "stats")));
        home.add(choices, BorderLayout.SOUTH);
        return home;
    }

    private JPanel createHomeChoice(String title, String description, Runnable action) {
        HomeChoiceCard choice = new HomeChoiceCard();
        choice.setBackground(new Color(255, 250, 247, 242));
        choice.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));

        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        heading.setForeground(NAVY);
        BufferedImage iconImage = title.equals("MAPS") ? mapsCardIcon : statsCardIcon;
        JLabel icon = new JLabel(new ImageIcon(iconImage.getScaledInstance(42, 42, java.awt.Image.SCALE_SMOOTH)));
        icon.setToolTipText(title + " icon");
        JPanel cardHeading = new JPanel(new BorderLayout());
        cardHeading.setOpaque(false);
        cardHeading.add(heading, BorderLayout.WEST);
        cardHeading.add(icon, BorderLayout.EAST);
        JLabel detail = new JLabel("<html><div style='width:230px'>" + description + "</div></html>");
        detail.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        detail.setForeground(MUTED);
        JButton openButton = new AnimatedButton("OPEN " + title + "  →");
        styleActionButton(openButton, BLUE);
        openButton.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        openButton.addActionListener(event -> action.run());

        choice.add(cardHeading, BorderLayout.NORTH);
        choice.add(detail, BorderLayout.CENTER);
        choice.add(openButton, BorderLayout.SOUTH);
        choice.installHoverListeners();
        return choice;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.setOpaque(false);

        JLabel title = new JLabel("FLIGHT PLANNER");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        title.setForeground(NAVY);
        JButton homeButton = new AnimatedButton("HOME");
        styleActionButton(homeButton, DEEP_INDIGO);
        homeButton.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        homeButton.addActionListener(event -> showHome());
        JButton viewMapsButton = new AnimatedButton("VIEW MAPS");
        viewMapsButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        styleActionButton(viewMapsButton, BLUE);
        viewMapsButton.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        viewMapsButton.addActionListener(event -> showMaps());
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(homeButton);
        actions.add(viewMapsButton);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 2, 0, DEEP_INDIGO),
                BorderFactory.createEmptyBorder(10, 0, 10, 0)));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(actions, BorderLayout.EAST);

        JPanel routeOptions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 5, 2));
        routeOptions.setOpaque(false);
        ButtonGroup routeOptionGroup = new ButtonGroup();
        addRouteOption(routeOptions, routeOptionGroup, "FASTEST PATH", PathFinder.RouteStrategy.FASTEST);
        addRouteOption(routeOptions, routeOptionGroup, "SAFEST PATH", PathFinder.RouteStrategy.SAFEST);
        addRouteOption(routeOptions, routeOptionGroup, "RECOMMENDED PATH", PathFinder.RouteStrategy.RECOMMENDED);

        JLabel subtitle = new JLabel("Wind-adjusted flight performance and weather assessment");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        header.add(titleRow, BorderLayout.NORTH);
        header.add(routeOptions, BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);
        return header;
    }

    private void addRouteOption(JPanel panel, ButtonGroup group, String label,
            PathFinder.RouteStrategy strategy) {
        JButton button = new AnimatedButton(label);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        button.setOpaque(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setSelected(strategy == selectedRouteStrategy);
        styleRouteOption(button, strategy == selectedRouteStrategy);
        mainRouteButtons.put(strategy, button);
        button.addActionListener(event -> {
            selectRouteStrategy(strategy);
        });
        group.add(button);
        panel.add(button);
    }

    private void selectRouteStrategy(PathFinder.RouteStrategy strategy) {
        selectedRouteStrategy = strategy;
        updateMainRouteButtonStyles(mainRouteButtons);
        updateRouteButtonStyles(mapRouteButtons);
        statusLabel.setText("Selected route mode: " + routeLabel(strategy) + ".");
        refreshFlightPath();
    }

    private void updateMainRouteButtonStyles(Map<PathFinder.RouteStrategy, JButton> buttons) {
        for (Map.Entry<PathFinder.RouteStrategy, JButton> entry : buttons.entrySet()) {
            boolean selected = entry.getKey() == selectedRouteStrategy;
            entry.getValue().setSelected(selected);
            styleRouteOption(entry.getValue(), selected);
        }
    }

    private void updateRouteButtonStyles(Map<PathFinder.RouteStrategy, List<JButton>> buttons) {
        for (Map.Entry<PathFinder.RouteStrategy, List<JButton>> entry : buttons.entrySet()) {
            boolean selected = entry.getKey() == selectedRouteStrategy;
            for (JButton button : entry.getValue()) {
                button.setSelected(selected);
                styleRouteOption(button, selected);
            }
        }
    }

    private String routeLabel(PathFinder.RouteStrategy strategy) {
        switch (strategy) {
            case FASTEST:
                return "FASTEST PATH";
            case SAFEST:
                return "SAFEST PATH";
            case RECOMMENDED:
                return "RECOMMENDED PATH";
            default:
                throw new IllegalStateException("Unsupported route strategy: " + strategy);
        }
    }

    private void styleRouteOption(JButton button, boolean selected) {
        button.setBackground(selected ? DEEP_INDIGO : new Color(239, 231, 235));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? DEEP_INDIGO : SURFACE_BORDER, 1, true),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        button.setForeground(Color.WHITE);
        if (!selected) {
            button.setForeground(NAVY);
        }
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private void showMaps() {
        if (mapsPage != null) {
            pages.remove(mapsPage);
        }

        mapRouteButtons.clear();
        mapCanvases.clear();
        Route route = calculateSelectedRoute();

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(MAPS_PANEL);
        tabs.setForeground(MAPS_TEXT);
        tabs.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tabs.addTab("Topographic", createMapTab(
                "Elevation map", "Green is low land, yellow/red is higher terrain, blue is below sea level; selected route is overlaid.",
                createMapCanvas(MapKind.TERRAIN, route)));
        tabs.addTab("Wind", createMapTab(
                "Wind map", "Green is land, blue is ocean, and the gray overlay darkens with wind speed; arrows show direction.",
                createMapCanvas(MapKind.WIND, route)));
        tabs.addTab("Precipitation", createMapTab(
                "Precipitation map", "Green is land, blue is ocean, and the blue overlay darkens with precipitation chance.",
                createMapCanvas(MapKind.PRECIPITATION, route)));
        tabs.addTab("Danger", createMapTab(
                "Weather danger map", "Green is lower danger, yellow is moderate, and red is higher weather danger.",
                createMapCanvas(MapKind.DANGER, route)));

        mapsPage = new JPanel(new BorderLayout(0, 12));
        mapsPage.setBackground(MAPS_DARK);
        mapsPage.setBorder(BorderFactory.createEmptyBorder(20, 24, 24, 24));
        JButton backButton = new AnimatedButton("← HOME");
        styleActionButton(backButton, DEEP_INDIGO);
        backButton.addActionListener(event -> showHome());
        JLabel title = new JLabel("FLIGHT MAPS");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        title.setForeground(MAPS_TEXT);
        JPanel mapHeader = new JPanel(new BorderLayout());
        mapHeader.setOpaque(false);
        mapHeader.add(title, BorderLayout.WEST);
        mapHeader.add(backButton, BorderLayout.EAST);
        mapsPage.add(mapHeader, BorderLayout.NORTH);
        mapsPage.add(tabs, BorderLayout.CENTER);
        pages.add(mapsPage, "maps");
        pageLayout.show(pages, "maps");
        pages.revalidate();
        pages.repaint();
    }

    private void showHome() {
        homeBackgroundPanel.restartAnimation();
        pageLayout.show(pages, "home");
    }

    private JPanel createMapTab(String title, String description, MapCanvas mapCanvas) {
        JPanel tab = new JPanel(new BorderLayout(0, 8));
        tab.setBackground(MAPS_PANEL);
        tab.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));

        JLabel heading = new JLabel(title);
        heading.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        heading.setForeground(MAPS_TEXT);
        JLabel help = new JLabel(description);
        help.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        help.setForeground(MAPS_MUTED);

        JPanel labels = new JPanel(new GridLayout(2, 1, 0, 3));
        labels.setOpaque(false);
        labels.add(heading);
        labels.add(help);
        JPanel routeOptions = createMapRouteOptions();
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.add(labels, BorderLayout.NORTH);
        header.add(routeOptions, BorderLayout.SOUTH);
        tab.add(header, BorderLayout.NORTH);
        tab.add(mapCanvas, BorderLayout.CENTER);
        return tab;
    }

    private MapCanvas createMapCanvas(MapKind kind, Route route) {
        MapCanvas canvas = new MapCanvas(
                pathFinder, kind, route,
                readEndpointCoordinate(startXField), readEndpointCoordinate(startYField),
                readEndpointCoordinate(destinationXField), readEndpointCoordinate(destinationYField),
                this::handleRouteEndpointChange);
        mapCanvases.add(canvas);
        return canvas;
    }

    private JPanel createMapRouteOptions() {
        JPanel routeOptions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 0));
        routeOptions.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        addMapRouteOption(routeOptions, group, PathFinder.RouteStrategy.FASTEST);
        addMapRouteOption(routeOptions, group, PathFinder.RouteStrategy.SAFEST);
        addMapRouteOption(routeOptions, group, PathFinder.RouteStrategy.RECOMMENDED);
        return routeOptions;
    }

    private void addMapRouteOption(JPanel panel, ButtonGroup group, PathFinder.RouteStrategy strategy) {
        JButton button = new AnimatedButton(routeLabel(strategy));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.setSelected(strategy == selectedRouteStrategy);
        styleRouteOption(button, strategy == selectedRouteStrategy);
        mapRouteButtons.computeIfAbsent(strategy, ignored -> new ArrayList<>()).add(button);
        group.add(button);
        panel.add(button);
        button.addActionListener(event -> selectRouteStrategy(strategy));
    }

    private Route calculateSelectedRoute() {
        try {
            Route route = calculateRoute(selectedRouteStrategy);
            if (route != null && selectedRouteStrategy == PathFinder.RouteStrategy.RECOMMENDED
                    && hasDangerAboveLimit(route)) {
                statusLabel.setText("No route met the 10% danger limit; showing the safest available route.");
            }
            return route;
        } catch (IllegalArgumentException error) {
            statusLabel.setText(error.getMessage());
            return null;
        }
    }

    private boolean hasDangerAboveLimit(Route route) {
        for (double[] coordinate : route.getRoute()) {
            Weather weather = pathFinder.getWeatherAt((int) coordinate[0], (int) coordinate[1]);
            if (weather.getDanger() > PathFinder.RECOMMENDED_MAX_DANGER) {
                return true;
            }
        }
        return false;
    }

    private void refreshFlightPath() {
        if (mapsPage == null || !mapsPage.isShowing()) {
            return;
        }
        Route route = calculateSelectedRoute();
        for (MapCanvas canvas : mapCanvases) {
            canvas.setRoute(route);
            canvas.setEndpoints(
                    readEndpointCoordinate(startXField), readEndpointCoordinate(startYField),
                    readEndpointCoordinate(destinationXField), readEndpointCoordinate(destinationYField));
        }
        updateRouteButtonStyles(mapRouteButtons);
    }

    private void handleRouteEndpointChange(Endpoint endpoint, int x, int y, boolean dragging) {
        JTextField xField = endpoint == Endpoint.START ? startXField : destinationXField;
        JTextField yField = endpoint == Endpoint.START ? startYField : destinationYField;
        xField.setText(Integer.toString(x));
        yField.setText(Integer.toString(y));
        gridXField.setText(Integer.toString(x));
        gridYField.setText(Integer.toString(y));
        routeDragActive = dragging;
        if (dragging) {
            if (!routeDragUpdateTimer.isRunning()) {
                routeDragUpdateTimer.start();
            }
        } else {
            routeDragUpdateTimer.stop();
            refreshFlightPath();
            calculate(false);
        }
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
        JPanel section = new SurfacePanel(new BorderLayout(0, 14), 18);
        section.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

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

    private static final class PageBackgroundPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final transient BufferedImage image;

        private PageBackgroundPanel(BufferedImage image) {
            this.image = image;
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

            int lowerHalfY = getHeight() / 2;
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, getWidth(), lowerHalfY);
            g.setColor(DEEP_INDIGO);
            g.fillRect(0, lowerHalfY, getWidth(), getHeight() - lowerHalfY);

            double scale = (double) getWidth() / image.getWidth();
            int imageWidth = getWidth();
            int imageHeight = (int) Math.round(image.getHeight() * scale);
            int lowerHalfHeight = getHeight() - lowerHalfY;
            int imageY = lowerHalfY + (lowerHalfHeight - imageHeight) / 2;

            g.clipRect(0, lowerHalfY, getWidth(), lowerHalfHeight);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f));
            g.drawImage(image, 0, imageY, imageWidth, imageHeight, null);
            g.dispose();
        }
    }

    private static final class HomeBackgroundPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int ANIMATION_DURATION_MS = 1700;
        private final transient BufferedImage backgroundImage;
        private final transient BufferedImage planeImage;
        private final Timer animationTimer;
        private long animationStart;
        private transient BufferedImage scaledBackground;
        private transient BufferedImage scaledPlane;
        private int cachedWidth = -1;
        private int cachedHeight = -1;

        private HomeBackgroundPanel(BufferedImage backgroundImage, BufferedImage planeImage) {
            this.backgroundImage = backgroundImage;
            this.planeImage = planeImage;
            setOpaque(true);
            animationTimer = new Timer(16, event -> {
                repaint();
                if (System.nanoTime() - animationStart >= ANIMATION_DURATION_MS * 1_000_000L) {
                    ((Timer) event.getSource()).stop();
                }
            });
            restartAnimation();
        }

        private void restartAnimation() {
            animationStart = System.nanoTime();
            if (!animationTimer.isRunning()) {
                animationTimer.start();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }

            updateScaledImages(width, height);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(DEEP_INDIGO);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f));
            g.drawImage(scaledBackground, 0, 0, null);

            double progress = Math.min(1.0,
                    (System.nanoTime() - animationStart) / (ANIMATION_DURATION_MS * 1_000_000.0));
            double easedProgress = progress < 0.5
                    ? 4 * progress * progress * progress
                    : 1 - Math.pow(-2 * progress + 2, 3) / 2;
            int planeWidth = scaledPlane.getWidth();
            int planeHeight = scaledPlane.getHeight();
            int finalX = (int) (width * 0.58 - planeWidth / 2.0);
            int finalY = (int) (height * 0.48 - planeHeight / 2.0);
            int startX = -planeWidth + (int) (planeWidth * 0.18);
            int startY = height + (int) (planeHeight * 0.08);
            int planeX = startX + (int) ((finalX - startX) * easedProgress);
            int planeY = startY + (int) ((finalY - startY) * easedProgress);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.40f));
            g.drawImage(scaledPlane, planeX, planeY, null);
            g.dispose();
        }

        private void updateScaledImages(int width, int height) {
            if (width == cachedWidth && height == cachedHeight) {
                return;
            }

            cachedWidth = width;
            cachedHeight = height;

            scaledBackground = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D backgroundGraphics = scaledBackground.createGraphics();
            backgroundGraphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            backgroundGraphics.drawImage(backgroundImage, 0, 0, width, height, null);
            backgroundGraphics.dispose();

            int planeSize = Math.max(1, Math.min((int) (width * 0.40), (int) (height * 0.46)));
            scaledPlane = new BufferedImage(planeSize, planeSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D planeGraphics = scaledPlane.createGraphics();
            planeGraphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            planeGraphics.drawImage(planeImage, 0, 0, planeSize, planeSize, null);
            planeGraphics.dispose();
        }
    }

    private static final class HomeChoiceCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final double HOVER_SCALE = 1.045;
        private static final int CORNER_RADIUS = 20;
        private static final int RESTING_SURFACE_INSET = 6;
        private double scale = 1.0;
        private double targetScale = 1.0;
        private Timer hoverTimer;
        private final transient MouseAdapter hoverListener;

        private HomeChoiceCard() {
            super(new BorderLayout(0, 12));
            setOpaque(false);
            hoverListener = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    setHovered(true);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    java.awt.Point point = SwingUtilities.convertPoint(
                            event.getComponent(), event.getPoint(), HomeChoiceCard.this);
                    setHovered(contains(point));
                }
            };
        }

        private void installHoverListeners() {
            addMouseListenerToTree(this, hoverListener);
        }

        private static void addMouseListenerToTree(
                java.awt.Container container, MouseAdapter listener) {
            container.addMouseListener(listener);
            for (java.awt.Component child : container.getComponents()) {
                child.addMouseListener(listener);
                if (child instanceof java.awt.Container childContainer) {
                    for (java.awt.Component grandchild : childContainer.getComponents()) {
                        if (grandchild instanceof java.awt.Container grandchildContainer) {
                            addMouseListenerToTree(grandchildContainer, listener);
                        } else {
                            grandchild.addMouseListener(listener);
                        }
                    }
                }
            }
        }

        private void setHovered(boolean hovered) {
            targetScale = hovered ? HOVER_SCALE : 1.0;
            if (hoverTimer == null) {
                hoverTimer = new Timer(16, event -> {
                    double difference = targetScale - scale;
                    if (Math.abs(difference) < 0.002) {
                        scale = targetScale;
                        hoverTimer.stop();
                    } else {
                        scale += difference * 0.28;
                    }
                    repaint();
                });
            }
            if (!hoverTimer.isRunning()) {
                hoverTimer.start();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int inset = (int) Math.round(
                    RESTING_SURFACE_INSET * (HOVER_SCALE - scale) / (HOVER_SCALE - 1));
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRoundRect(
                    inset, inset, getWidth() - inset * 2 - 1, getHeight() - inset * 2 - 1,
                    CORNER_RADIUS, CORNER_RADIUS);
            g.dispose();
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            int inset = (int) Math.round(
                    RESTING_SURFACE_INSET * (HOVER_SCALE - scale) / (HOVER_SCALE - 1));
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SURFACE_BORDER);
            g.drawRoundRect(
                    inset, inset, getWidth() - inset * 2 - 1, getHeight() - inset * 2 - 1,
                    CORNER_RADIUS, CORNER_RADIUS);
            g.dispose();
        }

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.translate(getWidth() * (1 - scale) / 2, getHeight() * (1 - scale) / 2);
            g.scale(scale, scale);
            super.paintChildren(g);
            g.dispose();
        }
    }

    private static final class AnimatedButton extends JButton {
        private static final long serialVersionUID = 1L;
        private static final double RESTING_SCALE = 0.94;
        private double scale = RESTING_SCALE;
        private double targetScale = RESTING_SCALE;
        private Timer hoverTimer;

        private AnimatedButton(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    animateTo(1.0);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    animateTo(RESTING_SCALE);
                }
            });
        }

        private void animateTo(double nextScale) {
            targetScale = nextScale;
            if (hoverTimer == null) {
                hoverTimer = new Timer(16, event -> {
                    double difference = targetScale - scale;
                    if (Math.abs(difference) < 0.002) {
                        scale = targetScale;
                        hoverTimer.stop();
                    } else {
                        scale += difference * 0.35;
                    }
                    repaint();
                });
            }
            if (!hoverTimer.isRunning()) {
                hoverTimer.start();
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int buttonWidth = (int) Math.round(getWidth() * scale);
            int buttonHeight = (int) Math.round(getHeight() * scale);
            int x = (getWidth() - buttonWidth) / 2;
            int y = (getHeight() - buttonHeight) / 2;
            g.setColor(getBackground());
            g.fillRoundRect(x, y, buttonWidth, buttonHeight, 12, 12);
            g.setColor(getModel().isSelected() ? getBackground() : SURFACE_BORDER);
            g.drawRoundRect(x, y, buttonWidth - 1, buttonHeight - 1, 12, 12);

            g.translate(getWidth() * (1.0 - scale) / 2.0, getHeight() * (1.0 - scale) / 2.0);
            g.scale(scale, scale);
            super.paintComponent(g);
            g.dispose();
        }
    }

    private static class SurfacePanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final int cornerRadius;

        private SurfacePanel(java.awt.LayoutManager layout, int cornerRadius) {
            super(layout);
            this.cornerRadius = cornerRadius;
            setOpaque(false);
            setBackground(new Color(255, 250, 247, 242));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
            g.dispose();
        }

        @Override
        protected void paintBorder(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SURFACE_BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, cornerRadius, cornerRadius);
            g.dispose();
        }
    }

    private JPanel createSection(String title, String[] labels, JTextField[] fields) {
        JPanel section = new SurfacePanel(new BorderLayout(0, 14), 18);
        section.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

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
            styleTextField(fields[i]);
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
        styleTextField(field);
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

        JButton calculateButton = new AnimatedButton("Calculate");
        calculateButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        styleActionButton(calculateButton, BLUE);
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
        JPanel card = new SurfacePanel(new BorderLayout(0, 12), 14);
        card.setBorder(BorderFactory.createEmptyBorder(15, 13, 15, 13));

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

    private void styleTextField(JTextField field) {
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        field.setForeground(NAVY);
        field.setBackground(new Color(255, 251, 249));
        field.setCaretColor(BLUE);
        field.setMargin(new Insets(8, 10, 8, 10));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SURFACE_BORDER, 1, true),
                BorderFactory.createEmptyBorder(1, 2, 1, 2)));
    }

    private void styleActionButton(JButton button, Color background) {
        button.setForeground(Color.WHITE);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private JLabel conditionValue() {
        JLabel value = new JLabel("No map data");
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        value.setForeground(NAVY);
        return value;
    }

    private void calculate() {
        calculate(true);
    }

    private void calculate(boolean showValidationDialog) {
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

            validateSpeeds(maxSpeed, cruiseSpeed);

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
            if (showValidationDialog) {
                JOptionPane.showMessageDialog(
                        frame, error.getMessage(), "Check flight details", JOptionPane.WARNING_MESSAGE);
            } else {
                statusLabel.setText(error.getMessage());
            }
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

    private Route calculateRoute(PathFinder.RouteStrategy strategy) {
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

        validateSpeeds(maxSpeed, cruiseSpeed);
        if (!isMapCoordinate(startX) || !isMapCoordinate(startY)
                || !isMapCoordinate(destinationX) || !isMapCoordinate(destinationY)) {
            throw new IllegalArgumentException("Route coordinates must be between 0 and 63.");
        }

        Plane plane = new Plane(model, 900, maxSpeed, 1000);
        plane.accelerate(cruiseSpeed);
        plane.changeDirection(heading);
        double[][] path = pathFinder.plotRoute(
                plane, startX, startY, destinationX, destinationY, pathFinder.generateDangerMap(), strategy);
        return path == null ? null : new Route(path);
    }

    private boolean isMapCoordinate(int coordinate) {
        return coordinate >= 0 && coordinate < MAP_SIZE;
    }

    private void validateSpeeds(double maxSpeed, double cruiseSpeed) {
        if (maxSpeed <= 0) {
            throw new IllegalArgumentException(
                    "Maximum speed must be greater than 0 km/h (entered " + maxSpeed + " km/h).");
        }
        if (cruiseSpeed < 0 || cruiseSpeed > maxSpeed) {
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT,
                    "Cruise speed %.1f km/h is outside the allowed range [0, %.1f] km/h.",
                    cruiseSpeed, maxSpeed));
        }
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
        DANGER
    }

    private enum Endpoint {
        START,
        DESTINATION
    }

    @FunctionalInterface
    private interface EndpointDragListener {
        void endpointMoved(Endpoint endpoint, int x, int y, boolean dragging);
    }

    private static final class MapBounds {
        private final int x;
        private final int y;
        private final int size;

        private MapBounds(int x, int y, int size) {
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    private static final class MapCanvas extends JPanel {
        private static final long serialVersionUID = 1L;

        private final MapKind kind;
        private final transient EndpointDragListener endpointDragListener;
        private final double[][] terrain = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] windSpeeds = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] windDirections = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] precipitation = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] weatherDanger = new double[MAP_SIZE][MAP_SIZE];
        private double[][] routeCoordinates;
        private int startX;
        private int startY;
        private int destinationX;
        private int destinationY;
        private Endpoint draggedEndpoint;
        private final double minimumElevation;
        private final double averageElevation;
        private final double maximumElevation;
        private final double minimumWindSpeed;
        private final double averageWindSpeed;
        private final double maximumWindSpeed;
        private final double minimumPrecipitation;
        private final double averagePrecipitation;
        private final double maximumPrecipitation;
        private final double minimumDanger;
        private final double averageDanger;
        private final double maximumDanger;

        private MapCanvas(
                PathFinder pathFinder, MapKind kind, Route route,
                int startX, int startY, int destinationX, int destinationY,
                EndpointDragListener endpointDragListener) {
            this.kind = kind;
            this.routeCoordinates = route == null ? null : route.getRoute();
            this.startX = startX;
            this.startY = startY;
            this.destinationX = destinationX;
            this.destinationY = destinationY;
            this.endpointDragListener = endpointDragListener;
            setBackground(MAPS_DARK);
            setPreferredSize(new java.awt.Dimension(680, 650));
            installEndpointDragging();
            double elevationSum = 0;
            double windSpeedSum = 0;
            double precipitationSum = 0;
            double dangerSum = 0;
            double lowestElevation = Double.POSITIVE_INFINITY;
            double highestElevation = Double.NEGATIVE_INFINITY;
            double lowestWindSpeed = Double.POSITIVE_INFINITY;
            double highestWindSpeed = Double.NEGATIVE_INFINITY;
            double lowestPrecipitation = Double.POSITIVE_INFINITY;
            double highestPrecipitation = Double.NEGATIVE_INFINITY;
            double lowestDanger = Double.POSITIVE_INFINITY;
            double highestDanger = Double.NEGATIVE_INFINITY;
            for (int y = 0; y < MAP_SIZE; y++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    terrain[y][x] = pathFinder.getTerrainAt(x, y);
                    Wind wind = pathFinder.getWindAt(x, y);
                    windSpeeds[y][x] = wind.getSpeed();
                    windDirections[y][x] = wind.getDirection();
                    Weather weather = pathFinder.getWeatherAt(x, y);
                    precipitation[y][x] = weather.getCOF();
                    weatherDanger[y][x] = weather.getDanger();
                    elevationSum += terrain[y][x];
                    windSpeedSum += windSpeeds[y][x];
                    precipitationSum += precipitation[y][x];
                    dangerSum += weatherDanger[y][x];
                    lowestElevation = Math.min(lowestElevation, terrain[y][x]);
                    highestElevation = Math.max(highestElevation, terrain[y][x]);
                    lowestWindSpeed = Math.min(lowestWindSpeed, windSpeeds[y][x]);
                    highestWindSpeed = Math.max(highestWindSpeed, windSpeeds[y][x]);
                    lowestPrecipitation = Math.min(lowestPrecipitation, precipitation[y][x]);
                    highestPrecipitation = Math.max(highestPrecipitation, precipitation[y][x]);
                    lowestDanger = Math.min(lowestDanger, weatherDanger[y][x]);
                    highestDanger = Math.max(highestDanger, weatherDanger[y][x]);
                }
            }
            int cellCount = MAP_SIZE * MAP_SIZE;
            minimumElevation = lowestElevation;
            averageElevation = elevationSum / cellCount;
            maximumElevation = highestElevation;
            minimumWindSpeed = lowestWindSpeed;
            averageWindSpeed = windSpeedSum / cellCount;
            maximumWindSpeed = highestWindSpeed;
            minimumPrecipitation = lowestPrecipitation;
            averagePrecipitation = precipitationSum / cellCount;
            maximumPrecipitation = highestPrecipitation;
            minimumDanger = lowestDanger;
            averageDanger = dangerSum / cellCount;
            maximumDanger = highestDanger;
        }

        private void installEndpointDragging() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    MapBounds bounds = mapBounds();
                    draggedEndpoint = bounds.size > 0
                            ? endpointAt(event.getX(), event.getY(), bounds)
                            : null;
                    if (draggedEndpoint != null) {
                        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (draggedEndpoint != null) {
                        Endpoint movedEndpoint = draggedEndpoint;
                        int movedX = endpointX(movedEndpoint);
                        int movedY = endpointY(movedEndpoint);
                        draggedEndpoint = null;
                        setCursor(java.awt.Cursor.getDefaultCursor());
                        endpointDragListener.endpointMoved(movedEndpoint, movedX, movedY, false);
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent event) {
                    if (draggedEndpoint == null) {
                        return;
                    }
                    MapBounds bounds = mapBounds();
                    if (bounds.size <= 0) {
                        return;
                    }
                    int x = clamp((event.getX() - bounds.x) * MAP_SIZE / bounds.size, 0, MAP_SIZE - 1);
                    int y = clamp((event.getY() - bounds.y) * MAP_SIZE / bounds.size, 0, MAP_SIZE - 1);
                    setEndpoint(draggedEndpoint, x, y);
                    repaint();
                    endpointDragListener.endpointMoved(draggedEndpoint, x, y, true);
                }

                @Override
                public void mouseMoved(MouseEvent event) {
                    MapBounds bounds = mapBounds();
                    boolean overEndpoint = endpointAt(event.getX(), event.getY(), bounds) != null;
                    setCursor(java.awt.Cursor.getPredefinedCursor(
                            overEndpoint ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
                }
            });
        }

        private Endpoint endpointAt(int mouseX, int mouseY, MapBounds bounds) {
            Endpoint nearest = null;
            double nearestDistance = 12 * 12;
            for (Endpoint endpoint : Endpoint.values()) {
                int x = endpointX(endpoint);
                int y = endpointY(endpoint);
                if (!isValidMapCoordinate(x) || !isValidMapCoordinate(y)) {
                    continue;
                }
                int markerX = bounds.x + (int) ((x + 0.5) * bounds.size / MAP_SIZE);
                int markerY = bounds.y + (int) ((y + 0.5) * bounds.size / MAP_SIZE);
                double distance = Math.pow(mouseX - markerX, 2) + Math.pow(mouseY - markerY, 2);
                if (distance <= nearestDistance) {
                    nearest = endpoint;
                    nearestDistance = distance;
                }
            }
            return nearest;
        }

        private int endpointX(Endpoint endpoint) {
            return endpoint == Endpoint.START ? startX : destinationX;
        }

        private int endpointY(Endpoint endpoint) {
            return endpoint == Endpoint.START ? startY : destinationY;
        }

        private void setEndpoint(Endpoint endpoint, int x, int y) {
            if (endpoint == Endpoint.START) {
                startX = x;
                startY = y;
            } else {
                destinationX = x;
                destinationY = y;
            }
        }

        private MapBounds mapBounds() {
            int footerHeight = 100;
            int size = Math.min(getWidth() - 48, getHeight() - footerHeight - 40);
            return new MapBounds((getWidth() - size) / 2,
                    Math.max(8, (getHeight() - footerHeight - size) / 2), size);
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        private static boolean isValidMapCoordinate(int coordinate) {
            return coordinate >= 0 && coordinate < MAP_SIZE;
        }

        private void setRoute(Route route) {
            routeCoordinates = route == null ? null : route.getRoute();
            repaint();
        }

        private void setEndpoints(int startX, int startY, int destinationX, int destinationY) {
            this.startX = startX;
            this.startY = startY;
            this.destinationX = destinationX;
            this.destinationY = destinationY;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            MapBounds bounds = mapBounds();
            int mapSize = bounds.size;
            if (mapSize <= 0) {
                g.dispose();
                return;
            }
            int mapX = bounds.x;
            int mapY = bounds.y;

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
            }
            if (routeCoordinates != null && routeCoordinates.length > 0) {
                drawRoute(g, mapX, mapY, mapSize);
            }
            if (isValidMapCoordinate(startX) && isValidMapCoordinate(startY)) {
                drawEndpoint(g, mapX + (int) ((startX + 0.5) * mapSize / MAP_SIZE),
                        mapY + (int) ((startY + 0.5) * mapSize / MAP_SIZE),
                        new Color(25, 145, 75), "S");
            }
            if (isValidMapCoordinate(destinationX) && isValidMapCoordinate(destinationY)) {
                drawEndpoint(g, mapX + (int) ((destinationX + 0.5) * mapSize / MAP_SIZE),
                        mapY + (int) ((destinationY + 0.5) * mapSize / MAP_SIZE),
                        new Color(190, 45, 45), "D");
            }

            g.setColor(new Color(221, 194, 187));
            g.drawRect(mapX, mapY, mapSize, mapSize);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString("0", mapX - 10, mapY + 10);
            g.drawString("63", mapX + mapSize - 13, mapY + 10);
            g.drawString("X →", mapX, mapY + mapSize + 18);
            g.drawString("Y ↓", mapX - 28, mapY + mapSize + 18);
            drawStatsBox(g);
            g.dispose();
        }

        private void drawStatsBox(Graphics2D g) {
            int boxWidth = 270;
            int boxHeight = 82;
            int x = getWidth() - boxWidth - 12;
            int y = getHeight() - boxHeight - 8;

            g.setColor(new Color(53, 42, 57, 238));
            g.fillRoundRect(x, y, boxWidth, boxHeight, 16, 16);
            g.setColor(new Color(180, 122, 111));
            g.drawRoundRect(x, y, boxWidth, boxHeight, 16, 16);
            g.setColor(MAPS_TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.drawString("SELECTED ROUTE", x + 12, y + 16);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString(routeSummary(), x + 12, y + 31);
            g.setColor(new Color(103, 77, 84));
            g.drawLine(x + 12, y + 39, x + boxWidth - 12, y + 39);
            g.setColor(MAPS_TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            g.drawString(statsTitle(), x + 12, y + 53);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            g.drawString(statsSummary(), x + 12, y + 66);
            g.drawString(statsDetail(), x + 12, y + 77);
        }

        private String statsTitle() {
            switch (kind) {
                case TERRAIN:
                    return "ELEVATION STATS";
                case WIND:
                    return "WIND SPEED STATS";
                case PRECIPITATION:
                    return "PRECIPITATION STATS";
                case DANGER:
                    return "WEATHER DANGER STATS";
                default:
                    throw new IllegalStateException("Unsupported map kind: " + kind);
            }
        }

        private String statsSummary() {
            switch (kind) {
                case TERRAIN:
                    return String.format(java.util.Locale.ROOT, "Min %.0f m   Avg %.0f m   Max %.0f m",
                            minimumElevation, averageElevation, maximumElevation);
                case WIND:
                    return String.format(java.util.Locale.ROOT, "Min %.1f   Avg %.1f   Max %.1f km/h",
                            minimumWindSpeed, averageWindSpeed, maximumWindSpeed);
                case PRECIPITATION:
                    return String.format(java.util.Locale.ROOT, "Min %.0f%%   Avg %.0f%%   Max %.0f%%",
                            minimumPrecipitation * 100, averagePrecipitation * 100, maximumPrecipitation * 100);
                case DANGER:
                    return String.format(java.util.Locale.ROOT, "Min %.0f%%   Avg %.0f%%   Max %.0f%%",
                            minimumDanger * 100, averageDanger * 100, maximumDanger * 100);
                default:
                    throw new IllegalStateException("Unsupported map kind: " + kind);
            }
        }

        private String statsDetail() {
            switch (kind) {
                case TERRAIN:
                    return "Generated terrain elevation across 4,096 cells";
                case WIND:
                    return "Generated wind field across 4,096 cells";
                case PRECIPITATION:
                    return "Generated precipitation chance across 4,096 cells";
                case DANGER:
                    return "Generated weather danger across 4,096 cells";
                default:
                    throw new IllegalStateException("Unsupported map kind: " + kind);
            }
        }

        private String routeSummary() {
            if (routeCoordinates == null || routeCoordinates.length == 0) {
                return "Selected route: unavailable";
            }
            double distance = 0;
            double danger = 0;
            for (int i = 0; i < routeCoordinates.length; i++) {
                int x = (int) routeCoordinates[i][0];
                int y = (int) routeCoordinates[i][1];
                danger += weatherDanger[y][x];
                if (i > 0) {
                    double dx = routeCoordinates[i][0] - routeCoordinates[i - 1][0];
                    double dy = routeCoordinates[i][1] - routeCoordinates[i - 1][1];
                    distance += Math.hypot(dx, dy);
                }
            }
            return String.format(java.util.Locale.ROOT, "Route %.1f cells   Avg danger on path %.0f%%",
                    distance, danger / routeCoordinates.length * 100);
        }

        private Color cellColor(int x, int y) {
            if (kind == MapKind.DANGER) {
                double danger = Math.max(0, Math.min(1, weatherDanger[y][x]));
                Color dangerShade;
                if (danger <= 0.05) {
                    dangerShade = interpolate(
                            new Color(112, 177, 118), new Color(244, 205, 96), danger / 0.05);
                } else if (danger <= 0.10) {
                    dangerShade = interpolate(
                            new Color(244, 205, 96), new Color(198, 56, 54), (danger - 0.05) / 0.05);
                } else {
                    dangerShade = interpolate(new Color(198, 56, 54), Color.BLACK, (danger - 0.10) / 0.90);
                }
                return blend(surfaceColor(x, y), dangerShade, 0.65);
            }
            if (kind == MapKind.PRECIPITATION) {
                Color precipitationShade = interpolate(
                        new Color(239, 247, 255), new Color(12, 74, 145), precipitation[y][x]);
                return blend(surfaceColor(x, y), precipitationShade, 0.65);
            }
            if (kind == MapKind.WIND) {
                double speedRange = maximumWindSpeed - minimumWindSpeed;
                double intensity = speedRange == 0
                        ? 0
                        : (windSpeeds[y][x] - minimumWindSpeed) / speedRange;
                Color windShade = interpolate(new Color(225, 225, 225), new Color(45, 45, 45), intensity);
                return blend(surfaceColor(x, y), windShade, 0.5);
            }
            return terrainColor(terrain[y][x]);
        }

        private Color surfaceColor(int x, int y) {
            return terrain[y][x] < 0
                    ? new Color(55, 145, 205)
                    : new Color(83, 158, 92);
        }

        private void drawRoute(Graphics2D g, int mapX, int mapY, int mapSize) {
            if (routeCoordinates == null || routeCoordinates.length == 0) {
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
            g.setColor(new Color(232, 112, 70));
            g.drawPolyline(xPoints, yPoints, points.length);
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

        private static Color blend(Color base, Color overlay, double opacity) {
            double alpha = Math.max(0, Math.min(1, opacity));
            return new Color(
                    (int) Math.round(base.getRed() * (1 - alpha) + overlay.getRed() * alpha),
                    (int) Math.round(base.getGreen() * (1 - alpha) + overlay.getGreen() * alpha),
                    (int) Math.round(base.getBlue() * (1 - alpha) + overlay.getBlue() * alpha));
        }
    }
}
