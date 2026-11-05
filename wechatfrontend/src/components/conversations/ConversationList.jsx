import { Archive, ArchiveRestore, BellOff, BellRing, MessageCircleDashed, MoreHorizontal, Pin, PinOff } from 'lucide-react'
import { useCallback, useRef, useState } from 'react'
import { Avatar } from '../common/Avatar.jsx'
import { useDismiss } from '../../hooks/useDismiss.js'
import { conversationAvatar, conversationName, conversationPreview, directPeerStatus, formatConversationTime, isOnline, messagePreview } from '../../utils/chat.js'

function ConversationMenu({ conversation, onAction, onClose }) {
  const ref = useRef(null)
  useDismiss(ref, onClose)
  const items = [
    { key: 'pin', label: conversation.pinnedAt ? 'Bỏ ghim' : 'Ghim lên đầu', Icon: conversation.pinnedAt ? PinOff : Pin },
    { key: 'mute', label: conversation.muted ? 'Bật thông báo' : 'Tắt thông báo', Icon: conversation.muted ? BellRing : BellOff },
    { key: 'archive', label: conversation.archivedAt ? 'Bỏ lưu trữ' : 'Lưu trữ', Icon: conversation.archivedAt ? ArchiveRestore : Archive },
  ]
  return (
    <div ref={ref} className="popover-menu conversation-menu" role="menu">
      {items.map(({ key, label, Icon }) => (
        <button type="button" role="menuitem" key={key} onClick={() => { onClose(); onAction(key, conversation) }}>
          <Icon size={15} /> {label}
        </button>
      ))}
    </div>
  )
}

export function ConversationList({
  conversations,
  activeId,
  messagesByConversation,
  onSelect,
  onAction,
  currentUserId = null,
  people = {},
  query = '',
  emptyTitle,
  emptyText,
}) {
  const [menuFor, setMenuFor] = useState(null)
  const closeMenu = useCallback(() => setMenuFor(null), [])

  if (!conversations.length) {
    return (
      <div className="sidebar-empty">
        <MessageCircleDashed size={28} />
        <strong>{query.trim() ? 'Không tìm thấy cuộc trò chuyện' : emptyTitle || 'Chưa có cuộc trò chuyện'}</strong>
        <span>{query.trim() ? 'Thử tên, username hoặc từ khóa khác.' : emptyText || 'Bấm nút “Tạo mới” hoặc mở Danh bạ để bắt đầu trò chuyện.'}</span>
      </div>
    )
  }

  return (
    <nav className="conversation-list" aria-label="Danh sách cuộc trò chuyện">
      {conversations.map((conversation) => {
        const options = { currentUserId, people }
        const name = conversationName(conversation, options)
        const latest = (messagesByConversation[conversation.id] || []).at(-1)
        const unread = conversation.unreadCount > 0
        const preview = latest
          ? messagePreview(latest, { ...options, group: conversation.type === 'GROUP' })
          : conversation.lastMessage ? conversationPreview(conversation)
            : unread ? `${conversation.unreadCount > 99 ? '99+' : conversation.unreadCount} tin nhắn mới`
              : conversation.lastMessageId ? (conversation.type === 'GROUP' ? `${conversation.memberIds?.length || 0} thành viên` : 'Nhấn để xem tin nhắn') : 'Bắt đầu cuộc trò chuyện'
        const active = activeId === conversation.id
        return (
          <div key={conversation.id} className={`conversation-entry ${menuFor === conversation.id ? 'has-menu' : ''}`}>
            <button
              type="button"
              className={`conversation-item ${active ? 'is-active' : ''} ${unread ? 'is-unread' : ''}`}
              onClick={() => onSelect(conversation.id)}
              aria-current={active ? 'page' : undefined}
            >
              <Avatar name={name} src={conversationAvatar(conversation, options)} size="large" group={conversation.type === 'GROUP'} online={isOnline(directPeerStatus(conversation, options))} />
              <span className="conversation-copy">
                <span className="conversation-row">
                  <strong>{name}</strong>
                  <time dateTime={conversation.lastMessageAt || conversation.updatedAt}>{formatConversationTime(conversation.lastMessageAt || conversation.updatedAt)}</time>
                </span>
                <span className="conversation-row conversation-row--meta">
                  <span>{preview}</span>
                  <span className="conversation-flags">
                    {conversation.muted && <BellOff size={13} aria-label="Đã tắt thông báo" />}
                    {conversation.pinnedAt && <Pin size={13} aria-label="Đã ghim" />}
                    {unread && (
                      <span className={`unread-badge ${conversation.muted ? 'unread-badge--muted' : ''}`} aria-label={`${conversation.unreadCount} tin nhắn chưa đọc`}>
                        {conversation.unreadCount > 99 ? '99+' : conversation.unreadCount}
                      </span>
                    )}
                  </span>
                </span>
              </span>
            </button>
            {onAction && (
              <button
                type="button"
                className="icon-button conversation-more"
                aria-label={`Tùy chọn cho ${name}`}
                aria-haspopup="menu"
                aria-expanded={menuFor === conversation.id}
                onClick={() => setMenuFor((current) => (current === conversation.id ? null : conversation.id))}
              >
                <MoreHorizontal size={17} />
              </button>
            )}
            {menuFor === conversation.id && <ConversationMenu conversation={conversation} onAction={onAction} onClose={closeMenu} />}
          </div>
        )
      })}
    </nav>
  )
}
