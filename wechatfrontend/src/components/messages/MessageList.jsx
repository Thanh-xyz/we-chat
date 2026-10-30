import { ChevronUp, MessageCircleDashed, RotateCcw } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { formatMessageDate, messageDateKey } from '../../utils/chat.js'
import { MessageBubble } from './MessageBubble.jsx'

export function MessageList({ conversationId, messages, page, currentUserId, typingUsers, onLoadOlder, onRetry }) {
  const scrollRef = useRef(null)
  const initializedFor = useRef(null)
  const stickToBottomRef = useRef(true)
  const lastMessageId = messages.at(-1)?.id

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
      node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' })
      stickToBottomRef.current = true
    }
  }, [conversationId, currentUserId, lastMessageId, messages])

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
    stickToBottomRef.current = node.scrollHeight - node.scrollTop - node.clientHeight < 180
    if (event.currentTarget.scrollTop < 100) loadOlder()
  }

  function handleMediaLoad() {
    const node = scrollRef.current
    if (!node || !stickToBottomRef.current) return
    requestAnimationFrame(() => node.scrollTo({ top: node.scrollHeight, behavior: 'smooth' }))
  }

  if (page?.status === 'loading' && !messages.length) {
    return <div className="message-loading" aria-label="Đang tải tin nhắn"><span className="spinner" /><span>Đang tải cuộc trò chuyện…</span></div>
  }
  if (page?.status === 'error' && !messages.length) {
    return <div className="message-state" role="alert"><RotateCcw size={28} /><strong>Không tải được tin nhắn</strong><span>{page.error?.message}</span><button type="button" className="secondary-button" onClick={onRetry}>Thử lại</button></div>
  }

  return (
    <div className="message-scroll" ref={scrollRef} onScroll={handleScroll}>
      <div className="message-stack">
        {page?.hasNext && (
          <button type="button" className="load-older" onClick={loadOlder} disabled={page.loadingOlder}>
            <ChevronUp size={15} /> {page.loadingOlder ? 'Đang tải…' : 'Tải tin nhắn cũ hơn'}
          </button>
        )}
        {!messages.length ? (
          <div className="message-state message-state--empty"><MessageCircleDashed size={34} /><strong>Chưa có tin nhắn</strong><span>Hãy gửi lời chào đầu tiên.</span></div>
        ) : messages.map((message, index) => {
          const previous = messages[index - 1]
          const showDate = !previous || messageDateKey(previous.createdAt) !== messageDateKey(message.createdAt)
          return <div className="message-entry" key={message.id}>
            {showDate && <div className="message-date-separator"><span>{formatMessageDate(message.createdAt)}</span></div>}
            <MessageBubble message={message} mine={message.senderId === currentUserId} onMediaLoad={handleMediaLoad} />
          </div>
        })}
        {typingUsers.length > 0 && <div className="typing-bubble" aria-live="polite"><span /><span /><span /><small>Đang nhập…</small></div>}
      </div>
    </div>
  )
}
