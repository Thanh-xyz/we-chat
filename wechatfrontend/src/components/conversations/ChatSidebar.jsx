import { Bell, LogOut, MessageCircleMore, Search, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '../common/Avatar.jsx'
import { ConnectionBadge } from '../common/ConnectionBadge.jsx'
import { ConversationList } from './ConversationList.jsx'
import { NotificationPanel } from '../notifications/NotificationPanel.jsx'

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
  onSearch,
  onSelect,
  onLogout,
  onRetryConnection,
  onToggleNotifications,
  onLoadNotifications,
  onReadNotification,
  onReadAllNotifications,
  onRemoveNotification,
}) {
  const [query, setQuery] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    const controller = new AbortController()
    const timer = setTimeout(() => onSearch(query, controller.signal), 300)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [onSearch, query])

  return (
    <aside className={`chat-sidebar ${activeId ? 'has-mobile-selection' : ''}`}>
      <header className="sidebar-header">
        <div className="sidebar-brand">
          <span className="brand-mark"><MessageCircleMore size={21} /></span>
          <strong>ChatSpace</strong>
        </div>
        <div className="sidebar-actions">
          <button type="button" className="notification-indicator" title={`${notificationUnread} thông báo chưa đọc`} onClick={onToggleNotifications} aria-label="Mở thông báo">
            <Bell size={19} />
            {notificationUnread > 0 && <span>{notificationUnread > 9 ? '9+' : notificationUnread}</span>}
          </button>
          <button className="icon-button" type="button" onClick={() => navigate('/app/contacts')} aria-label="Mở danh bạ" title="Danh bạ">
            <Users size={19} />
          </button>
          <button className="icon-button" type="button" onClick={onLogout} aria-label="Đăng xuất" title="Đăng xuất">
            <LogOut size={19} />
          </button>
        </div>
      </header>
      {notificationPanel && <NotificationPanel {...notificationData} onClose={onToggleNotifications} onLoad={onLoadNotifications} onRead={onReadNotification} onReadAll={onReadAllNotifications} onRemove={onRemoveNotification} />}

      <div className="profile-strip">
        <Avatar name={user.displayName || user.username} src={user.avatarUrl} />
        <span><strong>{user.displayName || user.username}</strong><small>@{user.username}</small></span>
        <ConnectionBadge status={connectionStatus} onRetry={onRetryConnection} />
      </div>

      <label className="search-box" htmlFor="conversation-search">
        <Search size={18} />
        <input id="conversation-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm cuộc trò chuyện" />
      </label>

      <div className="sidebar-section-title"><span>Tin nhắn</span><small>{conversations.length}</small></div>
      {status === 'loading' && !conversations.length ? (
        <div className="conversation-skeletons" aria-label="Đang tải cuộc trò chuyện">
          {[1, 2, 3, 4].map((item) => <span key={item} />)}
        </div>
      ) : error ? (
        <div className="sidebar-empty sidebar-empty--error"><strong>Không tải được danh sách</strong><span>{error.message}</span></div>
      ) : (
        <ConversationList conversations={conversations} activeId={activeId} messagesByConversation={messagesByConversation} onSelect={onSelect} />
      )}
    </aside>
  )
}
