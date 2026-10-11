import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFrame;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;

public class UserInterface {
    private static final Color NAVY = Color.BLACK;
    private static final Color ROUTE_BLUE = new Color(38, 112, 230);
    private static final Color MUTED = new Color(92, 92, 92);
    private static final Color BACKGROUND = new Color(248, 248, 248);
    private static final int MAP_SIZE = 64;

    private final JFrame frame = new JFrame("Flight Planner");
    private final JComboBox<AircraftPreset> aircraftPreset = new JComboBox<>(aircraftPresets());
    private final PathFinder pathFinder = new PathFinder();
    private final GptProtoChatbot gptProtoChatbot = new GptProtoChatbot();
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea chatTranscript = new JTextArea();
    private final JTextField chatInput = new JTextField();
    private final Map<PathFinder.RouteStrategy, JButton> mainRouteButtons =
            new EnumMap<>(PathFinder.RouteStrategy.class);
    private final Map<DataSource, JButton> dataSourceButtons = new EnumMap<>(DataSource.class);
    private final List<MapCanvas> mapCanvases = new ArrayList<>();
    private final Map<MapKind, MapCanvas> mapCanvasByKind = new EnumMap<>(MapKind.class);
    private final Map<MapKind, List<JLabel>> mapNavigationLabels = new EnumMap<>(MapKind.class);
    private PathFinder.RouteStrategy selectedRouteStrategy = PathFinder.RouteStrategy.RECOMMENDED;
    private MapKind selectedMapKind = MapKind.TERRAIN;
    private DataSource selectedDataSource = DataSource.ALGORITHMIC;
    private String aircraftModel = "Boeing 747-8";
    private double aircraftMaxSpeedKmh = 988;
    private double aircraftCruiseSpeedKmh = 920;
    private int startX = 10;
    private int startY = 8;
    private int destinationX = 54;
    private int destinationY = 48;
    private volatile ApiWeatherGrid.Snapshot apiSnapshot;
    private volatile boolean apiLoading;
    private boolean chatRequestInProgress;
    private JButton chatSendButton;
    private JPanel mapCanvasHost;

