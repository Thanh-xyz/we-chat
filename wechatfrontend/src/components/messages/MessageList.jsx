import { ArrowDown, ChevronUp, MessageCircleDashed, RotateCcw } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { MessageBubble } from './MessageBubble.jsx'
import { formatMessageDate, isSameDay, messageDateKey } from '../../utils/chat.js'

const GROUP_GAP_MS = 5 * 60 * 1000

function personLabel(person) {
  return person?.displayName || person?.username || ''
}

export function MessageList({
  conversationId,
  messages,
  page,
  currentUserId,
  typingUsers,
  onLoadOlder,
  onRetry,
  isGroup,
  people = {},
  highlightId = null,
  onReply,
  onEdit,
  onRecall,
  onDelete,
  onReact,
  onJumpTo,
}) {
  const scrollRef = useRef(null)
  const initializedFor = useRef(null)
  const stickToBottomRef = useRef(true)
  const [showJump, setShowJump] = useState(false)
  const lastMessageId = messages.at(-1)?.id
  const byId = useMemo(() => new Map(messages.map((message) => [message.id, message])), [messages])

  useEffect(() => {
    initializedFor.current = null
    stickToBottomRef.current = true
  }, [conversationId])

  useEffect(() => {
    const node = scrollRef.current
    if (!node || page?.status !== 'success') return
    if (initializedFor.current !== conversationId) {
      node.scrollTop = node.scrollHeight
      initializedFor.current = conversationId
      stickToBottomRef.current = true
    }
  }, [conversationId, page?.status])

  useEffect(() => {
    const node = scrollRef.current
    if (!node || initializedFor.current !== conversationId || !lastMessageId) return
    const latest = messages.at(-1)
    if (stickToBottomRef.current || latest?.senderId === currentUserId) {
      node.scrollTo?.({ top: node.scrollHeight, behavior: 'smooth' })
      stickToBottomRef.current = true
    }
  }, [conversationId, currentUserId, lastMessageId, messages])

  useEffect(() => {
    if (!highlightId) return
    const node = scrollRef.current?.querySelector(`[data-message-id="${highlightId}"]`)
    node?.scrollIntoView?.({ block: 'center', behavior: 'smooth' })
  }, [highlightId])

  async function loadOlder() {
    const node = scrollRef.current
    if (!node || page?.loadingOlder || !page?.hasNext) return
    const previousHeight = node.scrollHeight
    const previousTop = node.scrollTop
    const result = await onLoadOlder()
    if (!result) return
    requestAnimationFrame(() => {
      node.scrollTop = previousTop + (node.scrollHeight - previousHeight)
    })
  }

  function handleScroll(event) {
    const node = event.currentTarget
    const distanceFromBottom = node.scrollHeight - node.scrollTop - node.clientHeight
    stickToBottomRef.current = distanceFromBottom < 180
    if (node.scrollTop < 100) loadOlder()
    setShowJump(distanceFromBottom > 480)
  }

  function handleMediaLoad() {
    const node = scrollRef.current
    if (!node || !stickToBottomRef.current) return
    requestAnimationFrame(() => node.scrollTo?.({ top: node.scrollHeight, behavior: 'smooth' }))
  }

  function scrollToBottom() {
    scrollRef.current?.scrollTo?.({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }

  if (page?.status === 'loading' && !messages.length) {
    return <div className="message-loading" aria-label="Đang tải tin nhắn"><span className="spinner" /><span>Đang tải cuộc trò chuyện…</span></div>
  }
  if (page?.status === 'error' && !messages.length) {
    return <div className="message-state" role="alert"><RotateCcw size={28} /><strong>Không tải được tin nhắn</strong><span>{page.error?.message}</span><button type="button" className="secondary-button" onClick={onRetry}>Thử lại</button></div>
  }

  const typingNames = typingUsers.map((id) => personLabel(people[id])).filter(Boolean)

  return (
    <div className="message-scroll-wrap">
      <div className="message-scroll" ref={scrollRef} onScroll={handleScroll}>
        <div className="message-stack">
          {page?.hasNext && (
            <button type="button" className="load-older" onClick={loadOlder} disabled={page.loadingOlder}>
              <ChevronUp size={15} /> {page.loadingOlder ? 'Đang tải…' : 'Tải tin nhắn cũ hơn'}
            </button>
          )}
          {!messages.length ? (
            <div className="message-state message-state--empty"><MessageCircleDashed size={34} /><strong>Chưa có tin nhắn</strong><span>Hãy gửi lời chào đầu tiên 👋</span></div>
          ) : messages.map((message, index) => {
            const previous = messages[index - 1]
            const next = messages[index + 1]
            const newDay = !previous || messageDateKey(previous.createdAt) !== messageDateKey(message.createdAt)
            const sameAsPrevious = previous && !newDay && previous.senderId === message.senderId && previous.messageType !== 'SYSTEM'
              && new Date(message.createdAt) - new Date(previous.createdAt) < GROUP_GAP_MS
            const sameAsNext = next && isSameDay(next.createdAt, message.createdAt) && next.senderId === message.senderId && next.messageType !== 'SYSTEM'
              && new Date(next.createdAt) - new Date(message.createdAt) < GROUP_GAP_MS
            const sender = people[message.senderId] || message.sender
            const replyTarget = message.replyToMessageId ? byId.get(message.replyToMessageId) : undefined
            const replyTargetName = replyTarget
              ? (replyTarget.senderId === currentUserId ? 'Bạn' : personLabel(people[replyTarget.senderId] || replyTarget.sender))
              : ''
            return (
              <div key={message.id} className="message-group-item">
                {newDay && <div className="day-separator"><span>{formatMessageDate(message.createdAt)}</span></div>}
                <MessageBubble
                  message={message}
                  mine={message.senderId === currentUserId}
                  senderName={personLabel(sender)}
                  senderAvatar={sender?.avatarUrl}
                  showSender={isGroup !== false && !sameAsPrevious}
                  showAvatar={!sameAsNext}
                  grouped={Boolean(sameAsPrevious)}
                  replyTarget={replyTarget}
                  replyTargetName={replyTargetName}
                  highlighted={highlightId === message.id}
                  onReply={onReply}
                  onEdit={onEdit}
                  onRecall={onRecall}
                  onDelete={onDelete}
                  onReact={onReact}
                  onJumpTo={onJumpTo}
                  onMediaLoad={handleMediaLoad}
                />
              </div>
            )
          })}
          {typingUsers.length > 0 && (
            <div className="typing-bubble" aria-live="polite">
              <span /><span /><span />
              <small>{typingNames.length ? `${typingNames.slice(0, 2).join(', ')} đang nhập…` : 'Đang nhập…'}</small>
            </div>
          )}
        </div>
      </div>
      {showJump && <button type="button" className="jump-latest" onClick={scrollToBottom} aria-label="Xuống tin nhắn mới nhất"><ArrowDown size={18} /></button>}
    </div>
  )
}
