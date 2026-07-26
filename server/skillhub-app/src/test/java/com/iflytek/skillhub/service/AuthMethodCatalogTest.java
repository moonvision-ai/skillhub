package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.iflytek.skillhub.auth.bootstrap.PassiveSessionAuthenticator;
import com.iflytek.skillhub.auth.direct.DirectAuthProvider;
import com.iflytek.skillhub.auth.direct.DirectAuthRequest;
import com.iflytek.skillhub.auth.oauth.OAuthProviderPolicy;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.config.AuthSessionBootstrapProperties;
import com.iflytek.skillhub.config.DirectAuthProperties;
import com.iflytek.skillhub.config.LocalAuthUiProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;

class AuthMethodCatalogTest {

    @Test
    void listMethodsShouldHideLocalLoginAndDisallowedOAuthProviders() {
        OAuth2ClientProperties oauthProperties = oauthProperties();
        LocalAuthUiProperties localAuthUiProperties = new LocalAuthUiProperties();
        localAuthUiProperties.setEnabled(false);
        OAuthProviderPolicy oauthProviderPolicy = new OAuthProviderPolicy();
        oauthProviderPolicy.setAllowedProviders(List.of(" OIDC "));

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            new DirectAuthProperties(),
            new AuthSessionBootstrapProperties(),
            localAuthUiProperties,
            oauthProviderPolicy,
            List.of(),
            List.of()
        );

        assertThat(catalog.listMethods(null))
            .extracting(method -> method.id())
            .contains("oauth-oidc")
            .doesNotContain("local-password", "oauth-github");
        assertThat(catalog.listOAuthProviders(null))
            .extracting(provider -> provider.id())
            .containsExactly("oidc");
    }

    private OAuth2ClientProperties oauthProperties() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        OAuth2ClientProperties.Registration github = new OAuth2ClientProperties.Registration();
        github.setClientName("GitHub");
        OAuth2ClientProperties.Registration oidc = new OAuth2ClientProperties.Registration();
        oidc.setClientName("Authelia");
        properties.getRegistration().put("github", github);
        properties.getRegistration().put("oidc", oidc);
        return properties;
    }

    @Test
    void listMethodsShouldUseProviderDisplayNamesForCompatibleAuthMethods() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        directAuthProperties.setEnabled(true);
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();
        bootstrapProperties.setEnabled(true);

        DirectAuthProvider directProvider = new DirectAuthProvider() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public String displayName() {
                return "Enterprise Password";
            }

            @Override
            public PlatformPrincipal authenticate(DirectAuthRequest request) {
                throw new UnsupportedOperationException("not used in catalog test");
            }
        };

        PassiveSessionAuthenticator bootstrapProvider = new PassiveSessionAuthenticator() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public String displayName() {
                return "Enterprise SSO";
            }

            @Override
            public Optional<PlatformPrincipal> authenticate(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.empty();
            }
        };

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            bootstrapProperties,
            enabledLocalAuthUiProperties(),
            new OAuthProviderPolicy(),
            List.of(directProvider),
            List.of(bootstrapProvider)
        );

        assertThat(catalog.listMethods(null))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains(
                "local-password:Local Account",
                "direct-private-sso:Enterprise Password",
                "bootstrap-private-sso:Enterprise SSO"
            );
    }

    @Test
    void listMethodsShouldFallBackToProviderCodeWhenDisplayNameIsNotOverridden() {
        OAuth2ClientProperties oauthProperties = new OAuth2ClientProperties();
        DirectAuthProperties directAuthProperties = new DirectAuthProperties();
        directAuthProperties.setEnabled(true);
        AuthSessionBootstrapProperties bootstrapProperties = new AuthSessionBootstrapProperties();
        bootstrapProperties.setEnabled(true);

        DirectAuthProvider directProvider = new DirectAuthProvider() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public PlatformPrincipal authenticate(DirectAuthRequest request) {
                return mock(PlatformPrincipal.class);
            }
        };

        PassiveSessionAuthenticator bootstrapProvider = new PassiveSessionAuthenticator() {
            @Override
            public String providerCode() {
                return "private-sso";
            }

            @Override
            public Optional<PlatformPrincipal> authenticate(jakarta.servlet.http.HttpServletRequest request) {
                return Optional.empty();
            }
        };

        AuthMethodCatalog catalog = new AuthMethodCatalog(
            oauthProperties,
            directAuthProperties,
            bootstrapProperties,
            enabledLocalAuthUiProperties(),
            new OAuthProviderPolicy(),
            List.of(directProvider),
            List.of(bootstrapProvider)
        );

        assertThat(catalog.listMethods(null))
            .extracting(method -> method.id() + ":" + method.displayName())
            .contains(
                "direct-private-sso:private-sso",
                "bootstrap-private-sso:private-sso"
            );
    }

    private LocalAuthUiProperties enabledLocalAuthUiProperties() {
        return new LocalAuthUiProperties();
    }
}
