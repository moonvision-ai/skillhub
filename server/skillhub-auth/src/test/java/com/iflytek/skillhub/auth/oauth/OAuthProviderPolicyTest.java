package com.iflytek.skillhub.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OAuthProviderPolicyTest {

    @Test
    void emptyAllowlistAllowsEveryProvider() {
        OAuthProviderPolicy policy = new OAuthProviderPolicy();
        policy.setAllowedProviders(List.of());

        assertThat(policy.isAllowed("github")).isTrue();
        assertThat(policy.isAllowed("oidc")).isTrue();
    }

    @Test
    void configuredAllowlistNormalizesWhitespaceAndCase() {
        OAuthProviderPolicy policy = new OAuthProviderPolicy();
        policy.setAllowedProviders(List.of(" oidc ", "GITHUB", ""));

        assertThat(policy.isAllowed("OIDC")).isTrue();
        assertThat(policy.isAllowed(" github ")).isTrue();
        assertThat(policy.isAllowed("gitlab")).isFalse();
    }
}
