import { MessageCircleDashed } from 'lucide-react'
import { Avatar } from '../common/Avatar.jsx'
import { conversationName, formatConversationTime } from '../../utils/chat.js'

export function ConversationList({ conversations, activeId, messagesByConversation, onSelect }) {
  if (!conversations.length) {
    return (
      <div className="sidebar-empty">
        <MessageCircleDashed size={28} />
        <strong>Chưa có cuộc trò chuyện</strong>
        <span>Các cuộc trò chuyện thật từ máy chủ sẽ xuất hiện tại đây.</span>
      </div>
    )
  }

  return (
    <nav className="conversation-list" aria-label="Danh sách cuộc trò chuyện">
      {conversations.map((conversation) => {
        const name = conversationName(conversation)
        const messages = messagesByConversation[conversation.id] || []
        const latest = messages.at(-1)
        const preview = latest?.recalled
          ? 'Tin nhắn đã được thu hồi'
          : latest?.content || (conversation.lastMessageId ? 'Có tin nhắn mới' : 'Bắt đầu cuộc trò chuyện')
        return (
          <button
            type="button"
            key={conversation.id}
            className={`conversation-item ${activeId === conversation.id ? 'is-active' : ''}`}
            onClick={() => onSelect(conversation.id)}
            aria-current={activeId === conversation.id ? 'page' : undefined}
          >
            <Avatar name={name} src={conversation.avatarUrl} size="large" />
            <span className="conversation-copy">
              <span className="conversation-row">
                <strong>{name}</strong>
                <time>{formatConversationTime(conversation.lastMessageAt || conversation.updatedAt)}</time>
              </span>
              <span className="conversation-row conversation-row--meta">
                <span>{preview}</span>
                {conversation.unreadCount > 0 && (
                  <span className="unread-badge" aria-label={`${conversation.unreadCount} tin nhắn chưa đọc`}>
                    {conversation.unreadCount > 99 ? '99+' : conversation.unreadCount}
                  </span>
                )}
              </span>
            </span>
          </button>
        )
      })}
    </nav>
  )
}
