package com.synx.devkit.identity.application.service;

import com.synx.devkit.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Generates opaque enrollment secrets and derives the digest stored by the server. */
public final class DeviceEnrollmentTokenCodec {
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 128;
    private final SecureRandom random;

    public DeviceEnrollmentTokenCodec(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        byte[] value = new byte[TOKEN_BYTES];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public byte[] digest(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new ValidationException("enrollment token is invalid");
        }
        try {
            // SHA-256 is appropriate here because the input has 256 bits of
            // randomness; a slow password hash would not add useful entropy.
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
