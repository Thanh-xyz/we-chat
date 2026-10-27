import { create } from 'zustand'
import { useChatStore } from '../conversations/chatStore.js'
import { useNotificationStore } from '../notifications/notificationStore.js'
import { getUsableAccessToken } from '../../services/api/client.js'
import { webSocketManager } from '../../services/websocket/WebSocketManager.js'

export const useRealtimeStore = create((set, get) => ({
  status: 'offline',
  activeUserId: null,

  connect: (userId) => {
    if (!userId || get().activeUserId === userId) return
    if (get().activeUserId) webSocketManager.disconnect()
    set({ activeUserId: userId })
    webSocketManager.connect({
      userId,
      getAccessToken: getUsableAccessToken,
      onState: (status) => set({ status }),
      onConversationEvent: (event) => useChatStore.getState().handleRealtimeEvent(event),
      onNotificationEvent: (event) => {
        useChatStore.getState().handleNotificationEvent(event)
        useNotificationStore.getState().handleRealtimeEvent(event)
      },
      onConnected: ({ reconnected } = {}) => {
        if (!reconnected) return Promise.resolve([])
        const chatStore = useChatStore.getState()
        const notificationStore = useNotificationStore.getState()
        return Promise.allSettled([
          chatStore.loadConversations({ query: chatStore.conversationQuery }),
          notificationStore.load(),
        ])
      },
    })
  },

  disconnect: () => {
    webSocketManager.disconnect()
    set({ status: 'offline', activeUserId: null })
  },

  retry: () => webSocketManager.retry(),
  sendTyping: (conversationId, typing) => webSocketManager.sendTyping(conversationId, typing),
}))
