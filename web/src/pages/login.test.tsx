import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AuthMethod } from '@/api/types'

const authMethodState = vi.hoisted(() => ({
  data: [] as AuthMethod[],
  isLoading: false,
}))

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
  useNavigate: () => vi.fn(),
  useSearch: () => ({ returnTo: '' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { resolvedLanguage: 'en' },
    }),
  }
})

vi.mock('lucide-react', () => ({
  Eye: () => null,
  EyeOff: () => null,
}))

vi.mock('@/api/client', () => ({
  getDirectAuthRuntimeConfig: () => ({ enabled: false }),
}))

vi.mock('@/features/auth/login-button', () => ({
  LoginButton: () => null,
}))

vi.mock('@/features/auth/session-bootstrap-entry', () => ({
  SessionBootstrapEntry: () => null,
}))

vi.mock('@/features/auth/use-auth-methods', () => ({
  useAuthMethods: () => authMethodState,
}))

vi.mock('@/features/auth/use-password-login', () => ({
  usePasswordLogin: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { LoginPage } from './login'

describe('LoginPage', () => {
  beforeEach(() => {
    authMethodState.data = [{
      id: 'local-password',
      methodType: 'PASSWORD',
      provider: 'local',
      displayName: 'Local Account',
      actionUrl: '/api/v1/auth/local/login',
    }]
    authMethodState.isLoading = false
  })

  it('exports a named component function', () => {
    expect(typeof LoginPage).toBe('function')
  })

  it('renders the login title and form elements', () => {
    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.title')
    expect(html).toContain('login.subtitle')
    expect(html).toContain('login.submit')
    expect(html).toContain('login.tabPassword')
    expect(html).toContain('login.tabOAuth')
    expect(html).toContain('aria-selected="true"')
  })

  it('renders only OAuth controls when password login is not advertised', () => {
    authMethodState.data = [{
      id: 'oauth-oidc',
      methodType: 'OAUTH_REDIRECT',
      provider: 'oidc',
      displayName: 'Authelia',
      actionUrl: '/oauth2/authorization/oidc',
    }]

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).toContain('login.oauthHint')
    expect(html).not.toContain('login.tabPassword')
    expect(html).not.toContain('id="username"')
    expect(html).not.toContain('id="password"')
    expect(html).not.toContain('login.forgotPassword')
    expect(html).not.toContain('login.register')
  })

  it('does not expose password controls while auth methods are loading', () => {
    authMethodState.data = []
    authMethodState.isLoading = true

    const html = renderToStaticMarkup(<LoginPage />)

    expect(html).not.toContain('login.tabPassword')
    expect(html).not.toContain('id="username"')
    expect(html).not.toContain('login.forgotPassword')
  })
})
