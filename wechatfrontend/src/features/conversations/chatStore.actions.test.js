import { beforeEach, describe, expect, it, vi } from 'vitest'

const messageApi = vi.hoisted(() => ({ list: vi.fn(), send: vi.fn(), addReaction: vi.fn(), removeReaction: vi.fn(), deleteForMe: vi.fn(), edit: vi.fn() }))
const conversationApi = vi.hoisted(() => ({ list: vi.fn(), get: vi.fn(), archive: vi.fn(), pin: vi.fn(), create: vi.fn(), leave: vi.fn() }))
vi.mock('../../services/api/messageApi.js', () => ({ messageApi }))
vi.mock('../../services/api/conversationApi.js', () => ({ conversationApi }))

import { useChatStore } from './chatStore.js'

const message = { id: 'm1', conversationId: 'c1', senderId: 'u1', content: 'hi', createdAt: '2026-01-01T00:00:00Z', reactions: [] }

describe('chat store feature actions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useChatStore.getState().reset()
  })

  it('adds a reaction, then removes it when the same emoji is toggled again', async () => {
    useChatStore.setState({ messagesByConversation: { c1: [message] } })
    messageApi.addReaction.mockResolvedValue([{ emoji: '👍', count: 1, reactedByMe: true }])
    await useChatStore.getState().toggleReaction(message, '👍')
    const reacted = useChatStore.getState().messagesByConversation.c1[0]
    expect(reacted.reactions).toEqual([{ emoji: '👍', count: 1, reactedByMe: true }])

    messageApi.removeReaction.mockResolvedValue([])
    await useChatStore.getState().toggleReaction(reacted, '👍')
    expect(messageApi.removeReaction).toHaveBeenCalledWith('m1', '👍')
    expect(useChatStore.getState().messagesByConversation.c1[0].reactions).toEqual([])
  })

  it('removes a message deleted for me locally and via realtime event', async () => {
    useChatStore.setState({ messagesByConversation: { c1: [message, { ...message, id: 'm2' }] } })
    messageApi.deleteForMe.mockResolvedValue()
    await useChatStore.getState().deleteMessageForMe(message)
    expect(useChatStore.getState().messagesByConversation.c1.map(({ id }) => id)).toEqual(['m2'])
    await useChatStore.getState().handleRealtimeEvent({ type: 'message.deleted_for_me', conversationId: 'c1', messageId: 'm2' })
    expect(useChatStore.getState().messagesByConversation.c1).toEqual([])
  })

  it('hides a conversation once archived while the archive filter is off', async () => {
    useChatStore.setState({ conversations: [{ id: 'c1' }, { id: 'c2' }] })
    conversationApi.archive.mockResolvedValue({ id: 'c1', archivedAt: '2026-01-01T00:00:00Z' })
    await useChatStore.getState().toggleArchive({ id: 'c1', archivedAt: null })
    expect(useChatStore.getState().conversations.map(({ id }) => id)).toEqual(['c2'])
  })

  it('refreshes conversation metadata on conversation events and drops it when access is lost', async () => {
    useChatStore.setState({ conversations: [{ id: 'c1', name: 'Old' }] })
    conversationApi.get.mockResolvedValueOnce({ id: 'c1', name: 'New' })
    await useChatStore.getState().handleRealtimeEvent({ type: 'conversation.updated', conversationId: 'c1' })
    expect(useChatStore.getState().conversations[0].name).toBe('New')

    conversationApi.get.mockRejectedValueOnce({ response: { status: 403 } })
    await useChatStore.getState().handleRealtimeEvent({ type: 'conversation.member.removed', conversationId: 'c1' })
    expect(useChatStore.getState().conversations).toEqual([])
  })

  it('reloads the list when a message arrives for an unknown conversation', async () => {
    conversationApi.list.mockResolvedValue([{ id: 'c9' }])
    messageApi.list.mockResolvedValue({ items: [], nextCursor: null, hasNext: false })
    await useChatStore.getState().handleRealtimeEvent({ type: 'message.created', conversationId: 'c9' })
    expect(conversationApi.list).toHaveBeenCalled()
  })

  it('keeps the canonical conversation cache in sync after pin and leave', async () => {
    useChatStore.setState({ conversations: [{ id: 'c1' }], conversationsById: { c1: { id: 'c1', displayName: 'Nhóm' } } })
    conversationApi.pin.mockResolvedValue({ id: 'c1', pinnedAt: '2026-01-01T00:00:00Z' })
    await useChatStore.getState().togglePin({ id: 'c1', pinnedAt: null })
    expect(useChatStore.getState().conversationsById.c1).toMatchObject({ displayName: 'Nhóm', pinnedAt: '2026-01-01T00:00:00Z' })

    conversationApi.leave.mockResolvedValue({})
    await useChatStore.getState().leaveConversation('c1')
    expect(useChatStore.getState().conversationsById.c1).toBeUndefined()
    expect(useChatStore.getState().conversations).toEqual([])
  })
})
