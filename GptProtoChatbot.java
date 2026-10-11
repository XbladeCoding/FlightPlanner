import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Sends FlightPlanner chat messages to GPTProto's OpenAI-compatible chat API. */
public final class GptProtoChatbot {
    private static final URI CHAT_ENDPOINT = URI.create("https://gptproto.com/v1/chat/completions");
    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final String SYSTEM_PROMPT = "You are FlightPlanner's helpful assistant. "
            + "Explain this app's route modes, aircraft settings, weather layers, and terrain. "
            + "Use the supplied dashboard state for current values and do not invent measurements. "
            + "Describe estimates clearly. This project is for visualization and learning, "
            + "not certified flight-navigation guidance. Keep replies concise.";

    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final List<ChatMessage> history = new ArrayList<>();

    public GptProtoChatbot() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .build(),
                new ObjectMapper(),
                CHAT_ENDPOINT,
                System.getenv("GPTPROTO_API_KEY"),
                environmentOrDefault("GPTPROTO_MODEL", DEFAULT_MODEL));
    }

    GptProtoChatbot(HttpClient httpClient, ObjectMapper json, URI endpoint,
            String apiKey, String model) {
        this.httpClient = httpClient;
        this.json = json;
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = endpoint;
    }

    private final URI endpoint;

    /** Makes one non-streaming request and keeps up to six turns for follow-up questions. */
    public synchronized String reply(String userMessage, String dashboardContext)
            throws IOException, InterruptedException {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("Enter a message before sending it.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Set GPTPROTO_API_KEY before using the chat assistant.");
        }

        ArrayNode messages = json.createArrayNode();
        String systemContent = SYSTEM_PROMPT;
        if (dashboardContext != null && !dashboardContext.isBlank()) {
            systemContent += "\n\nCurrent FlightPlanner dashboard state:\n" + dashboardContext;
        }
        addMessage(messages, "system", systemContent);
        for (ChatMessage message : history) {
            addMessage(messages, message.role(), message.content());
        }
        addMessage(messages, "user", userMessage.trim());

        ObjectNode payload = json.createObjectNode();
        payload.put("model", model);
        payload.put("stream", false);
        payload.set("messages", messages);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(75))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
        JsonNode responseJson;
        try {
            responseJson = json.readTree(response.body());
        } catch (IOException error) {
            throw new IOException("GPTProto returned a response that was not valid JSON.", error);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = responseJson.path("error").path("message").asText("");
            if (detail.isBlank()) detail = "HTTP " + response.statusCode();
            throw new IOException("GPTProto request failed: " + detail);
        }

        String answer = readAssistantText(responseJson.path("choices").path(0)
                .path("message").path("content"));
        if (answer.isBlank()) {
            throw new IOException("GPTProto returned an empty chat response.");
        }

        history.add(new ChatMessage("user", userMessage.trim()));
        history.add(new ChatMessage("assistant", answer));
        while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
        return answer;
    }

    private void addMessage(ArrayNode messages, String role, String content) {
        ObjectNode message = messages.addObject();
        message.put("role", role);
        message.put("content", content);
    }

    private String readAssistantText(JsonNode content) {
        if (content == null || content.isNull()) return "";
        if (content.isTextual()) return content.asText();
        if (!content.isArray()) return "";

        StringBuilder result = new StringBuilder();
        for (JsonNode part : content) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                if (result.length() > 0) result.append('\n');
                result.append(text);
            }
        }
        return result.toString();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record ChatMessage(String role, String content) {}
}
