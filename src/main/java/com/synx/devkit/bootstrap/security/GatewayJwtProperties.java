package com.synx.devkit.bootstrap.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Trusted gateway identity-token configuration. */
@Validated
@ConfigurationProperties("devkit.security")
public class GatewayJwtProperties {
    @NotBlank
    private String issuer;
    @NotBlank
    private String jwkSetUri;
    @NotBlank
    private String audience = "devkit-sync-api";
    @NotBlank
    private String upstreamIssuer;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getUpstreamIssuer() {
        return upstreamIssuer;
    }

    public void setUpstreamIssuer(String upstreamIssuer) {
        this.upstreamIssuer = upstreamIssuer;
    }
}
