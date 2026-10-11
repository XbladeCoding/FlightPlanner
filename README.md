# FlightPlanner

## Run it

Use JDK 21 and Maven. Set your GPTProto key for chat and your WeatherAPI key if you want LIVE weather, then run from this directory:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
export GPTPROTO_API_KEY="your_gptproto_key"
export GPTPROTO_MODEL="gpt-4o"
export WEATHERAPI_KEY="your_weatherapi_key"
mvn clean compile exec:java
```

`GPTPROTO_MODEL` is optional; it defaults to `gpt-4o`. Set it to another model name enabled for your GPTProto account if needed. The GPTProto key is read from the environment and is never stored in the Java source. The chat sends the user's message plus current route and map statistics to GPTProto.

The app opens on the black-and-white welcome screen. Choose **Get Started** to open the dashboard.

## Dashboard

- Choose **ALGO** for the original algorithm-generated 64×64 world or **LIVE** for current WeatherAPI weather over the U.S. grid.
- The aircraft dropdown lists common planes with their typical maximum and cruise speeds.
- Click **Topography**, **Wind**, or **Precipitation** at the top to switch the full-width map in place.
- Topography uses a green-to-red elevation gradient, and precipitation uses a blue intensity scale.
- In **ALGO** mode, drag the `S` or `D` marker on the map to move the start or destination. **LIVE** uses a default route across the U.S.
- Both modes include fastest, safest, and recommended paths. The existing pathfinding algorithm remains unchanged; only its route drawing is curved.
- The welcome screen plane follows a looping cubic curve and reveals its trail as it flies.
- The bottom FlightPlanner chat panel sends questions and current dashboard context to GPTProto, keeping recent turns for follow-up questions.

## Live data and CSV files

Weather comes from WeatherAPI's single-location current-conditions endpoint; the project makes no Open-Meteo requests. The program requests weather at sample points inside the U.S. outline, then interpolates the samples onto the 64×64 map. Set `WEATHERAPI_KEY` before selecting **LIVE** or pressing **REFRESH**.

Live weather values are saved to `assets/danger_matrix_64x64.csv`, `wind_speed_64x64.csv`, `wind_direction_64x64.csv`, `precipitation_64x64.csv`, and `temperature_64x64.csv`. Elevation comes from the bundled `assets/topography_64x64.csv`, sampled from the provided U.S. relief image in `assets/us_topo_shi.jpeg`. Topography is local and makes no network requests.

All maps stay on the same dashboard page, fill the available width, and can scroll vertically on shorter screens; selecting a map never opens another window. Route and elevation statistics sit centered below the map.

## How the GPTProto Java code works

- `UserInterface.java` listens for the Send button or Enter key. It collects the current route, selected aircraft, data mode, and map statistics for context.
- A background `Thread` calls `GptProtoChatbot.reply(...)`, so waiting for the network does not freeze the Swing window.
- `GptProtoChatbot.java` uses Java's `HttpClient` to POST a chat request to GPTProto. Jackson's `ObjectMapper` turns Java message objects into JSON and parses GPTProto's JSON reply.
- `GPTPROTO_API_KEY` is read with `System.getenv`, so the secret stays outside the source code. The chatbot keeps up to six exchanges in memory for follow-up questions.
- `SwingUtilities.invokeLater` updates the chat transcript after the network thread finishes; Swing components should be changed on Swing's event-dispatch thread.
