import { beforeEach, describe, expect, it, vi } from 'vitest'

const messageApi = vi.hoisted(() => ({ list: vi.fn(), send: vi.fn() }))
const conversationApi = vi.hoisted(() => ({ list: vi.fn(), get: vi.fn(), markRead: vi.fn() }))
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

  it('ignores a stale conversation search response', async () => {
    let resolveFirst
    const first = new Promise((resolve) => { resolveFirst = resolve })
    conversationApi.list.mockReturnValueOnce(first).mockResolvedValueOnce([{ id: 'new' }])

    const firstSearch = useChatStore.getState().loadConversations({ query: 'old' })
    const secondSearch = useChatStore.getState().loadConversations({ query: 'new' })
    await secondSearch
    resolveFirst([{ id: 'old' }])
    await firstSearch

    expect(useChatStore.getState().conversations).toEqual([{ id: 'new' }])
    expect(useChatStore.getState().conversationQuery).toBe('new')
    expect(useChatStore.getState().conversationStatus).toBe('success')
  })

  it('retries a stale conversation reload after a realtime update', async () => {
    let resolveFirstLoad
    conversationApi.list
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirstLoad = resolve }))
      .mockResolvedValueOnce([{ id: 'c1', unreadCount: 7 }])

    const loadPromise = useChatStore.getState().loadConversations()
    await useChatStore.getState().handleRealtimeEvent({
      type: 'conversation.unread.updated',
      conversationId: 'c1',
      payload: { unreadCount: 7 },
    })
    resolveFirstLoad([{ id: 'c1', unreadCount: 1 }])
    await loadPromise

    expect(conversationApi.list).toHaveBeenCalledTimes(2)
    expect(useChatStore.getState().conversations[0].unreadCount).toBe(7)
  })

  it('keeps the active conversation in the canonical cache when search returns no rows', async () => {
    useChatStore.setState({
      conversations: [{ id: 'c1', displayName: 'Bạn chat' }],
      conversationsById: { c1: { id: 'c1', displayName: 'Bạn chat' } },
      messagesByConversation: { c1: [{ id: 'm1', content: 'hello', createdAt: '2026-01-01T00:00:00Z' }] },
      drafts: { c1: 'bản nháp' },
    })
    conversationApi.list.mockResolvedValue([])

    await useChatStore.getState().loadConversations({ query: 'không có' })

    expect(useChatStore.getState().conversations).toEqual([])
    expect(useChatStore.getState().conversationsById.c1.displayName).toBe('Bạn chat')
    expect(useChatStore.getState().messagesByConversation.c1).toHaveLength(1)
    expect(useChatStore.getState().drafts.c1).toBe('bản nháp')
  })

  it('loads a deep-linked conversation into the canonical cache', async () => {
    conversationApi.get.mockResolvedValue({ id: 'c-deep', displayName: 'Deep link' })

    await useChatStore.getState().loadConversation('c-deep')

    expect(conversationApi.get).toHaveBeenCalledWith('c-deep', { signal: undefined })
    expect(useChatStore.getState().conversationsById['c-deep'].displayName).toBe('Deep link')
  })

  it('ignores a previous-session conversation load after reset and a new load', async () => {
    let resolvePreviousLoad
    conversationApi.list
      .mockReturnValueOnce(new Promise((resolve) => { resolvePreviousLoad = resolve }))
      .mockResolvedValueOnce([{ id: 'new' }])

    const previousLoad = useChatStore.getState().loadConversations()
    useChatStore.getState().reset()
    await useChatStore.getState().loadConversations()
    resolvePreviousLoad([{ id: 'old' }])
    await previousLoad

    expect(useChatStore.getState().conversations).toEqual([{ id: 'new' }])
  })
})
