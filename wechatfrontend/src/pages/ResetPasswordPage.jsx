import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthLayout, FormAlert } from '../components/auth/AuthLayout.jsx'
import { PasswordField } from '../components/auth/PasswordField.jsx'
import { authApi } from '../services/api/authApi.js'
import { errorMessage } from '../services/api/errors.js'

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [password, setPassword] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    setError('')
    try {
      await authApi.resetPassword(token, password)
      setDone(true)
    } catch (requestError) {
      setError(errorMessage(requestError))
    }
  }

  return (
    <AuthLayout eyebrow="Bảo mật" title="Đặt mật khẩu mới" footer={<Link to="/login">Quay lại đăng nhập</Link>}>
      {!token ? <FormAlert>Liên kết đặt lại mật khẩu không hợp lệ.</FormAlert> : done ? <FormAlert tone="success">Mật khẩu đã được cập nhật. Bạn có thể đăng nhập lại.</FormAlert> : (
        <form className="auth-form" onSubmit={submit}>
          <FormAlert>{error}</FormAlert>
          <PasswordField id="new-password" label="Mật khẩu mới" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" />
          <button className="primary-button" disabled={password.length < 8}>Đổi mật khẩu</button>
        </form>
      )}
    </AuthLayout>
  )
}
