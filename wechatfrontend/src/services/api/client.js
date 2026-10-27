import axios from 'axios'
import {
  clearSession,
  getAccessToken,
  getRefreshToken,
  hasUsableAccessToken,
  setSession,
} from '../auth/tokenStorage.js'
import { normalizeApiError } from './errors.js'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { Accept: 'application/json' },
})

let refreshPromise = null

function expireSession() {
  clearSession()
  window.dispatchEvent(new Event('wechat:session-expired'))
}

function sessionChangedError() {
  const error = new Error('Session changed while refreshing credentials')
  error.code = 'SESSION_CHANGED'
  return error
}

export async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    expireSession()
    throw normalizeApiError({ response: { status: 401 } })
  }

  if (!refreshPromise) {
    refreshPromise = axios
      .post(
        `${API_BASE_URL}/auth/refresh`,
        { refreshToken },
        { timeout: 15_000, headers: { 'Content-Type': 'application/json' } },
      )
      .then(({ data }) => {
        if (getRefreshToken() !== refreshToken) throw sessionChangedError()
        setSession(data)
        return data.accessToken
      })
      .catch((error) => {
        if (error?.code === 'SESSION_CHANGED' || getRefreshToken() !== refreshToken) {
          throw sessionChangedError()
        }
        expireSession()
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export async function getUsableAccessToken() {
  const token = getAccessToken()
  if (token && hasUsableAccessToken()) return token
  try {
    return await refreshAccessToken()
  } catch (error) {
    throw normalizeApiError(error)
  }
}

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  config.headers['X-Request-ID'] = crypto.randomUUID()
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const request = error.config
    const canRefresh =
      error.response?.status === 401 &&
      request &&
      !request._retry &&
      getRefreshToken() &&
      !String(request.url).includes('/auth/refresh')

    if (canRefresh) {
      request._retry = true
      try {
        const token = await refreshAccessToken()
        request.headers.Authorization = `Bearer ${token}`
        return apiClient(request)
      } catch {
        // refreshAccessToken already expires the shared session.
      }
    }
    throw normalizeApiError(error)
  },
)
