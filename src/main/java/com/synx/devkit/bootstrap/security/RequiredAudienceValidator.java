package com.synx.devkit.bootstrap.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Rejects a validly signed gateway token intended for another backend. */
public final class RequiredAudienceValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token", "The required audience is missing", null);
    private final String audience;

    public RequiredAudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(ERROR);
    }
}
