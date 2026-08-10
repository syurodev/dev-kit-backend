package com.synx.devkit.gateway.security;

import com.synx.devkit.gateway.config.DesktopConfigAbuseProperties;
import com.synx.devkit.gateway.config.DesktopConfigProperties;
import com.synx.devkit.gateway.configuration.GatewayAbuseProperties;
import com.synx.devkit.gateway.configuration.GatewayTokenProperties;
import com.synx.devkit.gateway.configuration.KeycloakJwtProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/** Validates external identity and exposes only the intended public endpoints. */
@Configuration
@EnableConfigurationProperties({
        KeycloakJwtProperties.class,
        GatewayTokenProperties.class,
        GatewayAbuseProperties.class,
        DesktopConfigProperties.class,
        DesktopConfigAbuseProperties.class
})
public class SecurityConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    FixedWindowRateLimiter fixedWindowRateLimiter(Clock clock, GatewayAbuseProperties properties) {
        return new FixedWindowRateLimiter(clock, properties.getMaxTrackedKeys());
    }

    @Bean
    DesktopClientHeaderFilter desktopClientHeaderFilter() {
        return new DesktopClientHeaderFilter();
    }

    @Bean
    RedisFixedWindowRateLimiter redisFixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        return new RedisFixedWindowRateLimiter(redisTemplate);
    }

    @Bean
    DesktopConfigRateLimitFilter desktopConfigRateLimitFilter(
            RedisFixedWindowRateLimiter limiter,
            DesktopConfigAbuseProperties properties) {
        return new DesktopConfigRateLimitFilter(limiter, properties.getRequestsPerMinute());
    }

    @Bean
    IpRateLimitFilter ipRateLimitFilter(
            FixedWindowRateLimiter limiter,
            GatewayAbuseProperties properties) {
        return new IpRateLimitFilter(limiter, properties.getIpRequestsPerMinute());
    }

    @Bean
    SubjectAbuseProtectionFilter subjectAbuseProtectionFilter(
            FixedWindowRateLimiter limiter,
            GatewayAbuseProperties properties) {
        return new SubjectAbuseProtectionFilter(
                limiter,
                properties.getSubjectRequestsPerMinute(),
                properties.getMaxConcurrentRequests());
    }

    @Bean
    JwtDecoder keycloakJwtDecoder(KeycloakJwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                new RequiredAudienceValidator(properties.getAudience()),
                new RequiredSubjectValidator()));
        return decoder;
    }

    @Bean
    SecurityFilterChain gatewaySecurity(
            HttpSecurity http,
            DesktopClientHeaderFilter desktopClientHeaderFilter,
            DesktopConfigRateLimitFilter desktopConfigRateLimitFilter,
            IpRateLimitFilter ipRateLimitFilter,
            SubjectAbuseProtectionFilter subjectAbuseProtectionFilter,
            GatewayIdentityRelayFilter identityRelayFilter,
            GatewayAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/.well-known/jwks.json", "/actuator/health", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v1/desktop/config")
                        .permitAll()
                        .requestMatchers("/v1/sync/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(Customizer.withDefaults()))
                // Chain desktop-config filters so invalid headers never hit Redis RL.
                // Inbound /v1/desktop/config: ip RL (skips path) -> header check -> Redis RL -> ...
                .addFilterBefore(desktopConfigRateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(desktopClientHeaderFilter, DesktopConfigRateLimitFilter.class)
                .addFilterBefore(ipRateLimitFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(subjectAbuseProtectionFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(identityRelayFilter, SubjectAbuseProtectionFilter.class)
                .build();
    }
}
