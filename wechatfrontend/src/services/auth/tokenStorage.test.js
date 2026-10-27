import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as tokenStorage from './tokenStorage.js'

describe('token storage expiry', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-26T00:00:00Z'))
    tokenStorage.clearSession()
  })

  afterEach(() => vi.useRealTimers())

  it('keeps the access token in memory and tracks its server-provided expiry', () => {
    tokenStorage.setSession({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 900 })

    expect(tokenStorage.hasUsableAccessToken()).toBe(true)
    vi.advanceTimersByTime(870_001)
    expect(tokenStorage.hasUsableAccessToken()).toBe(false)
  })

  it('clears access-token expiry with the rest of the session', () => {
    tokenStorage.setSession({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 900 })
    tokenStorage.clearSession()

    expect(tokenStorage.getAccessToken()).toBeNull()
    expect(tokenStorage.hasUsableAccessToken()).toBe(false)
  })
})
