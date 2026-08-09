package com.synx.devkit.gateway.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** External identity-provider settings used only to validate Keycloak access tokens. */
@Validated
@ConfigurationProperties("devkit.keycloak")
public class KeycloakJwtProperties {
    @NotBlank
    private String issuer;

    @NotBlank
    private String jwkSetUri;

    @NotBlank
    private String audience = "devkit-sync-gateway";

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
}
