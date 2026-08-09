package com.synx.devkit.replication.adapter.in.web;

import com.synx.devkit.identity.adapter.in.web.SyncHeaderResolver;
import com.synx.devkit.identity.adapter.in.web.SyncRequestAuthorizer;
import com.synx.devkit.replication.application.port.in.PushReplicationCommand;
import com.synx.devkit.replication.application.port.in.PushReplicationUseCase;
import com.synx.devkit.shared.adapter.in.web.RequestIdFilter;
import com.synx.devkit.shared.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sync/push")
public final class PushController {
    private final SyncRequestAuthorizer authorizer;
    private final PushReplicationUseCase push;

    public PushController(SyncRequestAuthorizer authorizer, PushReplicationUseCase push) {
        this.authorizer = authorizer;
        this.push = push;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public PushResponse push(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(SyncHeaderResolver.DEVICE_HEADER) String deviceId,
            @RequestHeader(SyncHeaderResolver.PROTOCOL_HEADER) String protocolText,
            @RequestBody PushRequest request,
            HttpServletRequest servletRequest) {
        var context = authorizer.authorize(jwt, deviceId, protocolText);
        if (request == null || request.operations() == null) {
            throw new ValidationException("push operations are required");
        }
        var operations = request.operations().stream()
                .map(operation -> {
                    if (operation == null) {
                        throw new ValidationException("push operation is required");
                    }
                    return operation.toDomain();
                })
                .toList();
        var result = push.push(new PushReplicationCommand(
                context, operations, requestId(servletRequest)));
        List<ConflictResponse> conflicts = result.conflicts().stream()
                .map(conflict -> new ConflictResponse(
                        conflict.id(),
                        conflict.recordId(),
                        conflict.recordType(),
                        conflict.localChangeKey(),
                        conflict.remoteChangeKey(),
                        conflict.detectedAt()))
                .toList();
        return new PushResponse(result.sentIdempotencyKeys(), conflicts);
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
