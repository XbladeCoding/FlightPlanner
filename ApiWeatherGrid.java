import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Loads a WeatherAPI current-conditions snapshot over the 64x64 U.S. grid. */
public final class ApiWeatherGrid {
    public static final int SIZE = 64;
    private static final int[] SAMPLE_CELLS = {0, 8, 16, 24, 32, 40, 48, 56, 63};
    private static final int MAX_PARALLEL_REQUESTS = 4;
    private static final double NORTH = 49.4;
    private static final double SOUTH = 24.5;
    private static final double WEST = -125.0;
    private static final double EAST = -66.9;
    private static final String WEATHER_API_URL = "https://api.weatherapi.com/v1/current.json";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final double[][] US_OUTLINE = {
        {49,-124.7},{48.6,-122.8},{48.2,-123.2},{46.3,-124},{42,-124.2},
        {40,-124.4},{38.8,-123.1},{37.8,-122.5},{36,-121},{34.4,-119.5},
        {33,-117.1},{32.54,-117.1},{32.54,-114.7},{32.7,-113},{31.3,-109.05},
        {31.3,-108.2},{31.8,-106.5},{31.8,-103},{29.8,-103},{29,-101},
        {27.5,-99.5},{26,-97.1},{25.8,-97.4},{28,-96.7},{29.5,-94.7},
        {29,-91.5},{30,-89.5},{30.3,-85},{29.7,-83},{27.5,-82.7},
        {25.5,-81.8},{24.5,-81.8},{24.5,-80.5},{25.4,-80.1},{27,-80},
        {29.5,-80.7},{30.7,-81.4},{32,-80.9},{34.8,-76.9},{36.5,-75.8},
        {38.5,-74.8},{40.5,-73.7},{42,-70.5},{43,-70.7},{45,-67},
        {47.4,-69},{47.3,-71},{45,-71.5},{45,-73.5},{44.8,-74.7},
        {43,-75},{42,-79.8},{42,-80.5},{41.4,-82.6},{42,-83.1},
        {43,-82.5},{45,-84.7},{46,-89.5},{47,-90},{48,-89.5},{49,-95},
        {49,-104},{49,-110},{49,-117},{49,-123},{49,-124.7}
    };

    private ApiWeatherGrid() {}

    public interface ProgressListener {
        void onProgress(String message);
    }

    public record Snapshot(
            double[][] elevationMeters,
            double[][] windSpeedKmh,
            double[][] windDirectionDeg,
            double[][] precipitationMm,
            double[][] temperatureC,
            String[][] conditionText,
            double[][] danger,
            String updatedAt,
            String terrainSource) {}

    private record TerrainData(double[][] values, String source) {}
    private record SampleLocation(int row, int col, double latitude, double longitude) {}
    private record WeatherSample(SampleLocation location, double windSpeedKmh,
            double windDirectionDeg, double precipitationMm, double temperatureC,
            String condition, String updatedAt) {}

    /** Fetches one live snapshot. Call from a background thread. */
    public static Snapshot fetch(ProgressListener progress) throws IOException, InterruptedException {
        String apiKey = System.getenv("WEATHERAPI_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Set WEATHERAPI_KEY to your WeatherAPI.com key, then refresh live data.");
        }

        TerrainData cachedTerrain = loadCachedTopography();
        if (cachedTerrain == null) {
            throw new IOException("Local topography is missing. Add assets/topography_64x64.csv and retry.");
        }
        double[][] elevation = cachedTerrain.values();
        List<WeatherSample> samples = fetchWeatherSamples(apiKey, progress);
        double[][] windSpeed = emptyGrid();
        double[][] windDirection = emptyGrid();
        double[][] precipitation = emptyGrid();
        double[][] temperature = emptyGrid();
        String[][] conditions = new String[SIZE][SIZE];
        interpolateSamples(samples, windSpeed, windDirection, precipitation, temperature, conditions);

