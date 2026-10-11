import { describe, expect, it, vi } from 'vitest'

const authApi = vi.hoisted(() => ({
  register: vi.fn(),
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
  verifyEmail: vi.fn(),
}))

vi.mock('../../services/api/authApi.js', () => ({ authApi }))

import {
  registerAccount,
  requestPasswordReset,
  resetAccountPassword,
  verifyAccountEmail,
} from './authActions.js'

describe('auth actions', () => {
  it('normalizes registration input before calling the API service', async () => {
    authApi.register.mockResolvedValue({ email: 'user@example.com' })

    await registerAccount({
      username: '  user  ',
      email: ' user@example.com ',
      displayName: '  User  ',
      password: 'password',
    })

    expect(authApi.register).toHaveBeenCalledWith({
      username: 'user',
      email: 'user@example.com',
      displayName: 'User',
      password: 'password',
    })
  })

  it('keeps password and verification actions within the feature boundary', async () => {
    await requestPasswordReset(' user@example.com ')
    await resetAccountPassword('reset-token', 'new-password')
    await verifyAccountEmail('verify-token', { signal: 'signal' })

    expect(authApi.forgotPassword).toHaveBeenCalledWith('user@example.com')
    expect(authApi.resetPassword).toHaveBeenCalledWith('reset-token', 'new-password')
    expect(authApi.verifyEmail).toHaveBeenCalledWith('verify-token', { signal: 'signal' })
  })
})
