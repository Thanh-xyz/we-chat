import { ArrowLeft, LockKeyhole, MessageCircleMore, Users } from 'lucide-react'
import { Avatar } from '../common/Avatar.jsx'
import { conversationName } from '../../utils/chat.js'
import { MessageComposer } from './MessageComposer.jsx'
import { MessageList } from './MessageList.jsx'

export function ConversationPanel({ conversation, messages, page, userId, typingUsers, onBack, onLoadOlder, onRetry, onSend, onTyping }) {
  if (!conversation) {
    return (
      <section className="chat-empty-panel">
        <span className="empty-illustration"><MessageCircleMore size={44} /></span>
        <h2>Chọn một cuộc trò chuyện</h2>
        <p>Tin nhắn của bạn sẽ xuất hiện ở đây.</p>
        <span><LockKeyhole size={14} /> Kết nối được xác thực</span>
      </section>
    )
  }

  const name = conversationName(conversation)
  return (
    <section className="conversation-panel">
      <header className="conversation-header">
        <button type="button" className="icon-button mobile-back" onClick={onBack} aria-label="Quay lại danh sách"><ArrowLeft size={21} /></button>
        <Avatar name={name} src={conversation.avatarUrl} />
        <div className="conversation-heading">
          <h1>{name}</h1>
          <span><Users size={13} /> {conversation.memberIds?.length || 0} thành viên</span>
        </div>
      </header>
      <MessageList conversationId={conversation.id} messages={messages} page={page} currentUserId={userId} typingUsers={typingUsers} onLoadOlder={onLoadOlder} onRetry={onRetry} />
      <MessageComposer conversationId={conversation.id} onSend={onSend} onTyping={onTyping} />
    </section>
  )
}
