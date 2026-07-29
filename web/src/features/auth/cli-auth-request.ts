const DEFAULT_CLI_SCOPES = ['skill:read', 'skill:publish'] as const

export function requestedCliTokenScopes(allowDelete: string | null): string[] {
  return allowDelete === 'true'
    ? [...DEFAULT_CLI_SCOPES, 'skill:delete']
    : [...DEFAULT_CLI_SCOPES]
}

export function requiresDeleteConsent(allowDelete: string | null): boolean {
  return allowDelete === 'true'
}

