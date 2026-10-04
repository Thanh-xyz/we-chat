import { beforeEach, describe, expect, it, vi } from 'vitest'

const authApi = vi.hoisted(() => ({
  login: vi.fn(),
  me: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('../../services/api/authApi.js', () => ({ authApi }))
vi.mock('../../services/api/client.js', () => ({ refreshAccessToken: vi.fn() }))

import { useAuthStore } from './authStore.js'
import { clearSession, getAccessToken, getRefreshToken } from '../../services/auth/tokenStorage.js'

describe('auth store', () => {
  beforeEach(() => {
    clearSession()
    useAuthStore.setState({ status: 'anonymous', user: null, error: null })
  })

  it('stores the session and loads the authenticated profile', async () => {
    authApi.login.mockResolvedValue({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 900 })
    authApi.me.mockResolvedValue({ id: 'u1', username: 'thanh' })

    await useAuthStore.getState().login({ identifier: 'thanh', password: 'password' })

    expect(useAuthStore.getState().status).toBe('authenticated')
    expect(useAuthStore.getState().user.id).toBe('u1')
    expect(getAccessToken()).toBe('access')
    expect(getRefreshToken()).toBe('refresh')
  })

  it('clears credentials on logout even when the server request fails', async () => {
    authApi.login.mockResolvedValue({ accessToken: 'access', refreshToken: 'refresh', expiresIn: 900 })
    authApi.me.mockResolvedValue({ id: 'u1' })
    authApi.logout.mockRejectedValue(new Error('offline'))
    await useAuthStore.getState().login({ identifier: 'a', password: 'password' })

    await useAuthStore.getState().logout()
    expect(useAuthStore.getState().status).toBe('anonymous')
    expect(getRefreshToken()).toBeNull()
  })
})
