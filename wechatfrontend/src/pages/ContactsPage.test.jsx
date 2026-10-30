import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const startDirectChat = vi.fn().mockResolvedValue({ id: 'conversation-1' })
const contactState = {
  friends: [{ userId: 'friend-1', username: 'minh', displayName: 'Minh Trần', status: 'ONLINE' }],
  incoming: [],
  outgoing: [],
  blocked: [],
  searchResults: [],
  summary: { friendCount: 1 },
  status: 'success',
  loadAll: vi.fn().mockResolvedValue(undefined),
  search: vi.fn().mockResolvedValue([]),
  sendRequest: vi.fn(),
  startDirectChat,
  accept: vi.fn(),
  decline: vi.fn(),
  cancel: vi.fn(),
  unfriend: vi.fn(),
  block: vi.fn(),
  unblock: vi.fn(),
  reset: vi.fn(),
}

vi.mock('../features/contacts/contactStore.js', () => ({
  useContactStore: (selector) => selector(contactState),
}))

import { ContactsPage } from './ContactsPage.jsx'

describe('ContactsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    startDirectChat.mockResolvedValue({ id: 'conversation-1' })
  })

  it('starts a direct chat from a friend card and navigates to it', async () => {
    render(
      <MemoryRouter initialEntries={['/app/contacts']}>
        <Routes>
          <Route path="/app/contacts" element={<ContactsPage />} />
          <Route path="/app/chat/:conversationId" element={<h1>Cuộc trò chuyện đã mở</h1>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('tab', { name: 'Bạn bè' }))
    fireEvent.click(screen.getByRole('button', { name: 'Nhắn tin' }))

    await waitFor(() => expect(startDirectChat).toHaveBeenCalledWith('friend-1'))
    expect(await screen.findByRole('heading', { name: 'Cuộc trò chuyện đã mở' })).toBeInTheDocument()
  })

  it('localizes presence status labels', () => {
    render(
      <MemoryRouter>
        <ContactsPage />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('tab', { name: 'Bạn bè' }))

    expect(screen.getByText('@minh · Đang hoạt động')).toBeInTheDocument()
    expect(screen.queryByText(/ONLINE/)).not.toBeInTheDocument()
  })

  it('shows contextual copy for an empty contact category', () => {
    render(
      <MemoryRouter>
        <ContactsPage />
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('tab', { name: 'Lời mời đến' }))

    expect(screen.getByText('Không có lời mời đến')).toBeInTheDocument()
    expect(screen.getByText('Lời mời kết bạn mới sẽ xuất hiện tại đây.')).toBeInTheDocument()
  })
})
