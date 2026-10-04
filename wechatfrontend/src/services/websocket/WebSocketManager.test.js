import { describe, expect, it, vi } from 'vitest'
import { WebSocketManager } from './WebSocketManager.js'

function setup() {
  const subscriptions = []
  const client = {
    connected: false,
    activate: vi.fn(),
    deactivate: vi.fn().mockResolvedValue(undefined),
    subscribe: vi.fn((destination, callback) => {
      const subscription = { destination, callback, unsubscribe: vi.fn() }
      subscriptions.push(subscription)
      return subscription
    }),
    publish: vi.fn(),
  }
  const states = []
  const onConversationEvent = vi.fn()
  const manager = new WebSocketManager(() => client)
  manager.connect({
    userId: '11111111-1111-1111-1111-111111111111',
    getAccessToken: () => 'access-token',
    onState: (state) => states.push(state),
    onConversationEvent,
    onNotificationEvent: vi.fn(),
  })
  return { client, manager, states, subscriptions, onConversationEvent }
}

describe('WebSocketManager', () => {
  it('authenticates CONNECT and restores the allowed subscriptions', async () => {
    const { client, subscriptions } = setup()
    await client.beforeConnect()
    expect(client.connectHeaders.Authorization).toBe('Bearer access-token')
    client.connected = true
    client.onConnect()
    expect(client.subscribe).toHaveBeenCalledWith('/user/queue/conversation-events', expect.any(Function))
    expect(client.subscribe).toHaveBeenCalledWith('/topic/users/11111111-1111-1111-1111-111111111111/notifications', expect.any(Function))
    expect(subscriptions).toHaveLength(2)
  })

  it('normalizes an incoming JSON frame and can unsubscribe', async () => {
    const { client, manager, subscriptions, onConversationEvent } = setup()
    client.onConnect()
    subscriptions[0].callback({ body: JSON.stringify({ type: 'message.created', messageId: 'm1' }) })
    expect(onConversationEvent).toHaveBeenCalledWith({ type: 'message.created', messageId: 'm1' })
    await manager.disconnect()
    expect(subscriptions.every((item) => item.unsubscribe.mock.calls.length === 1)).toBe(true)
  })

  it('uses bounded reconnect attempts and exposes offline state', () => {
    const { client, states } = setup()
    for (let index = 0; index < 7; index += 1) client.onWebSocketClose()
    expect(client.reconnectDelay).toBe(0)
    expect(states.at(-1)).toBe('offline')
  })
})
