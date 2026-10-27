import { Download, File, Image, Mic } from 'lucide-react'
import { useRef, useState } from 'react'
import { useAttachmentPreview } from '../../features/messages/useAttachmentPreview.js'
import { formatFileSizeInKilobytes } from '../../utils/attachments.js'
import { formatMessageTime } from '../../utils/chat.js'
import { Avatar } from '../common/Avatar.jsx'
import { ImageLightbox } from './ImageLightbox.jsx'

const TYPE_ICONS = { IMAGE: Image, FILE: File, VOICE: Mic }

const LONG_MESSAGE_LIMIT = 800

function AttachmentCard({ attachment, onMediaLoad }) {
  const fileType = attachment.fileType || 'FILE'
  const fileName = attachment.originalFileName || attachment.fileName || 'attachment'
  const { url, download } = useAttachmentPreview(attachment, fileType, fileName)
  const [lightboxOpen, setLightboxOpen] = useState(false)
  const imageTriggerRef = useRef(null)
  const imageAlt = fileName === 'attachment' ? 'Hình ảnh đính kèm' : fileName

  return <div className={`attachment-card attachment-card--${fileType}`}>
    {fileType === 'IMAGE' && url ? <button ref={imageTriggerRef} type="button" className="attachment-preview-button" onClick={() => setLightboxOpen(true)} aria-label={`Mở ${imageAlt}`}><img className="attachment-preview" src={url} alt={imageAlt} onLoad={onMediaLoad} /></button> : fileType === 'VOICE' && url ? <audio controls src={url} aria-label={fileName === 'attachment' ? 'Tin nhắn thoại' : fileName} onLoadedMetadata={onMediaLoad} /> : <File size={18} />}
    <span><strong>{fileName === 'attachment' ? 'Tệp đính kèm' : fileName}</strong>{attachment.fileSize && <small>{formatFileSizeInKilobytes(attachment.fileSize)}</small>}</span>
    <button type="button" className="icon-button" onClick={download} aria-label={`Tải ${fileName === 'attachment' ? 'tệp đính kèm' : fileName}`}><Download size={15} /></button>
    {lightboxOpen && <ImageLightbox src={url} alt={imageAlt} triggerRef={imageTriggerRef} onClose={() => setLightboxOpen(false)} />}
  </div>
}

export function MessageBubble({ message, mine, onMediaLoad }) {
  const [expanded, setExpanded] = useState(false)
  if (message.messageType === 'SYSTEM') {
    return <div className="system-message">{message.content}</div>
  }
  const TypeIcon = TYPE_ICONS[message.messageType]
  const senderName = message.sender?.displayName || message.sender?.username || 'Người gửi'
  const longMessage = (message.content?.length || 0) > LONG_MESSAGE_LIMIT
  const visibleContent = longMessage && !expanded ? `${message.content.slice(0, LONG_MESSAGE_LIMIT)}…` : message.content
  return (
    <article className={`message-row ${mine ? 'message-row--mine' : ''}`} data-message-id={message.id}>
      {!mine && <Avatar name={senderName} src={message.sender?.avatarUrl} />}
      <div className={`message-bubble ${message.recalled ? 'message-bubble--recalled' : ''}`}>
        {!mine && <strong className="message-sender">{senderName}</strong>}
        {TypeIcon && (
          <div className="attachment-type"><TypeIcon size={17} /><span>{message.messageType === 'IMAGE' ? 'Hình ảnh' : message.messageType === 'VOICE' ? 'Tin nhắn thoại' : 'Tệp đính kèm'}</span></div>
        )}
        {visibleContent && <p>{visibleContent}</p>}
        {longMessage && <button type="button" className="message-expand" onClick={() => {
          setExpanded((current) => !current)
          requestAnimationFrame(() => onMediaLoad?.())
        }}>{expanded ? 'Xem bớt' : 'Xem thêm'}</button>}
        {message.attachments?.map((attachment) => <AttachmentCard attachment={attachment} onMediaLoad={onMediaLoad} key={attachment.id} />)}
        <footer>
          {message.edited && <span>đã sửa</span>}
          <time dateTime={message.createdAt}>{formatMessageTime(message.createdAt)}</time>
          {mine && <span aria-label="Đã gửi">✓</span>}
        </footer>
      </div>
    </article>
  )
}
