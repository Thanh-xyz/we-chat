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
    this.desired = false
    this.options = null
  }

  connect(options) {
    if (this.desired || this.client?.connected) return
    this.options = options
    this.desired = true
    this.options.onState('connecting')
    this.createClient()
    this.client.activate()
  }

  createClient() {
    this.client = this.clientFactory({
      brokerURL: resolveWebSocketUrl(),
      connectionTimeout: 10_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: BACKOFF_DELAYS[0],
      debug: () => {},
    })
    this.client.beforeConnect = () => {
      const token = this.options.getAccessToken()
      if (!token) throw new Error('Missing access token')
      this.client.connectHeaders = { Authorization: `Bearer ${token}` }
      if (this.reconnectAttempt > 0) this.options.onState('reconnecting')
    }
    this.client.onConnect = () => {
      this.reconnectAttempt = 0
      this.client.reconnectDelay = BACKOFF_DELAYS[0]
      this.options.onState('connected')
      this.restoreSubscriptions()
      this.options.onConnected?.()
    }
    this.client.onStompError = () => this.options.onState('offline')
    this.client.onWebSocketError = () => this.options.onState('reconnecting')
    this.client.onWebSocketClose = () => {
      this.clearSubscriptions()
      if (!this.desired) return
      this.reconnectAttempt += 1
      if (this.reconnectAttempt > BACKOFF_DELAYS.length) {
        this.client.reconnectDelay = 0
        this.options.onState('offline')
        return
      }
      this.client.reconnectDelay = BACKOFF_DELAYS[this.reconnectAttempt - 1]
      this.options.onState('reconnecting')
    }
  }

  restoreSubscriptions() {
    this.clearSubscriptions()
    this.subscriptions = [
      this.client.subscribe('/user/queue/conversation-events', (frame) => {
        const event = this.parseFrame(frame)
        if (event) this.options.onConversationEvent(event)
      }),
      this.client.subscribe(`/topic/users/${this.options.userId}/notifications`, (frame) => {
        const event = this.parseFrame(frame)
        if (event) this.options.onNotificationEvent(event)
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
    if (!this.options || this.client?.connected) return
    this.desired = false
    const previousClient = this.client
    Promise.resolve(previousClient?.deactivate({ force: true })).finally(() => {
      this.reconnectAttempt = 0
      this.desired = true
      this.options.onState('connecting')
      this.createClient()
      this.client.activate()
    })
  }

  disconnect() {
    this.desired = false
    this.clearSubscriptions()
    this.options?.onState('offline')
    const client = this.client
    this.client = null
    this.options = null
    if (client) {
      client.onWebSocketClose = () => {}
      client.onWebSocketError = () => {}
      client.onStompError = () => {}
      return client.deactivate({ force: true })
    }
    return Promise.resolve()
  }

  clearSubscriptions() {
    this.subscriptions.forEach((subscription) => subscription?.unsubscribe())
    this.subscriptions = []
  }
}

export const webSocketManager = new WebSocketManager()
