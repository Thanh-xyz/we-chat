import { Download, File, Image, Mic } from 'lucide-react'
import { useEffect, useState } from 'react'
import { attachmentApi } from '../../services/api/attachmentApi.js'
import { formatMessageTime } from '../../utils/chat.js'

const TYPE_ICONS = { IMAGE: Image, FILE: File, VOICE: Mic }

function AttachmentCard({ attachment }) {
  const [url, setUrl] = useState('')
  const fileType = attachment.fileType || 'FILE'
  const fileName = attachment.originalFileName || attachment.fileName || 'attachment'
  const source = attachment.fileUrl || `/api/attachments/${attachment.id}/download`

  useEffect(() => {
    let active = true
    let objectUrl = ''
    attachmentApi.download(source).then(({ data }) => {
      if (!active) return
      objectUrl = URL.createObjectURL(data)
      setUrl(objectUrl)
    }).catch(() => {})
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [source])

  async function download() {
    try {
      const { data } = await attachmentApi.download(source)
      const objectUrl = URL.createObjectURL(data)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = fileName
      link.click()
      URL.revokeObjectURL(objectUrl)
    } catch {
      // The bubble remains visible; the next authenticated refresh can retry.
    }
  }

  return <div className={`attachment-card attachment-card--${fileType}`}>
    {fileType === 'IMAGE' && url ? <img className="attachment-preview" src={url} alt={fileName === 'attachment' ? 'Hình ảnh đính kèm' : fileName} /> : fileType === 'VOICE' && url ? <audio controls src={url} aria-label={fileName === 'attachment' ? 'Tin nhắn thoại' : fileName} /> : <File size={18} />}
    <span><strong>{fileName === 'attachment' ? 'Tệp đính kèm' : fileName}</strong>{attachment.fileSize && <small>{Math.ceil(attachment.fileSize / 1024)} KB</small>}</span>
    <button type="button" className="icon-button" onClick={download} aria-label={`Tải ${fileName === 'attachment' ? 'tệp đính kèm' : fileName}`}><Download size={15} /></button>
  </div>
}

export function MessageBubble({ message, mine }) {
  if (message.messageType === 'SYSTEM') {
    return <div className="system-message">{message.content}</div>
  }
  const TypeIcon = TYPE_ICONS[message.messageType]
  return (
    <article className={`message-row ${mine ? 'message-row--mine' : ''}`} data-message-id={message.id}>
      <div className={`message-bubble ${message.recalled ? 'message-bubble--recalled' : ''}`}>
        {TypeIcon && (
          <div className="attachment-type"><TypeIcon size={17} /><span>{message.messageType === 'IMAGE' ? 'Hình ảnh' : message.messageType === 'VOICE' ? 'Tin nhắn thoại' : 'Tệp đính kèm'}</span></div>
        )}
        {message.content && <p>{message.content}</p>}
        {message.attachments?.map((attachment) => <AttachmentCard attachment={attachment} key={attachment.id} />)}
        <footer>
          {message.edited && <span>đã sửa</span>}
          <time dateTime={message.createdAt}>{formatMessageTime(message.createdAt)}</time>
          {mine && <span aria-label="Đã gửi">✓</span>}
        </footer>
      </div>
    </article>
  )
}
