import { SendHorizontal } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { errorMessage } from '../../services/api/errors.js'

export function MessageComposer({ conversationId, onSend, onTyping }) {
  const [content, setContent] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const stopTimer = useRef(null)
  const lastTypingSentAt = useRef(0)

  useEffect(() => () => {
    clearTimeout(stopTimer.current)
    onTyping(false)
  }, [conversationId, onTyping])

  function signalTyping(value) {
    clearTimeout(stopTimer.current)
    if (!value.trim()) {
      onTyping(false)
      return
    }
    const now = Date.now()
    if (now - lastTypingSentAt.current > 2_000) {
      onTyping(true)
      lastTypingSentAt.current = now
    }
    stopTimer.current = setTimeout(() => onTyping(false), 1_800)
  }

  async function submit() {
    const normalized = content.trim()
    if (!normalized || sending || normalized.length > 10_000) return
    setSending(true)
    setError('')
    try {
      await onSend(normalized)
      setContent('')
      onTyping(false)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSending(false)
    }
  }

  function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <div className="composer-wrap">
      {error && <div className="composer-error" role="alert">{error}</div>}
      <div className="composer">
        <textarea
          aria-label="Nội dung tin nhắn"
          placeholder="Nhập tin nhắn…"
          value={content}
          onChange={(event) => { setContent(event.target.value); signalTyping(event.target.value) }}
          onKeyDown={handleKeyDown}
          maxLength={10_000}
          rows={1}
        />
        <button type="button" className="send-button" onClick={submit} disabled={sending || !content.trim()} aria-label="Gửi tin nhắn">
          <SendHorizontal size={20} />
        </button>
      </div>
      <small className="composer-hint">Enter để gửi · Shift + Enter để xuống dòng</small>
    </div>
  )
}
