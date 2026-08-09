package com.synx.devkit.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.synx.devkit.identity.adapter.in.web.SyncHeaderResolver;
import com.synx.devkit.support.PostgresTestSupport;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Exercises the real Nimbus decoder instead of Spring Security's mock Jwt. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GatewayJwtSecurityIT extends PostgresTestSupport {
    private static final String GATEWAY_ISSUER = "https://gateway.test";
    private static final String KEYCLOAK_ISSUER = "https://keycloak.test/realms/devkit";
    private static final RSAKey SIGNING_KEY = createKey("gateway-key");
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Autowired
    MockMvc mvc;
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
    void clearIdentityData() {
        jdbc.sql("DELETE FROM audit_events").update();
        jdbc.sql("DELETE FROM devices").update();
        jdbc.sql("DELETE FROM accounts").update();
    }

    @Test
    void acceptsAValidSignedGatewayToken() throws Exception {
        mvc.perform(sessionRequest(token(SIGNING_KEY, GATEWAY_ISSUER,
                        List.of("devkit-sync-api"), KEYCLOAK_ISSUER, Instant.now().plusSeconds(900))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value("signed-device"));
    }

    @Test
    void rejectsWrongSignatureIssuerAudienceAndUpstreamExpiry() throws Exception {
        RSAKey attackerKey = createKey("attacker-key");
        mvc.perform(sessionRequest(token(attackerKey, GATEWAY_ISSUER,
                        List.of("devkit-sync-api"), KEYCLOAK_ISSUER, Instant.now().plusSeconds(900))))
                .andExpect(status().isUnauthorized());

        mvc.perform(sessionRequest(token(SIGNING_KEY, "https://wrong-issuer.test",
                        List.of("devkit-sync-api"), KEYCLOAK_ISSUER, Instant.now().plusSeconds(900))))
                .andExpect(status().isUnauthorized());

        mvc.perform(sessionRequest(token(SIGNING_KEY, GATEWAY_ISSUER,
                        List.of("another-api"), KEYCLOAK_ISSUER, Instant.now().plusSeconds(900))))
                .andExpect(status().isUnauthorized());

        mvc.perform(sessionRequest(token(SIGNING_KEY, GATEWAY_ISSUER,
                        List.of("devkit-sync-api"), KEYCLOAK_ISSUER, Instant.now().minusSeconds(1))))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder sessionRequest(
            String token) {
        return get("/v1/sync/session")
                .header("Authorization", "Bearer " + token)
                .header(SyncHeaderResolver.DEVICE_HEADER, "signed-device")
                .header(SyncHeaderResolver.PROTOCOL_HEADER, "1");
    }

    private static String token(
            RSAKey key,
            String issuer,
            List<String> audience,
            String upstreamIssuer,
            Instant upstreamExpiry) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("signed-keycloak-subject")
                .issueTime(Date.from(now.minusSeconds(5)))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("upstream_iss", upstreamIssuer)
                .claim("upstream_exp", Date.from(upstreamExpiry))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static RSAKey createKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception error) {
            throw new IllegalStateException("cannot create test RSA key", error);
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
            throw new IllegalStateException("cannot start test JWKS server", error);
        }
    }
}