        double[][] danger = makeDangerGrid(elevation, windSpeed, precipitation);
        String terrainSource = cachedTerrain.source();
        String updatedAt = samples.stream().map(WeatherSample::updatedAt)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("unknown");
        Snapshot snapshot = new Snapshot(elevation, windSpeed, windDirection, precipitation,
                temperature, conditions, danger, updatedAt, terrainSource);
        writeCsvs(snapshot);
        return snapshot;
    }

    public static boolean insideUs(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE
                && insideUs(latitude(row), longitude(col));
    }

    private static List<WeatherSample> fetchWeatherSamples(String apiKey, ProgressListener progress)
            throws IOException, InterruptedException {
        List<SampleLocation> locations = new ArrayList<>();
        for (int row : SAMPLE_CELLS) {
            for (int col : SAMPLE_CELLS) {
                if (insideUs(row, col)) {
                    locations.add(new SampleLocation(row, col, latitude(row), longitude(col)));
                }
            }
        }
        if (locations.size() < 4) throw new IOException("Not enough WeatherAPI sample points fell inside the U.S. map.");

        AtomicInteger completed = new AtomicInteger();
        int workers = Math.min(MAX_PARALLEL_REQUESTS, locations.size());
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CompletionService<WeatherSample> completion = new ExecutorCompletionService<>(executor);
        try {
            for (SampleLocation location : locations) {
                completion.submit(() -> {
                    WeatherSample sample = fetchWeatherSample(apiKey, location);
                    int count = completed.incrementAndGet();
                    if (progress != null) progress.onProgress("WeatherAPI current conditions: "
                            + count + "/" + locations.size() + " sample locations");
                    return sample;
                });
            }
            List<WeatherSample> samples = new ArrayList<>(locations.size());
            for (int i = 0; i < locations.size(); i++) {
                try {
                    Future<WeatherSample> finished = completion.take();
                    samples.add(finished.get());
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof IOException io) throw io;
                    throw new IOException("WeatherAPI current-conditions request failed: "
                            + (cause == null ? error.getMessage() : cause.getMessage()), cause);
                }
            }
            return samples;
        } finally {
            executor.shutdownNow();
        }
    }

    private static WeatherSample fetchWeatherSample(String apiKey, SampleLocation location)
            throws IOException, InterruptedException {
        String query = String.format(Locale.ROOT, "%.4f,%.4f", location.latitude(), location.longitude());
        String url = WEATHER_API_URL + "?key=" + encode(apiKey) + "&q=" + encode(query);
        JsonNode root = getJson(url, "WeatherAPI");
        JsonNode current = root.path("current");
        if (!current.isObject()) throw new IOException("WeatherAPI returned no current weather for a U.S. sample point.");

        double windSpeed = number(current.path("wind_kph"));
        double windDirection = number(current.path("wind_degree"));
        double precipitation = number(current.path("precip_mm"));
        double temperature = number(current.path("temp_c"));
        if (!Double.isFinite(windSpeed) || !Double.isFinite(windDirection)
                || !Double.isFinite(precipitation) || !Double.isFinite(temperature)) {
            throw new IOException("WeatherAPI returned incomplete current conditions for a U.S. sample point.");
        }
        String condition = current.path("condition").path("text").asText("Current conditions");
        String updated = current.path("last_updated").asText("");
        return new WeatherSample(location, windSpeed, windDirection, precipitation,
                temperature, condition, updated);
    }

    private static void interpolateSamples(List<WeatherSample> samples, double[][] windSpeed,
            double[][] windDirection, double[][] precipitation, double[][] temperature,
            String[][] conditions) {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (!insideUs(row, col)) continue;
                final int cellRow = row;
                final int cellCol = col;
                List<WeatherSample> nearest = new ArrayList<>(samples);
                nearest.sort(Comparator.comparingDouble(sample ->
                        squaredDistance(cellRow, cellCol,
                                sample.location().row(), sample.location().col())));
                int count = Math.min(4, nearest.size());
                double totalWeight = 0, speedSum = 0, precipSum = 0, temperatureSum = 0;
                double directionX = 0, directionY = 0;
                WeatherSample closest = nearest.get(0);
                boolean exactSample = closest.location().row() == row && closest.location().col() == col;
                if (exactSample) {
                    windSpeed[row][col] = closest.windSpeedKmh();
                    windDirection[row][col] = closest.windDirectionDeg();
                    precipitation[row][col] = closest.precipitationMm();
                    temperature[row][col] = closest.temperatureC();
                    conditions[row][col] = closest.condition();
                    continue;
                }
                for (int i = 0; i < count; i++) {
                    WeatherSample sample = nearest.get(i);
                    double distanceSquared = squaredDistance(row, col,
                            sample.location().row(), sample.location().col());
                    double weight = 1.0 / distanceSquared;
                    double direction = Math.toRadians(sample.windDirectionDeg());
                    totalWeight += weight;
                    speedSum += weight * sample.windSpeedKmh();
                    precipSum += weight * sample.precipitationMm();
                    temperatureSum += weight * sample.temperatureC();
                    directionX += weight * Math.cos(direction);
                    directionY += weight * Math.sin(direction);
                }
                windSpeed[row][col] = speedSum / totalWeight;
                windDirection[row][col] = (Math.toDegrees(Math.atan2(directionY, directionX)) + 360) % 360;
                precipitation[row][col] = precipSum / totalWeight;
                temperature[row][col] = temperatureSum / totalWeight;
                conditions[row][col] = closest.condition();
            }
        }
    }

    private static double squaredDistance(int row, int col, int sampleRow, int sampleCol) {
        double dy = row - sampleRow, dx = col - sampleCol;
        return dx * dx + dy * dy;
    }

    private static double[][] makeDangerGrid(double[][] elevation, double[][] windSpeed,
            double[][] precipitation) {
        double[][] danger = emptyGrid();
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (!insideUs(row, col)) continue;
                double windRisk = normalize(windSpeed[row][col], 90.0);
                double rainRisk = normalize(precipitation[row][col], 20.0);
                double terrainRisk = Math.min(1.0, terrainRelief(elevation, row, col) / 1200.0);
                danger[row][col] = Math.min(1.0,
                        0.55 * windRisk + 0.30 * rainRisk + 0.15 * terrainRisk);
            }
        }
        return danger;
    }

    private static double terrainRelief(double[][] elevation, int row, int col) {
        double center = elevation[row][col];
        if (!Double.isFinite(center)) return 0;
        double relief = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr, c = col + dc;
                if (insideUs(r, c) && Double.isFinite(elevation[r][c]))
                    relief = Math.max(relief, Math.abs(center - elevation[r][c]));
            }
        }
        return relief;
    }

    private static double normalize(double value, double maximum) {
        return Double.isFinite(value) ? Math.max(0, Math.min(1, value / maximum)) : 0;
    }

    private static void writeCsvs(Snapshot data) throws IOException {
        Path assets = Path.of("assets");
        Files.createDirectories(assets);
        writeGrid(assets.resolve("danger_matrix_64x64.csv"), data.danger());
        writeGrid(assets.resolve("wind_speed_64x64.csv"), data.windSpeedKmh());
        writeGrid(assets.resolve("wind_direction_64x64.csv"), data.windDirectionDeg());
        writeGrid(assets.resolve("precipitation_64x64.csv"), data.precipitationMm());
        writeGrid(assets.resolve("temperature_64x64.csv"), data.temperatureC());
    }

    private static TerrainData loadCachedTopography() {
        List<Path> files = List.of(
                Path.of("assets", "topography_128x256.csv"),
                Path.of("assets", "elevation_m_128x256.csv"),
                Path.of("assets", "topography_64x128.csv"),
                Path.of("assets", "topography_64x64.csv"),
                Path.of("assets", "elevation_m_64x64.csv"),
                Path.of("assets", "topography_32x64.csv"));
        for (Path file : files) {
            if (!Files.isRegularFile(file)) continue;
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (lines.isEmpty()) continue;
                String[] first = lines.get(0).split(",", -1);
                int sourceRows = lines.size(), sourceCols = first.length;
                if (!((sourceRows == 128 && sourceCols == 256)
                        || (sourceRows == 64 && (sourceCols == 128 || sourceCols == 64))
                        || (sourceRows == 32 && sourceCols == 64))) continue;
                double[][] source = new double[sourceRows][sourceCols];
                boolean valid = true;
                for (int row = 0; row < sourceRows && valid; row++) {
                    String[] values = lines.get(row).split(",", -1);
                    if (values.length != sourceCols) { valid = false; break; }
                    for (int col = 0; col < sourceCols; col++) {
                        String text = values[col].trim();
                        source[row][col] = text.isEmpty() || text.equalsIgnoreCase("NaN")
                                ? Double.NaN : Double.parseDouble(text);
                        if (insideUs(latitude(row, sourceRows), longitude(col, sourceCols))
                                && !Double.isFinite(source[row][col])) valid = false;
                    }
                }
                if (!valid) continue;
                double[][] resized = emptyGrid();
                for (int row = 0; row < SIZE; row++) {
                    int sourceRow = (int) Math.round(row * (sourceRows - 1.0) / (SIZE - 1.0));
                    for (int col = 0; col < SIZE; col++) {
                        if (!insideUs(row, col)) continue;
                        int sourceCol = (int) Math.round(col * (sourceCols - 1.0) / (SIZE - 1.0));
                        resized[row][col] = source[sourceRow][sourceCol];
                    }
                }
                return new TerrainData(resized, file.getFileName().toString());
            } catch (IOException | NumberFormatException error) {
                // Ignore invalid local elevation files and try the next supported format.
            }
        }
        return null;
    }

    private static void writeGrid(Path file, double[][] values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    if (col > 0) writer.write(',');
                    writer.write(Double.isFinite(values[row][col])
                            ? String.format(Locale.ROOT, "%.6f", values[row][col]) : "NaN");
                }
                writer.newLine();
            }
        }
    }

    private static JsonNode getJson(String url, String service) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(45))
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json;
        try {
            json = JSON.readTree(response.body());
        } catch (IOException error) {
            throw new IOException(service + " returned invalid JSON (HTTP "
                    + response.statusCode() + ").", error);
        }
        if (response.statusCode() != 200 || json.has("error") || json.path("error").asBoolean(false)) {
            String message = json.path("error").path("message").asText("");
            if (message.isBlank()) message = json.path("reason").asText("HTTP " + response.statusCode());
            throw new IOException(service + " API error: " + message);
        }
        return json;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static double number(JsonNode node) {
        return node != null && node.isNumber() ? node.asDouble() : Double.NaN;
    }

    private static double[][] emptyGrid() {
        double[][] result = new double[SIZE][SIZE];
        for (double[] row : result) Arrays.fill(row, Double.NaN);
        return result;
    }

    private static double latitude(int row) {
        return latitude(row, SIZE);
    }

    private static double longitude(int col) {
        return longitude(col, SIZE);
    }

    private static double latitude(int row, int rows) {
        return NORTH - (NORTH - SOUTH) * row / (rows - 1.0);
    }

    private static double longitude(int col, int cols) {
        return WEST + (EAST - WEST) * col / (cols - 1.0);
    }

    private static boolean insideUs(double lat, double lon) {
        boolean inside = false;
        for (int i = 0, j = US_OUTLINE.length - 1; i < US_OUTLINE.length; j = i++) {
            double latI = US_OUTLINE[i][0], lonI = US_OUTLINE[i][1];
            double latJ = US_OUTLINE[j][0], lonJ = US_OUTLINE[j][1];
            if ((lonI > lon) != (lonJ > lon)
                    && lat < latI + (latJ - latI) * (lon - lonI) / (lonJ - lonI)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
