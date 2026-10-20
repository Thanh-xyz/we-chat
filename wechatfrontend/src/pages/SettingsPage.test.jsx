import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import App from '../App.jsx'
import { useAuthStore } from '../features/auth/authStore.js'
import { useSettingsStore } from '../features/settings/settingsStore.js'

function renderAppAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('SettingsPage route and theme control', () => {
  beforeEach(() => {
    window.localStorage.clear()
    useSettingsStore.getState().setTheme('light')
    useAuthStore.setState({
      status: 'anonymous',
      user: null,
      initialize: vi.fn(),
    })
  })

  it('redirects a guest to login instead of rendering Settings', () => {
    renderAppAt('/app/settings')

    expect(screen.getByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Cài đặt' })).not.toBeInTheDocument()
  })

  it('renders Settings for an authenticated user and applies the selected theme', () => {
    useAuthStore.setState({ status: 'authenticated', user: { username: 'test-user' } })
    renderAppAt('/app/settings')

    expect(screen.getByRole('heading', { name: 'Cài đặt' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Quay lại trò chuyện' })).toHaveAttribute('href', '/app')

    fireEvent.click(screen.getByRole('radio', { name: /Tối/ }))

    expect(useSettingsStore.getState().theme).toBe('dark')
    expect(window.localStorage.getItem('wechat.theme')).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})
