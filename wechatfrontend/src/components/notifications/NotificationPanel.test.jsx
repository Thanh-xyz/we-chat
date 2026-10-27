import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createRef } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { NotificationPanel } from './NotificationPanel.jsx'

function renderPanel(overrides = {}) {
  const triggerRef = createRef()
  const onLoad = vi.fn().mockResolvedValue(undefined)
  const onClose = vi.fn()
  const props = {
    notifications: [],
    status: 'idle',
    error: null,
    triggerRef,
    onLoad,
    onClose,
    onRead: vi.fn(),
    onReadAll: vi.fn(),
    onRemove: vi.fn(),
    ...overrides,
  }
  const result = render(<><button ref={triggerRef}>Chuông</button><NotificationPanel {...props} /></>)
  return { ...result, onClose, onLoad, triggerRef }
}

describe('NotificationPanel', () => {
  it('loads once on mount and does not restart when status changes', async () => {
    const { onLoad, rerender, triggerRef } = renderPanel()
    await waitFor(() => expect(onLoad).toHaveBeenCalledTimes(1))

    rerender(<><button ref={triggerRef}>Chuông</button><NotificationPanel notifications={[]} status="loading" error={null} triggerRef={triggerRef} onLoad={onLoad} onClose={vi.fn()} onRead={vi.fn()} onReadAll={vi.fn()} onRemove={vi.fn()} /></>)

    expect(onLoad).toHaveBeenCalledTimes(1)
  })

  it('closes with Escape and returns focus to the trigger', () => {
    const { onClose, triggerRef } = renderPanel({ status: 'success' })

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(triggerRef.current).toHaveFocus()
  })

  it('closes on an outside pointer interaction and returns focus to the trigger', () => {
    const { onClose, triggerRef } = renderPanel({ status: 'success' })

    fireEvent.pointerDown(document.body)

    expect(onClose).toHaveBeenCalledTimes(1)
    expect(triggerRef.current).toHaveFocus()
  })

  it('offers a retry after a load error', () => {
    const onLoad = vi.fn().mockResolvedValue(undefined)
    renderPanel({ status: 'error', error: { message: 'Mất kết nối' }, onLoad })

    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))

    expect(onLoad).toHaveBeenCalledTimes(1)
  })
})
