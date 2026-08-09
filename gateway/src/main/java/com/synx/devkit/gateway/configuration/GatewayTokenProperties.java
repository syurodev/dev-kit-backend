package com.synx.devkit.gateway.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Settings for short-lived identity JWTs that only the backend accepts. */
@Validated
@ConfigurationProperties("devkit.gateway.token")
public class GatewayTokenProperties {
    @NotBlank
    private String issuer;

    @NotBlank
    private String audience = "devkit-sync-api";

    @NotNull
    @DurationMin(seconds = 5)
    @DurationMax(seconds = 120)
    private Duration ttl = Duration.ofSeconds(45);

    @NotBlank
    private String keyId = "devkit-gateway-local";

    @NotBlank
    private String privateKeyPath;

    @NotBlank
    private String publicKeyPath;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }
}
