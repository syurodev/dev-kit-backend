package com.synx.devkit.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class GatewayAuthenticationEntryPointTest {
    @Test
    void categorizesDecoderFailuresWithoutReturningTheirRawText() {
        assertThat(reason("An error occurred while attempting to decode the Jwt: "
                + "Couldn't retrieve remote JWK set: Connection refused"))
                .isEqualTo("jwks_unavailable");
        assertThat(reason("Signed JWT rejected: Another algorithm expected, or no matching key(s) found"))
                .isEqualTo("signing_key_unknown");
        assertThat(reason("Signed JWT rejected: Invalid signature")).isEqualTo("signature_invalid");
        assertThat(reason("The iss claim is not valid")).isEqualTo("issuer_invalid");
    }

    @Test
    void exposesOnlySafeTokenCorrelationData() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer "
                + "eyJhbGciOiJSUzI1NiIsImtpZCI6ImtleS0xMjMifQ.e30.AA");

        GatewayAuthenticationEntryPoint.TokenDiagnostic diagnostic =
                GatewayAuthenticationEntryPoint.tokenDiagnostic(request);

        assertThat(diagnostic.fingerprint()).matches("[a-f0-9]{16}");
        assertThat(diagnostic.keyId()).isEqualTo("key-123");
        assertThat(diagnostic.algorithm()).isEqualTo("RS256");
        assertThat(diagnostic.type()).isEqualTo("unavailable");
        assertThat(diagnostic.certificateBound()).isEqualTo("absent");
    }

    private static String reason(String description) {
        return GatewayAuthenticationEntryPoint.safeReason(
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null)));
    }
}
