import { create } from 'zustand'
import { authApi } from '../../services/api/authApi.js'
import { refreshAccessToken } from '../../services/api/client.js'
import {
  clearSession,
  getAccessToken,
  getRefreshToken,
  hasSession,
  setSession,
} from '../../services/auth/tokenStorage.js'

export const useAuthStore = create((set, get) => ({
  status: hasSession() ? 'checking' : 'anonymous',
  user: null,
  error: null,

  initialize: async () => {
    if (!hasSession()) {
      set({ status: 'anonymous', user: null })
      return
    }
    set({ status: 'checking', error: null })
    try {
      if (!getAccessToken()) await refreshAccessToken()
      const user = await authApi.me()
      set({ status: 'authenticated', user })
    } catch {
      clearSession()
      set({ status: 'anonymous', user: null })
    }
  },

  login: async (credentials) => {
    set({ error: null })
    try {
      const tokens = await authApi.login(credentials)
      setSession(tokens)
      const user = await authApi.me()
      set({ status: 'authenticated', user })
      return user
    } catch (error) {
      clearSession()
      set({ status: 'anonymous', user: null, error })
      throw error
    }
  },

  logout: async () => {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) await authApi.logout(refreshToken)
    } catch {
      // Local credentials must still be cleared when the server is unavailable.
    } finally {
      clearSession()
      set({ status: 'anonymous', user: null, error: null })
    }
  },

  expireSession: () => {
    clearSession()
    set({ status: 'anonymous', user: null, error: null })
  },

  clearError: () => get().error && set({ error: null }),
}))
