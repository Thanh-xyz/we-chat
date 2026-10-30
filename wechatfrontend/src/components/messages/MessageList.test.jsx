import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../features/messages/useAttachmentPreview.js', () => ({
  useAttachmentPreview: () => ({ url: 'blob:image', download: vi.fn() }),
}))
import { MessageList } from './MessageList.jsx'

describe('MessageList', () => {
  beforeEach(() => {
    Element.prototype.scrollTo.mockClear()
  })

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

  it('renders date separators and received sender identity', () => {
    render(<MessageList conversationId="c1" messages={[
      { id: 'm1', senderId: 'u2', sender: { displayName: 'Minh Trần' }, content: 'Ngày một', messageType: 'TEXT', createdAt: '2026-01-01T10:00:00Z', attachments: [] },
      { id: 'm2', senderId: 'u2', sender: { displayName: 'Minh Trần' }, content: 'Ngày hai', messageType: 'TEXT', createdAt: '2026-01-02T10:00:00Z', attachments: [] },
    ]} page={{ status: 'success', hasNext: false }} currentUserId="u1" typingUsers={[]} onLoadOlder={() => {}} onRetry={() => {}} />)

    expect(screen.getAllByText('Minh Trần')).toHaveLength(2)
    expect(screen.getByText('01/01/2026')).toBeInTheDocument()
    expect(screen.getByText('02/01/2026')).toBeInTheDocument()
  })

  it('scrolls again after an image loads only while the user stays near the bottom', async () => {
    const message = { id: 'm1', senderId: 'u2', sender: { displayName: 'Minh' }, content: null, messageType: 'IMAGE', createdAt: '2026-01-01T10:00:00Z', attachments: [{ id: 'a1', fileType: 'IMAGE', originalFileName: 'photo.png' }] }
    const { container } = render(<MessageList conversationId="c1" messages={[message]} page={{ status: 'success', hasNext: false }} currentUserId="u1" typingUsers={[]} onLoadOlder={() => {}} onRetry={() => {}} />)
    const scroller = container.querySelector('.message-scroll')
    Object.defineProperties(scroller, {
      scrollHeight: { configurable: true, value: 1000 },
      clientHeight: { configurable: true, value: 400 },
      scrollTop: { configurable: true, writable: true, value: 550 },
    })
    fireEvent.scroll(scroller)
    Element.prototype.scrollTo.mockClear()

    fireEvent.load(screen.getByRole('img', { name: 'photo.png' }))

    await waitFor(() => expect(Element.prototype.scrollTo).toHaveBeenCalled())

    scroller.scrollTop = 100
    fireEvent.scroll(scroller)
    Element.prototype.scrollTo.mockClear()
    fireEvent.load(screen.getByRole('img', { name: 'photo.png' }))
    await new Promise((resolve) => setTimeout(resolve, 5))
    expect(Element.prototype.scrollTo).not.toHaveBeenCalled()
  })
})
