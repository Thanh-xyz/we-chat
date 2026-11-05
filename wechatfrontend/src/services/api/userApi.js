import { apiClient } from './client.js'

export const userApi = {
  me: async ({ signal } = {}) => (await apiClient.get('/users/me', { signal })).data,
  updateMe: async ({ displayName, avatarUrl }) =>
    (await apiClient.put('/users/me', { displayName: displayName || null, avatarUrl: avatarUrl ?? null })).data,
}
