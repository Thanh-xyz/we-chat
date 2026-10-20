import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthLayout, FormAlert } from '../components/auth/AuthLayout.jsx'
import { PasswordField } from '../components/auth/PasswordField.jsx'
import { registerAccount } from '../features/auth/authActions.js'
import { errorMessage } from '../services/api/errors.js'

const INITIAL_FORM = { username: '', email: '', displayName: '', password: '' }

export function RegisterPage() {
  const [form, setForm] = useState(INITIAL_FORM)
  const [error, setError] = useState('')
  const [registration, setRegistration] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const update = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }))

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const result = await registerAccount(form)
      setRegistration(result)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  if (registration) {
    const verificationEmailSent = registration.verificationEmailSent
    const statusTitle = verificationEmailSent === true
      ? 'Kiểm tra email của bạn'
      : 'Tài khoản đã được tạo'
    const statusDescription = verificationEmailSent === true
      ? `Liên kết xác minh đã được gửi đến ${registration.email}.`
      : verificationEmailSent === false
        ? 'Email xác thực chưa được gửi. Bạn có thể đăng nhập theo cấu hình hiện tại.'
        : 'Nếu hệ thống yêu cầu xác minh, hãy làm theo hướng dẫn xác minh tài khoản.'
    const statusMessage = verificationEmailSent === true
      ? 'Tài khoản đã được tạo. Hãy xác minh email nếu máy chủ yêu cầu trước khi đăng nhập.'
      : verificationEmailSent === false
        ? 'Tính năng gửi email xác thực hiện đang tạm tắt.'
        : 'Tài khoản đã được tạo.'

    return (
      <AuthLayout eyebrow="Gần xong rồi" title={statusTitle} description={statusDescription} footer={<Link to="/login">Quay lại đăng nhập</Link>}>
        <FormAlert tone="success">{statusMessage}</FormAlert>
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
