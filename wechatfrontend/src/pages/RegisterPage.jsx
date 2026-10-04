import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthLayout, FormAlert } from '../components/auth/AuthLayout.jsx'
import { PasswordField } from '../components/auth/PasswordField.jsx'
import { authApi } from '../services/api/authApi.js'
import { errorMessage } from '../services/api/errors.js'

const INITIAL_FORM = { username: '', email: '', displayName: '', password: '' }

export function RegisterPage() {
  const [form, setForm] = useState(INITIAL_FORM)
  const [error, setError] = useState('')
  const [registeredEmail, setRegisteredEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const result = await authApi.register({
        username: form.username.trim(),
        email: form.email.trim(),
        displayName: form.displayName.trim() || null,
        password: form.password,
      })
      setRegisteredEmail(result.email)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  if (registeredEmail) {
    return (
      <AuthLayout eyebrow="Gần xong rồi" title="Kiểm tra email của bạn" description={`Liên kết xác minh đã được gửi đến ${registeredEmail}.`} footer={<Link to="/login">Quay lại đăng nhập</Link>}>
        <FormAlert tone="success">Tài khoản đã được tạo. Hãy xác minh email nếu máy chủ yêu cầu trước khi đăng nhập.</FormAlert>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout eyebrow="Tham gia ChatSpace" title="Tạo tài khoản" description="Bắt đầu kết nối chỉ trong một phút." footer={<p>Đã có tài khoản? <Link to="/login">Đăng nhập</Link></p>}>
      <form className="auth-form auth-form--compact" onSubmit={handleSubmit}>
        <FormAlert>{error}</FormAlert>
        <label className="field" htmlFor="username"><span>Tên người dùng</span><input id="username" value={form.username} onChange={update('username')} minLength={3} maxLength={50} pattern="[a-zA-Z0-9._-]+" autoComplete="username" required autoFocus /></label>
        <label className="field" htmlFor="email"><span>Email</span><input id="email" type="email" value={form.email} onChange={update('email')} maxLength={255} autoComplete="email" required /></label>
        <label className="field" htmlFor="displayName"><span>Tên hiển thị <small>(không bắt buộc)</small></span><input id="displayName" value={form.displayName} onChange={update('displayName')} maxLength={120} autoComplete="name" /></label>
        <PasswordField id="password" label="Mật khẩu (tối thiểu 8 ký tự)" value={form.password} onChange={update('password')} autoComplete="new-password" />
        <button className="primary-button" type="submit" disabled={submitting}>{submitting ? 'Đang tạo…' : 'Tạo tài khoản'}</button>
      </form>
    </AuthLayout>
  )
}
