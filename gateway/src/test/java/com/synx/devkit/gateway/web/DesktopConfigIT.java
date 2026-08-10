package com.synx.devkit.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.synx.devkit.gateway.security.DesktopClientHeaderFilter;
import com.synx.devkit.gateway.security.DesktopConfigRateLimitFilter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DesktopConfigIT {
    private static final String TEST_OIDC_ISSUER = "https://auth.test/realms/devkit";
    private static final RSAKey GATEWAY_KEY = generateKey("gateway-desktop-config-test");
    private static final Path KEY_DIRECTORY = createGatewayPemFiles();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("devkit.keycloak.issuer", () -> "https://keycloak.test/realms/devkit");
        registry.add("devkit.keycloak.jwk-set-uri", () -> "https://keycloak.test/jwks");
        registry.add("devkit.keycloak.audience", () -> "devkit-sync-gateway");
        registry.add("devkit.gateway.token.issuer", () -> "https://gateway.test");
        registry.add("devkit.gateway.token.audience", () -> "devkit-sync-api");
        registry.add("devkit.gateway.token.ttl", () -> "45s");
        registry.add("devkit.gateway.token.key-id", () -> GATEWAY_KEY.getKeyID());
        registry.add(
                "devkit.gateway.token.private-key-path",
                () -> KEY_DIRECTORY.resolve("private.pem").toString());
        registry.add(
                "devkit.gateway.token.public-key-path",
                () -> KEY_DIRECTORY.resolve("public.pem").toString());
        registry.add("devkit.desktop.config.oidc-issuer", () -> TEST_OIDC_ISSUER);
        registry.add("devkit.desktop.config.config-version", () -> "1");
        registry.add("devkit.desktop.config.oidc-client-id", () -> "devkit-desktop");
        registry.add("devkit.desktop.config.oidc-scopes", () -> "openid profile email roles");
    }

    @AfterAll
    static void cleanup() throws Exception {
        Files.deleteIfExists(KEY_DIRECTORY.resolve("private.pem"));
        Files.deleteIfExists(KEY_DIRECTORY.resolve("public.pem"));
        Files.deleteIfExists(KEY_DIRECTORY);
    }

    @Test
    void desktopConfigHeaderFilterRunsBeforeRedisRateLimit() {
        List<Filter> filters = securityFilterChain.getFilters();
        int headerIndex = filterIndex(filters, DesktopClientHeaderFilter.class);
        int redisRateLimitIndex = filterIndex(filters, DesktopConfigRateLimitFilter.class);
        assertThat(headerIndex).isGreaterThan(-1).isLessThan(redisRateLimitIndex);
    }

    @Test
    void missingClientHeaderIsRejected() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(gatewayUri("/v1/desktop/config")).GET().build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("invalid_client_header");
    }

    @Test
    void invalidClientHeaderIsRejected() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/v1/desktop/config"))
                .header("X-DevKit-Client", "mobile/1.0.0")
                .GET()
                .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("invalid_client_header");
    }

    @Test
    void validClientHeaderReturnsDesktopConfig() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/v1/desktop/config"))
                .header("X-DevKit-Client", "desktop/0.1.0")
                .GET()
                .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"oidc_issuer\":\"" + TEST_OIDC_ISSUER + "\"");
        assertThat(response.body()).contains("\"config_version\":\"1\"");
        assertThat(response.body()).contains("\"oidc_client_id\":\"devkit-desktop\"");
        assertThat(response.body()).contains("\"oidc_scopes\":\"openid profile email roles\"");
    }

    private URI gatewayUri(String path) {
        return URI.create("http://127.0.0.1:" + gatewayPort + path);
    }

    private static int filterIndex(List<Filter> filters, Class<?> type) {
        for (int index = 0; index < filters.size(); index++) {
            if (type.isInstance(filters.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Path createGatewayPemFiles() {
        try {
            Path directory = Files.createTempDirectory("devkit-desktop-config-test-");
            Files.writeString(
                    directory.resolve("private.pem"),
                    pem("PRIVATE KEY", GATEWAY_KEY.toRSAPrivateKey().getEncoded()));
            Files.writeString(
                    directory.resolve("public.pem"),
                    pem("PUBLIC KEY", GATEWAY_KEY.toRSAPublicKey().getEncoded()));
            return directory;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        String payload = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + payload + "\n-----END " + type + "-----\n";
    }
}
