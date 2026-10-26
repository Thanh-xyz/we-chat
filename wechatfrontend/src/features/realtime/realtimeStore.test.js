import { beforeEach, describe, expect, it, vi } from 'vitest'

const webSocketManager = vi.hoisted(() => ({
  connect: vi.fn(),
  disconnect: vi.fn(),
  retry: vi.fn(),
  sendTyping: vi.fn(),
}))

vi.mock('../../services/websocket/WebSocketManager.js', () => ({ webSocketManager }))
vi.mock('../../services/api/client.js', () => ({ getUsableAccessToken: vi.fn(() => Promise.resolve('access-token')) }))

import { useChatStore } from '../conversations/chatStore.js'
import { useNotificationStore } from '../notifications/notificationStore.js'
import { useRealtimeStore } from './realtimeStore.js'

describe('realtime store reconnect integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useRealtimeStore.setState({ status: 'offline', activeUserId: null })
    useChatStore.getState().reset()
    useNotificationStore.getState().reset()
  })

  it('resynchronizes persisted state only after a reconnect', async () => {
    useChatStore.setState({ conversationQuery: 'team' })
    const loadConversations = vi.spyOn(useChatStore.getState(), 'loadConversations').mockResolvedValue([])
    const loadNotifications = vi.spyOn(useNotificationStore.getState(), 'load').mockResolvedValue({ notifications: [], unreadCount: 0 })

    useRealtimeStore.getState().connect('user-1')
    const options = webSocketManager.connect.mock.calls[0][0]

    await options.onConnected({ reconnected: false })
    expect(loadConversations).not.toHaveBeenCalled()
    expect(loadNotifications).not.toHaveBeenCalled()

    await options.onConnected({ reconnected: true })
    expect(loadConversations).toHaveBeenCalledWith({ query: 'team' })
    expect(loadNotifications).toHaveBeenCalledTimes(1)
  })

  it('isolates one failed resync request from the other', async () => {
    const loadConversations = vi.spyOn(useChatStore.getState(), 'loadConversations').mockRejectedValue(new Error('offline'))
    const loadNotifications = vi.spyOn(useNotificationStore.getState(), 'load').mockResolvedValue({ notifications: [], unreadCount: 0 })
    useRealtimeStore.getState().connect('user-1')
    const options = webSocketManager.connect.mock.calls[0][0]

    const results = await options.onConnected({ reconnected: true })

    expect(results.map(({ status }) => status)).toEqual(['rejected', 'fulfilled'])
    expect(loadConversations).toHaveBeenCalledTimes(1)
    expect(loadNotifications).toHaveBeenCalledTimes(1)
  })
})
