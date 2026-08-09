package com.synx.devkit.gateway.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Loads standard PKCS#8 private and X.509 public RSA PEM files. */
public final class PemKeyLoader {
    private PemKeyLoader() {}

    public static RSAPrivateKey loadPrivateKey(String file) {
        byte[] encoded = readPem(file, "PRIVATE KEY");
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Gateway private key is not a valid PKCS#8 RSA key", exception);
        }
    }

    public static RSAPublicKey loadPublicKey(String file) {
        byte[] encoded = readPem(file, "PUBLIC KEY");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Gateway public key is not a valid X.509 RSA key", exception);
        }
    }

    private static byte[] readPem(String file, String type) {
        try {
            String pem = Files.readString(Path.of(file), StandardCharsets.US_ASCII);
            String payload = pem
                    .replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "")
                    .replaceAll("\\s", "");
            if (payload.isBlank()) {
                throw new IllegalStateException("Gateway " + type + " PEM has no payload");
            }
            return Base64.getDecoder().decode(payload);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot read gateway " + type + " PEM", exception);
        }
    }
}
