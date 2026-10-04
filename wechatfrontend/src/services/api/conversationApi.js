import { apiClient } from './client.js'

export const conversationApi = {
  list: async ({ signal, query = '' } = {}) => {
    const endpoint = query.trim() ? '/conversations/search' : '/conversations'
    const { data } = await apiClient.get(endpoint, {
      signal,
      params: { limit: 100, offset: 0, ...(query.trim() ? { q: query.trim() } : {}) },
    })
    return data
  },
  get: async (conversationId, { signal } = {}) =>
    (await apiClient.get(`/conversations/${conversationId}`, { signal })).data,
  markRead: async (conversationId, lastReadMessageId = null) =>
    (await apiClient.post(`/conversations/${conversationId}/read`, { lastReadMessageId })).data,
}
