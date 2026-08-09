package com.synx.devkit.gateway.web;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes only the public half of the key used for backend identity JWTs. */
@RestController
public class JwksController {
    private final RSAKey publicKey;

    public JwksController(RSAKey signingKey) {
        this.publicKey = signingKey.toPublicJWK();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(publicKey.toJSONObject()));
    }
}
