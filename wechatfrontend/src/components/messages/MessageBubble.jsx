import { CornerUpLeft, Download, File, Image, Mic, MoreHorizontal, Pencil, SmilePlus, Trash2, Undo2 } from 'lucide-react'
import { useCallback, useRef, useState } from 'react'
import { Avatar } from '../common/Avatar.jsx'
import { useDismiss } from '../../hooks/useDismiss.js'
import { useAttachmentPreview } from '../../features/messages/useAttachmentPreview.js'
import { formatFileSizeInKilobytes } from '../../utils/attachments.js'
import { formatMessageTime } from '../../utils/chat.js'
import { ImageLightbox } from './ImageLightbox.jsx'

const TYPE_ICONS = { IMAGE: Image, FILE: File, VOICE: Mic }
const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏']
const EDIT_WINDOW_MS = 15 * 60 * 1000
const RECALL_WINDOW_MS = 24 * 60 * 60 * 1000
const LONG_MESSAGE_LIMIT = 800

function AttachmentCard({ attachment, onMediaLoad }) {
  const fileType = attachment.fileType || 'FILE'
  const fileName = attachment.originalFileName || attachment.fileName || 'attachment'
  const { url, download } = useAttachmentPreview(attachment, fileType, fileName)
  const [lightboxOpen, setLightboxOpen] = useState(false)
  const imageTriggerRef = useRef(null)
  const label = fileName === 'attachment' ? 'Tệp đính kèm' : fileName
  const imageAlt = fileName === 'attachment' ? 'Hình ảnh đính kèm' : fileName

  if (fileType === 'IMAGE' && url) {
    return (
      <figure className="attachment-image">
        <button ref={imageTriggerRef} type="button" className="attachment-preview-button" onClick={() => setLightboxOpen(true)} aria-label={`Mở ${imageAlt}`}>
          <img src={url} alt={imageAlt} onLoad={onMediaLoad} />
        </button>
        <button type="button" className="attachment-image-download" onClick={download} aria-label={`Tải ${label}`}><Download size={15} /></button>
        {lightboxOpen && <ImageLightbox src={url} alt={imageAlt} triggerRef={imageTriggerRef} onClose={() => setLightboxOpen(false)} />}
      </figure>
    )
  }

  return <div className={`attachment-card attachment-card--${fileType}`}>
    {fileType === 'VOICE' && url ? <audio controls src={url} aria-label={fileName === 'attachment' ? 'Tin nhắn thoại' : fileName} onLoadedMetadata={onMediaLoad} /> : <span className="attachment-icon"><File size={18} /></span>}
    <span><strong>{label}</strong>{attachment.fileSize && <small>{formatFileSizeInKilobytes(attachment.fileSize)}</small>}</span>
    <button type="button" className="icon-button" onClick={download} aria-label={`Tải ${fileName === 'attachment' ? 'tệp đính kèm' : fileName}`}><Download size={15} /></button>
  </div>
}

function withinWindow(createdAt, windowMs) {
  return createdAt && Date.now() - new Date(createdAt).getTime() < windowMs
}

function ActionMenu({ items, onClose }) {
  const ref = useRef(null)
  useDismiss(ref, onClose)
  return (
    <div ref={ref} className="popover-menu message-menu" role="menu">
      {items.map(({ key, label, Icon, danger, run }) => (
        <button type="button" role="menuitem" key={key} className={danger ? 'is-danger' : ''} onClick={() => { onClose(); run() }}>
          <Icon size={15} /> {label}
        </button>
      ))}
    </div>
  )
}

function ReactionPicker({ onPick, onClose }) {
  const ref = useRef(null)
  useDismiss(ref, onClose)
  return (
    <div ref={ref} className="reaction-picker" role="menu" aria-label="Chọn cảm xúc">
      {QUICK_REACTIONS.map((emoji) => (
        <button type="button" role="menuitem" key={emoji} onClick={() => { onClose(); onPick(emoji) }} aria-label={`Thả ${emoji}`}>{emoji}</button>
      ))}
    </div>
  )
}

