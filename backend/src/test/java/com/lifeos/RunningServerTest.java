package com.lifeos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same checks, but through a socket.
 *
 * MockMvc runs the filter chain without a servlet container, so it cannot show
 * that the application binds a port, that a header written by a filter survives
 * to the wire, or that a browser's preflight is answered. Those are exactly the
 * three things that were the gateway's job and are now this service's.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("The service answers on a real port")
class RunningServerTest extends PostgresBackedTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("health answers without a token, which is what the platform calls")
    void healthIsReachable() {
        ResponseEntity<String> response = rest.getForEntity(url("/actuator/health"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("the hardening headers the gateway used to add are still on every response")
    void securityHeadersArePresent() {
        HttpHeaders headers = rest.getForEntity(url("/api/habits"), String.class).getHeaders();

        assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.getFirst("Content-Security-Policy")).contains("frame-ancestors 'none'");
    }

    @Test
    @DisplayName("a preflight from the configured web origin is allowed")
    void preflightFromTheWebAppIsAllowed() throws Exception {
        HttpResponse<String> response = preflight("http://localhost:5273");

        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(header(response, "Access-Control-Allow-Origin")).isEqualTo("http://localhost:5273");
        // Without this the browser drops the response even on a 200, and the app
        // looks broken with nothing at all in the server log.
        assertThat(header(response, "Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    @DisplayName("a preflight from anywhere else is not")
    void preflightFromElsewhereIsRejected() throws Exception {
        HttpResponse<String> response = preflight("https://not-your-app.example");

        assertThat(header(response, "Access-Control-Allow-Origin"))
                .as("an unlisted origin must not be echoed back")
                .isNull();
    }

    @Test
    @DisplayName("the Swagger UI is served, and is not blanked by the API's own CSP")
    void documentationIsUsable() {
        ResponseEntity<String> spec = rest.getForEntity(url("/v3/api-docs/1-auth"), String.class);
        assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spec.getBody()).contains("/api/auth/login");

        ResponseEntity<String> ui = rest.getForEntity(url("/swagger-ui/index.html"), String.class);
        assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ui.getHeaders().getFirst("Content-Security-Policy"))
                .as("default-src 'none' would leave the page blank")
                .isNull();
    }

    /**
     * Sent with the JDK's own client, not {@link TestRestTemplate}.
     *
     * TestRestTemplate's default factory is {@code HttpURLConnection}, which
     * silently drops {@code Origin} and {@code Access-Control-Request-Method} —
     * they are on its restricted list. The request then arrives as an ordinary
     * OPTIONS, gets an ordinary 200 with no CORS headers, and the test fails
     * against a server that is behaving perfectly.
     */
    private HttpResponse<String> preflight(String origin) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/api/auth/login")))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
