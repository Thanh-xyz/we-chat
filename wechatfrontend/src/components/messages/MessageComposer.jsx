import { File, Image, Mic, Paperclip, RotateCcw, SendHorizontal, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useAttachmentQueue } from '../../features/messages/useAttachmentQueue.js'
import { errorMessage } from '../../services/api/errors.js'
import {
  ATTACHMENT_ACCEPT,
  formatFileSize,
} from '../../utils/attachments.js'

function attachmentIcon(type) {
  if (type === 'IMAGE') return Image
  if (type === 'VOICE') return Mic
  return File
}

export function MessageComposer({ conversationId, onSend, onTyping, draft = '', onDraftChange }) {
  const [content, setContent] = useState(draft)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const stopTimer = useRef(null)
  const lastTypingSentAt = useRef(0)
  const inputRef = useRef(null)
  const { attachments, addFiles, removeAttachment, retryAttachment, clearAttachments } = useAttachmentQueue(conversationId)

  useEffect(() => () => {
    clearTimeout(stopTimer.current)
    onTyping(false)
  }, [conversationId, onTyping])

  function updateContent(value) {
    setContent(value)
    onDraftChange?.(value)
    signalTyping(value)
  }

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
    const successful = attachments.filter((item) => item.status === 'success')
    const pending = attachments.some((item) => item.status === 'queued' || item.status === 'uploading')
    const failed = attachments.some((item) => item.status === 'failed' || item.status === 'cancelled')
    if (sending || pending || failed || (!normalized && !successful.length) || normalized.length > 10_000) {
      if (pending) setError('Vui lòng chờ tệp tải lên hoàn tất.')
      if (failed) setError('Hãy thử lại hoặc xóa tệp tải lên thất bại.')
      return
    }
    const messageType = successful.length ? successful[0].fileType : 'TEXT'
    if (successful.some((item) => item.fileType !== messageType)) {
      setError('Mỗi tin nhắn chỉ hỗ trợ một loại tệp.')
      return
    }
    setSending(true)
    setError('')
    try {
      await onSend({ content: normalized || null, messageType, attachmentIds: successful.map((item) => item.result.id) })
      clearAttachments()
      setContent('')
      onDraftChange?.('')
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

  const successful = attachments.filter((item) => item.status === 'success')
  const canSend = content.trim() || successful.length

  return (
    <div className="composer-wrap">
      {error && <div className="composer-error" role="alert">{error}</div>}
      {attachments.length > 0 && <div className="attachment-queue" aria-live="polite">{attachments.map((item) => {
        const Icon = attachmentIcon(item.fileType)
        return <div className={`attachment-queue-item attachment-queue-item--${item.status}`} key={item.id}>
          <Icon size={15} />
          <span><strong>{item.file.name}</strong><small>{formatFileSize(item.file.size)} · {item.status === 'uploading' ? `Đang tải ${item.progress}%` : item.status === 'success' ? 'Sẵn sàng gửi' : item.error || item.status}</small></span>
          {(item.status === 'failed' || item.status === 'cancelled') && <button type="button" className="icon-button" onClick={() => retryAttachment(item)} aria-label={`Thử lại ${item.file.name}`}><RotateCcw size={14} /></button>}
          <button type="button" className="icon-button" onClick={() => removeAttachment(item)} aria-label={`Xóa ${item.file.name}`}><X size={14} /></button>
          {item.status === 'uploading' && <span className="upload-progress" style={{ '--progress': `${item.progress}%` }} />}
        </div>
      })}</div>}
      <div className="composer">
        <input ref={inputRef} type="file" hidden multiple accept={ATTACHMENT_ACCEPT} onChange={addFiles} />
        <button type="button" className="icon-button composer-attach" onClick={() => inputRef.current?.click()} aria-label="Đính kèm tệp" title="Đính kèm tệp"><Paperclip size={19} /></button>
        <textarea
          aria-label="Nội dung tin nhắn"
          placeholder="Nhập tin nhắn…"
          value={content}
          onChange={(event) => updateContent(event.target.value)}
          onKeyDown={handleKeyDown}
          maxLength={10_000}
          rows={1}
        />
        <button type="button" className="send-button" onClick={submit} disabled={sending || !canSend} aria-label="Gửi tin nhắn">
          <SendHorizontal size={20} />
        </button>
      </div>
      <small className="composer-hint">Enter để gửi · Shift + Enter để xuống dòng · Tối đa 10 tệp / tin nhắn</small>
    </div>
  )
}
