import { Bell, LogOut, MessageCircleMore, PenSquare, Search, Settings, Users } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Avatar } from '../common/Avatar.jsx'
import { ConnectionBadge } from '../common/ConnectionBadge.jsx'
import { ConversationList } from './ConversationList.jsx'
import { NotificationPanel } from '../notifications/NotificationPanel.jsx'

const FILTERS = [
  ['all', 'Tất cả'],
  ['unread', 'Chưa đọc'],
  ['groups', 'Nhóm'],
  ['archived', 'Lưu trữ'],
]

const EMPTY_COPY = {
  unread: ['Không có tin nhắn chưa đọc', 'Bạn đã xem hết mọi thứ 🎉'],
  groups: ['Chưa có nhóm nào', 'Tạo nhóm với từ hai người bạn trở lên.'],
  archived: ['Không có cuộc trò chuyện lưu trữ', 'Cuộc trò chuyện bạn lưu trữ sẽ nằm ở đây.'],
}

export function ChatSidebar({
  user,
  conversations,
  messagesByConversation,
  activeId,
  status,
  error,
  connectionStatus,
  notificationUnread,
  notificationPanel,
  notificationData,
  people = {},
  filter = 'all',
  onFilterChange,
  onNewConversation,
  onConversationAction,
  onSearch,
  onSelect,
  onLogout,
  onRetryConnection,
  onToggleNotifications,
  onCloseNotifications,
  onLoadNotifications,
  onReadNotification,
  onReadAllNotifications,
  onRemoveNotification,
}) {
  const [query, setQuery] = useState('')
  const notificationTriggerRef = useRef(null)
  const location = useLocation()
  const navigate = useNavigate()

  useEffect(() => {
    const controller = new AbortController()
    const timer = setTimeout(() => onSearch(query, controller.signal), 300)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [onSearch, query])

  useEffect(() => {
    return () => onCloseNotifications?.()
  }, [location.pathname, onCloseNotifications])

  const visible = useMemo(() => {
    if (filter === 'archived') return conversations.filter((item) => item.archivedAt)
    const active = conversations.filter((item) => !item.archivedAt)
    if (filter === 'unread') return active.filter((item) => item.unreadCount > 0)
    if (filter === 'groups') return active.filter((item) => item.type === 'GROUP')
    return active
  }, [conversations, filter])

  const totalUnread = useMemo(
    () => conversations.reduce((sum, item) => sum + (item.archivedAt || item.muted ? 0 : item.unreadCount || 0), 0),
    [conversations],
  )
  const name = user.displayName || user.username
  const [emptyTitle, emptyText] = EMPTY_COPY[filter] || []

  return (
    <aside className={`chat-sidebar ${activeId ? 'has-mobile-selection' : ''}`}>
      <header className="sidebar-header">
        <div className="sidebar-brand">
          <span className="brand-mark"><MessageCircleMore size={21} /></span>
          <strong>ChatSpace</strong>
        </div>
        <div className="sidebar-actions">
          <button ref={notificationTriggerRef} type="button" className="notification-indicator" title={`${notificationUnread} thông báo chưa đọc`} onClick={onToggleNotifications} aria-label="Mở thông báo" aria-expanded={notificationPanel} aria-haspopup="dialog">
            <Bell size={19} />
            {notificationUnread > 0 && <span>{notificationUnread > 9 ? '9+' : notificationUnread}</span>}
          </button>
          <button className="icon-button" type="button" onClick={() => navigate('/app/contacts')} aria-label="Mở danh bạ" title="Danh bạ">
            <Users size={19} />
          </button>
          <button className="icon-button" type="button" onClick={() => navigate('/app/settings')} aria-label="Mở cài đặt" title="Cài đặt">
            <Settings size={19} />
          </button>
          <button className="icon-button" type="button" onClick={onLogout} aria-label="Đăng xuất" title="Đăng xuất">
            <LogOut size={19} />
          </button>
        </div>
      </header>
      {notificationPanel && <NotificationPanel {...notificationData} triggerRef={notificationTriggerRef} onClose={onCloseNotifications || onToggleNotifications} onLoad={onLoadNotifications} onRead={onReadNotification} onReadAll={onReadAllNotifications} onRemove={onRemoveNotification} />}

      <div className="profile-strip">
        <Avatar name={name} src={user.avatarUrl} />
        <span><strong>{name}</strong><small>@{user.username}</small></span>
        <ConnectionBadge status={connectionStatus} onRetry={onRetryConnection} />
      </div>

      <div className="sidebar-search-row">
        <label className="search-box" htmlFor="conversation-search">
          <Search size={18} />
          <input id="conversation-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm cuộc trò chuyện" />
        </label>
        {onNewConversation && (
          <button type="button" className="new-chat-button" onClick={onNewConversation} aria-label="Tạo cuộc trò chuyện mới" title="Tạo cuộc trò chuyện mới">
            <PenSquare size={18} />
          </button>
        )}
      </div>

      {onFilterChange && (
        <div className="filter-chips" role="tablist" aria-label="Lọc cuộc trò chuyện">
          {FILTERS.map(([id, label]) => (
            <button type="button" role="tab" key={id} aria-selected={filter === id} className={filter === id ? 'is-active' : ''} onClick={() => onFilterChange(id)}>
              {label}
              {id === 'unread' && totalUnread > 0 && <b>{totalUnread > 99 ? '99+' : totalUnread}</b>}
            </button>
          ))}
        </div>
      )}

      {status === 'loading' && !conversations.length ? (
        <div className="conversation-skeletons" aria-label="Đang tải cuộc trò chuyện">
          {[1, 2, 3, 4, 5].map((item) => <span key={item} />)}
        </div>
      ) : error ? (
        <div className="sidebar-empty sidebar-empty--error" role="alert">
          <strong>Không tải được danh sách</strong>
          <span>{error.message}</span>
          <button type="button" className="secondary-button" onClick={() => onSearch(query)}>Thử lại</button>
        </div>
      ) : (
        <ConversationList
          conversations={visible}
          activeId={activeId}
          messagesByConversation={messagesByConversation}
          onSelect={onSelect}
          onAction={onConversationAction}
          currentUserId={user.id}
          people={people}
          query={query}
          {...(emptyTitle ? { emptyTitle, emptyText } : {})}
        />
      )}
    </aside>
  )
}
