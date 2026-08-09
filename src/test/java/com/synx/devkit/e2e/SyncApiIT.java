package com.synx.devkit.e2e;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synx.devkit.identity.adapter.in.web.SyncHeaderResolver;
import com.synx.devkit.replication.adapter.in.web.PushOperationDto;
import com.synx.devkit.replication.adapter.in.web.PushRequest;
import com.synx.devkit.replication.adapter.in.web.ReplicationEnvelopeDto;
import com.synx.devkit.shared.domain.WireLimits;
import com.synx.devkit.support.PostgresTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncApiIT extends PostgresTestSupport {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clearBusinessData() {
        jdbc.sql("DELETE FROM audit_events").update();
        jdbc.sql("DELETE FROM entity_heads").update();
        jdbc.sql("DELETE FROM replication_log").update();
        jdbc.sql("DELETE FROM devices").update();
        jdbc.sql("DELETE FROM accounts").update();
    }

    @Test
    void sessionPushPullAndConflictMatchDesktopContract() throws Exception {
        String accountId = establishSession("device-a");

        PushRequest first = pushRequest(accountId, "device-a", "idem-a", "op-a", 1);
        mvc.perform(post("/v1/sync/push")
                        .with(identity())
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent_idempotency_keys[0]").value("idem-a"))
                .andExpect(jsonPath("$.conflicts").isEmpty());

        mvc.perform(get("/v1/sync/pull")
                        .with(identity())
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .queryParam("cursor", "")
                        .queryParam("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(accountId))
                .andExpect(jsonPath("$.device_id").value("device-a"))
                .andExpect(jsonPath("$.protocol_version").value(1))
                .andExpect(jsonPath("$.operations[0].idempotency_key").value("idem-a"))
                .andExpect(jsonPath("$.operations[0].envelope.device_id").value("device-a"))
                .andExpect(jsonPath("$.next_cursor").value(org.hamcrest.Matchers.startsWith("v1.")));

        // A second device for the same subject sees the same account, then
        // pushing the already-used version produces metadata-only conflict.
        establishSession("device-b");
        PushRequest stale = pushRequest(accountId, "device-b", "idem-b", "op-b", 1);
        mvc.perform(post("/v1/sync/push")
                        .with(identity())
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-b")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(stale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent_idempotency_keys").isEmpty())
                .andExpect(jsonPath("$.conflicts[0].record_id").value("record-1"))
                .andExpect(jsonPath("$.conflicts[0].local_change_key").value("idem-b"))
                .andExpect(jsonPath("$.conflicts[0].remote_change_key").value("idem-a"));
    }

    @Test
    void accountAndDeviceMetadataCannotBeForged() throws Exception {
        String accountId = establishSession("device-a");
        PushRequest forged = pushRequest(accountId, "other-device", "idem-x", "op-x", 1);
        mvc.perform(post("/v1/sync/push")
                        .with(identity())
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(forged)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void revokedDeviceIsRejectedAndAccountsCannotReadEachOther() throws Exception {
        String firstAccount = establishSession("subject-1", "device-a");
        PushRequest first = pushRequest(firstAccount, "device-a", "idem-a", "op-a", 1);
        mvc.perform(post("/v1/sync/push")
                        .with(identity("subject-1"))
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(first)))
                .andExpect(status().isOk());

        String secondAccount = establishSession("subject-2", "device-b");
        mvc.perform(get("/v1/sync/pull")
                        .with(identity("subject-2"))
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-b")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .queryParam("cursor", "")
                        .queryParam("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(secondAccount))
                .andExpect(jsonPath("$.operations").isEmpty());

        jdbc.sql("UPDATE devices SET status = 'revoked' WHERE device_id = 'device-a'").update();
        mvc.perform(get("/v1/sync/session")
                        .with(identity("subject-1"))
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/sync/push")
                        .with(identity("subject-1"))
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(first)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/sync/pull")
                        .with(identity("subject-1"))
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .queryParam("cursor", "")
                        .queryParam("limit", "100"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownJsonFieldIsRejected() throws Exception {
        establishSession("device-a");
        mvc.perform(post("/v1/sync/push")
                        .with(identity())
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operations\":[],\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    void duplicateTrailingAndOversizedJsonAreRejected() throws Exception {
        establishSession("device-a");
        var request = post("/v1/sync/push")
                .with(identity())
                .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON);

        mvc.perform(request.content("{\"operations\":[],\"operations\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(request.content("{\"operations\":[]} true"))
                .andExpect(status().isBadRequest());
        mvc.perform(request.content(new byte[WireLimits.MAX_REQUEST_BYTES + 1]))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    void pullResponseStaysInsideClientWireBudget() throws Exception {
        String accountId = establishSession("device-a");
        byte[] ciphertext = new byte[WireLimits.MAX_CIPHERTEXT_BYTES];
        for (int index = 0; index < 5; index++) {
            PushRequest operation = pushRequest(
                    accountId,
                    "device-a",
                    "idem-large-" + index,
                    "op-large-" + index,
                    "record-large-" + index,
                    ciphertext);
            mvc.perform(post("/v1/sync/push")
                            .with(identity())
                            .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                            .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsBytes(operation)))
                    .andExpect(status().isOk());
        }

        byte[] response = mvc.perform(get("/v1/sync/pull")
                        .with(identity())
                        .header(SyncHeaderResolver.DEVICE_HEADER, "device-a")
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1")
                        .queryParam("cursor", "")
                        .queryParam("limit", "1000"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(response.length <= WireLimits.MAX_RESPONSE_BYTES);
        assertTrue(objectMapper.readTree(response).get("operations").size() < 5);
    }

    private String establishSession(String deviceId) throws Exception {
        return establishSession("keycloak-subject-1", deviceId);
    }

    private String establishSession(String subject, String deviceId) throws Exception {
        String json = mvc.perform(get("/v1/sync/session")
                        .with(identity(subject))
                        .header(SyncHeaderResolver.DEVICE_HEADER, deviceId)
                        .header(SyncHeaderResolver.PROTOCOL_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_id").value(deviceId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json).get("account_id").asString();
    }

    private static PushRequest pushRequest(
            String accountId,
            String deviceId,
            String idempotencyKey,
            String operationId,
            long entityVersion) {
        byte[] ciphertext = "{\"v\":2,\"ciphertext\":\"opaque\"}"
                .getBytes(StandardCharsets.UTF_8);
        var envelope = new ReplicationEnvelopeDto(
                "record-1",
                "note",
                1,
                2,
                accountId,
                deviceId,
                1,
                operationId,
                entityVersion,
                "update",
                ciphertext);
        return new PushRequest(List.of(new PushOperationDto(
                idempotencyKey, "update", envelope)));
    }

    private static PushRequest pushRequest(
            String accountId,
            String deviceId,
            String idempotencyKey,
            String operationId,
            String recordId,
            byte[] ciphertext) {
        var envelope = new ReplicationEnvelopeDto(
                recordId,
                "note",
                1,
                2,
                accountId,
                deviceId,
                1,
                operationId,
                1,
                "update",
                ciphertext);
        return new PushRequest(List.of(new PushOperationDto(
                idempotencyKey, "update", envelope)));
    }

    private static JwtRequestPostProcessor identity() {
        return identity("keycloak-subject-1");
    }

    private static JwtRequestPostProcessor identity(String subject) {
        Instant now = Instant.now();
        return jwt().jwt(token -> token
                .subject(subject)
                .issuer("https://gateway.test")
                .audience(List.of("devkit-sync-api"))
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .claim("upstream_iss", "https://keycloak.test/realms/devkit")
                .claim("upstream_exp", now.plusSeconds(3600))
                .claim("email", "developer@example.test")
                .claim("preferred_username", "developer"));
    }
}
