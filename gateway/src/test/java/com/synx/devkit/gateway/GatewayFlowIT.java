package com.synx.devkit.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.synx.devkit.gateway.security.RevokedDeviceDenylist;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayFlowIT {
    private static final String KEYCLOAK_ISSUER = "https://keycloak.test/realms/devkit";
    private static final String GATEWAY_ISSUER = "https://gateway.test";
    private static final RSAKey KEYCLOAK_KEY = generateKey("keycloak-test-key");
    private static final RSAKey GATEWAY_KEY = generateKey("gateway-test-key");
    private static final Path KEY_DIRECTORY = createGatewayPemFiles();
    private static final AtomicReference<Map<String, List<String>>> BACKEND_HEADERS =
            new AtomicReference<>();
    private static final HttpServer KEYCLOAK_JWKS = startKeycloakJwks();
    private static final HttpServer BACKEND = startBackend();

    @LocalServerPort
    private int gatewayPort;

    @MockitoBean
    private RevokedDeviceDenylist revokedDeviceDenylist;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("devkit.keycloak.issuer", () -> KEYCLOAK_ISSUER);
        registry.add("devkit.keycloak.jwk-set-uri", () -> url(KEYCLOAK_JWKS, "/jwks"));
        registry.add("devkit.keycloak.audience", () -> "devkit-sync-gateway");
        registry.add("devkit.gateway.token.issuer", () -> GATEWAY_ISSUER);
        registry.add("devkit.gateway.token.audience", () -> "devkit-sync-api");
        registry.add("devkit.gateway.token.ttl", () -> "45s");
        registry.add("devkit.gateway.token.key-id", () -> GATEWAY_KEY.getKeyID());
        registry.add(
                "devkit.gateway.token.private-key-path",
                () -> KEY_DIRECTORY.resolve("private.pem").toString());
        registry.add(
                "devkit.gateway.token.public-key-path",
                () -> KEY_DIRECTORY.resolve("public.pem").toString());
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "test-backend");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].uri", () -> url(BACKEND, ""));
        registry.add(
                "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]",
                () -> "Path=/v1/sync/**");
    }

    @BeforeEach
    void resetCapturedRequest() {
        BACKEND_HEADERS.set(null);
    }

    @AfterAll
    static void stopServers() throws IOException {
        KEYCLOAK_JWKS.stop(0);
        BACKEND.stop(0);
        Files.deleteIfExists(KEY_DIRECTORY.resolve("private.pem"));
        Files.deleteIfExists(KEY_DIRECTORY.resolve("public.pem"));
        Files.deleteIfExists(KEY_DIRECTORY);
    }

    @Test
    void validKeycloakTokenIsReplacedWithGatewayIdentityJwt() throws Exception {
        String externalToken = keycloakToken(List.of("devkit-sync-gateway"));
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/v1/sync/session"))
                .header("Authorization", "Bearer " + externalToken)
                .header("Content-Type", "application/json")
                .header("X-Request-ID", "gateway-flow-test")
                .header("X-User-Id", "spoofed-user")
                .header("X-Roles", "spoofed-role")
                .header("Forwarded", "for=203.0.113.10")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, List<String>> headers = BACKEND_HEADERS.get();
        assertThat(headers).isNotNull();
        assertThat(first(headers, "X-User-Id")).isNull();
        assertThat(first(headers, "X-Roles")).isNull();
        assertThat(first(headers, "Forwarded")).isNull();
        assertThat(first(headers, "X-Request-ID")).isEqualTo("gateway-flow-test");

        String authorization = first(headers, "Authorization");
        assertThat(authorization).startsWith("Bearer ");
        assertThat(authorization).doesNotContain(externalToken);
        SignedJWT internal = SignedJWT.parse(authorization.substring("Bearer ".length()));
        assertThat(internal.verify(new RSASSAVerifier(GATEWAY_KEY.toRSAPublicKey()))).isTrue();
        assertThat(internal.getJWTClaimsSet().getIssuer()).isEqualTo(GATEWAY_ISSUER);
        assertThat(internal.getJWTClaimsSet().getAudience()).containsExactly("devkit-sync-api");
        assertThat(internal.getJWTClaimsSet().getSubject()).isEqualTo("keycloak-user-1");
        assertThat(internal.getJWTClaimsSet().getStringClaim("upstream_iss"))
                .isEqualTo(KEYCLOAK_ISSUER);
    }

    @Test
    void revokedDeviceIsRejectedBeforeBackend() throws Exception {
        when(revokedDeviceDenylist.isDenied(eq("keycloak-user-1"), eq("revoked-device-a")))
                .thenReturn(true);
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/v1/sync/session"))
                .header("Authorization", "Bearer " + keycloakToken(List.of("devkit-sync-gateway")))
                .header("Content-Type", "application/json")
                .header("X-DevKit-Device-ID", "revoked-device-a")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(BACKEND_HEADERS.get()).isNull();
    }

    @Test
    void wrongAudienceIsRejectedBeforeBackend() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/v1/sync/session"))
                .header("Authorization", "Bearer " + keycloakToken(List.of("another-service")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(BACKEND_HEADERS.get()).isNull();
    }

    @Test
    void publicJwksNeverContainsPrivateKeyMaterial() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(gatewayUri("/.well-known/jwks.json")).GET().build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("gateway-test-key", "\"kty\":\"RSA\"");
        assertThat(response.body()).doesNotContain("\"d\":", "\"p\":", "\"q\":");
    }

    private URI gatewayUri(String path) {
        return URI.create("http://127.0.0.1:" + gatewayPort + path);
    }

    private static String keycloakToken(List<String> audience) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(KEYCLOAK_ISSUER)
                .audience(audience)
                .subject("keycloak-user-1")
                .issueTime(Date.from(now.minusSeconds(1)))
                .notBeforeTime(Date.from(now.minusSeconds(1)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("preferred_username", "devkit-user")
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEYCLOAK_KEY.getKeyID()).build(),
                claims);
        token.sign(new RSASSASigner(KEYCLOAK_KEY));
        return token.serialize();
    }

    private static HttpServer startKeycloakJwks() {
        HttpServer server = newServer();
        server.createContext("/jwks", exchange -> sendJson(
                exchange,
                200,
                "{\"keys\":[" + KEYCLOAK_KEY.toPublicJWK().toJSONString() + "]}"));
        server.start();
        return server;
    }

    private static HttpServer startBackend() {
        HttpServer server = newServer();
        server.createContext("/v1/sync/", exchange -> {
            BACKEND_HEADERS.set(exchange.getRequestHeaders());
            exchange.getRequestBody().readAllBytes();
            sendJson(exchange, 200, "{\"status\":\"proxied\"}");
        });
        server.start();
        return server;
    }

    private static HttpServer newServer() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String url(HttpServer server, String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
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
            Path directory = Files.createTempDirectory("devkit-gateway-test-");
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

    private static String first(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }
}
