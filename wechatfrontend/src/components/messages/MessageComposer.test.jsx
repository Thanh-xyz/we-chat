import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { MessageComposer } from './MessageComposer.jsx'

describe('MessageComposer', () => {
  it('sends trimmed content with Enter and clears the field', async () => {
    const onSend = vi.fn().mockResolvedValue({})
    render(<MessageComposer conversationId="c1" onSend={onSend} onTyping={() => {}} />)
    const field = screen.getByLabelText('Nội dung tin nhắn')
    fireEvent.change(field, { target: { value: '  hello  ' } })
    fireEvent.keyDown(field, { key: 'Enter' })
    await waitFor(() => expect(onSend).toHaveBeenCalledWith('hello'))
    expect(field).toHaveValue('')
  })

  it('keeps a newline on Shift+Enter and does not send empty content', () => {
    const onSend = vi.fn()
    render(<MessageComposer conversationId="c1" onSend={onSend} onTyping={() => {}} />)
    const field = screen.getByLabelText('Nội dung tin nhắn')
    fireEvent.keyDown(field, { key: 'Enter', shiftKey: true })
    fireEvent.click(screen.getByLabelText('Gửi tin nhắn'))
    expect(onSend).not.toHaveBeenCalled()
  })
})
