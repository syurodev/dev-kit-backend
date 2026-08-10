package com.synx.devkit.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Records a small, non-sensitive reason when the public gateway rejects a JWT.
 *
 * <p>Do not log the exception itself: some lower-level decoder exceptions can
 * contain untrusted request data. The response remains Spring Security's
 * standard bearer challenge and does not disclose this diagnostic to clients.
 */
@Component
public final class GatewayAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayAuthenticationEntryPoint.class);
    private static final Pattern SAFE_HEADER_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure) throws IOException {
        TokenDiagnostic token = tokenDiagnostic(request);
        LOG.warn("Gateway rejected OIDC token for {} {}: reason={} token_fingerprint={} kid={} alg={} typ={} certificate_bound={}",
                request.getMethod(),
                request.getRequestURI(),
                safeReason(failure),
                token.fingerprint(),
                token.keyId(),
                token.algorithm(),
                token.type(),
                token.certificateBound());
        delegate.commence(request, response, failure);
    }

    /**
     * Maps framework decoder text to a small allow-list of operational reasons.
     *
     * <p>The original text is deliberately never logged. It may change between
     * Spring Security/Nimbus versions and is not suitable for a public security
     * log, while these categories are enough to distinguish IdP reachability,
     * signing-key rotation and a bad caller token.
     */
    static String safeReason(AuthenticationException failure) {
        if (!(failure instanceof OAuth2AuthenticationException oauthFailure)) {
            return "authentication_failed";
        }
        String description = oauthFailure.getError().getDescription();
        if (description == null) {
            return "invalid_token";
        }
        String normalized = description.toLowerCase(Locale.ROOT);
        if ("required audience is missing".equals(normalized)) {
            return "required_audience_missing";
        }
        if ("token subject is missing".equals(normalized)) {
            return "subject_missing";
        }
        if (normalized.startsWith("jwt expired at ")) {
            return "expired";
        }
        if (normalized.contains("iss claim is not valid")) {
            return "issuer_invalid";
        }
        if (normalized.contains("couldn't retrieve remote jwk set")
                || normalized.contains("could not retrieve remote jwk set")) {
            return "jwks_unavailable";
        }
        if (normalized.contains("no matching key") || normalized.contains("no key found")) {
            return "signing_key_unknown";
        }
        if (normalized.contains("invalid signature") || normalized.contains("signature verification failed")) {
            return "signature_invalid";
        }
        if (normalized.contains("invalid jwt") || normalized.contains("malformed jwt")) {
            return "jwt_malformed";
        }
        if (normalized.contains("typ") && normalized.contains("jwt")) {
            return "jwt_type_invalid";
        }
        if (normalized.contains("x509certificate") || normalized.contains("thumbprint")) {
            return "certificate_bound_token_rejected";
        }
        return "invalid_token";
    }

    /**
     * Produces correlation data without retaining or disclosing a bearer token.
     *
     * <p>The header is untrusted until Spring verifies it. Therefore its values
     * are restricted to a narrow printable allow-list before they reach logs.
     */
    static TokenDiagnostic tokenDiagnostic(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return TokenDiagnostic.unavailable();
        }
        String token = authorization.substring(7);
        if (token.isBlank() || token.length() > 8 * 1024) {
            return TokenDiagnostic.unavailable();
        }
        try {
            SignedJWT parsed = SignedJWT.parse(token);
            return new TokenDiagnostic(
                    tokenFingerprint(token),
                    safeHeaderValue(parsed.getHeader().getKeyID()),
                    safeHeaderValue(parsed.getHeader().getAlgorithm().getName()),
                    safeHeaderValue(parsed.getHeader().getType() == null
                            ? null
                            : parsed.getHeader().getType().toString()),
                    hasCertificateThumbprint(parsed));
        } catch (Exception exception) {
            return new TokenDiagnostic(tokenFingerprint(token), "unavailable", "unavailable", "unavailable", "unknown");
        }
    }

    private static String hasCertificateThumbprint(SignedJWT token) {
        try {
            Object confirmation = token.getJWTClaimsSet().getClaim("cnf");
            if (!(confirmation instanceof java.util.Map<?, ?> confirmationMap)) {
                return "absent";
            }
            return confirmationMap.containsKey("x5t#S256") ? "present" : "absent";
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private static String tokenFingerprint(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            StringBuilder output = new StringBuilder(16);
            for (int index = 0; index < 8; index++) {
                output.append(String.format("%02x", hash[index]));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory in the JRE. Keep the fallback non-sensitive
            // even if a broken runtime does not provide it.
            return "unavailable";
        }
    }

    private static String safeHeaderValue(String value) {
        return value != null && SAFE_HEADER_VALUE.matcher(value).matches() ? value : "unavailable";
    }

    record TokenDiagnostic(
            String fingerprint,
            String keyId,
            String algorithm,
            String type,
            String certificateBound) {
        static TokenDiagnostic unavailable() {
            return new TokenDiagnostic("unavailable", "unavailable", "unavailable", "unavailable", "unknown");
        }
    }
}
