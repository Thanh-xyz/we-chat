import { apiClient } from './client.js'

export const friendApi = {
  searchUsers: async (q, { signal } = {}) => (await apiClient.get('/users/search', { signal, params: { q, limit: 50, offset: 0 } })).data,
  list: async ({ q = '', signal } = {}) => (await apiClient.get('/friends', { signal, params: { q: q || undefined, limit: 100, offset: 0 } })).data,
  summary: async () => (await apiClient.get('/friends/summary')).data,
  incoming: async () => (await apiClient.get('/friends/requests/incoming', { params: { limit: 100, offset: 0 } })).data,
  outgoing: async () => (await apiClient.get('/friends/requests/outgoing', { params: { limit: 100, offset: 0 } })).data,
  blocked: async () => (await apiClient.get('/friends/blocked', { params: { limit: 100, offset: 0 } })).data,
  sendRequest: async (receiverId, message = null) => (await apiClient.post('/friends/requests', { receiverId, message })).data,
  acceptRequest: async (requestId) => (await apiClient.post(`/friends/requests/${requestId}/accept`)).data,
  declineRequest: async (requestId) => (await apiClient.post(`/friends/requests/${requestId}/decline`)).data,
  cancelRequest: async (requestId) => (await apiClient.post(`/friends/requests/${requestId}/cancel`)).data,
  unfriend: async (friendId) => { await apiClient.delete(`/friends/${friendId}`) },
  block: async (userId, reason = null) => (await apiClient.post(`/friends/block/${userId}`, { reason })).data,
  unblock: async (userId) => { await apiClient.delete(`/friends/block/${userId}`) },
}
