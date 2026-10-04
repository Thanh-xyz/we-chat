const REFRESH_TOKEN_KEY = 'wechat.refresh-token'
let accessToken = null

export function getAccessToken() {
  return accessToken
}

export function getRefreshToken() {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY)
}

export function hasSession() {
  return Boolean(accessToken || getRefreshToken())
}

export function setSession(tokens) {
  accessToken = tokens.accessToken
  sessionStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

export function updateAccessToken(token) {
  accessToken = token
}

export function clearSession() {
  accessToken = null
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
}
