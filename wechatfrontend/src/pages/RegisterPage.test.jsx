import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RegisterPage } from './RegisterPage.jsx'

const { registerAccount } = vi.hoisted(() => ({ registerAccount: vi.fn() }))

vi.mock('../features/auth/authActions.js', () => ({ registerAccount }))

describe('RegisterPage', () => {
  beforeEach(() => {
    registerAccount.mockReset()
  })

  async function submitRegistration() {
    fireEvent.change(screen.getByLabelText('Tên người dùng'), { target: { value: 'thanh_user' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'thanh@example.com' } })
    fireEvent.change(screen.getByLabelText('Mật khẩu (tối thiểu 8 ký tự)'), { target: { value: 'Password123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
  }

  it('shows that the verification email was sent when the API confirms it', async () => {
    registerAccount.mockResolvedValue({ email: 'thanh@example.com', verificationEmailSent: true })
    render(<MemoryRouter><RegisterPage /></MemoryRouter>)

    await submitRegistration()

    expect(await screen.findByRole('heading', { name: 'Kiểm tra email của bạn' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Hãy xác minh email')
  })

  it('says verification email was not sent when the API confirms email is disabled', async () => {
    registerAccount.mockResolvedValue({ email: 'thanh@example.com', verificationEmailSent: false })
    render(<MemoryRouter><RegisterPage /></MemoryRouter>)

    await submitRegistration()

    expect(await screen.findByRole('heading', { name: 'Tài khoản đã được tạo' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('đang tạm tắt')
    expect(screen.getByText('Email xác thực chưa được gửi. Bạn có thể đăng nhập theo cấu hình hiện tại.')).toBeInTheDocument()
  })

  it('uses neutral copy when an older API omits the email status field', async () => {
    registerAccount.mockResolvedValue({ email: 'thanh@example.com' })
    render(<MemoryRouter><RegisterPage /></MemoryRouter>)

    await submitRegistration()

    expect(await screen.findByRole('heading', { name: 'Tài khoản đã được tạo' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Tài khoản đã được tạo.')
    expect(screen.queryByText(/Liên kết xác minh đã được gửi/)).not.toBeInTheDocument()
  })
})
