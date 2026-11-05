import { apiClient } from './client.js'

const unwrap = async (request) => (await request).data

export const conversationApi = {
  list: async ({ signal, query = '', includeArchived = false } = {}) => {
    const q = query.trim()
    const endpoint = q ? '/conversations/search' : '/conversations'
    return unwrap(apiClient.get(endpoint, {
      signal,
      params: { limit: 100, offset: 0, includeArchived, ...(q ? { q } : {}) },
    }))
  },
  get: (conversationId, { signal } = {}) => unwrap(apiClient.get(`/conversations/${conversationId}`, { signal })),
  create: ({ type, memberIds, name = null, avatarUrl = null }) =>
    unwrap(apiClient.post('/conversations', { type, memberIds, name, avatarUrl })),
  createDirect: (userId) => conversationApi.create({ type: 'DIRECT', memberIds: [userId] }),
  update: (conversationId, { name, avatarUrl } = {}) =>
    unwrap(apiClient.patch(`/conversations/${conversationId}`, { name: name ?? null, avatarUrl: avatarUrl ?? null })),
  addMembers: (conversationId, userIds) =>
    unwrap(apiClient.post(`/conversations/${conversationId}/members`, { userIds })),
  removeMember: (conversationId, userId) =>
    unwrap(apiClient.delete(`/conversations/${conversationId}/members/${userId}`)),
  leave: (conversationId) => unwrap(apiClient.post(`/conversations/${conversationId}/leave`)),
  markRead: (conversationId, lastReadMessageId = null) =>
    unwrap(apiClient.post(`/conversations/${conversationId}/read`, { lastReadMessageId })),
  unreadCount: (conversationId) => unwrap(apiClient.get(`/conversations/${conversationId}/unread-count`)),
  totalUnreadCount: ({ includeArchived = false, signal } = {}) =>
    unwrap(apiClient.get('/conversations/unread-count', { signal, params: { includeArchived } })),
  readReceipts: (conversationId, { signal } = {}) =>
    unwrap(apiClient.get(`/conversations/${conversationId}/read-receipts`, { signal })),
  pin: (conversationId) => unwrap(apiClient.post(`/conversations/${conversationId}/pin`)),
  unpin: (conversationId) => unwrap(apiClient.delete(`/conversations/${conversationId}/pin`)),
  mute: (conversationId, mutedUntil = null) =>
    unwrap(apiClient.post(`/conversations/${conversationId}/mute`, { mutedUntil })),
  unmute: (conversationId) => unwrap(apiClient.delete(`/conversations/${conversationId}/mute`)),
  archive: (conversationId) => unwrap(apiClient.post(`/conversations/${conversationId}/archive`)),
  unarchive: (conversationId) => unwrap(apiClient.delete(`/conversations/${conversationId}/archive`)),
}
