import { ChatSidebar } from '../components/conversations/ChatSidebar.jsx'
import { ConversationPanel } from '../components/messages/ConversationPanel.jsx'
import { useChatPageController } from '../features/conversations/useChatPageController.js'

export function ChatPage() {
  const { sidebar, panel, className } = useChatPageController()

  return (
    <main className={className}>
      <ChatSidebar {...sidebar} />
      <ConversationPanel {...panel} />
    </main>
  )
}
