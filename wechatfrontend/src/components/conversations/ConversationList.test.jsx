import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConversationList } from './ConversationList.jsx'

describe('ConversationList', () => {
  it('renders real unread data and selects a conversation', () => {
    const onSelect = vi.fn()
    render(<ConversationList conversations={[{ id: 'c1', type: 'GROUP', name: 'Nhóm dự án', unreadCount: 3, updatedAt: '2026-01-01T00:00:00Z' }]} activeId={null} messagesByConversation={{}} onSelect={onSelect} />)
    expect(screen.getByText('Nhóm dự án')).toBeInTheDocument()
    expect(screen.getByLabelText('3 tin nhắn chưa đọc')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Nhóm dự án/i }))
    expect(onSelect).toHaveBeenCalledWith('c1')
  })

  it('shows an explicit empty state', () => {
    render(<ConversationList conversations={[]} activeId={null} messagesByConversation={{}} onSelect={() => {}} />)
    expect(screen.getByText('Chưa có cuộc trò chuyện')).toBeInTheDocument()
  })

  it('uses contextual direct identity and the server last-message summary', () => {
    render(<ConversationList conversations={[{
      id: 'c1',
      type: 'DIRECT',
      displayName: 'Minh Trần',
      displayAvatarUrl: '/avatar.png',
      lastMessageAt: '2026-01-01T00:00:00Z',
      lastMessage: { messageType: 'IMAGE', sentByCurrentUser: true, attachmentCount: 1 },
    }]} activeId={null} messagesByConversation={{}} onSelect={() => {}} />)

    expect(screen.getByText('Minh Trần')).toBeInTheDocument()
    expect(screen.getByText('Bạn: [Hình ảnh]')).toBeInTheDocument()
  })

  it('distinguishes an empty search result from an empty inbox', () => {
    render(<ConversationList conversations={[]} activeId={null} messagesByConversation={{}} query="không có" onSelect={() => {}} />)
    expect(screen.getByText('Không tìm thấy cuộc trò chuyện')).toBeInTheDocument()
  })
})
