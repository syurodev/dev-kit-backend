package com.synx.devkit.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Remote desktop bootstrap settings served from the public config endpoint. */
@ConfigurationProperties("devkit.desktop.config")
public class DesktopConfigProperties {
    private String configVersion = "1";
    private String oidcIssuer;
    private String oidcClientId = "devkit-desktop";
    private String oidcScopes = "openid profile email roles";
    private String oidcAuthUrl;
    private String oidcTokenUrl;
    private String minAppVersion;
    private String latestAppVersion;
    private String updateUrl;

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public String getOidcIssuer() {
        return oidcIssuer;
    }

    public void setOidcIssuer(String oidcIssuer) {
        this.oidcIssuer = oidcIssuer;
    }

    public String getOidcClientId() {
        return oidcClientId;
    }

    public void setOidcClientId(String oidcClientId) {
        this.oidcClientId = oidcClientId;
    }

    public String getOidcScopes() {
        return oidcScopes;
    }

    public void setOidcScopes(String oidcScopes) {
        this.oidcScopes = oidcScopes;
    }

    public String getOidcAuthUrl() {
        return oidcAuthUrl;
    }

    public void setOidcAuthUrl(String oidcAuthUrl) {
        this.oidcAuthUrl = oidcAuthUrl;
    }

    public String getOidcTokenUrl() {
        return oidcTokenUrl;
    }

    public void setOidcTokenUrl(String oidcTokenUrl) {
        this.oidcTokenUrl = oidcTokenUrl;
    }

    public String getMinAppVersion() {
        return minAppVersion;
    }

    public void setMinAppVersion(String minAppVersion) {
        this.minAppVersion = minAppVersion;
    }

    public String getLatestAppVersion() {
        return latestAppVersion;
    }

    public void setLatestAppVersion(String latestAppVersion) {
        this.latestAppVersion = latestAppVersion;
    }

    public String getUpdateUrl() {
        return updateUrl;
    }

    public void setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
    }
}
