import { act, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ForgotPasswordPage } from './ForgotPasswordPage.jsx'

const { requestPasswordReset } = vi.hoisted(() => ({ requestPasswordReset: vi.fn() }))

vi.mock('../features/auth/authActions.js', () => ({ requestPasswordReset }))

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    requestPasswordReset.mockReset()
  })

  it('shows the server unavailable message and does not show a success state', async () => {
    requestPasswordReset.mockImplementation(() => {
      const error = new Error('Service unavailable')
      error.response = {
        status: 503,
        data: { message: 'Tính năng email hiện tạm thời không khả dụng.' },
      }
      throw error
    })
    render(<MemoryRouter><ForgotPasswordPage /></MemoryRouter>)

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'thanh@example.com' } })
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Yêu cầu liên kết' }))
    })

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Tính năng email hiện tạm thời không khả dụng.')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
