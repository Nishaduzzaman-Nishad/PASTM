package service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.WellnessTip;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WellnessTipFetcher {

    private static final String URL = "https://dummyjson.com/quotes/random";
    private static final String USER_AGENT = "PASTM/1.0 (Educational Project)";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public WellnessTip fetchRandom() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            throw new IOException("Rate limited (HTTP 429). Try again in a moment.");
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from API");
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new IOException("Empty response from API");
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            throw new IOException("Unexpected non-JSON response (likely HTML)");
        }

        try {
            return mapper.readValue(body, WellnessTip.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IOException("Invalid JSON from API: " + ex.getOriginalMessage(), ex);
        }
    }
}