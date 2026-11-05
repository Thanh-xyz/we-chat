import { create } from 'zustand'
import { useChatStore } from '../conversations/chatStore.js'
import { useContactStore } from '../contacts/contactStore.js'
import { usePeopleStore } from '../people/peopleStore.js'
import { useNotificationStore } from '../notifications/notificationStore.js'
import { getUsableAccessToken } from '../../services/api/client.js'
import { webSocketManager } from '../../services/websocket/WebSocketManager.js'

export function applyPresenceEvent(event) {
  const userId = event?.actorUserId
  const status = event?.payload?.status
  if (!userId || !status) return
  usePeopleStore.getState().setPresence(userId, status)
  useChatStore.getState().applyPresence(userId, status)
  useContactStore.getState().applyPresence(userId, status)
}

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
      onConversationEvent: (event) => {
        if (event?.type === 'presence.updated') {
          applyPresenceEvent(event)
          return
        }
        useChatStore.getState().handleRealtimeEvent(event)
      },
      onNotificationEvent: (event) => {
        useChatStore.getState().handleNotificationEvent(event)
        useNotificationStore.getState().handleRealtimeEvent(event)
      },
      onConnected: ({ reconnected } = {}) => {
        if (!reconnected) return Promise.resolve([])
        const chatStore = useChatStore.getState()
        const notificationStore = useNotificationStore.getState()
        // Presence events are not replayed, so a reconnect re-reads friends and conversations.
        return Promise.allSettled([
          chatStore.loadConversations({ query: chatStore.conversationQuery }),
          notificationStore.load(),
          usePeopleStore.getState().loadFriends(),
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
