import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../features/auth/authStore.js'
import { LoginPage } from './LoginPage.jsx'

describe('LoginPage', () => {
  const login = vi.fn()

  beforeEach(() => {
    login.mockReset()
    useAuthStore.setState({ status: 'anonymous', user: null, login })
  })

  it('submits the backend identifier contract', async () => {
    login.mockResolvedValue({ id: 'u1' })
    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    fireEvent.change(screen.getByLabelText('Email hoặc tên người dùng'), { target: { value: 'thanh@example.com' } })
    fireEvent.change(screen.getByLabelText('Mật khẩu'), { target: { value: 'Password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    await waitFor(() => expect(login).toHaveBeenCalledWith({ identifier: 'thanh@example.com', password: 'Password123' }))
  })
})
