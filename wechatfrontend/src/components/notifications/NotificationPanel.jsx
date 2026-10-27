import { CheckCheck, LoaderCircle, RotateCcw, Trash2, X } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { formatConversationTime } from '../../utils/chat.js'

const FOCUSABLE_SELECTOR = 'button:not(:disabled), [href], input:not(:disabled), [tabindex]:not([tabindex="-1"])'

export function NotificationPanel({ notifications, status, error, triggerRef, onLoad, onClose, onRead, onReadAll, onRemove }) {
  const panelRef = useRef(null)
  const closeButtonRef = useRef(null)
  const shouldLoadOnMountRef = useRef(!notifications.length && status === 'idle')

  useEffect(() => {
    const controller = new AbortController()
    if (shouldLoadOnMountRef.current) onLoad(controller.signal).catch(() => {})
    return () => controller.abort()
  }, [onLoad])

  useEffect(() => {
    closeButtonRef.current?.focus()

    function handlePointerDown(event) {
      if (panelRef.current?.contains(event.target) || triggerRef?.current?.contains(event.target)) return
      onClose()
      triggerRef?.current?.focus()
    }

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        triggerRef?.current?.focus()
        return
      }
      if (event.key !== 'Tab') return
      const focusable = [...(panelRef.current?.querySelectorAll(FOCUSABLE_SELECTOR) || [])]
      if (!focusable.length) return
      const first = focusable[0]
      const last = focusable.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose, triggerRef])

  return (
    <section ref={panelRef} className="notification-panel" role="dialog" aria-modal="true" aria-labelledby="notification-panel-title">
      <header>
        <div><strong id="notification-panel-title">Thông báo</strong><small>Cập nhật từ các cuộc trò chuyện và liên hệ</small></div>
        <div className="notification-panel-actions">
          <button type="button" className="icon-button" onClick={onReadAll} aria-label="Đánh dấu tất cả đã đọc" title="Đánh dấu tất cả đã đọc"><CheckCheck size={17} /></button>
          <button ref={closeButtonRef} type="button" className="icon-button" onClick={onClose} aria-label="Đóng thông báo"><X size={17} /></button>
        </div>
      </header>
      {status === 'loading' && <div className="panel-state"><LoaderCircle className="spin" size={20} /> Đang tải…</div>}
      {error && <div className="panel-state panel-state--error" role="alert"><span>{error.message}</span><button type="button" className="secondary-button panel-retry" onClick={() => onLoad()}><RotateCcw size={14} /> Thử lại</button></div>}
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
