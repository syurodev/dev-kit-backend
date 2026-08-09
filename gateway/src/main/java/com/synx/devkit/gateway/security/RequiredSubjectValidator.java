package com.synx.devkit.gateway.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** A stable Keycloak subject is mandatory because email is mutable metadata. */
public final class RequiredSubjectValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error ERROR =
            new OAuth2Error("invalid_token", "Token subject is missing", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        return token.getSubject() != null && !token.getSubject().isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(ERROR);
    }
}
