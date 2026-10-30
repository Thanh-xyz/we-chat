import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ChatSidebar } from './ChatSidebar.jsx'

function renderSidebar() {
  const props = {
    user: { username: 'test-user', displayName: 'Test User' },
    conversations: [],
    messagesByConversation: {},
    activeId: null,
    status: 'success',
    error: null,
    connectionStatus: 'connected',
    notificationUnread: 0,
    notificationPanel: false,
    notificationData: {},
    onSearch: vi.fn(),
    onSelect: vi.fn(),
    onLogout: vi.fn(),
    onRetryConnection: vi.fn(),
    onToggleNotifications: vi.fn(),
    onCloseNotifications: vi.fn(),
    onLoadNotifications: vi.fn(),
    onReadNotification: vi.fn(),
    onReadAllNotifications: vi.fn(),
    onRemoveNotification: vi.fn(),
  }

  return {
    ...render(
    <MemoryRouter initialEntries={['/app']}>
      <Routes>
        <Route path="/app" element={<ChatSidebar {...props} />} />
        <Route path="/app/settings" element={<h1>Cài đặt đích</h1>} />
      </Routes>
    </MemoryRouter>,
    ),
    props,
  }
}

describe('ChatSidebar Settings navigation', () => {
  it('opens the Settings route from the sidebar', () => {
    const { props } = renderSidebar()

    expect(props.onCloseNotifications).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Mở cài đặt' }))

    expect(screen.getByRole('heading', { name: 'Cài đặt đích' })).toBeInTheDocument()
    expect(props.onCloseNotifications).toHaveBeenCalledTimes(1)
  })
})
