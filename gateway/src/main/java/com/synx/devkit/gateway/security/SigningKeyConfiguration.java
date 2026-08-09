package com.synx.devkit.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.synx.devkit.gateway.configuration.GatewayTokenProperties;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates one immutable signing-key view shared by the signer and JWKS endpoint. */
@Configuration
public class SigningKeyConfiguration {
    @Bean
    RSAKey gatewaySigningKey(GatewayTokenProperties properties) {
        RSAPrivateKey privateKey = PemKeyLoader.loadPrivateKey(properties.getPrivateKeyPath());
        RSAPublicKey publicKey = PemKeyLoader.loadPublicKey(properties.getPublicKeyPath());

        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("Gateway private and public RSA keys do not match");
        }

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(properties.getKeyId())
                .build();
    }
}
