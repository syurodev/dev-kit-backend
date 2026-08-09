package com.synx.devkit.identity.adapter.in.web;

import com.synx.devkit.identity.adapter.in.security.GatewayIdentityResolver;
import com.synx.devkit.identity.application.port.in.AuthorizeSyncRequestCommand;
import com.synx.devkit.identity.application.port.in.AuthorizeSyncRequestUseCase;
import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Re-checks account/device authorization for every push and pull request. */
@Component
public final class SyncRequestAuthorizer {
    private final GatewayIdentityResolver identities;
    private final SyncHeaderResolver headers;
    private final AuthorizeSyncRequestUseCase authorize;

    public SyncRequestAuthorizer(
            GatewayIdentityResolver identities,
            SyncHeaderResolver headers,
            AuthorizeSyncRequestUseCase authorize) {
        this.identities = identities;
        this.headers = headers;
        this.authorize = authorize;
    }

    public AuthorizedSyncContext authorize(Jwt jwt, String deviceId, String protocolText) {
        var identity = identities.resolve(jwt);
        var syncHeaders = headers.resolve(deviceId, protocolText);
        return authorize.authorize(new AuthorizeSyncRequestCommand(
                identity.subject(), syncHeaders.deviceId(), syncHeaders.protocolVersion()));
    }
}
