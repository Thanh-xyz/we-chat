import { describe, expect, it } from 'vitest'
import { mergeMessages, sortConversations, updateConversationUnread } from './chat.js'

describe('chat state helpers', () => {
  it('deduplicates HTTP and WebSocket messages by server id', () => {
    const current = [{ id: 'm1', content: 'old', createdAt: '2026-01-01T10:00:00Z' }]
    const incoming = [
      { id: 'm1', content: 'updated', createdAt: '2026-01-01T10:00:00Z' },
      { id: 'm2', content: 'new', createdAt: '2026-01-01T10:01:00Z' },
    ]
    expect(mergeMessages(current, incoming)).toEqual([
      expect.objectContaining({ id: 'm1', content: 'updated' }),
      expect.objectContaining({ id: 'm2' }),
    ])
  })

  it('updates only the matching unread count', () => {
    const result = updateConversationUnread([{ id: 'a', unreadCount: 2 }, { id: 'b', unreadCount: 4 }], 'a', 0)
    expect(result).toEqual([{ id: 'a', unreadCount: 0 }, { id: 'b', unreadCount: 4 }])
  })

  it('sorts pinned and most recently active conversations first', () => {
    const result = sortConversations([
      { id: 'old', updatedAt: '2026-01-01T00:00:00Z' },
      { id: 'new', updatedAt: '2026-01-03T00:00:00Z' },
      { id: 'pinned', pinnedAt: '2026-01-01T00:00:00Z', updatedAt: '2025-01-01T00:00:00Z' },
    ])
    expect(result.map(({ id }) => id)).toEqual(['pinned', 'new', 'old'])
  })
})
