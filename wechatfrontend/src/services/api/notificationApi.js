import { apiClient } from './client.js'

export const notificationApi = {
  list: async ({ signal } = {}) => (await apiClient.get('/notifications', { signal, params: { limit: 50, offset: 0 } })).data,
  unreadCount: async () => (await apiClient.get('/notifications/unread-count')).data,
  markRead: async (notificationId) => (await apiClient.post(`/notifications/${notificationId}/read`)).data,
  markAllRead: async () => (await apiClient.post('/notifications/read-all')).data,
  remove: async (notificationId) => { await apiClient.delete(`/notifications/${notificationId}`) },
  preferences: async () => (await apiClient.get('/notifications/preferences')).data,
  updatePreferences: async (preferences) => (await apiClient.put('/notifications/preferences', preferences)).data,
}
