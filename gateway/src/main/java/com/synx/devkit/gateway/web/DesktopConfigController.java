package com.synx.devkit.gateway.web;

import com.synx.devkit.gateway.config.DesktopConfigProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves non-secret desktop bootstrap settings without authentication. */
@RestController
public class DesktopConfigController {
    private final DesktopConfigProperties properties;

    public DesktopConfigController(DesktopConfigProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/v1/desktop/config")
    public ResponseEntity<DesktopConfigResponse> config() {
        if (!StringUtils.hasText(properties.getOidcIssuer())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(new DesktopConfigResponse(
                properties.getConfigVersion(),
                properties.getOidcIssuer(),
                properties.getOidcClientId(),
                properties.getOidcScopes(),
                properties.getOidcAuthUrl(),
                properties.getOidcTokenUrl(),
                properties.getMinAppVersion(),
                properties.getLatestAppVersion(),
                properties.getUpdateUrl()));
    }
}
