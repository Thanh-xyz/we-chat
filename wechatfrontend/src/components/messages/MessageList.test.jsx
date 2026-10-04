import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MessageList } from './MessageList.jsx'

describe('MessageList', () => {
  it('renders sent and received messages once', () => {
    render(<MessageList conversationId="c1" messages={[
      { id: 'm1', senderId: 'u1', content: 'Xin chào', messageType: 'TEXT', createdAt: '2026-01-01T10:00:00Z', attachments: [] },
      { id: 'm2', senderId: 'u2', content: 'Chào bạn', messageType: 'TEXT', createdAt: '2026-01-01T10:01:00Z', attachments: [] },
    ]} page={{ status: 'success', hasNext: false }} currentUserId="u1" typingUsers={[]} onLoadOlder={() => {}} onRetry={() => {}} />)
    expect(screen.getAllByText('Xin chào')).toHaveLength(1)
    expect(screen.getAllByText('Chào bạn')).toHaveLength(1)
  })

  it('renders loading and error states', () => {
    const { rerender } = render(<MessageList conversationId="c1" messages={[]} page={{ status: 'loading' }} currentUserId="u1" typingUsers={[]} onLoadOlder={() => {}} onRetry={() => {}} />)
    expect(screen.getByLabelText('Đang tải tin nhắn')).toBeInTheDocument()
    rerender(<MessageList conversationId="c1" messages={[]} page={{ status: 'error', error: { message: 'Lỗi mạng' } }} currentUserId="u1" typingUsers={[]} onLoadOlder={() => {}} onRetry={() => {}} />)
    expect(screen.getByText('Lỗi mạng')).toBeInTheDocument()
  })
})
