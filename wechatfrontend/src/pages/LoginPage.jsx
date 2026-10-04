import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AuthLayout, FormAlert } from '../components/auth/AuthLayout.jsx'
import { PasswordField } from '../components/auth/PasswordField.jsx'
import { useAuthStore } from '../features/auth/authStore.js'
import { errorMessage } from '../services/api/errors.js'

export function LoginPage() {
  const login = useAuthStore((state) => state.login)
  const navigate = useNavigate()
  const location = useLocation()
  const [identifier, setIdentifier] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await login({ identifier: identifier.trim(), password })
      navigate(location.state?.from || '/app', { replace: true })
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      eyebrow="Chào mừng trở lại"
      title="Đăng nhập"
      description="Tiếp tục những cuộc trò chuyện đang chờ bạn."
      footer={<p>Chưa có tài khoản? <Link to="/register">Tạo tài khoản</Link></p>}
    >
      <form className="auth-form" onSubmit={handleSubmit}>
        <FormAlert>{error}</FormAlert>
        <label className="field" htmlFor="identifier">
          <span>Email hoặc tên người dùng</span>
          <input
            id="identifier"
            name="identifier"
            value={identifier}
            onChange={(event) => setIdentifier(event.target.value)}
            autoComplete="username"
            maxLength={255}
            placeholder="ban@example.com"
            required
            autoFocus
          />
        </label>
        <PasswordField
          id="password"
          label="Mật khẩu"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
        />
        <div className="form-options">
          <span />
          <Link to="/forgot-password">Quên mật khẩu?</Link>
        </div>
        <button className="primary-button" type="submit" disabled={submitting || !identifier.trim() || !password}>
          {submitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
        </button>
      </form>
    </AuthLayout>
  )
}
