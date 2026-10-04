import { useCallback, useEffect, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ChatSidebar } from '../components/conversations/ChatSidebar.jsx'
import { ConversationPanel } from '../components/messages/ConversationPanel.jsx'
import { useAuthStore } from '../features/auth/authStore.js'
import { useChatStore } from '../features/conversations/chatStore.js'
import { useRealtimeStore } from '../features/realtime/realtimeStore.js'
import { useNotificationStore } from '../features/notifications/notificationStore.js'

export function ChatPage() {
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
  const notificationUnread = useNotificationStore((state) => state.unreadCount)
  const loadConversations = useChatStore((state) => state.loadConversations)
  const loadLatestMessages = useChatStore((state) => state.loadLatestMessages)
  const loadOlderMessages = useChatStore((state) => state.loadOlderMessages)
  const sendMessage = useChatStore((state) => state.sendMessage)
  const markRead = useChatStore((state) => state.markRead)
  const resetChat = useChatStore((state) => state.reset)
  const drafts = useChatStore((state) => state.drafts)
  const setDraft = useChatStore((state) => state.setDraft)
  const connectionStatus = useRealtimeStore((state) => state.status)
  const connectRealtime = useRealtimeStore((state) => state.connect)
  const disconnectRealtime = useRealtimeStore((state) => state.disconnect)
  const retryRealtime = useRealtimeStore((state) => state.retry)
  const sendTyping = useRealtimeStore((state) => state.sendTyping)
  const notificationPanel = useNotificationStore((state) => state.open)
  const notificationData = useNotificationStore((state) => ({ notifications: state.notifications, status: state.status, error: state.error }))
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

  const search = useCallback((query, signal) => {
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

  async function handleLogout() {
    disconnectRealtime()
    resetChat()
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <main className={`chat-shell ${conversationId ? 'chat-shell--selected' : ''}`}>
      <ChatSidebar
        user={user}
        conversations={conversations}
        messagesByConversation={messagesByConversation}
        activeId={conversationId}
        status={conversationStatus}
        error={conversationError}
        connectionStatus={connectionStatus}
        notificationUnread={notificationUnread}
        notificationPanel={notificationPanel}
        notificationData={notificationData}
        onSearch={search}
        onSelect={(id) => navigate(`/app/chat/${id}`)}
        onLogout={handleLogout}
        onRetryConnection={retryRealtime}
        onToggleNotifications={() => setNotificationPanel(!notificationPanel)}
        onLoadNotifications={(signal) => loadNotifications({ signal })}
        onReadNotification={markNotificationRead}
        onReadAllNotifications={markAllNotificationsRead}
        onRemoveNotification={removeNotification}
      />
      <ConversationPanel
        conversation={conversation}
        messages={messages}
        page={page}
        userId={user.id}
        typingUsers={typingUsers}
        onBack={() => navigate('/app')}
        onLoadOlder={handleLoadOlder}
        onRetry={handleRetryMessages}
        draft={drafts[conversationId] || ''}
        onDraftChange={(value) => setDraft(conversationId, value)}
        onSend={handleSend}
        onTyping={handleTyping}
      />
    </main>
  )
}
