package me.blindle.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Étape 10 : retrait à chaud d'un thème d'un canal via l'API d'administration (§8). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "blindle.admin.token=test-secret")
class ThemeWithdrawalIntegrationTest {

    private static final String BLOCK = "/api/admin/themes/pays-du-monde/channels/web/block";

    @Autowired
    TestRestTemplate rest;

    private HttpEntity<Void> withToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Admin-Token", token);
        return new HttpEntity<>(headers);
    }

    private List<String> presetIds() {
        List<?> presets = rest.getForObject("/api/presets", List.class);
        return presets.stream().map(p -> (String) ((Map<?, ?>) p).get("id")).toList();
    }

    @Test
    void withdrawnThemeDisappearsFromTheCatalogUntilRestored() {
        assertThat(presetIds()).contains("pays-classique", "manga-classique");

        ResponseEntity<String> refused = rest.exchange(BLOCK, HttpMethod.POST, withToken("wrong"), String.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(presetIds()).contains("pays-classique");

        ResponseEntity<Map> blocked = rest.exchange(BLOCK, HttpMethod.POST, withToken("test-secret"), Map.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(presetIds()).doesNotContain("pays-classique", "solo-pays-bots").contains("manga-classique");
        List<?> daily = rest.getForObject("/api/daily", List.class);
        assertThat(daily).noneMatch(d -> "defi-pays".equals(((Map<?, ?>) d).get("presetId")));

        rest.exchange(BLOCK, HttpMethod.DELETE, withToken("test-secret"), Map.class);
        assertThat(presetIds()).contains("pays-classique");
    }

    @Test
    void unknownThemeOrChannelIsABadRequest() {
        ResponseEntity<String> r = rest.exchange("/api/admin/themes/nope/channels/web/block", HttpMethod.POST,
                withToken("test-secret"), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
