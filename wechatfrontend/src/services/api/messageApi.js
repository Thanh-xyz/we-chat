import { apiClient } from './client.js'

const unwrap = async (request) => (await request).data

export const messageApi = {
  list: (conversationId, { cursor, limit = 50, signal } = {}) =>
    unwrap(apiClient.get(`/conversations/${conversationId}/messages`, {
      signal,
      params: { limit, ...(cursor ? { cursor } : {}) },
    })),
  search: (conversationId, q, { cursor, limit = 30, signal } = {}) =>
    unwrap(apiClient.get(`/conversations/${conversationId}/messages/search`, {
      signal,
      params: { q, limit, ...(cursor ? { cursor } : {}) },
    })),
  send: (conversationId, { content = null, messageType = 'TEXT', attachmentIds = [], replyToMessageId = null } = {}) =>
    unwrap(apiClient.post(`/conversations/${conversationId}/messages`, {
      content,
      messageType,
      replyToMessageId,
      attachmentIds,
      attachments: [],
    })),
  edit: (messageId, content) => unwrap(apiClient.patch(`/messages/${messageId}`, { content })),
  recall: (messageId) => unwrap(apiClient.post(`/messages/${messageId}/recall`)),
  deleteForMe: async (messageId) => { await apiClient.delete(`/messages/${messageId}`) },
  addReaction: (messageId, emoji) => unwrap(apiClient.post(`/messages/${messageId}/reactions`, { emoji })),
  removeReaction: (messageId, emoji) =>
    unwrap(apiClient.delete(`/messages/${messageId}/reactions`, { data: { emoji } })),
}
