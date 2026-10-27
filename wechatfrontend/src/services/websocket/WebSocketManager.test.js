import { afterEach, describe, expect, it, vi } from 'vitest'
import { WebSocketManager } from './WebSocketManager.js'

async function setup({ getAccessToken = () => 'access-token', awaitConnect = true } = {}) {
  const subscriptions = []
  const clients = []
  const configurations = []
  const states = []
  const onConversationEvent = vi.fn()
  const onConnected = vi.fn()
  const manager = new WebSocketManager((configuration) => {
    configurations.push(configuration)
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
    clients.push(client)
    return client
  })
  const connectPromise = manager.connect({
    userId: '11111111-1111-1111-1111-111111111111',
    getAccessToken,
    onState: (state) => states.push(state),
    onConversationEvent,
    onNotificationEvent: vi.fn(),
    onConnected,
  })
  if (awaitConnect) await connectPromise
  return {
    client: clients[0],
    clients,
    configurations,
    manager,
    states,
    subscriptions,
    connectPromise,
    onConnected,
    onConversationEvent,
  }
}

describe('WebSocketManager', () => {
  afterEach(() => vi.useRealTimers())

  it('authenticates CONNECT and restores the allowed subscriptions', async () => {
    const { client, configurations, subscriptions, onConnected } = await setup()
    expect(configurations[0].connectHeaders.Authorization).toBe('Bearer access-token')
    expect(configurations[0].reconnectDelay).toBe(0)
    client.connected = true
    client.onConnect()
    expect(client.subscribe).toHaveBeenCalledWith('/user/queue/conversation-events', expect.any(Function))
    expect(client.subscribe).toHaveBeenCalledWith('/topic/users/11111111-1111-1111-1111-111111111111/notifications', expect.any(Function))
    expect(subscriptions).toHaveLength(2)
    expect(onConnected).toHaveBeenCalledWith({ reconnected: false })
  })

  it('normalizes an incoming JSON frame and can unsubscribe', async () => {
    const { client, manager, subscriptions, onConversationEvent } = await setup()
    client.onConnect()
    subscriptions[0].callback({ body: JSON.stringify({ type: 'message.created', messageId: 'm1' }) })
    expect(onConversationEvent).toHaveBeenCalledWith({ type: 'message.created', messageId: 'm1' })
    await manager.disconnect()
    expect(subscriptions.every((item) => item.unsubscribe.mock.calls.length === 1)).toBe(true)
  })

  it('uses the exact bounded reconnect schedule and stays offline after it is exhausted', async () => {
    vi.useFakeTimers()
    const { clients, states } = await setup()
    const expectedDelays = [1_000, 2_000, 5_000, 10_000, 20_000, 30_000]

    for (let index = 0; index < expectedDelays.length; index += 1) {
      clients[index].onWebSocketClose()
      expect(states.at(-1)).toBe('reconnecting')
      await vi.advanceTimersByTimeAsync(expectedDelays[index] - 1)
      expect(clients).toHaveLength(index + 1)
      await vi.advanceTimersByTimeAsync(1)
      expect(clients).toHaveLength(index + 2)
    }

    clients.at(-1).onWebSocketClose()
    expect(states.at(-1)).toBe('offline')
    expect(vi.getTimerCount()).toBe(0)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(states.at(-1)).toBe('offline')
    expect(clients).toHaveLength(7)
  })

  it('coalesces repeated manual retries while the previous client is deactivating', async () => {
    let finishDeactivate
    const { client, clients, manager } = await setup()
    client.deactivate.mockReturnValue(new Promise((resolve) => { finishDeactivate = resolve }))

    const firstRetry = manager.retry()
    const secondRetry = manager.retry()

    expect(secondRetry).toBe(firstRetry)
    expect(client.deactivate).toHaveBeenCalledTimes(1)
    finishDeactivate()
    await firstRetry
    expect(clients).toHaveLength(2)
  })

  it('continues a forced manual retry when old-client deactivation rejects', async () => {
    const { client, clients, manager } = await setup()
    client.deactivate.mockRejectedValue(new Error('deactivate failed'))

    await expect(manager.retry()).resolves.toBeUndefined()

    expect(clients).toHaveLength(2)
  })

  it('ignores callbacks from a client invalidated by a manual retry', async () => {
    const { client, clients, manager, states, onConnected } = await setup()
    await manager.retry()

    client.onConnect()

    expect(clients).toHaveLength(2)
    expect(onConnected).not.toHaveBeenCalled()
    expect(states.at(-1)).toBe('connecting')
  })

  it('marks a successful retry as a reconnect', async () => {
    vi.useFakeTimers()
    const { clients, onConnected } = await setup()
    clients[0].onWebSocketClose()
    await vi.advanceTimersByTimeAsync(1_000)

    clients[1].onConnect()

    expect(onConnected).toHaveBeenCalledWith({ reconnected: true })
  })

  it('keeps the connection lifecycle stable when the connected callback throws', async () => {
    const { client, manager, states, onConnected } = await setup()
    onConnected.mockImplementation(() => { throw new Error('resync failed') })

    client.onConnect()
    await Promise.resolve()

    expect(states.at(-1)).toBe('connected')
    expect(manager.client).toBe(client)
  })

  it('stays offline without creating a client when token preparation fails', async () => {
    const { clients, states } = await setup({ getAccessToken: () => Promise.reject(new Error('expired')) })

    expect(clients).toHaveLength(0)
    expect(states).toEqual(['connecting', 'offline'])
  })

  it('does not activate a client when disconnect wins a pending token request', async () => {
    let resolveToken
    const tokenPromise = new Promise((resolve) => { resolveToken = resolve })
    const { clients, connectPromise, manager, states } = await setup({
      getAccessToken: () => tokenPromise,
      awaitConnect: false,
    })

    await manager.disconnect()
    resolveToken('late-access-token')
    await connectPromise

    expect(clients).toHaveLength(0)
    expect(states.at(-1)).toBe('offline')
  })

  it('uses the same reconnect path when STOMP deactivation fails', async () => {
    const { client, manager, states } = await setup()
    client.deactivate.mockRejectedValue(new Error('deactivate failed'))

    client.onStompError()
    await vi.waitFor(() => expect(states.at(-1)).toBe('reconnecting'))

    expect(client.deactivate).toHaveBeenCalledTimes(1)
    await manager.disconnect()
    expect(states.at(-1)).toBe('offline')
  })

  it('finishes disconnect state even when client deactivation rejects', async () => {
    const { client, manager, states } = await setup()
    client.deactivate.mockRejectedValue(new Error('deactivate failed'))

    await expect(manager.disconnect()).resolves.toBeUndefined()

    expect(states.at(-1)).toBe('offline')
  })
})