export function MessageBubble({
  message,
  mine,
  senderName = '',
  senderAvatar = null,
  showSender = false,
  showAvatar = false,
  grouped = false,
  replyTarget = null,
  replyTargetName = '',
  highlighted = false,
  onReply,
  onEdit,
  onRecall,
  onDelete,
  onReact,
  onJumpTo,
  onMediaLoad,
}) {
  const [menu, setMenu] = useState(null)
  const [expanded, setExpanded] = useState(false)
  const closeMenu = useCallback(() => setMenu(null), [])

  if (message.messageType === 'SYSTEM') {
    return <div className="system-message">{message.content}</div>
  }

  const TypeIcon = TYPE_ICONS[message.messageType]
  const interactive = Boolean(onReply || onReact || onDelete)
  const canEdit = mine && !message.recalled && message.messageType === 'TEXT' && withinWindow(message.createdAt, EDIT_WINDOW_MS) && onEdit
  const canRecall = mine && !message.recalled && withinWindow(message.createdAt, RECALL_WINDOW_MS) && onRecall
  const menuItems = [
    canEdit && { key: 'edit', label: 'Chỉnh sửa', Icon: Pencil, run: () => onEdit(message) },
    canRecall && { key: 'recall', label: 'Thu hồi với mọi người', Icon: Undo2, danger: true, run: () => onRecall(message) },
    onDelete && { key: 'delete', label: 'Xóa ở phía tôi', Icon: Trash2, danger: true, run: () => onDelete(message) },
  ].filter(Boolean)
  const reactions = (message.reactions || []).filter((reaction) => reaction.count > 0)
  const resolvedSenderName = senderName || message.sender?.displayName || message.sender?.username || 'Người gửi'
  const resolvedSenderAvatar = senderAvatar || message.sender?.avatarUrl || null
  const longMessage = (message.content?.length || 0) > LONG_MESSAGE_LIMIT
  const visibleContent = longMessage && !expanded ? `${message.content.slice(0, LONG_MESSAGE_LIMIT)}…` : message.content

  return (
    <article
      className={`message-row ${mine ? 'message-row--mine' : ''} ${grouped ? 'message-row--grouped' : ''} ${highlighted ? 'is-highlighted' : ''}`}
      data-message-id={message.id}
    >
      {!mine && (showAvatar
        ? <Avatar name={resolvedSenderName} src={resolvedSenderAvatar} size="small" />
        : <span className="avatar-spacer" aria-hidden="true" />)}
      <div className="message-body">
        {showSender && !mine && <span className="message-sender">{resolvedSenderName}</span>}
        <div className="message-bubble-wrap">
          <div className={`message-bubble ${message.recalled ? 'message-bubble--recalled' : ''}`} tabIndex={interactive ? 0 : undefined}>
            {message.replyToMessageId && (
              <button type="button" className="reply-quote" onClick={() => onJumpTo?.(message.replyToMessageId)}>
                <strong>{replyTargetName || 'Tin nhắn'}</strong>
                <span>{replyTarget ? (replyTarget.recalled ? 'Tin nhắn đã được thu hồi' : replyTarget.content || 'Tệp đính kèm') : 'Tin nhắn cũ hơn'}</span>
              </button>
            )}
            {TypeIcon && !message.recalled && !message.attachments?.length && (
              <div className="attachment-type"><TypeIcon size={17} /><span>{message.messageType === 'IMAGE' ? 'Hình ảnh' : message.messageType === 'VOICE' ? 'Tin nhắn thoại' : 'Tệp đính kèm'}</span></div>
            )}
            {!message.recalled && message.attachments?.map((attachment) => <AttachmentCard attachment={attachment} onMediaLoad={onMediaLoad} key={attachment.id} />)}
            {visibleContent && <p>{visibleContent}</p>}
            {longMessage && <button type="button" className="message-expand" onClick={() => {
              setExpanded((current) => !current)
              requestAnimationFrame(() => onMediaLoad?.())
            }}>{expanded ? 'Xem bớt' : 'Xem thêm'}</button>}
            <footer>
              {message.edited && !message.recalled && <span>đã sửa</span>}
              <time dateTime={message.createdAt}>{formatMessageTime(message.createdAt)}</time>
              {mine && <span aria-label="Đã gửi">✓</span>}
            </footer>
          </div>
          {interactive && !message.recalled && (
            <div className={`message-actions ${menu ? 'is-open' : ''}`}>
              {onReact && <button type="button" className="icon-button" onClick={() => setMenu(menu === 'react' ? null : 'react')} aria-label="Thả cảm xúc" title="Thả cảm xúc"><SmilePlus size={16} /></button>}
              {onReply && <button type="button" className="icon-button" onClick={() => onReply(message)} aria-label="Trả lời" title="Trả lời"><CornerUpLeft size={16} /></button>}
              {menuItems.length > 0 && <button type="button" className="icon-button" onClick={() => setMenu(menu === 'more' ? null : 'more')} aria-label="Thêm thao tác" aria-haspopup="menu" title="Thêm"><MoreHorizontal size={16} /></button>}
              {menu === 'react' && <ReactionPicker onPick={(emoji) => onReact(message, emoji)} onClose={closeMenu} />}
              {menu === 'more' && <ActionMenu items={menuItems} onClose={closeMenu} />}
            </div>
          )}
          {interactive && message.recalled && onDelete && (
            <div className="message-actions">
              <button type="button" className="icon-button" onClick={() => onDelete(message)} aria-label="Xóa ở phía tôi" title="Xóa ở phía tôi"><Trash2 size={15} /></button>
            </div>
          )}
        </div>
        {reactions.length > 0 && (
          <div className="reaction-row">
            {reactions.map((reaction) => (
              <button
                type="button"
                key={reaction.emoji}
                className={`reaction-chip ${reaction.reactedByMe ? 'is-mine' : ''}`}
                onClick={() => onReact?.(message, reaction.emoji)}
                aria-label={`${reaction.emoji} ${reaction.count}${reaction.reactedByMe ? ', bạn đã thả' : ''}`}
                aria-pressed={reaction.reactedByMe}
              >
                <span>{reaction.emoji}</span>{reaction.count > 1 && <small>{reaction.count}</small>}
              </button>
            ))}
          </div>
        )}
      </div>
    </article>
  )
}
