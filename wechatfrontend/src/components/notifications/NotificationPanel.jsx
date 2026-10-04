import { CheckCheck, LoaderCircle, Trash2, X } from 'lucide-react'
import { useEffect } from 'react'
import { formatConversationTime } from '../../utils/chat.js'

export function NotificationPanel({ notifications, status, error, onLoad, onClose, onRead, onReadAll, onRemove }) {
  useEffect(() => {
    const controller = new AbortController()
    if (!notifications.length && status === 'idle') onLoad(controller.signal).catch(() => {})
    return () => controller.abort()
  }, [notifications.length, onLoad, status])

  return (
    <section className="notification-panel" aria-label="Thông báo">
      <header>
        <div><strong>Thông báo</strong><small>Cập nhật từ các cuộc trò chuyện và liên hệ</small></div>
        <div className="notification-panel-actions">
          <button type="button" className="icon-button" onClick={onReadAll} aria-label="Đánh dấu tất cả đã đọc" title="Đánh dấu tất cả đã đọc"><CheckCheck size={17} /></button>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Đóng thông báo"><X size={17} /></button>
        </div>
      </header>
      {status === 'loading' && <div className="panel-state"><LoaderCircle className="spin" size={20} /> Đang tải…</div>}
      {error && <div className="panel-state panel-state--error">{error.message}</div>}
      {status !== 'loading' && !error && !notifications.length && <div className="panel-state">Bạn chưa có thông báo mới.</div>}
      <div className="notification-items">
        {notifications.map((notification) => (
          <article key={notification.id} className={`notification-item ${notification.read ? '' : 'is-unread'}`}>
            <button type="button" className="notification-content" onClick={() => !notification.read && onRead(notification.id)}>
              <span className="notification-dot" />
              <span><strong>{notification.title || notification.type}</strong><span>{notification.content}</span><time>{formatConversationTime(notification.createdAt)}</time></span>
            </button>
            <button type="button" className="icon-button notification-delete" onClick={() => onRemove(notification.id)} aria-label="Xóa thông báo"><Trash2 size={14} /></button>
          </article>
        ))}
      </div>
    </section>
  )
}
