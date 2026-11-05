import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuthStore } from '../auth/authStore.js'
import { useNotificationStore } from '../notifications/notificationStore.js'
import { usePeopleStore } from '../people/peopleStore.js'
import { useRealtimeStore } from '../realtime/realtimeStore.js'
import { toast } from '../ui/toastStore.js'
import { errorMessage } from '../../services/api/errors.js'
import { useChatStore } from './chatStore.js'

const MAX_JUMP_PAGES = 8

export function useChatPageController() {
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const conversations = useChatStore((state) => state.conversations)
  const conversationsById = useChatStore((state) => state.conversationsById)
  const conversationStatus = useChatStore((state) => state.conversationStatus)
  const conversationError = useChatStore((state) => state.conversationError)
  const messagesByConversation = useChatStore((state) => state.messagesByConversation)
  const pagesByConversation = useChatStore((state) => state.pagesByConversation)
  const typingByConversation = useChatStore((state) => state.typingByConversation)
  const composerModeByConversation = useChatStore((state) => state.composerModeByConversation)
  const drafts = useChatStore((state) => state.drafts)
  const showArchived = useChatStore((state) => state.showArchived)
  const loadConversations = useChatStore((state) => state.loadConversations)
  const loadConversation = useChatStore((state) => state.loadConversation)
  const loadLatestMessages = useChatStore((state) => state.loadLatestMessages)
  const loadOlderMessages = useChatStore((state) => state.loadOlderMessages)
  const sendMessage = useChatStore((state) => state.sendMessage)
  const markRead = useChatStore((state) => state.markRead)
  const resetChat = useChatStore((state) => state.reset)
  const setDraft = useChatStore((state) => state.setDraft)
  const setShowArchived = useChatStore((state) => state.setShowArchived)
  const createConversation = useChatStore((state) => state.createConversation)
  const togglePin = useChatStore((state) => state.togglePin)
  const toggleMute = useChatStore((state) => state.toggleMute)
  const toggleArchive = useChatStore((state) => state.toggleArchive)
  const editMessage = useChatStore((state) => state.editMessage)
  const recallMessage = useChatStore((state) => state.recallMessage)
  const deleteMessageForMe = useChatStore((state) => state.deleteMessageForMe)
  const toggleReaction = useChatStore((state) => state.toggleReaction)
  const setComposerMode = useChatStore((state) => state.setComposerMode)
  const people = usePeopleStore((state) => state.byId)
  const friendIds = usePeopleStore((state) => state.friendIds)
  const loadFriends = usePeopleStore((state) => state.loadFriends)
  const rememberPeople = usePeopleStore((state) => state.remember)
  const resetPeople = usePeopleStore((state) => state.reset)
  const connectionStatus = useRealtimeStore((state) => state.status)
  const connectRealtime = useRealtimeStore((state) => state.connect)
  const disconnectRealtime = useRealtimeStore((state) => state.disconnect)
  const retryRealtime = useRealtimeStore((state) => state.retry)
  const sendTyping = useRealtimeStore((state) => state.sendTyping)
  const notificationUnread = useNotificationStore((state) => state.unreadCount)
  const notificationPanel = useNotificationStore((state) => state.open)
  const notificationNotifications = useNotificationStore((state) => state.notifications)
  const notificationStatus = useNotificationStore((state) => state.status)
  const notificationError = useNotificationStore((state) => state.error)
  const setNotificationPanel = useNotificationStore((state) => state.setOpen)
  const loadNotifications = useNotificationStore((state) => state.load)
  const markNotificationRead = useNotificationStore((state) => state.markRead)
  const markAllNotificationsRead = useNotificationStore((state) => state.markAllRead)
  const removeNotification = useNotificationStore((state) => state.remove)

  const [filter, setFilter] = useState(showArchived ? 'archived' : 'all')
  const [newConversationOpen, setNewConversationOpen] = useState(false)
  const [highlightId, setHighlightId] = useState(null)

  const conversation = useMemo(
    () => conversationsById[conversationId] || conversations.find((item) => item.id === conversationId),
    [conversations, conversationsById, conversationId],
  )
  const messages = messagesByConversation[conversationId] || []
  const page = pagesByConversation[conversationId]
  const typingUsers = typingByConversation[conversationId] || []
  const friends = useMemo(() => friendIds.map((id) => people[id]).filter(Boolean), [friendIds, people])

  useEffect(() => {
    rememberPeople([user])
    loadFriends().catch(() => {})
  }, [loadFriends, rememberPeople, user])

  const searchConversations = useCallback((query, signal) => {
    loadConversations({ query, signal }).catch(() => {})
  }, [loadConversations])

  const changeFilter = useCallback((next) => {
    setFilter(next)
    const wantArchived = next === 'archived'
    if (wantArchived !== useChatStore.getState().showArchived) setShowArchived(wantArchived).catch(() => {})
  }, [setShowArchived])

  const handleLoadOlder = useCallback(() => loadOlderMessages(conversationId), [conversationId, loadOlderMessages])
  const handleRetryMessages = useCallback(() => loadLatestMessages(conversationId), [conversationId, loadLatestMessages])
  const handleSend = useCallback((payload) => sendMessage(conversationId, payload), [conversationId, sendMessage])
  const handleTyping = useCallback((typing) => sendTyping(conversationId, typing), [conversationId, sendTyping])
  const selectConversation = useCallback((id) => navigate(`/app/chat/${id}`), [navigate])
  const goBackToConversations = useCallback(() => navigate('/app'), [navigate])
  const toggleNotifications = useCallback(
    () => setNotificationPanel(!notificationPanel),
    [notificationPanel, setNotificationPanel],
  )
  const closeNotifications = useCallback(
    () => setNotificationPanel(false),
    [setNotificationPanel],
  )
  const handleLoadNotifications = useCallback(
    (signal) => loadNotifications({ signal }),
    [loadNotifications],
  )

  const handleConversationAction = useCallback(async (action, target) => {
    try {
      if (action === 'pin') {
        await togglePin(target)
        toast.success(target.pinnedAt ? 'Đã bỏ ghim' : 'Đã ghim cuộc trò chuyện')
      } else if (action === 'mute') {
        await toggleMute(target)
        toast.success(target.muted ? 'Đã bật thông báo' : 'Đã tắt thông báo')
      } else if (action === 'archive') {
        await toggleArchive(target)
        toast.success(target.archivedAt ? 'Đã bỏ lưu trữ' : 'Đã lưu trữ cuộc trò chuyện')
        if (!target.archivedAt && target.id === conversationId && !useChatStore.getState().showArchived) navigate('/app')
      }
    } catch (error) {
      toast.error(errorMessage(error))
    }
  }, [conversationId, navigate, toggleArchive, toggleMute, togglePin])

  const handleCreateConversation = useCallback(async (payload) => {
    const created = await createConversation(payload)
    setNewConversationOpen(false)
    navigate(`/app/chat/${created.id}`)
    return created
  }, [createConversation, navigate])

  const guard = useCallback(async (operation) => {
    try {
      return await operation()
    } catch (error) {
      toast.error(errorMessage(error))
      return null
    }
  }, [])

  const handleReply = useCallback((message, label) => {
    setComposerMode(conversationId, { type: 'reply', message, label })
  }, [conversationId, setComposerMode])
  const handleStartEdit = useCallback((message) => {
    setComposerMode(conversationId, { type: 'edit', message })
  }, [conversationId, setComposerMode])
  const handleCancelMode = useCallback(() => setComposerMode(conversationId, null), [conversationId, setComposerMode])
  const handleRecall = useCallback((message) => guard(() => recallMessage(message)), [guard, recallMessage])
  const handleDelete = useCallback((message) => guard(() => deleteMessageForMe(message)), [deleteMessageForMe, guard])
  const handleReact = useCallback((message, emoji) => guard(() => toggleReaction(message, emoji)), [guard, toggleReaction])

  const handleJumpTo = useCallback(async (messageId) => {
    const isLoaded = () => (useChatStore.getState().messagesByConversation[conversationId] || []).some((item) => item.id === messageId)
    for (let attempt = 0; !isLoaded() && attempt < MAX_JUMP_PAGES; attempt += 1) {
      const result = await loadOlderMessages(conversationId).catch(() => null)
      if (!result) break
    }
    if (!isLoaded()) {
      toast.info('Tin nhắn này nằm quá xa trong lịch sử.')
      return
    }
    setHighlightId(null)
    requestAnimationFrame(() => setHighlightId(messageId))
    setTimeout(() => setHighlightId((current) => (current === messageId ? null : current)), 2_400)
  }, [conversationId, loadOlderMessages])

  useEffect(() => {
    connectRealtime(user.id)
    return () => disconnectRealtime()
  }, [connectRealtime, disconnectRealtime, user.id])

  useEffect(() => {
    if (connectionStatus !== 'connected' || !conversationId) return
    loadLatestMessages(conversationId, { background: true }).catch(() => {})
  }, [connectionStatus, conversationId, loadLatestMessages])

  useEffect(() => {
    if (!conversationId || conversation) return
    const controller = new AbortController()
    loadConversation(conversationId, { signal: controller.signal }).catch(() => {})
    return () => controller.abort()
  }, [conversation, conversationId, loadConversation])

  useEffect(() => {
    if (!conversationId) return
    const controller = new AbortController()
    loadLatestMessages(conversationId, { signal: controller.signal }).catch(() => {})
    return () => controller.abort()
  }, [loadLatestMessages, conversationId])

  const newestMessage = messages.at(-1)
  useEffect(() => {
    if (!conversationId || !conversation?.unreadCount || !newestMessage) return
    markRead(conversationId, newestMessage.id).catch(() => {})
  }, [conversation?.unreadCount, conversationId, markRead, newestMessage])

  const handleLogout = useCallback(async () => {
    disconnectRealtime()
    resetChat()
    resetPeople()
    await logout()
    navigate('/login', { replace: true })
  }, [disconnectRealtime, logout, navigate, resetChat, resetPeople])

  return {
    conversationId,
    user,
    newConversation: {
      open: newConversationOpen,
      friends,
      onCreate: handleCreateConversation,
      onClose: () => setNewConversationOpen(false),
      onOpenContacts: () => navigate('/app/contacts'),
    },
    sidebar: {
      user,
      conversations,
      messagesByConversation,
      activeId: conversationId,
      status: conversationStatus,
      error: conversationError,
      connectionStatus,
      notificationUnread,
      notificationPanel,
      notificationData: {
        notifications: notificationNotifications,
        status: notificationStatus,
        error: notificationError,
      },
      people,
      filter,
      onFilterChange: changeFilter,
      onNewConversation: () => setNewConversationOpen(true),
      onConversationAction: handleConversationAction,
      onSearch: searchConversations,
      onSelect: selectConversation,
      onLogout: handleLogout,
      onRetryConnection: retryRealtime,
      onToggleNotifications: toggleNotifications,
      onCloseNotifications: closeNotifications,
      onLoadNotifications: handleLoadNotifications,
      onReadNotification: markNotificationRead,
      onReadAllNotifications: markAllNotificationsRead,
      onRemoveNotification: removeNotification,
    },
    panel: {
      conversation,
      messages,
      page,
      userId: user.id,
      typingUsers,
      people,
      friends,
      composerMode: composerModeByConversation[conversationId] || null,
      highlightId,
      onBack: goBackToConversations,
      onLoadOlder: handleLoadOlder,
      onRetry: handleRetryMessages,
      draft: drafts[conversationId] || '',
      onDraftChange: (value) => setDraft(conversationId, value),
      onSend: handleSend,
      onTyping: handleTyping,
      onReply: handleReply,
      onStartEdit: handleStartEdit,
      onCancelMode: handleCancelMode,
      onEdit: editMessage,
      onRecall: handleRecall,
      onDelete: handleDelete,
      onReact: handleReact,
      onJumpTo: handleJumpTo,
      onNewConversation: () => setNewConversationOpen(true),
      onLeft: goBackToConversations,
    },
    className: `chat-shell ${conversationId ? 'chat-shell--selected' : ''}`,
  }
}
