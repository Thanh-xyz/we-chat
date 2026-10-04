import { create } from 'zustand'
import { useChatStore } from '../conversations/chatStore.js'
import { getAccessToken } from '../../services/auth/tokenStorage.js'
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
      getAccessToken,
      onState: (status) => set({ status }),
      onConversationEvent: (event) => useChatStore.getState().handleRealtimeEvent(event),
      onNotificationEvent: (event) => useChatStore.getState().handleNotificationEvent(event),
    })
  },

  disconnect: () => {
    webSocketManager.disconnect()
    set({ status: 'offline', activeUserId: null })
  },

  retry: () => webSocketManager.retry(),
  sendTyping: (conversationId, typing) => webSocketManager.sendTyping(conversationId, typing),
}))
