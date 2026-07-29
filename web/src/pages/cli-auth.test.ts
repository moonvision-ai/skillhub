// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import type { ReactNode } from 'react'

// CliAuthPage has internal helpers isValidRedirectUri and decodeLabel which are
// not exported. We test the component render paths and validate the redirect
// URI logic via the rendered error states.

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children, onClick }: { children: ReactNode, onClick?: () => void }) => createElement('button', { onClick }, children),
}))

const apiMocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn().mockResolvedValue({ id: 'u1', handle: 'sam', displayName: 'Sam' }),
  createToken: vi.fn().mockImplementation(() => new Promise(() => {})),
}))

vi.mock('@/api/client', () => ({
  getCurrentUser: apiMocks.getCurrentUser,
  tokenApi: { createToken: apiMocks.createToken },
}))

vi.mock('@/app/router', () => ({
  ORIGINAL_URL_SEARCH: 'redirect_uri=http%3A%2F%2F127.0.0.1%3A4567%2Fcallback&state=secure-state&allow_delete=true',
}))

import { CliAuthPage } from './cli-auth'

describe('CliAuthPage', () => {
  it('exports a named component function', () => {
    expect(typeof CliAuthPage).toBe('function')
    expect(CliAuthPage.name).toBe('CliAuthPage')
  })

  it('requires explicit consent before creating a delete-capable token', async () => {
    render(createElement(CliAuthPage))

    expect(await screen.findByText('cliAuth.deleteConsentTitle')).toBeTruthy()
    expect(apiMocks.createToken).not.toHaveBeenCalled()

    fireEvent.click(screen.getByText('cliAuth.deleteConsentConfirm'))
    await waitFor(() => expect(apiMocks.createToken).toHaveBeenCalledWith({
      name: 'CLI token',
      scopes: ['skill:read', 'skill:publish', 'skill:delete'],
    }))
  })
})
