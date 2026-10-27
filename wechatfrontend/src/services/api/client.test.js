import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const axios = vi.hoisted(() => {
  const instance = {
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  }
  return {
    create: vi.fn(() => instance),
    post: vi.fn(),
  }
})

vi.mock('axios', () => ({ default: axios }))

import { clearSession, getAccessToken, getRefreshToken, setSession } from '../auth/tokenStorage.js'
import * as clientModule from './client.js'

describe('API access-token provider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-26T00:00:00Z'))
    clearSession()
  })

  afterEach(() => vi.useRealTimers())

  it('reuses a token that remains valid beyond the safety window', async () => {
    setSession({ accessToken: 'current-access', refreshToken: 'refresh', expiresIn: 900 })

    await expect(clientModule.getUsableAccessToken()).resolves.toBe('current-access')
    expect(axios.post).not.toHaveBeenCalled()
  })

  it('shares one refresh request when concurrent callers need a new token', async () => {
    setSession({ accessToken: 'old-access', refreshToken: 'refresh', expiresIn: 1 })
    axios.post.mockResolvedValue({ data: { accessToken: 'new-access', refreshToken: 'new-refresh', expiresIn: 900 } })

    const [first, second] = await Promise.all([
      clientModule.getUsableAccessToken(),
      clientModule.getUsableAccessToken(),
    ])

    expect(first).toBe('new-access')
    expect(second).toBe('new-access')
    expect(axios.post).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBe('new-access')
  })

  it('expires the local session when a required refresh fails', async () => {
    const onSessionExpired = vi.fn()
    window.addEventListener('wechat:session-expired', onSessionExpired)
    setSession({ accessToken: 'old-access', refreshToken: 'refresh', expiresIn: 1 })
    axios.post.mockRejectedValue({ response: { status: 401 } })

    const results = await Promise.allSettled([
      clientModule.getUsableAccessToken(),
      clientModule.getUsableAccessToken(),
    ])

    expect(results.every(({ status }) => status === 'rejected')).toBe(true)
    expect(axios.post).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
    window.removeEventListener('wechat:session-expired', onSessionExpired)
  })

  it('does not restore credentials when logout wins an in-flight refresh', async () => {
    let resolveRefresh
    const refreshResponse = new Promise((resolve) => { resolveRefresh = resolve })
    setSession({ accessToken: 'old-access', refreshToken: 'old-refresh', expiresIn: 1 })
    axios.post.mockReturnValue(refreshResponse)

    const tokenPromise = clientModule.getUsableAccessToken()
    clearSession()
    resolveRefresh({ data: { accessToken: 'late-access', refreshToken: 'late-refresh', expiresIn: 900 } })

    await expect(tokenPromise).rejects.toMatchObject({ code: 'SESSION_CHANGED' })
    expect(getAccessToken()).toBeNull()
  })

  it('does not expire a replacement session when an old refresh request fails', async () => {
    let rejectRefresh
    const refreshResponse = new Promise((resolve, reject) => { rejectRefresh = reject })
    setSession({ accessToken: 'old-access', refreshToken: 'old-refresh', expiresIn: 1 })
    axios.post.mockReturnValue(refreshResponse)

    const tokenPromise = clientModule.getUsableAccessToken()
    setSession({ accessToken: 'new-access', refreshToken: 'new-refresh', expiresIn: 900 })
    rejectRefresh({ response: { status: 401 } })

    await expect(tokenPromise).rejects.toMatchObject({ code: 'SESSION_CHANGED' })
    expect(getAccessToken()).toBe('new-access')
    expect(getRefreshToken()).toBe('new-refresh')
  })
})
