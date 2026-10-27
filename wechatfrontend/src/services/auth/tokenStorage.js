const REFRESH_TOKEN_KEY = 'wechat.refresh-token'
const ACCESS_TOKEN_SAFETY_WINDOW_MS = 30_000
let accessToken = null
let accessTokenExpiresAt = 0

export function getAccessToken() {
  return accessToken
}

export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY)
}

export function hasUsableAccessToken(safetyWindowMs = ACCESS_TOKEN_SAFETY_WINDOW_MS) {
  return Boolean(accessToken && accessTokenExpiresAt > Date.now() + safetyWindowMs)
}

export function hasSession() {
  return Boolean(accessToken || getRefreshToken())
}

export function setSession(tokens) {
  accessToken = tokens.accessToken
  const expiresInSeconds = Number(tokens.expiresIn)
  accessTokenExpiresAt = Number.isFinite(expiresInSeconds) && expiresInSeconds > 0
    ? Date.now() + expiresInSeconds * 1_000
    : 0
  sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

export function updateAccessToken(token, expiresIn = 0) {
  accessToken = token
  const expiresInSeconds = Number(expiresIn)
  accessTokenExpiresAt = Number.isFinite(expiresInSeconds) && expiresInSeconds > 0
    ? Date.now() + expiresInSeconds * 1_000
    : 0
}

export function clearSession() {
  accessToken = null
  accessTokenExpiresAt = 0
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
}
