package com.iflytek.skillhub.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 authorization request resolver that preserves a sanitized post-login redirect target in
 * the HTTP session.
 */
@Component
public class SkillHubOAuth2AuthorizationRequestResolver
        implements org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver {

    private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final OAuthLoginFlowService oauthLoginFlowService;
    private final OAuthProviderPolicy oauthProviderPolicy;

    public SkillHubOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
                                                      OAuthLoginFlowService oauthLoginFlowService,
                                                      OAuthProviderPolicy oauthProviderPolicy) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                AUTHORIZATION_REQUEST_BASE_URI
        );
        this.oauthLoginFlowService = oauthLoginFlowService;
        this.oauthProviderPolicy = oauthProviderPolicy;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String registrationId = registrationIdFrom(request);
        if (registrationId != null && !oauthProviderPolicy.isAllowed(registrationId)) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        rememberReturnTo(request, authorizationRequest);
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        if (!oauthProviderPolicy.isAllowed(clientRegistrationId)) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        rememberReturnTo(request, authorizationRequest);
        return authorizationRequest;
    }

    private void rememberReturnTo(HttpServletRequest request,
                                  OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest != null) {
            oauthLoginFlowService.rememberReturnTo(request);
        }
    }

    private String registrationIdFrom(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String prefix = AUTHORIZATION_REQUEST_BASE_URI + "/";
        if (requestUri == null || !requestUri.startsWith(prefix)) {
            return null;
        }
        String registrationId = requestUri.substring(prefix.length());
        int nextSlash = registrationId.indexOf('/');
        return nextSlash >= 0 ? registrationId.substring(0, nextSlash) : registrationId;
    }
}