    private UserInterface() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new java.awt.Dimension(800, 600));
        frame.setSize(1360, 900);
        frame.setLocationRelativeTo(null);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        aircraftPreset.setSelectedIndex(4);
        styleAircraftPreset();
        aircraftPreset.addActionListener(event -> applyAircraftPreset());

        JPanel content = new JPanel();
        content.setBackground(BACKGROUND);
        content.setLayout(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        content.add(createHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setOpaque(false);
        mapCanvasHost = new ScrollableMapHost();
        mapCanvasHost.setOpaque(false);
        JScrollPane mapScroll = new JScrollPane(mapCanvasHost,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mapScroll.setBorder(BorderFactory.createEmptyBorder());
        mapScroll.setOpaque(false);
        mapScroll.getViewport().setOpaque(false);
        body.add(mapScroll, BorderLayout.CENTER);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        JPanel footer = new JPanel(new BorderLayout(0, 5));
        footer.setOpaque(false);
        footer.add(createChatbotPanel(), BorderLayout.CENTER);
        footer.add(statusLabel, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH);
        content.add(body, BorderLayout.CENTER);

        frame.setContentPane(content);
        refreshOpenMaps();
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            UIManager.put("Label.font", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            UIManager.put("TextField.font", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            UIManager.put("Button.font", new Font(Font.SANS_SERIF, Font.BOLD, 11));
            new UserInterface().frame.setVisible(true);
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.setOpaque(false);

        JLabel title = new JLabel("FLIGHT PLANNER");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        title.setForeground(NAVY);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(createMapNavigation(), BorderLayout.CENTER);
        titleRow.add(createHomeButton(), BorderLayout.EAST);

        JPanel controls = new JPanel(new GridLayout(2, 1, 0, 2));
        controls.setOpaque(false);
        controls.add(createDataSourceOptions());
        controls.add(createPlanningOptions());

        JLabel subtitle = new JLabel(
                "64×64 algorithmic routes or live WeatherAPI conditions · U.S. terrain map");
        subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        header.add(titleRow, BorderLayout.NORTH);
        header.add(controls, BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);
        return header;
    }

    private JPanel createChatbotPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        panel.setPreferredSize(new Dimension(1000, 150));

        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        JLabel title = new JLabel("FLIGHTPLANNER CHAT");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        title.setForeground(NAVY);
        JLabel hint = new JLabel("GPTProto · Ask about your route, aircraft, or map data.");
        hint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        hint.setForeground(MUTED);
        heading.add(title, BorderLayout.WEST);
        heading.add(hint, BorderLayout.CENTER);

        chatTranscript.setEditable(false);
        chatTranscript.setLineWrap(true);
        chatTranscript.setWrapStyleWord(true);
        chatTranscript.setRows(2);
        chatTranscript.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        chatTranscript.setForeground(NAVY);
        chatTranscript.setBackground(Color.WHITE);
        chatTranscript.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
        chatTranscript.setText("FlightPlanner: Hi! Ask me about fastest or safest routing, precipitation, wind, elevation, or aircraft speeds.\n");

        JScrollPane history = new JScrollPane(chatTranscript,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        history.setBorder(BorderFactory.createEmptyBorder());
        history.setOpaque(false);
        history.getViewport().setOpaque(false);
        history.setPreferredSize(new Dimension(900, 54));

        chatInput.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        chatInput.setForeground(NAVY);
        chatInput.setBackground(Color.WHITE);
        chatInput.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        chatInput.addActionListener(event -> submitChatMessage());

        chatSendButton = new RoundedButton("SEND");
        chatSendButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        styleActionButton(chatSendButton);
        chatSendButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        chatSendButton.addActionListener(event -> submitChatMessage());

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.add(chatInput, BorderLayout.CENTER);
        inputRow.add(chatSendButton, BorderLayout.EAST);

        panel.add(heading, BorderLayout.NORTH);
        panel.add(history, BorderLayout.CENTER);
        panel.add(inputRow, BorderLayout.SOUTH);
        return panel;
    }

    private void submitChatMessage() {
        if (chatRequestInProgress) return;
        String question = chatInput.getText().trim();
        if (question.isEmpty()) return;
        appendChatLine("You", question);
        chatInput.setText("");
        int responseStart = chatTranscript.getDocument().getLength();
        appendChatLine("FlightPlanner", "Thinking…");
        chatInput.setEnabled(false);
        chatSendButton.setEnabled(false);
        chatRequestInProgress = true;
        String dashboardContext = buildChatContext();

        Thread requestThread = new Thread(() -> {
            String reply;
            try {
                reply = gptProtoChatbot.reply(question, dashboardContext);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                reply = "The GPTProto request was interrupted.";
            } catch (IOException | RuntimeException error) {
                reply = "I couldn't get a GPTProto response: " + error.getMessage();
            }
            String finalReply = reply;
            SwingUtilities.invokeLater(() -> {
                int end = chatTranscript.getDocument().getLength();
                chatTranscript.replaceRange("FlightPlanner: " + finalReply + "\n", responseStart, end);
                chatInput.setEnabled(true);
                chatSendButton.setEnabled(true);
                chatRequestInProgress = false;
                chatInput.requestFocusInWindow();
            });
        }, "flightplanner-gptproto-chat");
        requestThread.setDaemon(true);
        requestThread.start();
    }

    private void appendChatLine(String speaker, String message) {
        chatTranscript.append(speaker + ": " + message + "\n");
        chatTranscript.setCaretPosition(chatTranscript.getDocument().getLength());
    }

    private String buildChatContext() {
        StringBuilder context = new StringBuilder();
        context.append("Data mode: ").append(selectedDataSource == DataSource.LIVE_API
                ? "LIVE WeatherAPI" : "algorithm-generated").append(".\n");
        context.append("Selected map: ").append(mapLabel(selectedMapKind)).append(".\n");
        context.append("Route mode: ").append(routeLabel(selectedRouteStrategy)).append(".\n");
        context.append("Aircraft: ").append(aircraftModel)
                .append("; cruise speed ").append(String.format(java.util.Locale.ROOT,
                        "%.0f km/h", aircraftCruiseSpeedKmh))
                .append("; maximum speed ").append(String.format(java.util.Locale.ROOT,
                        "%.0f km/h", aircraftMaxSpeedKmh)).append(".\n");
        MapCanvas routeMap = mapCanvasByKind.get(selectedMapKind);
        if (routeMap != null) context.append(routeMap.routeSummary()).append(".\n");
        for (MapKind kind : MapKind.values()) {
            MapCanvas map = mapCanvasByKind.get(kind);
            if (map != null) {
                context.append(mapLabel(kind)).append(" stats: ")
                        .append(map.statsSummary()).append(".\n");
            }
        }
        if (apiSnapshot != null && selectedDataSource == DataSource.LIVE_API) {
            context.append("WeatherAPI update time: ").append(apiSnapshot.updatedAt()).append(".\n");
        }
        return context.toString();
    }

    private String mapLabel(MapKind kind) {
        switch (kind) {
            case TERRAIN:
                return "Topography";
            case WIND:
                return "Wind";
            case PRECIPITATION:
                return "Precipitation";
            default:
                throw new IllegalStateException("Unsupported map kind: " + kind);
        }
    }

    private JPanel createMapNavigation() {
        JPanel navigation = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 14, 0));
        navigation.setOpaque(false);
        addMapNavigationLink(navigation, "Topography", MapKind.TERRAIN);
        addMapNavigationLink(navigation, "Wind", MapKind.WIND);
        addMapNavigationLink(navigation, "Precipitation", MapKind.PRECIPITATION);
        updateMapNavigationStyles();
        return navigation;
    }

    private void addMapNavigationLink(JPanel panel, String text, MapKind kind) {
        JLabel link = new JLabel("<html><u>" + text + "</u></html>");
        link.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        link.setForeground(NAVY);
        link.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        link.setToolTipText("Open the " + text.toLowerCase() + " map");
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                showMapOnPage(kind);
            }
        });
        mapNavigationLabels.computeIfAbsent(kind, ignored -> new ArrayList<>()).add(link);
        panel.add(link);
    }

    private void updateMapNavigationStyles() {
        for (Map.Entry<MapKind, List<JLabel>> entry : mapNavigationLabels.entrySet()) {
            boolean selected = entry.getKey() == selectedMapKind;
            for (JLabel label : entry.getValue()) {
                label.setFont(new Font(Font.SANS_SERIF, selected ? Font.BOLD : Font.PLAIN, 12));
            }
        }
    }

    private JButton createHomeButton() {
        JButton home = new RoundedButton("BACK HOME");
        home.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        styleActionButton(home);
        home.setBorder(BorderFactory.createEmptyBorder(9, 13, 9, 13));
        home.addActionListener(event -> goHome());
        return home;
    }

    private void goHome() {
        frame.dispose();
        HomePage.launch();
    }

    private JPanel createPlanningOptions() {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));
        row.setOpaque(false);
        JLabel routeLabel = new JLabel("ROUTE");
        routeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        routeLabel.setForeground(MUTED);
        row.add(routeLabel);
        ButtonGroup routeOptionGroup = new ButtonGroup();
        addRouteOption(row, routeOptionGroup, "FASTEST", PathFinder.RouteStrategy.FASTEST);
        addRouteOption(row, routeOptionGroup, "SAFEST", PathFinder.RouteStrategy.SAFEST);
        addRouteOption(row, routeOptionGroup, "RECOMMENDED", PathFinder.RouteStrategy.RECOMMENDED);
        JLabel planeLabel = new JLabel("AIRCRAFT");
        planeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        planeLabel.setForeground(MUTED);
        planeLabel.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 2));
        row.add(planeLabel);
        row.add(aircraftPreset);
        return row;
    }

    private JPanel createDataSourceOptions() {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));
        row.setOpaque(false);
        JLabel label = new JLabel("DATA");
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        label.setForeground(MUTED);
        row.add(label);
        ButtonGroup group = new ButtonGroup();
        addDataSourceOption(row, group, "ALGO", DataSource.ALGORITHMIC);
        addDataSourceOption(row, group, "LIVE", DataSource.LIVE_API);
        JButton refresh = new RoundedButton("REFRESH");
        refresh.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        refresh.setFocusPainted(false);
        styleActionButton(refresh);
        refresh.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        refresh.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        refresh.addActionListener(event -> loadLiveApiData());
        row.add(refresh);
        return row;
    }

    private void addDataSourceOption(JPanel panel, ButtonGroup group, String label, DataSource source) {
        JButton button = new RoundedButton(label);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        boolean selected = source == selectedDataSource;
        button.setSelected(selected);
        styleRouteOption(button, selected);
        dataSourceButtons.put(source, button);
        group.add(button);
        panel.add(button);
        button.addActionListener(event -> selectDataSource(source));
    }

    private void updateDataSourceStyles() {
        for (Map.Entry<DataSource, JButton> entry : dataSourceButtons.entrySet()) {
            boolean selected = entry.getKey() == selectedDataSource;
            entry.getValue().setSelected(selected);
            styleRouteOption(entry.getValue(), selected);
        }
    }

    private void selectDataSource(DataSource source) {
        if (source == DataSource.LIVE_API && apiSnapshot == null) {
            selectedDataSource = DataSource.LIVE_API;
            updateDataSourceStyles();
            statusLabel.setText("Loading live WeatherAPI grid…");
            refreshOpenMaps();
            loadLiveApiData();
            return;
        }
        selectedDataSource = source;
        updateDataSourceStyles();
        statusLabel.setText(source == DataSource.LIVE_API
                ? "Using the latest WeatherAPI weather and topography grid."
                : "Using the original algorithm-generated 64×64 grid.");
        refreshOpenMaps();
    }

    private void loadLiveApiData() {
        if (apiLoading) return;
        apiLoading = true;
        statusLabel.setText("Connecting to WeatherAPI for live conditions…");
        Thread worker = new Thread(() -> {
            try {
                ApiWeatherGrid.Snapshot loaded = ApiWeatherGrid.fetch(message ->
                        SwingUtilities.invokeLater(() -> statusLabel.setText(message)));
                SwingUtilities.invokeLater(() -> {
                    apiSnapshot = loaded;
                    apiLoading = false;
                    selectedDataSource = DataSource.LIVE_API;
                    updateDataSourceStyles();
                    selectUsDefaultsIfNeeded();
                    statusLabel.setText("WeatherAPI updated " + loaded.updatedAt()
                            + " · terrain: " + loaded.terrainSource()
                            + " · saved 64×64 weather CSVs under assets/.");
                    refreshOpenMaps();
                });
            } catch (Exception error) {
                SwingUtilities.invokeLater(() -> {
                    apiLoading = false;
                    selectedDataSource = DataSource.ALGORITHMIC;
                    updateDataSourceStyles();
                    statusLabel.setText("Live API load failed: " + error.getMessage());
                    refreshOpenMaps();
                });
            }
        }, "flightplanner-live-api-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void selectUsDefaultsIfNeeded() {
        if (apiSnapshot == null) return;
        startX = 3;
        startY = 5;
        destinationX = 58;
        destinationY = 18;
    }

    private void refreshOpenMaps() {
        rebuildMapCanvases();
    }

    private void addRouteOption(JPanel panel, ButtonGroup group, String label,
            PathFinder.RouteStrategy strategy) {
        JButton button = new RoundedButton(label);
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
        button.setBackground(Color.BLACK);
        button.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        button.setForeground(Color.WHITE);
        button.setFont(new Font(Font.SANS_SERIF, selected ? Font.BOLD : Font.PLAIN, 10));
        button.setBorderPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private void showMapOnPage(MapKind kind) {
        selectedMapKind = kind;
        updateMapNavigationStyles();
        if (mapCanvasHost == null) return;
        mapCanvasHost.removeAll();
        MapCanvas canvas = mapCanvasByKind.get(kind);
        if (canvas != null) mapCanvasHost.add(canvas, BorderLayout.CENTER);
        mapCanvasHost.revalidate();
        mapCanvasHost.repaint();
    }

    private void rebuildMapCanvases() {
        if (mapCanvasHost == null) return;
        mapCanvases.clear();
        mapCanvasByKind.clear();
        Route route = calculateSelectedRoute();
        createMapCanvas(MapKind.TERRAIN, route);
        createMapCanvas(MapKind.WIND, route);
        createMapCanvas(MapKind.PRECIPITATION, route);
        showMapOnPage(selectedMapKind);
    }

    private MapCanvas createMapCanvas(MapKind kind, Route route) {
        ApiWeatherGrid.Snapshot liveData = selectedDataSource == DataSource.LIVE_API
                ? apiSnapshot : null;
        MapCanvas canvas = new MapCanvas(this, pathFinder, kind, route, liveData);
        mapCanvases.add(canvas);
        mapCanvasByKind.put(kind, canvas);
        return canvas;
    }

    private Route calculateSelectedRoute() {
        try {
            Route route = calculateRoute(selectedRouteStrategy);
            if (route != null && selectedRouteStrategy == PathFinder.RouteStrategy.RECOMMENDED
                    && hasDangerAboveLimit(route)) {
                statusLabel.setText("No route met the 30% danger limit; showing the safest available route.");
            }
            return route;
        } catch (IllegalArgumentException error) {
            statusLabel.setText(error.getMessage());
            return null;
        }
    }

    private boolean hasDangerAboveLimit(Route route) {
        for (double[] coordinate : route.getRoute()) {
            if (dangerAt((int) coordinate[0], (int) coordinate[1]) > 0.3) {
                return true;
            }
        }
        return false;
    }

    private void refreshFlightPath() {
        Route route = calculateSelectedRoute();
        for (MapCanvas canvas : mapCanvases) {
            canvas.setRoute(route);
        }
        showMapOnPage(selectedMapKind);
    }

    private void moveAlgorithmEndpoint(boolean start, int x, int y) {
        if (selectedDataSource != DataSource.ALGORITHMIC) return;
        if (start) {
            startX = x;
            startY = y;
        } else {
            destinationX = x;
            destinationY = y;
        }
        refreshFlightPath();
        statusLabel.setText("Moved " + (start ? "start" : "destination") + " to (" + x + ", " + y + ").");
    }

    private static AircraftPreset[] aircraftPresets() {
        return new AircraftPreset[] {
            new AircraftPreset("Cessna 172 · 302 / 226 km/h", "Cessna 172", 302, 226),
            new AircraftPreset("Airbus A320neo · 871 / 828 km/h", "Airbus A320neo", 871, 828),
            new AircraftPreset("Boeing 737-800 · 876 / 842 km/h", "Boeing 737-800", 876, 842),
            new AircraftPreset("Boeing 787-9 · 954 / 903 km/h", "Boeing 787-9", 954, 903),
            new AircraftPreset("Boeing 747-8 · 988 / 920 km/h", "Boeing 747-8", 988, 920),
            new AircraftPreset("Airbus A380-800 · 1020 / 903 km/h", "Airbus A380-800", 1020, 903)
        };
    }

    private void styleAircraftPreset() {
        aircraftPreset.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        aircraftPreset.setForeground(Color.BLACK);
        aircraftPreset.setBackground(Color.WHITE);
        aircraftPreset.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 9));
        aircraftPreset.setFocusable(false);
        aircraftPreset.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel item = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                item.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                item.setBackground(Color.WHITE);
                item.setForeground(NAVY);
                return item;
            }
        });
    }

    private void applyAircraftPreset() {
        AircraftPreset preset = (AircraftPreset) aircraftPreset.getSelectedItem();
        if (preset == null) return;
        aircraftModel = preset.model();
        aircraftMaxSpeedKmh = preset.maxSpeedKmh();
        aircraftCruiseSpeedKmh = preset.cruiseSpeedKmh();
        refreshFlightPath();
        statusLabel.setText("Loaded typical planning speeds for " + preset.model() + ".");
    }

    private void styleActionButton(JButton button) {
        button.setForeground(Color.WHITE);
        button.setBackground(Color.BLACK);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private Route calculateRoute(PathFinder.RouteStrategy strategy) {
        String model = aircraftModel;
        double maxSpeed = aircraftMaxSpeedKmh;
        double cruiseSpeed = aircraftCruiseSpeedKmh;
        double heading = 0;
        validateSpeeds(maxSpeed, cruiseSpeed);
        if (!isMapCoordinate(startX) || !isMapCoordinate(startY)
                || !isMapCoordinate(destinationX) || !isMapCoordinate(destinationY)) {
            throw new IllegalArgumentException("Route coordinates must be between 0 and 63.");
        }

        Plane plane = new Plane(model, 900, maxSpeed, 1000);
        plane.accelerate(cruiseSpeed);
        plane.changeDirection(heading);
        double[][] path;
        if (selectedDataSource == DataSource.LIVE_API && apiSnapshot != null) {
            path = ApiRoutePlanner.plotRoute(apiSnapshot, pathFinder, plane,
                    startX, startY, destinationX, destinationY, strategy);
        } else {
            path = pathFinder.plotRoute(plane, startX, startY, destinationX, destinationY,
                    pathFinder.generateDangerMap(), strategy);
        }
        return path == null ? null : new Route(path);
    }

    private double dangerAt(int x, int y) {
        if (selectedDataSource == DataSource.LIVE_API && apiSnapshot != null)
            return apiSnapshot.danger()[y][x];
        Weather weather = pathFinder.getWeatherAt(x, y);
        return weather == null ? Double.NaN : weather.getDanger();
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

    private enum DataSource {
        ALGORITHMIC,
        LIVE_API
    }

    private enum MapKind {
        TERRAIN,
        WIND,
        PRECIPITATION
    }

    private record AircraftPreset(String label, String model, int maxSpeedKmh,
            int cruiseSpeedKmh) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Lets the map use the full dashboard width while allowing vertical scrolling on short screens. */
    private static final class ScrollableMapHost extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        private ScrollableMapHost() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(1200, 820);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(80, visibleRect.height - 48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static final class MapCanvas extends JPanel {
        private static final long serialVersionUID = 1L;

        private final UserInterface owner;
        private final MapKind kind;
        private final boolean liveApi;
        private final String terrainSource;
        private final double[][] terrain = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] windSpeeds = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] windDirections = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] precipitation = new double[MAP_SIZE][MAP_SIZE];
        private final double[][] weatherDanger = new double[MAP_SIZE][MAP_SIZE];
        private double[][] routeCoordinates;
        private final double minimumElevation;
        private final double averageElevation;
        private final double maximumElevation;
        private final double minimumWindSpeed;
        private final double averageWindSpeed;
        private final double maximumWindSpeed;
        private final double minimumPrecipitation;
        private final double averagePrecipitation;
        private final double maximumPrecipitation;
        private int draggedEndpoint;

        private MapCanvas(UserInterface owner, PathFinder pathFinder, MapKind kind, Route route,
                ApiWeatherGrid.Snapshot apiData) {
            this.owner = owner;
            this.kind = kind;
            this.liveApi = apiData != null;
            this.terrainSource = liveApi ? apiData.terrainSource() : "generated";
            this.routeCoordinates = route == null ? null : route.getRoute();
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(1280, 900));
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent event) {
                    if (getParent() != null) getParent().revalidate();
                }
            });
            setToolTipText(liveApi ? null : "Drag the S or D marker to move a route endpoint.");
            MouseAdapter endpointDrag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (!canDragEndpoints() || routeCoordinates == null || routeCoordinates.length == 0) return;
                    Point start = gridPoint(routeCoordinates[0]);
                    Point destination = gridPoint(routeCoordinates[routeCoordinates.length - 1]);
                    if (event.getPoint().distance(start) <= 16) draggedEndpoint = 1;
                    else if (event.getPoint().distance(destination) <= 16) draggedEndpoint = 2;
                    else draggedEndpoint = 0;
                    if (draggedEndpoint != 0) setCursor(java.awt.Cursor.getPredefinedCursor(
                            java.awt.Cursor.MOVE_CURSOR));
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (draggedEndpoint == 0 || !canDragEndpoints()) return;
                    int width = mapWidth();
                    int height = mapHeight();
                    if (width <= 0 || height <= 0) return;
                    int x = clamp((int) Math.floor((event.getX() - mapX()) * MAP_SIZE / (double) width), 0, 63);
                    int y = clamp((int) Math.floor((event.getY() - mapY()) * MAP_SIZE / (double) height), 0, 63);
                    owner.moveAlgorithmEndpoint(draggedEndpoint == 1, x, y);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    draggedEndpoint = 0;
                    setCursor(java.awt.Cursor.getDefaultCursor());
                }
            };
            addMouseListener(endpointDrag);
            addMouseMotionListener(endpointDrag);
            double elevationSum = 0;
            double windSpeedSum = 0;
            double precipitationSum = 0;
            double lowestElevation = Double.POSITIVE_INFINITY;
            double highestElevation = Double.NEGATIVE_INFINITY;
            double lowestWindSpeed = Double.POSITIVE_INFINITY;
            double highestWindSpeed = Double.NEGATIVE_INFINITY;
            double lowestPrecipitation = Double.POSITIVE_INFINITY;
            double highestPrecipitation = Double.NEGATIVE_INFINITY;
            int cellCount = 0;
            for (int y = 0; y < MAP_SIZE; y++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    if (liveApi) {
                        terrain[y][x] = apiData.elevationMeters()[y][x];
                        windSpeeds[y][x] = apiData.windSpeedKmh()[y][x];
                        windDirections[y][x] = apiData.windDirectionDeg()[y][x];
                        precipitation[y][x] = apiData.precipitationMm()[y][x];
                        weatherDanger[y][x] = apiData.danger()[y][x];
                    } else {
                        terrain[y][x] = pathFinder.getTerrainAt(x, y);
                        Wind wind = pathFinder.getWindAt(x, y);
                        windSpeeds[y][x] = wind.getSpeed();
                        windDirections[y][x] = wind.getDirection();
                        Weather weather = pathFinder.getWeatherAt(x, y);
                        precipitation[y][x] = weather.getCOF();
                        weatherDanger[y][x] = weather.getDanger();
                    }
                    if (!Double.isFinite(terrain[y][x]) || !Double.isFinite(windSpeeds[y][x])
                            || !Double.isFinite(precipitation[y][x])) continue;
                    cellCount++;
                    elevationSum += terrain[y][x];
                    windSpeedSum += windSpeeds[y][x];
                    precipitationSum += precipitation[y][x];
                    lowestElevation = Math.min(lowestElevation, terrain[y][x]);
                    highestElevation = Math.max(highestElevation, terrain[y][x]);
                    lowestWindSpeed = Math.min(lowestWindSpeed, windSpeeds[y][x]);
                    highestWindSpeed = Math.max(highestWindSpeed, windSpeeds[y][x]);
                    lowestPrecipitation = Math.min(lowestPrecipitation, precipitation[y][x]);
                    highestPrecipitation = Math.max(highestPrecipitation, precipitation[y][x]);
                }
            }
            minimumElevation = lowestElevation;
            averageElevation = cellCount == 0 ? 0 : elevationSum / cellCount;
            maximumElevation = highestElevation;
            minimumWindSpeed = lowestWindSpeed;
            averageWindSpeed = cellCount == 0 ? 0 : windSpeedSum / cellCount;
            maximumWindSpeed = highestWindSpeed;
            minimumPrecipitation = lowestPrecipitation;
            averagePrecipitation = cellCount == 0 ? 0 : precipitationSum / cellCount;
            maximumPrecipitation = highestPrecipitation;
        }

        private void setRoute(Route route) {
            routeCoordinates = route == null ? null : route.getRoute();
            repaint();
        }

        private boolean canDragEndpoints() {
            return !liveApi && owner.selectedDataSource == DataSource.ALGORITHMIC;
        }

        private int mapWidth() {
            return Math.max(1, getWidth() - 64);
        }

        private int mapHeight() {
            return Math.max(1, (int) Math.round(mapWidth() / 1.8));
        }

        private int mapX() {
            return 32;
        }

        private int mapY() {
            return 24;
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getWidth() > 0 ? getWidth() : 1280;
            int height = mapY() + (int) Math.round(Math.max(1, width - 64) / 1.8) + 154;
            return new Dimension(1280, Math.max(600, height));
        }

        private Point gridPoint(double[] cell) {
            int width = mapWidth();
            int height = mapHeight();
            return new Point(mapX() + (int) Math.round((cell[0] + 0.5) * width / MAP_SIZE),
                    mapY() + (int) Math.round((cell[1] + 0.5) * height / MAP_SIZE));
        }

        private static int clamp(int value, int minimum, int maximum) {
            return Math.max(minimum, Math.min(maximum, value));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int mapWidth = mapWidth();
            int mapHeight = mapHeight();
            if (mapWidth <= 0 || mapHeight <= 0) {
                g.dispose();
                return;
            }
            int mapX = mapX();
            int mapY = mapY();
            Shape originalClip = g.getClip();
            g.clip(new RoundRectangle2D.Double(mapX, mapY, mapWidth, mapHeight, 24, 24));

            for (int y = 0; y < MAP_SIZE; y++) {
                int cellTop = mapY + y * mapHeight / MAP_SIZE;
                int cellBottom = mapY + (y + 1) * mapHeight / MAP_SIZE;
                for (int x = 0; x < MAP_SIZE; x++) {
                    int cellLeft = mapX + x * mapWidth / MAP_SIZE;
                    int cellRight = mapX + (x + 1) * mapWidth / MAP_SIZE;
                    g.setColor(cellColor(x, y));
                    g.fillRect(cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop);
                }
            }

            if (kind == MapKind.WIND) {
                drawWindArrows(g, mapX, mapY, mapWidth, mapHeight);
            }
            if (routeCoordinates != null && routeCoordinates.length > 0) {
                drawRoute(g, mapX, mapY, mapWidth, mapHeight);
            }
            if (kind == MapKind.PRECIPITATION) {
                drawPrecipitationLegend(g, mapX, mapY);
            }

            g.setClip(originalClip);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.drawString("0", mapX - 10, mapY + 10);
            g.drawString("63", mapX + mapWidth - 13, mapY + 10);
            g.drawString("X →", mapX, mapY + mapHeight + 18);
            g.drawString("Y ↓", mapX - 28, mapY + mapHeight + 18);
            drawStatsBox(g);
            g.dispose();
        }

        private void drawStatsBox(Graphics2D g) {
            int boxWidth = Math.min(460, getWidth() - 24);
            int boxHeight = 96;
            int x = (getWidth() - boxWidth) / 2;
            int y = mapY() + mapHeight() + 38;

            g.setColor(new Color(255, 255, 255, 238));
            g.fillRoundRect(x, y, boxWidth, boxHeight, 16, 16);
            g.setColor(NAVY);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            drawCenteredText(g, "SELECTED ROUTE", x + boxWidth / 2, y + 15, boxWidth - 24);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            drawCenteredText(g, routeSummary(), x + boxWidth / 2, y + 30, boxWidth - 24);
            g.setColor(NAVY);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            drawCenteredText(g, statsTitle(), x + boxWidth / 2, y + 49, boxWidth - 24);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            drawCenteredText(g, statsSummary(), x + boxWidth / 2, y + 64, boxWidth - 24);
            drawCenteredText(g, statsDetail(), x + boxWidth / 2, y + 78, boxWidth - 24);
        }

        private void drawCenteredText(Graphics2D g, String text, int centerX, int baseline, int maxWidth) {
            java.awt.FontMetrics metrics = g.getFontMetrics();
            String display = text;
            while (display.length() > 3 && metrics.stringWidth(display) > maxWidth) {
                display = display.substring(0, display.length() - 4) + "…";
            }
            g.drawString(display, centerX - metrics.stringWidth(display) / 2, baseline);
        }

        private String statsTitle() {
            switch (kind) {
                case TERRAIN:
                    return "ELEVATION STATS";
                case WIND:
                    return "WIND SPEED STATS";
                case PRECIPITATION:
                    return "PRECIPITATION STATS";
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
                    if (liveApi) {
                        return String.format(java.util.Locale.ROOT,
                                "Min %.1f mm   Avg %.1f mm   Max %.1f mm",
                                minimumPrecipitation, averagePrecipitation, maximumPrecipitation);
                    }
                    return String.format(java.util.Locale.ROOT, "Min %.0f%%   Avg %.0f%%   Max %.0f%%",
                            minimumPrecipitation * 100, averagePrecipitation * 100, maximumPrecipitation * 100);
                default:
                    throw new IllegalStateException("Unsupported map kind: " + kind);
            }
        }

        private String statsDetail() {
            switch (kind) {
                case TERRAIN:
                    return liveApi ? "Live terrain: " + terrainSource
                            : "Generated terrain elevation across 4,096 cells";
                case WIND:
                    return liveApi ? "Live wind values converted to km/h"
                            : "Generated wind field across 4,096 cells";
                case PRECIPITATION:
                    return liveApi ? "Live current precipitation in millimeters"
                            : "Generated precipitation chance across 4,096 cells";
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
            if (!Double.isFinite(terrain[y][x]) || !Double.isFinite(precipitation[y][x]))
                return Color.WHITE;
            if (kind == MapKind.PRECIPITATION) {
                return precipitationColor(precipitation[y][x]);
            }
            return terrainColor(terrain[y][x]);
        }

        private Color precipitationColor(double precipitationAmount) {
            double amount = Math.max(0, precipitationAmount);
            double normalized;
            if (liveApi) {
                normalized = Math.log1p(amount) / Math.log(11.0);
            } else {
                normalized = amount;
            }
            normalized = Math.pow(Math.max(0, Math.min(1, normalized)), 0.72);
            return interpolate(new Color(235, 244, 255), new Color(8, 67, 150), normalized);
        }

        private void drawPrecipitationLegend(Graphics2D g, int mapX, int mapY) {
            int x = mapX + 16;
            int y = mapY + 16;
            int width = 202;
            int height = 48;
            g.setColor(new Color(255, 255, 255, 232));
            g.fillRoundRect(x, y, width, height, 14, 14);
            g.setColor(NAVY);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
            g.drawString("PRECIPITATION", x + 10, y + 13);

            int barX = x + 10;
            int barY = y + 20;
            int barWidth = width - 20;
            int segments = 36;
            for (int i = 0; i < segments; i++) {
                g.setColor(interpolate(new Color(235, 244, 255), new Color(8, 67, 150),
                        i / (double) (segments - 1)));
                int left = barX + i * barWidth / segments;
                int right = barX + (i + 1) * barWidth / segments;
                g.fillRect(left, barY, right - left, 7);
            }

            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
            g.drawString("0", barX, y + 41);
            g.drawString(liveApi ? "10+ mm" : "100%", barX + barWidth - 31, y + 41);
        }

        private void drawRoute(Graphics2D g, int mapX, int mapY, int mapWidth, int mapHeight) {
            if (routeCoordinates == null || routeCoordinates.length == 0) {
                return;
            }

            double[][] points = routeCoordinates;
            double[][] smoothPoints = RouteSmoother.smoothPath(points, 8);
            int[] xPoints = new int[smoothPoints.length];
            int[] yPoints = new int[smoothPoints.length];
            for (int i = 0; i < smoothPoints.length; i++) {
                xPoints[i] = mapX + (int) Math.round((smoothPoints[i][0] + 0.5) * mapWidth / MAP_SIZE);
                yPoints[i] = mapY + (int) Math.round((smoothPoints[i][1] + 0.5) * mapHeight / MAP_SIZE);
            }

            g.setStroke(new java.awt.BasicStroke(6, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(Color.WHITE);
            g.drawPolyline(xPoints, yPoints, smoothPoints.length);
            g.setStroke(new java.awt.BasicStroke(3, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(ROUTE_BLUE);
            g.drawPolyline(xPoints, yPoints, smoothPoints.length);
            drawEndpoint(g, xPoints[0], yPoints[0], Color.BLACK, "S");
            drawEndpoint(g, xPoints[smoothPoints.length - 1], yPoints[smoothPoints.length - 1],
                    Color.BLACK, "D");
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

        private void drawWindArrows(Graphics2D g, int mapX, int mapY, int mapWidth, int mapHeight) {
            double cellWidth = (double) mapWidth / MAP_SIZE;
            double cellHeight = (double) mapHeight / MAP_SIZE;
            double cellSize = Math.min(cellWidth, cellHeight);
            for (int y = 2; y < MAP_SIZE; y += 5) {
                for (int x = 2; x < MAP_SIZE; x += 5) {
                    if (!Double.isFinite(windDirections[y][x])
                            || !Double.isFinite(windSpeeds[y][x])) continue;
                    double angle = Math.toRadians(windDirections[y][x]);
                    double length = cellSize * (0.8 + windSpeeds[y][x] / 25.0 * 1.2);
                    double centerX = mapX + (x + 0.5) * cellWidth;
                    double centerY = mapY + (y + 0.5) * cellHeight;
                    double tipX = centerX + Math.sin(angle) * length;
                    double tipY = centerY - Math.cos(angle) * length;

                    g.setColor(new Color(255, 255, 255, 210));
                    g.setStroke(new java.awt.BasicStroke(3, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    g.drawLine((int) centerX, (int) centerY, (int) tipX, (int) tipY);
                    g.setColor(Color.BLACK);
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
            double amount = Math.max(0, Math.min(1, (elevation + 500.0) / 4500.0));
            Color low = new Color(54, 145, 83);
            Color middle = new Color(236, 218, 125);
            Color high = new Color(188, 58, 54);
            if (amount < 0.56) {
                return interpolate(low, middle, amount / 0.56);
            }
            return interpolate(middle, high, (amount - 0.56) / 0.44);
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
