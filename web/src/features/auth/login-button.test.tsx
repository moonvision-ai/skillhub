import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import type { AuthMethod } from '@/api/types'

const authMethodState = vi.hoisted(() => ({
  data: [] as AuthMethod[],
  isLoading: false,
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, values?: { name?: string }) => `${key}:${values?.name ?? ''}`,
  }),
}))

vi.mock('./use-auth-methods', () => ({
  useAuthMethods: () => authMethodState,
}))

import { LoginButton } from './login-button'

describe('LoginButton', () => {
  it('renders a built-in fallback icon for an OAuth provider without a bundled logo', () => {
    authMethodState.data = [{
      id: 'oauth-oidc',
      methodType: 'OAUTH_REDIRECT',
      provider: 'oidc',
      displayName: 'Authelia',
      actionUrl: '/oauth2/authorization/oidc',
    }]

    const html = renderToStaticMarkup(<LoginButton />)

    expect(html).toContain('<svg')
    expect(html).not.toContain('src="/oidc-logo.svg"')
    expect(html).toContain('loginButton.loginWith:Authelia')
  })
})
