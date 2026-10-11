import { authApi } from '../../services/api/authApi.js'

export function registerAccount(form) {
  return authApi.register({
    username: form.username.trim(),
    email: form.email.trim(),
    displayName: form.displayName.trim() || null,
    password: form.password,
  })
}

export function requestPasswordReset(email) {
  return authApi.forgotPassword(email.trim())
}

export function resetAccountPassword(token, password) {
  return authApi.resetPassword(token, password)
}

export function verifyAccountEmail(token, options) {
  return authApi.verifyEmail(token, options)
}
