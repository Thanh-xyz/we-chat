import { create } from 'zustand'
import { conversationApi } from '../../services/api/conversationApi.js'
import { messageApi } from '../../services/api/messageApi.js'
import { normalizeApiError } from '../../services/api/errors.js'
import { mergeMessages, sortConversations, updateConversationUnread } from '../../utils/chat.js'

const typingTimers = new Map()
const MAX_CONVERSATION_LOAD_ATTEMPTS = 2

function updateConversationActivity(conversations, conversationId, occurredAt) {
  return sortConversations(conversations.map((conversation) =>
    conversation.id === conversationId
      ? { ...conversation, lastMessageAt: occurredAt || conversation.lastMessageAt }
      : conversation,
  ))
}

function mergeConversationMap(current, conversations) {
  return conversations.reduce((result, conversation) => ({
    ...result,
    [conversation.id]: { ...result[conversation.id], ...conversation },
  }), current)
}

function updateConversationMap(current, conversationId, updater) {
  const conversation = current[conversationId]
  return conversation ? { ...current, [conversationId]: updater(conversation) } : current
}

export const useChatStore = create((set, get) => ({
  conversations: [],
  conversationsById: {},
  conversationQuery: '',
  conversationStatus: 'idle',
  conversationError: null,
  conversationRequestId: 0,
  conversationRevision: 0,
  messagesByConversation: {},
  pagesByConversation: {},
  typingByConversation: {},
  drafts: {},
  notificationUnread: 0,
  showArchived: false,
  composerModeByConversation: {},

  loadConversations: async ({ signal, query = '' } = {}) => {
    const requestId = get().conversationRequestId + 1
    set({ conversationQuery: query, conversationRequestId: requestId, conversationStatus: 'loading', conversationError: null })
    try {
      let conversations
      for (let attempt = 0; attempt < MAX_CONVERSATION_LOAD_ATTEMPTS; attempt += 1) {
        const revision = get().conversationRevision
        conversations = await conversationApi.list({ signal, query, includeArchived: get().showArchived })
        if (get().conversationRequestId !== requestId) return conversations
        if (get().conversationRevision !== revision) continue
        set((state) => ({
          conversations: sortConversations(conversations),
          conversationsById: mergeConversationMap(state.conversationsById, conversations),
          conversationStatus: 'success',
        }))
        return conversations
      }
      set({ conversationStatus: 'success' })
      return conversations
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (get().conversationRequestId !== requestId) return []
      if (normalized.code === 'REQUEST_CANCELED') {
        set((state) => ({
          conversationStatus: state.conversations.length ? 'success' : 'idle',
        }))
        return []
      }
      set({ conversationStatus: 'error', conversationError: normalized })
      throw normalized
    }
  },

  loadConversation: async (conversationId, { signal } = {}) => {
    const cached = get().conversationsById[conversationId]
    if (cached) return cached
    try {
      const conversation = await conversationApi.get(conversationId, { signal })
      set((state) => ({
        conversationsById: mergeConversationMap(state.conversationsById, [conversation]),
      }))
      return conversation
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (normalized.code === 'REQUEST_CANCELED') return null
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

  sendMessage: async (conversationId, { content = null, messageType = 'TEXT', attachmentIds = [], replyToMessageId = null } = {}) => {
    const message = await messageApi.send(conversationId, { content, messageType, attachmentIds, replyToMessageId })
    set((state) => ({
      messagesByConversation: {
        ...state.messagesByConversation,
        [conversationId]: mergeMessages(state.messagesByConversation[conversationId], [message]),
      },
      conversations: updateConversationActivity(state.conversations, conversationId, message.createdAt),
      conversationsById: updateConversationMap(state.conversationsById, conversationId, (conversation) => ({
        ...conversation,
        lastMessageAt: message.createdAt,
        lastMessageId: message.id,
        lastMessage: {
          id: message.id,
          senderId: message.senderId,
          messageType: message.messageType,
          contentPreview: message.content,
          recalled: message.recalled,
          createdAt: message.createdAt,
          sentByCurrentUser: true,
          attachmentCount: message.attachments?.length || 0,
        },
      })),
      conversationRevision: state.conversationRevision + 1,
    }))
    return message
  },

  setDraft: (conversationId, content) => set((state) => ({ drafts: { ...state.drafts, [conversationId]: content } })),

  markRead: async (conversationId, lastReadMessageId = null) => {
    const result = await conversationApi.markRead(conversationId, lastReadMessageId)
    set((state) => ({
      conversations: updateConversationUnread(state.conversations, conversationId, result.unreadCount),
      conversationsById: updateConversationMap(state.conversationsById, conversationId, (conversation) => ({ ...conversation, unreadCount: result.unreadCount })),
      conversationRevision: state.conversationRevision + 1,
    }))
    return result
  },

  handleRealtimeEvent: async (event) => {
    if (!event?.type) return
    const conversationId = event.conversationId
    const known = conversationId && get().conversations.some((item) => item.id === conversationId)
    if (event.type === 'message.created' && conversationId) {
      if (!known && !get().conversationQuery) {
        get().loadConversations({ query: '' }).catch(() => {})
      }
      set((state) => ({
        conversations: updateConversationActivity(state.conversations, conversationId, event.occurredAt),
        conversationsById: updateConversationMap(state.conversationsById, conversationId, (conversation) => ({
          ...conversation,
          lastMessageAt: event.occurredAt || conversation.lastMessageAt,
        })),
        conversationRevision: state.conversationRevision + 1,
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
        conversationsById: updateConversationMap(state.conversationsById, conversationId, (conversation) => ({ ...conversation, unreadCount })),
        conversationRevision: state.conversationRevision + 1,
      }))
      return
    }
    if (event.type === 'conversation.typing.started' || event.type === 'conversation.typing.stopped') {
      get().setTyping(conversationId, event.actorUserId, event.type.endsWith('started'))
      return
    }
    if (event.type === 'message.deleted_for_me' && conversationId) {
      const messageId = event.messageId || event.payload?.messageId
      if (messageId) get().removeMessageLocally(conversationId, messageId)
      return
    }
    if (event.type.startsWith('message.') && conversationId) {
      try {
        await get().loadLatestMessages(conversationId, { background: true })
      } catch {
        // An explicit retry remains available in the message panel.
      }
      return
    }
    if (event.type.startsWith('conversation.') && conversationId && event.type !== 'conversation.read') {
      await get().refreshConversation(conversationId)
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

  setShowArchived: async (showArchived) => {
    set({ showArchived })
    return get().loadConversations({ query: get().conversationQuery })
  },

  upsertConversation: (conversation) => {
    if (!conversation?.id) return
    set((state) => {
      const hidden = conversation.archivedAt && !state.showArchived
      const rest = state.conversations.filter((item) => item.id !== conversation.id)
      return {
        conversations: sortConversations(hidden ? rest : [...rest, conversation]),
        conversationsById: mergeConversationMap(state.conversationsById, [conversation]),
        conversationRevision: state.conversationRevision + 1,
      }
    })
  },

  removeConversation: (conversationId) => set((state) => {
    const conversationsById = { ...state.conversationsById }
    delete conversationsById[conversationId]
    return {
      conversations: state.conversations.filter((item) => item.id !== conversationId),
      conversationsById,
      conversationRevision: state.conversationRevision + 1,
    }
  }),

  refreshConversation: async (conversationId) => {
    try {
      const conversation = await conversationApi.get(conversationId)
      get().upsertConversation(conversation)
      return conversation
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (normalized.status === 403 || normalized.status === 404) get().removeConversation(conversationId)
      return null
    }
  },

  createConversation: async (payload) => {
    const conversation = await conversationApi.create(payload)
    get().upsertConversation(conversation)
    return conversation
  },

  updateConversation: async (conversationId, changes) => {
    const conversation = await conversationApi.update(conversationId, changes)
    get().upsertConversation(conversation)
    return conversation
  },

  addMembers: async (conversationId, userIds) => {
    const conversation = await conversationApi.addMembers(conversationId, userIds)
    get().upsertConversation(conversation)
    return conversation
  },

  removeMember: async (conversationId, userId) => {
    const conversation = await conversationApi.removeMember(conversationId, userId)
    get().upsertConversation(conversation)
    return conversation
  },

  leaveConversation: async (conversationId) => {
    await conversationApi.leave(conversationId)
    get().removeConversation(conversationId)
  },

  togglePin: async (conversation) => {
    const updated = conversation.pinnedAt
      ? await conversationApi.unpin(conversation.id)
      : await conversationApi.pin(conversation.id)
    get().upsertConversation(updated)
    return updated
  },

  toggleMute: async (conversation, mutedUntil = null) => {
    const updated = conversation.muted
      ? await conversationApi.unmute(conversation.id)
      : await conversationApi.mute(conversation.id, mutedUntil)
    get().upsertConversation(updated)
    return updated
  },

  toggleArchive: async (conversation) => {
    const updated = conversation.archivedAt
      ? await conversationApi.unarchive(conversation.id)
      : await conversationApi.archive(conversation.id)
    get().upsertConversation(updated)
    return updated
  },

  replaceMessage: (message) => {
    if (!message?.conversationId) return
    set((state) => ({
      messagesByConversation: {
        ...state.messagesByConversation,
        [message.conversationId]: mergeMessages(state.messagesByConversation[message.conversationId], [message]),
      },
    }))
  },

  removeMessageLocally: (conversationId, messageId) => set((state) => ({
    messagesByConversation: {
      ...state.messagesByConversation,
      [conversationId]: (state.messagesByConversation[conversationId] || []).filter((item) => item.id !== messageId),
    },
  })),

  editMessage: async (message, content) => {
    const updated = await messageApi.edit(message.id, content)
    get().replaceMessage(updated)
    return updated
  },

  recallMessage: async (message) => {
    const updated = await messageApi.recall(message.id)
    get().replaceMessage(updated)
    return updated
  },

  deleteMessageForMe: async (message) => {
    await messageApi.deleteForMe(message.id)
    get().removeMessageLocally(message.conversationId, message.id)
  },

  toggleReaction: async (message, emoji) => {
    const mine = message.reactions?.some((reaction) => reaction.emoji === emoji && reaction.reactedByMe)
    const reactions = mine
      ? await messageApi.removeReaction(message.id, emoji)
      : await messageApi.addReaction(message.id, emoji)
    get().replaceMessage({ ...message, reactions })
    return reactions
  },

  setComposerMode: (conversationId, mode) => set((state) => ({
    composerModeByConversation: { ...state.composerModeByConversation, [conversationId]: mode },
  })),

  reset: () => {
    typingTimers.forEach(clearTimeout)
    typingTimers.clear()
    set((state) => ({
      conversations: [],
      conversationsById: {},
      conversationQuery: '',
      conversationStatus: 'idle',
      conversationError: null,
      conversationRequestId: state.conversationRequestId + 1,
      conversationRevision: 0,
      messagesByConversation: {},
      pagesByConversation: {},
      typingByConversation: {},
      drafts: {},
      notificationUnread: 0,
      showArchived: false,
      composerModeByConversation: {},
    }))
  },
}))
