import { beforeEach, describe, expect, it, vi } from 'vitest'

const client = vi.hoisted(() => ({
  get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn(),
}))
vi.mock('./client.js', () => ({ apiClient: client }))

import { conversationApi } from './conversationApi.js'
import { messageApi } from './messageApi.js'
import { userApi } from './userApi.js'
import { mediaApi } from './mediaApi.js'

describe('frontend API contracts match backend controllers', () => {
  beforeEach(() => {
    Object.values(client).forEach((fn) => fn.mockReset().mockResolvedValue({ data: { ok: true } }))
  })

  it('conversation endpoints', async () => {
    await conversationApi.create({ type: 'GROUP', memberIds: ['a', 'b'], name: 'Team' })
    expect(client.post).toHaveBeenCalledWith('/conversations', { type: 'GROUP', memberIds: ['a', 'b'], name: 'Team', avatarUrl: null })
    await conversationApi.update('c1', { name: 'New' })
    expect(client.patch).toHaveBeenCalledWith('/conversations/c1', { name: 'New', avatarUrl: null })
    await conversationApi.addMembers('c1', ['u3'])
    expect(client.post).toHaveBeenCalledWith('/conversations/c1/members', { userIds: ['u3'] })
    await conversationApi.removeMember('c1', 'u3')
    expect(client.delete).toHaveBeenCalledWith('/conversations/c1/members/u3')
    await conversationApi.leave('c1')
    expect(client.post).toHaveBeenCalledWith('/conversations/c1/leave')
    await conversationApi.pin('c1')
    await conversationApi.unpin('c1')
    await conversationApi.mute('c1')
    await conversationApi.unmute('c1')
    await conversationApi.archive('c1')
    await conversationApi.unarchive('c1')
    expect(client.post).toHaveBeenCalledWith('/conversations/c1/pin')
    expect(client.delete).toHaveBeenCalledWith('/conversations/c1/pin')
    expect(client.post).toHaveBeenCalledWith('/conversations/c1/mute', { mutedUntil: null })
    expect(client.delete).toHaveBeenCalledWith('/conversations/c1/mute')
    expect(client.post).toHaveBeenCalledWith('/conversations/c1/archive')
    expect(client.delete).toHaveBeenCalledWith('/conversations/c1/archive')
    await conversationApi.list({ includeArchived: true })
    expect(client.get).toHaveBeenCalledWith('/conversations', { signal: undefined, params: { limit: 100, offset: 0, includeArchived: true } })
  })

  it('message endpoints', async () => {
    await messageApi.send('c1', { content: 'hi', replyToMessageId: 'm0' })
    expect(client.post).toHaveBeenCalledWith('/conversations/c1/messages', expect.objectContaining({ content: 'hi', replyToMessageId: 'm0', messageType: 'TEXT' }))
    await messageApi.edit('m1', 'fixed')
    expect(client.patch).toHaveBeenCalledWith('/messages/m1', { content: 'fixed' })
    await messageApi.recall('m1')
    expect(client.post).toHaveBeenCalledWith('/messages/m1/recall')
    await messageApi.deleteForMe('m1')
    expect(client.delete).toHaveBeenCalledWith('/messages/m1')
    await messageApi.addReaction('m1', '👍')
    expect(client.post).toHaveBeenCalledWith('/messages/m1/reactions', { emoji: '👍' })
    await messageApi.removeReaction('m1', '👍')
    expect(client.delete).toHaveBeenCalledWith('/messages/m1/reactions', { data: { emoji: '👍' } })
    await messageApi.search('c1', 'abc')
    expect(client.get).toHaveBeenCalledWith('/conversations/c1/messages/search', { signal: undefined, params: { q: 'abc', limit: 30 } })
  })

  it('profile and media endpoints', async () => {
    await userApi.updateMe({ displayName: 'An', avatarUrl: null })
    expect(client.put).toHaveBeenCalledWith('/users/me', { displayName: 'An', avatarUrl: null })
    await mediaApi.list('c1', { category: 'IMAGE' })
    expect(client.get).toHaveBeenCalledWith('/conversations/c1/media/images', { signal: undefined, params: { limit: 30, offset: 0 } })
    await mediaApi.stats('c1')
    expect(client.get).toHaveBeenCalledWith('/conversations/c1/media/stats', { signal: undefined })
  })
})
