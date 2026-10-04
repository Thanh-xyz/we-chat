import { File, Image, Mic, Paperclip, RotateCcw, SendHorizontal, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { attachmentApi } from '../../services/api/attachmentApi.js'
import { errorMessage } from '../../services/api/errors.js'
import { formatFileSize, validateAttachment } from '../../utils/attachments.js'

const MAX_ATTACHMENTS = 10

function attachmentIcon(type) {
  if (type === 'IMAGE') return Image
  if (type === 'VOICE') return Mic
  return File
}

export function MessageComposer({ conversationId, onSend, onTyping, draft = '', onDraftChange }) {
  const [content, setContent] = useState(draft)
  const [attachments, setAttachments] = useState([])
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const stopTimer = useRef(null)
  const lastTypingSentAt = useRef(0)
  const inputRef = useRef(null)
  const uploadControllers = useRef(new Map())

  useEffect(() => () => {
    clearTimeout(stopTimer.current)
    uploadControllers.current.forEach((controller) => controller.abort())
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

  function updateAttachment(id, patch) {
    setAttachments((current) => current.map((item) => item.id === id ? { ...item, ...patch } : item))
  }

  async function uploadAttachment(item) {
    const controller = new AbortController()
    uploadControllers.current.set(item.id, controller)
    updateAttachment(item.id, { status: 'uploading', progress: 0 })
    try {
      const result = await attachmentApi.upload(conversationId, item.file, item.fileType, {
        signal: controller.signal,
        onUploadProgress: (event) => updateAttachment(item.id, { progress: event.total ? Math.round((event.loaded / event.total) * 100) : 0 }),
      })
      updateAttachment(item.id, { status: 'success', progress: 100, result })
    } catch (requestError) {
      const cancelled = requestError.code === 'ERR_CANCELED' || requestError.code === 'REQUEST_CANCELED'
      updateAttachment(item.id, { status: cancelled ? 'cancelled' : 'failed', error: cancelled ? 'Đã hủy tải lên.' : errorMessage(requestError) })
    } finally {
      uploadControllers.current.delete(item.id)
    }
  }

  function addFiles(event) {
    const files = [...event.target.files]
    event.target.value = ''
    if (!files.length) return
    const available = Math.max(0, MAX_ATTACHMENTS - attachments.length)
    const next = files.slice(0, available).map((file) => {
      const validation = validateAttachment(file)
      return {
        id: crypto.randomUUID(),
        file,
        fileType: validation.fileType,
        status: validation.valid ? 'queued' : 'failed',
        progress: 0,
        error: validation.valid ? '' : validation.message,
      }
    })
    setAttachments((current) => [...current, ...next])
    next.filter((item) => item.status === 'queued').forEach(uploadAttachment)
  }

  async function removeAttachment(item) {
    uploadControllers.current.get(item.id)?.abort()
    if (item.result?.id) {
      try { await attachmentApi.remove(item.result.id) } catch { /* best-effort cleanup */ }
    }
    setAttachments((current) => current.filter((candidate) => candidate.id !== item.id))
  }

  function retryAttachment(item) {
    updateAttachment(item.id, { status: 'queued', error: '' })
    uploadAttachment(item)
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
      setAttachments([])
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
        <input ref={inputRef} type="file" hidden multiple accept="image/jpeg,image/png,image/webp,image/gif,audio/mpeg,audio/wav,audio/x-wav,audio/mp4,audio/webm,audio/x-m4a,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation,text/plain,application/zip,application/x-zip-compressed" onChange={addFiles} />
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
