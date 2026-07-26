package com.iflytek.skillhub.auth.oauth;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Controls which configured OAuth client registrations may be advertised and used.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.auth.oauth")
public class OAuthProviderPolicy {

    private Set<String> allowedProviders = Set.of();

    public void setAllowedProviders(List<String> allowedProviders) {
        if (allowedProviders == null || allowedProviders.isEmpty()) {
            this.allowedProviders = Set.of();
            return;
        }

        Set<String> normalizedProviders = new LinkedHashSet<>();
        allowedProviders.stream()
                .map(OAuthProviderPolicy::normalize)
                .filter(provider -> !provider.isEmpty())
                .forEach(normalizedProviders::add);
        this.allowedProviders = Collections.unmodifiableSet(normalizedProviders);
    }

    public boolean isAllowed(String provider) {
        return allowedProviders.isEmpty() || allowedProviders.contains(normalize(provider));
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
