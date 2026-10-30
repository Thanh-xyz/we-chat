import { X } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'

export function ImageLightbox({ src, alt, triggerRef, onClose }) {
  const closeRef = useRef(null)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()

    function handleKeyDown(event) {
      if (event.key === 'Tab') {
        event.preventDefault()
        closeRef.current?.focus()
        return
      }
      if (event.key !== 'Escape') return
      event.preventDefault()
      onClose()
      triggerRef.current?.focus()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose, triggerRef])

  function close() {
    onClose()
    triggerRef.current?.focus()
  }

  return createPortal(
    <div className="image-lightbox" role="dialog" aria-modal="true" aria-label="Xem ảnh đính kèm" onMouseDown={(event) => {
      if (event.target === event.currentTarget) close()
    }}>
      <button ref={closeRef} type="button" className="image-lightbox-close" onClick={close} aria-label="Đóng ảnh"><X size={22} /></button>
      <img src={src} alt={alt} />
    </div>,
    document.body,
  )
}
