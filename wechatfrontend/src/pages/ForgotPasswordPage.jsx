import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthLayout, FormAlert } from '../components/auth/AuthLayout.jsx'
import { authApi } from '../services/api/authApi.js'
import { errorMessage } from '../services/api/errors.js'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      await authApi.forgotPassword(email.trim())
      setSent(true)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout eyebrow="Khôi phục tài khoản" title="Quên mật khẩu?" description="Nhập email để nhận liên kết đặt lại mật khẩu." footer={<Link to="/login">Quay lại đăng nhập</Link>}>
      {sent ? <FormAlert tone="success">Nếu email tồn tại, hướng dẫn đặt lại mật khẩu đã được gửi.</FormAlert> : (
        <form className="auth-form" onSubmit={submit}>
          <FormAlert>{error}</FormAlert>
          <label className="field" htmlFor="email"><span>Email</span><input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" required autoFocus /></label>
          <button className="primary-button" disabled={submitting}>{submitting ? 'Đang gửi…' : 'Gửi liên kết'}</button>
        </form>
      )}
    </AuthLayout>
  )
}
