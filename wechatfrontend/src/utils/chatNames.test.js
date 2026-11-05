import { describe, expect, it } from 'vitest'
import { conversationName, messagePreview } from './chat.js'

const people = {
  me: { userId: 'me', displayName: 'Tôi' },
  a: { userId: 'a', displayName: 'An Nguyễn' },
  b: { userId: 'b', username: 'binh' },
}

describe('conversation naming from member ids', () => {
  it('uses the other member for direct conversations', () => {
    expect(conversationName({ type: 'DIRECT', memberIds: ['me', 'a'] }, { currentUserId: 'me', people })).toBe('An Nguyễn')
  })
  it('falls back when the other member is unknown', () => {
    expect(conversationName({ type: 'DIRECT', memberIds: ['me', 'x'] }, { currentUserId: 'me', people })).toBe('Trò chuyện trực tiếp')
  })
  it('lists member names for unnamed groups', () => {
    expect(conversationName({ type: 'GROUP', memberIds: ['me', 'a', 'b'] }, { currentUserId: 'me', people })).toBe('An Nguyễn, binh')
  })
  it('prefixes previews by sender', () => {
    expect(messagePreview({ senderId: 'me', content: 'hello' }, { currentUserId: 'me' })).toBe('Bạn: hello')
    expect(messagePreview({ senderId: 'a', messageType: 'IMAGE' }, { currentUserId: 'me', people, group: true })).toBe('Nguyễn: [Hình ảnh]')
  })
})
