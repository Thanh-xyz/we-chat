import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const attachmentApi = vi.hoisted(() => ({ upload: vi.fn(), remove: vi.fn() }))
vi.mock('../../services/api/attachmentApi.js', () => ({ attachmentApi }))
import { MessageComposer } from './MessageComposer.jsx'

describe('MessageComposer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    attachmentApi.upload.mockResolvedValue({ id: 'attachment-1' })
    attachmentApi.remove.mockResolvedValue(undefined)
  })

  it('sends trimmed content with Enter and clears the field', async () => {
    const onSend = vi.fn().mockResolvedValue({})
    render(<MessageComposer conversationId="c1" onSend={onSend} onTyping={() => {}} />)
    const field = screen.getByLabelText('Nội dung tin nhắn')
    fireEvent.change(field, { target: { value: '  hello  ' } })
    fireEvent.keyDown(field, { key: 'Enter' })
    await waitFor(() => expect(onSend).toHaveBeenCalledWith({ content: 'hello', messageType: 'TEXT', attachmentIds: [] }))
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

  it('rejects mixed attachment types before upload and clears the error after removal', async () => {
    const { container } = render(<MessageComposer conversationId="c1" onSend={vi.fn()} onTyping={() => {}} />)
    const image = new File(['image'], 'photo.png', { type: 'image/png' })
    const document = new File(['pdf'], 'report.pdf', { type: 'application/pdf' })

    fireEvent.change(container.querySelector('input[type="file"]'), { target: { files: [image, document] } })

    await waitFor(() => expect(attachmentApi.upload).toHaveBeenCalledTimes(1))
    expect(attachmentApi.upload.mock.calls[0][1]).toBe(image)
    expect(screen.getByRole('alert')).toHaveTextContent('report.pdf')

    fireEvent.click(screen.getByRole('button', { name: 'Xóa report.pdf' }))

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  })

  it('does not submit twice while the first send is pending', async () => {
    let resolveSend
    const onSend = vi.fn(() => new Promise((resolve) => { resolveSend = resolve }))
    render(<MessageComposer conversationId="c1" onSend={onSend} onTyping={() => {}} />)
    fireEvent.change(screen.getByLabelText('Nội dung tin nhắn'), { target: { value: 'hello' } })

    const sendButton = screen.getByLabelText('Gửi tin nhắn')
    fireEvent.click(sendButton)
    fireEvent.click(sendButton)

    expect(onSend).toHaveBeenCalledTimes(1)
    resolveSend()
    await waitFor(() => expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveValue(''))

    fireEvent.change(screen.getByLabelText('Nội dung tin nhắn'), { target: { value: 'next message' } })
    expect(sendButton).not.toBeDisabled()
  })
})
