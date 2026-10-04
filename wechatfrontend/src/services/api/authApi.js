import { apiClient } from './client.js'

export const authApi = {
  login: async (credentials) => (await apiClient.post('/auth/login', credentials)).data,
  register: async (details) => (await apiClient.post('/auth/register', details)).data,
  me: async () => (await apiClient.get('/users/me')).data,
  refresh: async (refreshToken) =>
    (await apiClient.post('/auth/refresh', { refreshToken })).data,
  logout: async (refreshToken) => {
    await apiClient.post('/auth/logout', { refreshToken })
  },
  verifyEmail: async (token) => {
    await apiClient.post('/auth/verify-email', { token })
  },
  resendVerification: async (email) => {
    await apiClient.post('/auth/resend-verification', { email })
  },
  forgotPassword: async (email) => {
    await apiClient.post('/auth/forgot-password', { email })
  },
  resetPassword: async (token, newPassword) => {
    await apiClient.post('/auth/reset-password', { token, newPassword })
  },
}
