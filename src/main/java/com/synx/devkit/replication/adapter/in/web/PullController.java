package com.synx.devkit.replication.adapter.in.web;

import com.synx.devkit.identity.adapter.in.web.SyncHeaderResolver;
import com.synx.devkit.identity.adapter.in.web.SyncRequestAuthorizer;
import com.synx.devkit.replication.application.port.in.PullReplicationQuery;
import com.synx.devkit.replication.application.port.in.PullReplicationUseCase;
import com.synx.devkit.shared.adapter.in.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sync/pull")
public final class PullController {
    private final SyncRequestAuthorizer authorizer;
    private final PullReplicationUseCase pull;

    public PullController(SyncRequestAuthorizer authorizer, PullReplicationUseCase pull) {
        this.authorizer = authorizer;
        this.pull = pull;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PullResponse pull(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(SyncHeaderResolver.DEVICE_HEADER) String deviceId,
            @RequestHeader(SyncHeaderResolver.PROTOCOL_HEADER) String protocolText,
            @RequestParam(defaultValue = "") String cursor,
            @RequestParam int limit,
            HttpServletRequest servletRequest) {
        var context = authorizer.authorize(jwt, deviceId, protocolText);
        Object requestId = servletRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        var result = pull.pull(new PullReplicationQuery(
                context,
                cursor,
                limit,
                requestId == null ? "unknown" : requestId.toString()));
        return new PullResponse(
                context.accountId().toString(),
                context.deviceId(),
                context.protocolVersion(),
                result.operations().stream().map(PushOperationDto::fromDomain).toList(),
                result.nextCursor());
    }
}
