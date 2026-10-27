import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../services/api/authApi.js', () => ({
  authApi: { changePassword: vi.fn() },
}))

import { ChangePasswordForm } from './ChangePasswordForm.jsx'
import { useAuthStore } from '../../features/auth/authStore.js'
import { authApi } from '../../services/api/authApi.js'
import { ApiError } from '../../services/api/errors.js'

function renderForm() {
  return render(
    <MemoryRouter initialEntries={['/app/settings']}>
      <Routes>
        <Route path="/app/settings" element={<ChangePasswordForm />} />
        <Route path="/login" element={<h1>Đăng nhập để tiếp tục</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

function fillPasswords({ current = 'old-pass-123', next = 'new-pass-123', confirmation = next } = {}) {
  fireEvent.change(screen.getByLabelText('Mật khẩu hiện tại'), { target: { value: current } })
  fireEvent.change(screen.getByLabelText('Mật khẩu mới'), { target: { value: next } })
  fireEvent.change(screen.getByLabelText('Xác nhận mật khẩu mới'), { target: { value: confirmation } })
}

describe('ChangePasswordForm', () => {
  beforeEach(() => {
    authApi.changePassword.mockReset()
    window.localStorage.clear()
    window.sessionStorage.clear()
    useAuthStore.setState({ status: 'authenticated', user: { username: 'test-user' } })
  })

  it.each([
    [{ current: '', next: 'new-pass-123', confirmation: 'new-pass-123' }, 'Vui lòng nhập mật khẩu hiện tại.'],
    [{ current: 'old-pass-123', next: '', confirmation: '' }, 'Vui lòng nhập mật khẩu mới.'],
    [{ current: 'old-pass-123', next: 'short', confirmation: 'short' }, 'Mật khẩu mới phải có từ 8 đến 128 ký tự.'],
    [{ current: 'old-pass-123', next: 'new-pass-123', confirmation: '' }, 'Vui lòng xác nhận mật khẩu mới.'],
  ])('shows Vietnamese validation without calling the API', (values, expectedMessage) => {
    renderForm()
    fillPasswords(values)

    fireEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(screen.getByRole('alert')).toHaveTextContent(expectedMessage)
    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('validates that the new password confirmation matches', () => {
    renderForm()
    fillPasswords({ confirmation: 'different-pass-123' })

    fireEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Mật khẩu mới và phần xác nhận không khớp.')
    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('disables duplicate submission, clears the session and redirects after success', async () => {
    let resolveRequest
    authApi.changePassword.mockImplementation(() => new Promise((resolve) => { resolveRequest = resolve }))
    renderForm()
    fillPasswords()

    fireEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(screen.getByRole('button', { name: 'Đang đổi mật khẩu…' })).toBeDisabled()
    expect(authApi.changePassword).toHaveBeenCalledWith({
      currentPassword: 'old-pass-123',
      newPassword: 'new-pass-123',
    })

    resolveRequest()

    expect(await screen.findByRole('heading', { name: 'Đăng nhập để tiếp tục' })).toBeInTheDocument()
    expect(useAuthStore.getState().status).toBe('anonymous')
  })

  it('shows an API error and keeps the user on the form', async () => {
    authApi.changePassword.mockRejectedValue(new ApiError('Mật khẩu hiện tại không đúng.'))
    renderForm()
    fillPasswords()

    fireEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Mật khẩu hiện tại không đúng.'))
    expect(screen.queryByRole('heading', { name: 'Đăng nhập để tiếp tục' })).not.toBeInTheDocument()
    expect(useAuthStore.getState().status).toBe('authenticated')
  })
})
