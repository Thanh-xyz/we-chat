import { beforeEach, describe, expect, it, vi } from 'vitest'

const messageApi = vi.hoisted(() => ({ list: vi.fn(), send: vi.fn() }))
const conversationApi = vi.hoisted(() => ({ list: vi.fn(), markRead: vi.fn() }))
vi.mock('../../services/api/messageApi.js', () => ({ messageApi }))
vi.mock('../../services/api/conversationApi.js', () => ({ conversationApi }))

import { useChatStore } from './chatStore.js'

describe('chat store cursor and realtime flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useChatStore.getState().reset()
  })

  it('replays the opaque cursor and prepends older messages without duplicates', async () => {
    useChatStore.setState({
      messagesByConversation: { c1: [{ id: 'm2', content: 'new', createdAt: '2026-01-02T00:00:00Z' }] },
      pagesByConversation: { c1: { status: 'success', hasNext: true, nextCursor: 'opaque-cursor' } },
    })
    messageApi.list.mockResolvedValue({
      items: [
        { id: 'm1', content: 'old', createdAt: '2026-01-01T00:00:00Z' },
        { id: 'm2', content: 'new', createdAt: '2026-01-02T00:00:00Z' },
      ],
      nextCursor: null,
      hasNext: false,
    })

    await useChatStore.getState().loadOlderMessages('c1')

    expect(messageApi.list).toHaveBeenCalledWith('c1', { cursor: 'opaque-cursor', limit: 50 })
    expect(useChatStore.getState().messagesByConversation.c1.map(({ id }) => id)).toEqual(['m1', 'm2'])
  })

  it('resyncs a realtime message event and keeps one message instance', async () => {
    useChatStore.setState({
      conversations: [{ id: 'c1', updatedAt: '2026-01-01T00:00:00Z', unreadCount: 0 }],
      messagesByConversation: { c1: [{ id: 'm1', content: 'hello', createdAt: '2026-01-01T00:00:00Z' }] },
    })
    messageApi.list.mockResolvedValue({ items: [{ id: 'm1', content: 'hello', createdAt: '2026-01-01T00:00:00Z' }], nextCursor: null, hasNext: false })

    await useChatStore.getState().handleRealtimeEvent({ type: 'message.created', conversationId: 'c1', occurredAt: '2026-01-01T00:00:00Z' })

    expect(useChatStore.getState().messagesByConversation.c1).toHaveLength(1)
  })

  it('applies unread updates delivered by WebSocket', async () => {
    useChatStore.setState({ conversations: [{ id: 'c1', unreadCount: 1 }] })
    await useChatStore.getState().handleRealtimeEvent({ type: 'conversation.unread.updated', conversationId: 'c1', payload: { unreadCount: 7 } })
    expect(useChatStore.getState().conversations[0].unreadCount).toBe(7)
  })
})
