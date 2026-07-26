package com.iflytek.skillhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Controls whether the public authentication catalog advertises local password login. */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.local-ui")
public class LocalAuthUiProperties {

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
