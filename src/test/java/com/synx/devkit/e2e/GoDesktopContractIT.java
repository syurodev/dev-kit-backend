package com.synx.devkit.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.synx.devkit.support.PostgresTestSupport;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Runs the production Go HTTP client against a real Spring HTTP server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GoDesktopContractIT extends PostgresTestSupport {
    private static final String GATEWAY_ISSUER = "https://gateway-contract.test";
    private static final String KEYCLOAK_ISSUER = "https://keycloak-contract.test/realms/devkit";
    private static final RSAKey SIGNING_KEY = createKey();
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @LocalServerPort
    int serverPort;
    @Autowired
    JdbcClient jdbc;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("devkit.security.issuer", () -> GATEWAY_ISSUER);
        registry.add("devkit.security.upstream-issuer", () -> KEYCLOAK_ISSUER);
        registry.add("devkit.security.audience", () -> "devkit-sync-api");
        registry.add("devkit.security.jwk-set-uri", () ->
                "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
    }

    @BeforeEach
    void clearBusinessData() {
        jdbc.sql("DELETE FROM audit_events").update();
        jdbc.sql("DELETE FROM device_enrollments").update();
        jdbc.sql("DELETE FROM entity_heads").update();
        jdbc.sql("DELETE FROM replication_log").update();
        jdbc.sql("DELETE FROM account_storage_usage").update();
        jdbc.sql("DELETE FROM devices").update();
        jdbc.sql("DELETE FROM accounts").update();
    }

    @Test
    void productionGoTransportCompletesThePhaseAContract() throws Exception {
        Path desktopRepository = desktopRepository();
        Assumptions.assumeTrue(Files.isRegularFile(desktopRepository.resolve("go.mod")),
                "desktop repository is unavailable; set DEVKIT_DESKTOP_REPO to run this gate");
        seedContractDevices();

        ProcessBuilder builder = new ProcessBuilder(
                "go", "test", "./internal/sync",
                "-run", "^TestBackendContractE2E$", "-count=1");
        builder.directory(desktopRepository.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("DEVKIT_BACKEND_E2E_URL", "http://127.0.0.1:" + serverPort);
        builder.environment().put("DEVKIT_BACKEND_E2E_TOKEN", signedToken());

        Process process = builder.start();
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new AssertionError("Go desktop contract test timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
    }

    private void seedContractDevices() {
        // Enrollment has its own API integration coverage. This cross-repo gate
        // pre-registers both fixtures so it can remain focused on the existing
        // Go replication transport until the desktop enrollment UI is added.
        var accountId = jdbc.sql("""
                        INSERT INTO accounts(identity_subject, created_at, updated_at)
                        VALUES ('go-contract-subject', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        RETURNING id
                        """)
                .query(java.util.UUID.class)
                .single();
        for (String deviceId : List.of("contract-device-a", "contract-device-b")) {
            jdbc.sql("""
                            INSERT INTO devices(
                                account_id, device_id, status, protocol_version,
                                first_seen_at, last_seen_at)
                            VALUES (
                                :accountId, :deviceId, 'active', 1,
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """)
                    .param("accountId", accountId)
                    .param("deviceId", deviceId)
                    .update();
        }
    }

    private static Path desktopRepository() {
        String configured = System.getenv("DEVKIT_DESKTOP_REPO");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        // The default matches this workspace layout:
        // Workspace/PERSONAL/dev-kit (backend) and Workspace/dev-kit (desktop).
        return Path.of(System.getProperty("user.dir"), "..", "..", "dev-kit")
                .toAbsolutePath()
                .normalize();
    }

    private static String signedToken() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(GATEWAY_ISSUER)
                .audience(List.of("devkit-sync-api"))
                .subject("go-contract-subject")
                .issueTime(Date.from(now.minusSeconds(5)))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("upstream_iss", KEYCLOAK_ISSUER)
                .claim("upstream_exp", Date.from(now.plusSeconds(900)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(SIGNING_KEY));
        return jwt.serialize();
    }

    private static RSAKey createKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("go-contract-key").generate();
        } catch (Exception error) {
            throw new IllegalStateException("cannot create contract RSA key", error);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            byte[] body = new JWKSet(SIGNING_KEY.toPublicJWK())
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception error) {
            throw new IllegalStateException("cannot start contract JWKS server", error);
        }
    }
}
