import { describe, expect, it } from 'vitest'
import { requestedCliTokenScopes } from './cli-auth-request'

describe('requestedCliTokenScopes', () => {
  it('keeps the backward-compatible scopes by default', () => {
    expect(requestedCliTokenScopes(null)).toEqual(['skill:read', 'skill:publish'])
    expect(requestedCliTokenScopes('false')).toEqual(['skill:read', 'skill:publish'])
    expect(requestedCliTokenScopes('TRUE')).toEqual(['skill:read', 'skill:publish'])
  })

  it('adds delete only for the literal allow_delete=true request', () => {
    expect(requestedCliTokenScopes('true')).toEqual(['skill:read', 'skill:publish', 'skill:delete'])
  })
})
