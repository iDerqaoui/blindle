package me.blindle.curation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client Tenrai (https://api.tenrai.org/v1, données publiques MyAnimeList, successeur de Jikan),
 * mêmes appels que {@code tools/import-anime.mjs} : pause entre deux requêtes, nouvel essai sur
 * 429 / 5xx. Appels séquentiels (synchronisés) pour respecter la limite de débit.
 */
@Component
public class TenraiClient implements MalClient {

    private static final long PAUSE_MS = 800;
    private static final int ATTEMPTS = 4;

    private final String api;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public TenraiClient(@Value("${blindle.curation.api-url:https://api.tenrai.org/v1}") String api) {
        this.api = api;
    }

    @Override
    public synchronized List<MalCharacter> cast(List<Integer> animeIds) {
        Map<Integer, MalCharacter> found = new LinkedHashMap<>();
        for (int id : animeIds) {
            for (JsonNode e : get("/anime/" + id + "/characters")) {
                JsonNode c = e.path("character");
                int malId = c.path("mal_id").asInt();
                String role = e.path("role").asText(null);
                MalCharacter known = found.get(malId);
                if (known == null) {
                    found.put(malId, new MalCharacter(malId, c.path("name").asText(""), e.path("favorites").asInt(0),
                            role, c.path("images").path("jpg").path("image_url").asText(null)));
                } else if ("Main".equals(role)) {
                    found.put(malId, new MalCharacter(malId, known.name(), known.favourites(), "Main", known.image()));
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    @Override
    public synchronized Optional<AnimeInfo> anime(int animeId) {
        JsonNode a = get("/anime/" + animeId);
        String title = a.hasNonNull("title_english") ? a.get("title_english").asText() : a.path("title").asText(null);
        JsonNode images = a.path("images");
        String cover = images.path("webp").path("large_image_url").asText(null);
        if (cover == null) {
            cover = images.path("jpg").path("large_image_url").asText(null);
        }
        return Optional.of(new AnimeInfo(title, cover));
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(api + path))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 429 || res.statusCode() >= 500) {
                    long wait = res.headers().firstValueAsLong("retry-after").orElse(10L * attempt);
                    Thread.sleep(wait * 1000);
                    continue;
                }
                if (res.statusCode() != 200) {
                    throw new IllegalStateException("Tenrai : " + res.statusCode() + " sur " + path);
                }
                Thread.sleep(PAUSE_MS);
                return mapper.readTree(res.body()).path("data");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Tenrai injoignable : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompu", e);
        }
        throw new IllegalStateException("Tenrai indisponible");
    }
}
