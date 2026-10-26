import { Client } from '@stomp/stompjs'

const BACKOFF_DELAYS = [1_000, 2_000, 5_000, 10_000, 20_000, 30_000]

export function resolveWebSocketUrl() {
  const configured = import.meta.env.VITE_WS_URL?.trim()
  const url = new URL(configured || '/ws', window.location.origin)
  if (url.protocol === 'http:') url.protocol = 'ws:'
  if (url.protocol === 'https:') url.protocol = 'wss:'
  return url.toString()
}

export class WebSocketManager {
  constructor(clientFactory = (configuration) => new Client(configuration)) {
    this.clientFactory = clientFactory
    this.client = null
    this.subscriptions = []
    this.reconnectAttempt = 0
    this.reconnectTimer = null
    this.restartPromise = null
    this.generation = 0
    this.hasConnected = false
    this.desired = false
    this.options = null
  }

  connect(options) {
    if (this.desired || this.client?.connected) return Promise.resolve()
    this.options = options
    this.desired = true
    this.reconnectAttempt = 0
    this.hasConnected = false
    const generation = ++this.generation
    this.options.onState('connecting')
    return this.startClient(generation)
  }

  async startClient(generation) {
    const options = this.options
    if (!this.isCurrentGeneration(generation, options)) return

    let token
    try {
      token = await options.getAccessToken()
      if (!token) throw new Error('Missing access token')
    } catch {
      if (this.isCurrentGeneration(generation, options)) {
        this.desired = false
        options.onState('offline')
      }
      return
    }

    if (!this.isCurrentGeneration(generation, options)) return

    const client = this.clientFactory({
      brokerURL: resolveWebSocketUrl(),
      connectionTimeout: 10_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: 0,
      connectHeaders: { Authorization: `Bearer ${token}` },
      debug: () => {},
    })
    this.client = client
    this.bindClient(client, generation, options)
    client.activate()
  }

  bindClient(client, generation, options) {
    let closeHandled = false
    const handleClose = () => {
      if (closeHandled) return
      closeHandled = true
      this.handleClientClose(client, generation, options)
    }

    client.onConnect = () => {
      if (!this.isCurrentClient(client, generation, options)) return
      const reconnected = this.hasConnected || this.reconnectAttempt > 0
      this.hasConnected = true
      this.reconnectAttempt = 0
      this.cancelReconnectTimer()
      options.onState('connected')
      this.restoreSubscriptions(client, generation, options)
      try {
        Promise.resolve(options.onConnected?.({ reconnected })).catch(() => {})
      } catch {
        // Connection state must not depend on optional resynchronization work.
      }
    }
    client.onStompError = () => {
      if (!this.isCurrentClient(client, generation, options)) return
      Promise.resolve()
        .then(() => client.deactivate({ force: true }))
        .then(handleClose, handleClose)
    }
    client.onWebSocketError = () => {}
    client.onWebSocketClose = handleClose
  }

  handleClientClose(client, generation, options) {
    if (!this.isCurrentClient(client, generation, options)) return
    this.clearSubscriptions()
    this.client = null
    if (!this.desired) return

    if (this.reconnectAttempt >= BACKOFF_DELAYS.length) {
      this.desired = false
      options.onState('offline')
      return
    }

    const delay = BACKOFF_DELAYS[this.reconnectAttempt]
    this.reconnectAttempt += 1
    options.onState('reconnecting')
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (!this.isCurrentGeneration(generation, options)) return
      void this.startClient(generation)
    }, delay)
  }

  restoreSubscriptions(client, generation, options) {
    this.clearSubscriptions()
    this.subscriptions = [
      client.subscribe('/user/queue/conversation-events', (frame) => {
        if (!this.isCurrentClient(client, generation, options)) return
        const event = this.parseFrame(frame)
        if (event) options.onConversationEvent(event)
      }),
      client.subscribe(`/topic/users/${options.userId}/notifications`, (frame) => {
        if (!this.isCurrentClient(client, generation, options)) return
        const event = this.parseFrame(frame)
        if (event) options.onNotificationEvent(event)
      }),
    ]
  }

  parseFrame(frame) {
    try {
      return JSON.parse(frame.body)
    } catch {
      return null
    }
  }

  sendTyping(conversationId, typing) {
    if (!this.client?.connected) return false
    this.client.publish({
      destination: `/app/conversations/${conversationId}/typing`,
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ typing }),
    })
    return true
  }

  retry() {
    if (!this.options || this.client?.connected) return Promise.resolve()
    if (this.restartPromise) return this.restartPromise

    const options = this.options
    const generation = ++this.generation
    const previousClient = this.client
    this.desired = true
    this.reconnectAttempt = 0
    this.cancelReconnectTimer()
    this.clearSubscriptions()
    this.client = null
    options.onState('connecting')

    const restartPromise = this.deactivateClient(previousClient)
      .then(() => this.startClient(generation))
      .finally(() => {
        if (this.restartPromise === restartPromise) this.restartPromise = null
      })
    this.restartPromise = restartPromise
    return restartPromise
  }

  disconnect() {
    const client = this.client
    const options = this.options
    this.generation += 1
    this.desired = false
    this.reconnectAttempt = 0
    this.cancelReconnectTimer()
    this.clearSubscriptions()
    this.client = null
    this.options = null
    this.restartPromise = null
    options?.onState('offline')
    return this.deactivateClient(client)
  }

  deactivateClient(client) {
    if (!client) return Promise.resolve()
    try {
      return Promise.resolve(client.deactivate({ force: true })).catch(() => {})
    } catch {
      return Promise.resolve()
    }
  }

  cancelReconnectTimer() {
    if (this.reconnectTimer !== null) clearTimeout(this.reconnectTimer)
    this.reconnectTimer = null
  }

  clearSubscriptions() {
    this.subscriptions.forEach((subscription) => subscription?.unsubscribe())
    this.subscriptions = []
  }

  isCurrentGeneration(generation, options) {
    return this.desired && this.generation === generation && this.options === options
  }

  isCurrentClient(client, generation, options) {
    return this.client === client && this.isCurrentGeneration(generation, options)
  }
}

export const webSocketManager = new WebSocketManager()
