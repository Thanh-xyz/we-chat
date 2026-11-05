import { X } from 'lucide-react'
import { useEffect, useId, useRef } from 'react'

export function Modal({ title, description, onClose, children, footer, size = 'medium' }) {
  const titleId = useId()
  const dialogRef = useRef(null)

  useEffect(() => {
    const previous = document.activeElement
    dialogRef.current?.querySelector('input, textarea, button:not([data-close])')?.focus()
    function onKey(event) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      previous?.focus?.()
    }
  }, [onClose])

  return (
    <div className="modal-backdrop" onPointerDown={(event) => event.target === event.currentTarget && onClose()}>
      <section ref={dialogRef} className={`modal modal--${size}`} role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <header className="modal-header">
          <div>
            <h2 id={titleId}>{title}</h2>
            {description && <p>{description}</p>}
          </div>
          <button type="button" className="icon-button" data-close onClick={onClose} aria-label="Đóng"><X size={18} /></button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-footer">{footer}</footer>}
      </section>
    </div>
  )
}

export function ConfirmDialog({ title, description, confirmLabel = 'Xác nhận', danger = false, busy = false, onConfirm, onClose }) {
  return (
    <Modal
      title={title}
      description={description}
      onClose={onClose}
      size="small"
      footer={<>
        <button type="button" className="secondary-button" onClick={onClose} disabled={busy}>Hủy</button>
        <button type="button" className={danger ? 'danger-button' : 'primary-button'} onClick={onConfirm} disabled={busy}>
          {busy ? 'Đang xử lý…' : confirmLabel}
        </button>
      </>}
    />
  )
}
