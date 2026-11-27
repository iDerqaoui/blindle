package me.blindle.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Connexion admin par mot de passe unique : session admin, page des personnages accessible. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "blindle.admin.password=s3cret-test")
class AdminPasswordIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    /** Cookie XSRF-TOKEN posé par le serveur sur une première requête. */
    private String xsrf() {
        List<String> cookies = rest.getForEntity("/api/me", String.class).getHeaders().get(HttpHeaders.SET_COOKIE);
        return cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow()
                .split(";")[0].substring("XSRF-TOKEN=".length());
    }

    private ResponseEntity<String> login(String password, String xsrf) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf);
        h.add("X-XSRF-TOKEN", xsrf);
        return rest.exchange("/api/auth/admin-password", HttpMethod.POST,
                new HttpEntity<>("{\"password\":\"" + password + "\"}", h), String.class);
    }

    @Test
    void passwordOpensAnAdminSession() {
        assertThat(rest.getForObject("/api/auth/providers", List.class)).containsExactly("password");
        String xsrf = xsrf();

        assertThat(login("wrong", xsrf).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<String> ok = login("s3cret-test", xsrf);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        String session = ok.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow().split(";")[0];

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, session);
        ResponseEntity<Map> me = rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(h), Map.class);
        assertThat(me.getBody()).containsEntry("admin", true).containsEntry("displayName", "Admin");
        ResponseEntity<List> universes = rest.exchange("/api/admin/curation/universes", HttpMethod.GET,
                new HttpEntity<>(h), List.class);
        assertThat(universes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(universes.getBody()).hasSize(8);
    }

    @Test
    void loginWithoutCsrfTokenIsRefused() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> r = rest.exchange("/api/auth/admin-password", HttpMethod.POST,
                new HttpEntity<>("{\"password\":\"s3cret-test\"}", h), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
