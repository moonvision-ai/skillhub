# Configurable Authentication Entry Policy

## Goal

Replace the deployment-time modification of compiled Web assets with a source-level, reusable authentication entry policy. Operators can hide local-account entry points and restrict OAuth providers without breaking existing deployments.

## Compatibility Contract

- Existing installations behave exactly as they do today when the new settings are omitted.
- Local username/password APIs remain available even when the Web UI hides local login. This preserves an operator-controlled emergency account path.
- An empty OAuth provider allowlist means all configured OAuth client registrations are allowed.
- A non-empty OAuth provider allowlist affects both discovery and authorization. A user cannot bypass it by visiting `/oauth2/authorization/{provider}` directly.

## Configuration

### Local login presentation

Spring property:

```yaml
skillhub:
  auth:
    local-ui:
      enabled: true
```

Environment variable:

```text
SKILLHUB_AUTH_LOCAL_UI_ENABLED=true
```

The default is `true`. Setting it to `false` removes the `PASSWORD` method from the public authentication-method catalog. It does not disable local registration, login, password reset, or bootstrap APIs.

### OAuth provider allowlist

Spring property:

```yaml
skillhub:
  auth:
    oauth:
      allowed-providers: []
```

Environment variable:

```text
SKILLHUB_AUTH_OAUTH_ALLOWED_PROVIDERS=oidc
```

Provider codes are trimmed and compared case-insensitively. Empty entries are ignored. An empty normalized set permits every configured OAuth provider. A non-empty set permits only matching registration IDs.

For the MoonVision deployment, the intended values are:

```text
SKILLHUB_AUTH_LOCAL_UI_ENABLED=false
SKILLHUB_AUTH_OAUTH_ALLOWED_PROVIDERS=oidc
```

## Backend Design

### Policy objects

Add two focused configuration components:

- `LocalAuthUiProperties` in the application module controls whether the catalog advertises the local password method.
- `OAuthProviderPolicy` in the authentication module owns normalization and the `isAllowed(provider)` decision. Keeping this policy in `skillhub-auth` lets both the authorization resolver and the application-layer catalog use the same rule without reversing module dependencies.

The policy exposes behavior rather than its mutable configuration collection. This keeps case normalization and empty-list semantics in one place.

### Authentication catalog

`AuthMethodCatalog` applies the policy consistently:

- Add `local-password` only when local UI presentation is enabled.
- Filter OAuth registrations in both `listMethods` and `listOAuthProviders` through `OAuthProviderPolicy`.
- Leave direct-password and session-bootstrap methods unchanged.

The catalog remains the frontend's single source of truth. No duplicate Web-container allowlist is introduced.

### Direct OAuth authorization

`SkillHubOAuth2AuthorizationRequestResolver` checks the requested client registration ID before delegating to Spring Security. A disallowed provider produces no authorization request, so the request cannot redirect to that identity provider. The resolver must not store `returnTo` for a rejected request.

Allowed providers retain the existing sanitized return-target behavior.

## Frontend Design

The login page derives its layout from `useAuthMethods`:

- While methods are loading, render the existing card structure without exposing a premature password form.
- If a `PASSWORD` method exists, preserve the current two-tab page and default to password login.
- If no `PASSWORD` method exists, render only the OAuth section, with no password tab, username/password fields, registration link, or password-reset link.
- `LoginButton` continues to render only `OAUTH_REDIRECT` methods returned by the backend.
- Session-bootstrap behavior and disabled-account messaging remain available in both layouts.

No frontend runtime setting or provider allowlist is added. This avoids configuration drift between the Web and server containers.

## Error and Edge-Case Behavior

- An allowlist naming no configured provider yields an empty OAuth button list; it does not fall back to all providers.
- An unknown or disallowed direct authorization URL does not initiate an OAuth redirect.
- Configuration comparison is case-insensitive, while response provider codes retain their configured registration IDs.
- Hiding local UI with no allowed OAuth providers is permitted because session bootstrap or an intentionally hidden local API may still be the operator's access path.
- Malformed `returnTo` values continue to use the existing sanitizer.

## Testing

Backend tests will cover:

- Default settings advertise local login and every configured OAuth provider.
- Disabled local UI omits only `local-password`.
- Empty allowlist permits all providers.
- A non-empty, mixed-case, whitespace-containing allowlist permits only normalized matches.
- Both catalog endpoints apply the same filter.
- The authorization resolver delegates for allowed providers and rejects disallowed providers without remembering `returnTo`.

Frontend tests will cover:

- Existing two-tab behavior when a password method is present.
- OAuth-only rendering when no password method is present.
- No password inputs, registration link, or reset link in OAuth-only mode.
- Loading state does not briefly expose local-login controls.

Operator-facing tests and documentation will verify the two new environment variables and their backward-compatible defaults in release Compose configuration.

## Rollout

The feature is opt-in. Existing deployments require no changes. MoonVision can build official source images from this Fork, set the two environment variables above, and remove the compiled-asset patch from the infrastructure repository after deployment verification.

