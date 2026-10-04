import { create } from 'zustand'
import { conversationApi } from '../../services/api/conversationApi.js'
import { messageApi } from '../../services/api/messageApi.js'
import { normalizeApiError } from '../../services/api/errors.js'
import { mergeMessages, sortConversations, updateConversationUnread } from '../../utils/chat.js'

const typingTimers = new Map()

function updateConversationActivity(conversations, conversationId, occurredAt) {
  return sortConversations(conversations.map((conversation) =>
    conversation.id === conversationId
      ? { ...conversation, lastMessageAt: occurredAt || conversation.lastMessageAt }
      : conversation,
  ))
}

export const useChatStore = create((set, get) => ({
  conversations: [],
  conversationStatus: 'idle',
  conversationError: null,
  messagesByConversation: {},
  pagesByConversation: {},
  typingByConversation: {},
  drafts: {},
  notificationUnread: 0,

  loadConversations: async ({ signal, query = '' } = {}) => {
    set({ conversationStatus: 'loading', conversationError: null })
    try {
      const conversations = await conversationApi.list({ signal, query })
      set({ conversations: sortConversations(conversations), conversationStatus: 'success' })
      return conversations
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (normalized.code === 'REQUEST_CANCELED') return []
      set({ conversationStatus: 'error', conversationError: normalized })
      throw normalized
    }
  },

  loadLatestMessages: async (conversationId, { signal, background = false } = {}) => {
    if (!background) {
      set((state) => ({
        pagesByConversation: {
          ...state.pagesByConversation,
          [conversationId]: { ...state.pagesByConversation[conversationId], status: 'loading', error: null },
        },
      }))
    }
    try {
      const page = await messageApi.list(conversationId, { signal, limit: 50 })
      set((state) => ({
        messagesByConversation: {
          ...state.messagesByConversation,
          [conversationId]: mergeMessages(state.messagesByConversation[conversationId], page.items),
        },
        pagesByConversation: {
          ...state.pagesByConversation,
          [conversationId]: {
            status: 'success',
            error: null,
            nextCursor: page.nextCursor,
            hasNext: page.hasNext,
            loadingOlder: false,
          },
        },
      }))
      return page
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (normalized.code === 'REQUEST_CANCELED') return null
      set((state) => ({
        pagesByConversation: {
          ...state.pagesByConversation,
          [conversationId]: { ...state.pagesByConversation[conversationId], status: 'error', error: normalized },
        },
      }))
      throw normalized
    }
  },

  loadOlderMessages: async (conversationId) => {
    const currentPage = get().pagesByConversation[conversationId]
    if (!currentPage?.hasNext || !currentPage.nextCursor || currentPage.loadingOlder) return null
    set((state) => ({
      pagesByConversation: {
        ...state.pagesByConversation,
        [conversationId]: { ...state.pagesByConversation[conversationId], loadingOlder: true, error: null },
      },
    }))
    try {
      const page = await messageApi.list(conversationId, { cursor: currentPage.nextCursor, limit: 50 })
      set((state) => ({
        messagesByConversation: {
          ...state.messagesByConversation,
          [conversationId]: mergeMessages(state.messagesByConversation[conversationId], page.items),
        },
        pagesByConversation: {
          ...state.pagesByConversation,
          [conversationId]: {
            ...state.pagesByConversation[conversationId],
            status: 'success',
            loadingOlder: false,
            nextCursor: page.nextCursor,
            hasNext: page.hasNext,
          },
        },
      }))
      return page
    } catch (error) {
      const normalized = normalizeApiError(error)
      set((state) => ({
        pagesByConversation: {
          ...state.pagesByConversation,
          [conversationId]: { ...state.pagesByConversation[conversationId], loadingOlder: false, error: normalized },
        },
      }))
      throw normalized
    }
  },

  sendMessage: async (conversationId, { content = null, messageType = 'TEXT', attachmentIds = [] } = {}) => {
    const message = await messageApi.send(conversationId, { content, messageType, attachmentIds })
    set((state) => ({
      messagesByConversation: {
        ...state.messagesByConversation,
        [conversationId]: mergeMessages(state.messagesByConversation[conversationId], [message]),
      },
      conversations: updateConversationActivity(state.conversations, conversationId, message.createdAt),
    }))
    return message
  },

  setDraft: (conversationId, content) => set((state) => ({ drafts: { ...state.drafts, [conversationId]: content } })),

  markRead: async (conversationId, lastReadMessageId = null) => {
    const result = await conversationApi.markRead(conversationId, lastReadMessageId)
    set((state) => ({
      conversations: updateConversationUnread(state.conversations, conversationId, result.unreadCount),
    }))
    return result
  },

  handleRealtimeEvent: async (event) => {
    if (!event?.type) return
    const conversationId = event.conversationId
    if (event.type === 'message.created' && conversationId) {
      set((state) => ({
        conversations: updateConversationActivity(state.conversations, conversationId, event.occurredAt),
      }))
      try {
        await get().loadLatestMessages(conversationId, { background: true })
      } catch {
        // The visible error state remains available for an explicit retry.
      }
      return
    }
    if (event.type === 'conversation.unread.updated' && conversationId) {
      const unreadCount = Number(event.payload?.unreadCount ?? 0)
      set((state) => ({
        conversations: updateConversationUnread(state.conversations, conversationId, unreadCount),
      }))
      return
    }
    if (event.type === 'conversation.typing.started' || event.type === 'conversation.typing.stopped') {
      get().setTyping(conversationId, event.actorUserId, event.type.endsWith('started'))
      return
    }
    if (event.type.startsWith('message.') && conversationId) {
      try {
        await get().loadLatestMessages(conversationId, { background: true })
      } catch {
        // An explicit retry remains available in the message panel.
      }
    }
  },

  handleNotificationEvent: (event) => {
    if (Number.isFinite(event?.unreadCount)) set({ notificationUnread: event.unreadCount })
  },

  setTyping: (conversationId, userId, typing) => {
    if (!conversationId || !userId) return
    const key = `${conversationId}:${userId}`
    clearTimeout(typingTimers.get(key))
    set((state) => {
      const current = new Set(state.typingByConversation[conversationId] || [])
      if (typing) current.add(userId)
      else current.delete(userId)
      return { typingByConversation: { ...state.typingByConversation, [conversationId]: [...current] } }
    })
    if (typing) {
      typingTimers.set(key, setTimeout(() => get().setTyping(conversationId, userId, false), 4_500))
    } else {
      typingTimers.delete(key)
    }
  },

  reset: () => {
    typingTimers.forEach(clearTimeout)
    typingTimers.clear()
    set({
      conversations: [],
      conversationStatus: 'idle',
      conversationError: null,
      messagesByConversation: {},
      pagesByConversation: {},
      typingByConversation: {},
      drafts: {},
      notificationUnread: 0,
    })
  },
}))
