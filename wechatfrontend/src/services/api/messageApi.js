import { apiClient } from './client.js'

export const messageApi = {
  list: async (conversationId, { cursor, limit = 50, signal } = {}) =>
    (await apiClient.get(`/conversations/${conversationId}/messages`, {
      signal,
      params: { limit, ...(cursor ? { cursor } : {}) },
    })).data,
  send: async (conversationId, { content = null, messageType = 'TEXT', attachmentIds = [] } = {}) =>
    (await apiClient.post(`/conversations/${conversationId}/messages`, {
      content,
      messageType,
      replyToMessageId: null,
      attachmentIds,
      attachments: [],
    })).data,
}
