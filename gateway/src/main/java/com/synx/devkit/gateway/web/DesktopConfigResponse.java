package com.synx.devkit.gateway.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** JSON payload for {@code GET /v1/desktop/config}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record DesktopConfigResponse(
        @JsonProperty("config_version") String configVersion,
        @JsonProperty("oidc_issuer") String oidcIssuer,
        @JsonProperty("oidc_client_id") String oidcClientId,
        @JsonProperty("oidc_scopes") String oidcScopes,
        @JsonProperty("oidc_auth_url") String oidcAuthUrl,
        @JsonProperty("oidc_token_url") String oidcTokenUrl,
        @JsonProperty("min_app_version") String minAppVersion,
        @JsonProperty("latest_app_version") String latestAppVersion,
        @JsonProperty("update_url") String updateUrl) {}
