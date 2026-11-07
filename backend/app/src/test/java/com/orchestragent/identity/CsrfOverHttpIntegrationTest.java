package com.orchestragent.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.orchestragent.PostgresTestConfiguration;
import com.orchestragent.identity.application.port.LoginCodeGenerator;
import com.orchestragent.identity.application.port.LoginCodeSender;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * CSRF behavior over a real HTTP server with a real cookie jar, like a browser. MockMvc cannot show two of
 * the things checked here: the {@code /error} forward that follows a rejected request, and how the cookie
 * jar evolves across consecutive requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class CsrfOverHttpIntegrationTest {

    private static final String CODE = "907314";

    @LocalServerPort
    int port;

    @MockitoBean
    LoginCodeSender sender;

    @MockitoBean
    LoginCodeGenerator generator;

    private CookieManager cookies;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        given(generator.generate()).willReturn(CODE);
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    @Test
    void consecutiveAuthenticatedWritesKeepWorkingWithoutTheTokenRotating() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        get("/api/health");
        String tokenBeforeLogin = cookie("XSRF-TOKEN");
        assertThat(post("/api/auth/code", "{\"email\":\"" + email + "\"}", true).statusCode()).isEqualTo(204);
        assertThat(post("/api/auth/verify", "{\"email\":\"" + email + "\",\"code\":\"" + CODE + "\"}", true).statusCode())
                .isEqualTo(200);
        assertThat(cookie("oa_session")).isNotBlank();

        for (int i = 0; i < 4; i++) {
            String other = "user-" + UUID.randomUUID() + "@example.com";
            assertThat(post("/api/auth/code", "{\"email\":\"" + other + "\"}", true).statusCode())
                    .as("authenticated write #%d", i + 1)
                    .isEqualTo(204);
        }
        assertThat(get("/api/me").statusCode()).isEqualTo(200);

        assertThat(cookie("XSRF-TOKEN")).as("token is not rotated by authenticated requests").isEqualTo(tokenBeforeLogin);
    }

    @Test
    void missingCsrfTokenIsAForbiddenNotAnUnauthorized() throws Exception {
        get("/api/health");

        HttpResponse<String> response = post("/api/auth/code", "{\"email\":\"nobody@example.com\"}", false);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void wrongCsrfTokenIsForbiddenEvenWhenLoggedIn() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        get("/api/health");
        post("/api/auth/code", "{\"email\":\"" + email + "\"}", true);
        post("/api/auth/verify", "{\"email\":\"" + email + "\",\"code\":\"" + CODE + "\"}", true);

        HttpRequest request = HttpRequest.newBuilder(uri("/api/me/tokens"))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", "not-the-token")
                .POST(BodyPublishers.ofString("{\"name\":\"cli\"}"))
                .build();

        assertThat(client.send(request, BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json, boolean withCsrfHeader) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json));
        if (withCsrfHeader) {
            request.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
        }
        return client.send(request.build(), BodyHandlers.ofString());
    }

    private String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElse("");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
