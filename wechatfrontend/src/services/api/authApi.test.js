import { beforeEach, describe, expect, it, vi } from 'vitest'

const { post } = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('./client.js', () => ({
  apiClient: { post },
}))

import { authApi } from './authApi.js'

describe('authApi.changePassword', () => {
  beforeEach(() => post.mockReset())

  it('sends only the current and new password to the existing endpoint', async () => {
    post.mockResolvedValue({ status: 204 })

    await authApi.changePassword({ currentPassword: 'old-pass-123', newPassword: 'new-pass-123' })

    expect(post).toHaveBeenCalledWith('/auth/change-password', {
      currentPassword: 'old-pass-123',
      newPassword: 'new-pass-123',
    })
  })
})
