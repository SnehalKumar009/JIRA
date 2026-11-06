package dev.tracker.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tracker.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "tracker.rate-limit.anonymous-per-minute=3")
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTests {

    @LocalServerPort
    int port;

    @Test
    void anonymousCallersAreLimitedPerIpWithRetryAfter() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/system/info")).build();

        for (int i = 0; i < 3; i++) {
            assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
        HttpResponse<String> limited = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(limited.headers().firstValue("Retry-After")).isPresent();
        assertThat(limited.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    }
}
