import { File, Image, Mic } from 'lucide-react'
import { formatMessageTime } from '../../utils/chat.js'

const TYPE_ICONS = { IMAGE: Image, FILE: File, VOICE: Mic }

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
        {message.attachments?.map((attachment) => (
          <div className="attachment-card" key={attachment.id}>
            <File size={16} />
            <span><strong>{attachment.fileName || 'Tệp đính kèm'}</strong>{attachment.fileSize && <small>{Math.ceil(attachment.fileSize / 1024)} KB</small>}</span>
          </div>
        ))}
        <footer>
          {message.edited && <span>đã sửa</span>}
          <time dateTime={message.createdAt}>{formatMessageTime(message.createdAt)}</time>
          {mine && <span aria-label="Đã gửi">✓</span>}
        </footer>
      </div>
    </article>
  )
}
