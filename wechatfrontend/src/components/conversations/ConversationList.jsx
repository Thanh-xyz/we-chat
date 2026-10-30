import { MessageCircleDashed } from 'lucide-react'
import { Avatar } from '../common/Avatar.jsx'
import { conversationAvatar, conversationName, conversationPreview, formatConversationTime } from '../../utils/chat.js'

export function ConversationList({ conversations, activeId, messagesByConversation, query = '', onSelect }) {
  if (!conversations.length) {
    return (
      <div className="sidebar-empty">
        <MessageCircleDashed size={28} />
        <strong>{query.trim() ? 'Không tìm thấy cuộc trò chuyện' : 'Chưa có cuộc trò chuyện'}</strong>
        <span>{query.trim() ? 'Thử tên, username hoặc từ khóa khác.' : 'Bắt đầu trò chuyện từ danh bạ bạn bè.'}</span>
      </div>
    )
  }

  return (
    <nav className="conversation-list" aria-label="Danh sách cuộc trò chuyện">
      {conversations.map((conversation) => {
        const name = conversationName(conversation)
        const messages = messagesByConversation[conversation.id] || []
        const latest = messages.at(-1)
        const preview = conversationPreview(conversation, latest)
        return (
          <button
            type="button"
            key={conversation.id}
            className={`conversation-item ${activeId === conversation.id ? 'is-active' : ''}`}
            onClick={() => onSelect(conversation.id)}
            aria-current={activeId === conversation.id ? 'page' : undefined}
          >
            <Avatar name={name} src={conversationAvatar(conversation)} size="large" />
            <span className="conversation-copy">
              <span className="conversation-row">
                <strong>{name}</strong>
                <time dateTime={conversation.lastMessageAt || conversation.updatedAt}>{formatConversationTime(conversation.lastMessageAt || conversation.updatedAt)}</time>
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
