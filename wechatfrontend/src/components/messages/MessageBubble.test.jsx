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
