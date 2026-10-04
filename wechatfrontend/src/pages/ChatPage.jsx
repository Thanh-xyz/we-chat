import { useCallback, useEffect, useMemo } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ChatSidebar } from '../components/conversations/ChatSidebar.jsx'
import { ConversationPanel } from '../components/messages/ConversationPanel.jsx'
import { useAuthStore } from '../features/auth/authStore.js'
import { useChatStore } from '../features/conversations/chatStore.js'
import { useRealtimeStore } from '../features/realtime/realtimeStore.js'

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
  const notificationUnread = useChatStore((state) => state.notificationUnread)
  const loadConversations = useChatStore((state) => state.loadConversations)
  const loadLatestMessages = useChatStore((state) => state.loadLatestMessages)
  const loadOlderMessages = useChatStore((state) => state.loadOlderMessages)
  const sendMessage = useChatStore((state) => state.sendMessage)
  const markRead = useChatStore((state) => state.markRead)
  const resetChat = useChatStore((state) => state.reset)
  const connectionStatus = useRealtimeStore((state) => state.status)
  const connectRealtime = useRealtimeStore((state) => state.connect)
  const disconnectRealtime = useRealtimeStore((state) => state.disconnect)
  const retryRealtime = useRealtimeStore((state) => state.retry)
  const sendTyping = useRealtimeStore((state) => state.sendTyping)

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
        onSearch={search}
        onSelect={(id) => navigate(`/app/chat/${id}`)}
        onLogout={handleLogout}
        onRetryConnection={retryRealtime}
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
        onSend={handleSend}
        onTyping={handleTyping}
      />
    </main>
  )
}
