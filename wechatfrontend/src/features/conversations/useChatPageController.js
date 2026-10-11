import { useCallback, useEffect, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useAuthStore } from '../auth/authStore.js'
import { useNotificationStore } from '../notifications/notificationStore.js'
import { useRealtimeStore } from '../realtime/realtimeStore.js'
import { useChatStore } from './chatStore.js'

export function useChatPageController() {
  const { conversationId } = useParams()
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const logout = useAuthStore((state) => state.logout)
  const conversations = useChatStore((state) => state.conversations)
  const conversationStatus = useChatStore((state) => state.conversationStatus)
  const conversationError = useChatStore((state) => state.conversationError)
  const messagesByConversation = useChatStore((state) => state.messagesByConversation)
  const pagesByConversation = useChatStore((state) => state.pagesByConversation)
  const typingByConversation = useChatStore((state) => state.typingByConversation)
  const drafts = useChatStore((state) => state.drafts)
  const loadConversations = useChatStore((state) => state.loadConversations)
  const loadLatestMessages = useChatStore((state) => state.loadLatestMessages)
  const loadOlderMessages = useChatStore((state) => state.loadOlderMessages)
  const sendMessage = useChatStore((state) => state.sendMessage)
  const markRead = useChatStore((state) => state.markRead)
  const resetChat = useChatStore((state) => state.reset)
  const setDraft = useChatStore((state) => state.setDraft)
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

  const conversation = useMemo(
    () => conversations.find((item) => item.id === conversationId),
    [conversations, conversationId],
  )
  const messages = messagesByConversation[conversationId] || []
  const page = pagesByConversation[conversationId]
  const typingUsers = typingByConversation[conversationId] || []

  const searchConversations = useCallback((query, signal) => {
    loadConversations({ query, signal }).catch(() => {})
  }, [loadConversations])

  const handleLoadOlder = useCallback(
    () => loadOlderMessages(conversationId),
    [conversationId, loadOlderMessages],
  )
  const handleRetryMessages = useCallback(
    () => loadLatestMessages(conversationId),
    [conversationId, loadLatestMessages],
  )
  const handleSend = useCallback(
    (content) => sendMessage(conversationId, content),
    [conversationId, sendMessage],
  )
  const handleTyping = useCallback(
    (typing) => sendTyping(conversationId, typing),
    [conversationId, sendTyping],
  )
  const selectConversation = useCallback(
    (id) => navigate(`/app/chat/${id}`),
    [navigate],
  )
  const goBackToConversations = useCallback(
    () => navigate('/app'),
    [navigate],
  )
  const toggleNotifications = useCallback(
    () => setNotificationPanel(!notificationPanel),
    [notificationPanel, setNotificationPanel],
  )

  useEffect(() => {
    connectRealtime(user.id)
    return () => disconnectRealtime()
  }, [connectRealtime, disconnectRealtime, user.id])

  useEffect(() => {
    if (connectionStatus !== 'connected' || !conversationId) return
    loadLatestMessages(conversationId, { background: true }).catch(() => {})
  }, [connectionStatus, conversationId, loadLatestMessages])

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
    await logout()
    navigate('/login', { replace: true })
  }, [disconnectRealtime, logout, navigate, resetChat])

  return {
    conversationId,
    user,
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
      onSearch: searchConversations,
      onSelect: selectConversation,
      onLogout: handleLogout,
      onRetryConnection: retryRealtime,
      onToggleNotifications: toggleNotifications,
      onLoadNotifications: (signal) => loadNotifications({ signal }),
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
      onBack: goBackToConversations,
      onLoadOlder: handleLoadOlder,
      onRetry: handleRetryMessages,
      draft: drafts[conversationId] || '',
      onDraftChange: (value) => setDraft(conversationId, value),
      onSend: handleSend,
      onTyping: handleTyping,
    },
    className: `chat-shell ${conversationId ? 'chat-shell--selected' : ''}`,
  }
}
