import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../../features/messages/useAttachmentPreview.js', () => ({
  useAttachmentPreview: () => ({ url: 'blob:image', download: vi.fn() }),
}))

import { MessageBubble } from './MessageBubble.jsx'

describe('MessageBubble', () => {
  it('collapses long text and expands it on demand', () => {
    const content = 'a'.repeat(900)
    render(<MessageBubble message={{ id: 'm1', senderId: 'u1', content, messageType: 'TEXT', createdAt: '2026-01-01T00:00:00Z', attachments: [] }} mine />)

    expect(screen.getByText('Xem thêm')).toBeInTheDocument()
    expect(screen.queryByText(content)).not.toBeInTheDocument()
    fireEvent.click(screen.getByText('Xem thêm'))
    expect(screen.getByText(content)).toBeInTheDocument()
    expect(screen.getByText('Xem bớt')).toBeInTheDocument()
  })

  it('opens an image lightbox and closes it with Escape while restoring focus', () => {
    render(<MessageBubble message={{ id: 'm1', senderId: 'u2', sender: { displayName: 'Minh' }, content: null, messageType: 'IMAGE', createdAt: '2026-01-01T00:00:00Z', attachments: [{ id: 'a1', fileType: 'IMAGE', originalFileName: 'photo.png' }] }} mine={false} />)
    const trigger = screen.getByRole('button', { name: 'Mở photo.png' })

    fireEvent.click(trigger)
    expect(screen.getByRole('dialog', { name: 'Xem ảnh đính kèm' })).toBeInTheDocument()
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(screen.queryByRole('dialog', { name: 'Xem ảnh đính kèm' })).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})

const recent = new Date().toISOString()

describe('MessageBubble actions', () => {
  it('reacts via the quick picker and toggles an existing reaction chip', () => {
    const onReact = vi.fn()
    const message = { id: 'm1', senderId: 'u2', content: 'Xin chào', messageType: 'TEXT', createdAt: recent, reactions: [{ emoji: '❤️', count: 2, reactedByMe: true }] }
    render(<MessageBubble message={message} mine={false} onReact={onReact} onReply={() => {}} />)
    fireEvent.click(screen.getByLabelText('Thả cảm xúc'))
    fireEvent.click(screen.getByLabelText('Thả 👍'))
    expect(onReact).toHaveBeenCalledWith(message, '👍')
    fireEvent.click(screen.getByLabelText('❤️ 2, bạn đã thả'))
    expect(onReact).toHaveBeenCalledWith(message, '❤️')
  })

  it('offers edit and recall only for my recent text messages', () => {
    const onEdit = vi.fn()
    const onRecall = vi.fn()
    const message = { id: 'm1', senderId: 'u1', content: 'typo', messageType: 'TEXT', createdAt: recent, reactions: [] }
    render(<MessageBubble message={message} mine onEdit={onEdit} onRecall={onRecall} onDelete={() => {}} onReply={() => {}} />)
    fireEvent.click(screen.getByLabelText('Thêm thao tác'))
    fireEvent.click(screen.getByRole('menuitem', { name: /Chỉnh sửa/ }))
    expect(onEdit).toHaveBeenCalledWith(message)
  })

  it('hides edit once the 15 minute window has passed', () => {
    const message = { id: 'm1', senderId: 'u1', content: 'old', messageType: 'TEXT', createdAt: '2020-01-01T00:00:00Z', reactions: [] }
    render(<MessageBubble message={message} mine onEdit={() => {}} onRecall={() => {}} onDelete={() => {}} onReply={() => {}} />)
    fireEvent.click(screen.getByLabelText('Thêm thao tác'))
    expect(screen.queryByRole('menuitem', { name: /Chỉnh sửa/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('menuitem', { name: /Thu hồi/ })).not.toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /Xóa ở phía tôi/ })).toBeInTheDocument()
  })
})
