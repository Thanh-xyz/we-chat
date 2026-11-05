import { ArrowLeft, BellOff, Info, LockKeyhole, MessageCircleMore, PenSquare, Search } from 'lucide-react'
import { useState } from 'react'
import { Avatar } from '../common/Avatar.jsx'
import { ConfirmDialog } from '../common/Modal.jsx'
import { ConversationInfoPanel } from '../conversations/ConversationInfoPanel.jsx'
import { conversationAvatar, conversationName, presenceLabel } from '../../utils/chat.js'
import { MessageComposer } from './MessageComposer.jsx'
import { MessageList } from './MessageList.jsx'
import { MessageSearch } from './MessageSearch.jsx'

export function ConversationPanel({
  conversation,
  messages,
  page,
  userId,
  typingUsers,
  draft,
  onDraftChange,
  onBack,
  onLoadOlder,
  onRetry,
  onSend,
  onTyping,
  people = {},
  friends = [],
  composerMode = null,
  highlightId = null,
  onReply,
  onStartEdit,
  onCancelMode,
  onEdit,
  onRecall,
  onDelete,
  onReact,
  onJumpTo,
  onNewConversation,
  onLeft,
}) {
  const [sidePanel, setSidePanel] = useState(null)
  const [confirm, setConfirm] = useState(null)
  const [busy, setBusy] = useState(false)

  if (!conversation) {
    return (
      <section className="chat-empty-panel">
        <span className="empty-illustration"><MessageCircleMore size={44} /></span>
        <h2>Chào mừng đến ChatSpace</h2>
        <p>Chọn một cuộc trò chuyện ở bên trái hoặc bắt đầu cuộc trò chuyện mới.</p>
        {onNewConversation && <button type="button" className="primary-button empty-cta" onClick={onNewConversation}><PenSquare size={17} /> Cuộc trò chuyện mới</button>}
        <span><LockKeyhole size={14} /> Kết nối được xác thực</span>
      </section>
    )
  }

  const options = { currentUserId: userId, people }
  const name = conversationName(conversation, options)
  const isGroup = conversation.type === 'GROUP'
  const otherId = !isGroup ? conversation.memberIds?.find((id) => id !== userId) : null
  const peer = !isGroup ? (people[otherId] || conversation.directPeer) : null
  const presence = presenceLabel(peer?.presenceStatus || peer?.status)
  const subtitle = typingUsers.length
    ? 'Đang nhập…'
    : isGroup ? `${conversation.memberIds?.length || 0} thành viên`
      : [peer?.username ? `@${peer.username}` : 'Trò chuyện trực tiếp', presence].filter(Boolean).join(' · ')

  async function runConfirm() {
    setBusy(true)
    try {
      await (confirm.type === 'recall' ? onRecall(confirm.message) : onDelete(confirm.message))
      setConfirm(null)
    } finally {
      setBusy(false)
    }
  }

  const replyLabel = (message) => (message.senderId === userId ? 'chính bạn' : people[message.senderId]?.displayName || '')

  return (
    <div className={`conversation-layout ${sidePanel ? 'has-side-panel' : ''}`}>
      <section className="conversation-panel">
        <header className="conversation-header">
          <button type="button" className="icon-button mobile-back" onClick={onBack} aria-label="Quay lại danh sách"><ArrowLeft size={21} /></button>
          <button type="button" className="conversation-identity" onClick={() => setSidePanel(sidePanel === 'info' ? null : 'info')}>
            <Avatar name={name} src={conversationAvatar(conversation, options)} group={isGroup} />
            <span className="conversation-heading">
              <h1>{name}{conversation.muted && <BellOff size={14} aria-label="Đã tắt thông báo" />}</h1>
              <span className={typingUsers.length ? 'is-typing' : ''}>{subtitle}</span>
            </span>
          </button>
          <div className="conversation-header-actions">
            <button type="button" className={`icon-button ${sidePanel === 'search' ? 'is-active' : ''}`} onClick={() => setSidePanel(sidePanel === 'search' ? null : 'search')} aria-label="Tìm kiếm tin nhắn" title="Tìm kiếm tin nhắn"><Search size={19} /></button>
            <button type="button" className={`icon-button ${sidePanel === 'info' ? 'is-active' : ''}`} onClick={() => setSidePanel(sidePanel === 'info' ? null : 'info')} aria-label="Thông tin cuộc trò chuyện" title="Thông tin cuộc trò chuyện"><Info size={19} /></button>
          </div>
        </header>
        <MessageList
          conversationId={conversation.id}
          messages={messages}
          page={page}
          currentUserId={userId}
          typingUsers={typingUsers}
          onLoadOlder={onLoadOlder}
          onRetry={onRetry}
          isGroup={isGroup}
          people={people}
          highlightId={highlightId}
          onReply={onReply ? (message) => onReply(message, replyLabel(message)) : undefined}
          onEdit={onStartEdit}
          onRecall={onRecall ? (message) => setConfirm({ type: 'recall', message }) : undefined}
          onDelete={onDelete ? (message) => setConfirm({ type: 'delete', message }) : undefined}
          onReact={onReact}
          onJumpTo={onJumpTo}
        />
        <MessageComposer
          key={conversation.id}
          conversationId={conversation.id}
          draft={draft}
          onDraftChange={onDraftChange}
          onSend={onSend}
          onTyping={onTyping}
          mode={composerMode}
          onCancelMode={onCancelMode}
          onEdit={onEdit}
        />
      </section>
      {sidePanel === 'search' && (
        <MessageSearch conversationId={conversation.id} currentUserId={userId} people={people} onJump={(message) => onJumpTo?.(message.id)} onClose={() => setSidePanel(null)} />
      )}
      {sidePanel === 'info' && (
        <ConversationInfoPanel
          key={conversation.id}
          conversation={conversation}
          currentUserId={userId}
          people={people}
          friends={friends}
          onClose={() => setSidePanel(null)}
          onOpenSearch={() => setSidePanel('search')}
          onLeft={onLeft}
        />
      )}
      {confirm && (
        <ConfirmDialog
          title={confirm.type === 'recall' ? 'Thu hồi tin nhắn?' : 'Xóa tin nhắn ở phía bạn?'}
          description={confirm.type === 'recall'
            ? 'Tin nhắn sẽ được thu hồi với tất cả mọi người trong cuộc trò chuyện.'
            : 'Tin nhắn chỉ bị ẩn với bạn, những người khác vẫn xem được.'}
          confirmLabel={confirm.type === 'recall' ? 'Thu hồi' : 'Xóa'}
          danger
          busy={busy}
          onConfirm={runConfirm}
          onClose={() => setConfirm(null)}
        />
      )}
    </div>
  )
}
