package com.iflytek.skillhub.auth.oauth;

import com.iflytek.skillhub.auth.identity.IdentityBindingService;
import com.iflytek.skillhub.auth.policy.AccessPolicy;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuth2AuthorizationRequestResolverTest {

    private SkillHubOAuth2AuthorizationRequestResolver resolver;
    private ClientRegistrationRepository clientRegistrationRepository;
    private OAuthLoginFlowService oauthLoginFlowService;

    @BeforeEach
    void setUp() {
        ClientRegistration github = clientRegistration("github");
        ClientRegistration gitlab = clientRegistration("gitlab");
        oauthLoginFlowService = new OAuthLoginFlowService(
                java.util.List.of(),
                mock(AccessPolicy.class),
                mock(IdentityBindingService.class)
        );
        clientRegistrationRepository = new InMemoryClientRegistrationRepository(github, gitlab);
        resolver = new SkillHubOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                oauthLoginFlowService,
                new OAuthProviderPolicy()
        );
    }

    private ClientRegistration clientRegistration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId("client")
                .clientSecret("secret")
                .authorizationUri("https://example.test/oauth/authorize")
                .tokenUri("https://example.test/oauth/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .userInfoUri("https://example.test/user")
                .userNameAttributeName("id")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("read:user")
                .clientName(registrationId)
                .build();
    }

    @Test
    void resolve_storesSanitizedReturnToInSession() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "/dashboard/publish?draft=1");

        resolver.resolve(request, "github");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE))
                .isEqualTo("/dashboard/publish?draft=1");
    }

    @Test
    void resolve_ignoresUnsafeReturnTo() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/github");
        request.setParameter("returnTo", "https://evil.example");

        resolver.resolve(request, "github");

        HttpSession session = request.getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(OAuthLoginRedirectSupport.SESSION_RETURN_TO_ATTRIBUTE)).isNull();
    }

    @Test
    void resolve_rejectsDisallowedExplicitProviderWithoutCreatingSession() {
        OAuthProviderPolicy policy = new OAuthProviderPolicy();
        policy.setAllowedProviders(java.util.List.of("github"));
        resolver = new SkillHubOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                oauthLoginFlowService,
                policy
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/gitlab");
        request.setParameter("returnTo", "/dashboard");

        assertThat(resolver.resolve(request, "gitlab")).isNull();
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void resolve_rejectsDisallowedProviderFromRequestPath() {
        OAuthProviderPolicy policy = new OAuthProviderPolicy();
        policy.setAllowedProviders(java.util.List.of("github"));
        resolver = new SkillHubOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                oauthLoginFlowService,
                policy
        );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/gitlab");

        assertThat(resolver.resolve(request)).isNull();
        assertThat(request.getSession(false)).isNull();
    }
}
