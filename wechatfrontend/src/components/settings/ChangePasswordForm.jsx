import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FormAlert } from '../auth/AuthLayout.jsx'
import { PasswordField } from '../auth/PasswordField.jsx'
import { useAuthStore } from '../../features/auth/authStore.js'
import { errorMessage } from '../../services/api/errors.js'
import { authApi } from '../../services/api/authApi.js'

export function ChangePasswordForm() {
  const navigate = useNavigate()
  const expireSession = useAuthStore((state) => state.expireSession)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError('')

    if (!currentPassword) {
      setError('Vui lòng nhập mật khẩu hiện tại.')
      return
    }

    if (!newPassword) {
      setError('Vui lòng nhập mật khẩu mới.')
      return
    }

    if (newPassword.length < 8 || newPassword.length > 128) {
      setError('Mật khẩu mới phải có từ 8 đến 128 ký tự.')
      return
    }

    if (!confirmPassword) {
      setError('Vui lòng xác nhận mật khẩu mới.')
      return
    }

    if (newPassword !== confirmPassword) {
      setError('Mật khẩu mới và phần xác nhận không khớp.')
      return
    }

    setIsSubmitting(true)
    try {
      await authApi.changePassword({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      expireSession()
      navigate('/login', { replace: true })
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form className="settings-password-form" onSubmit={submit} noValidate>
      {error && <FormAlert>{error}</FormAlert>}
      <PasswordField
        id="current-password"
        label="Mật khẩu hiện tại"
        value={currentPassword}
        onChange={(event) => setCurrentPassword(event.target.value)}
        autoComplete="current-password"
      />
      <PasswordField
        id="new-password"
        label="Mật khẩu mới"
        value={newPassword}
        onChange={(event) => setNewPassword(event.target.value)}
        autoComplete="new-password"
      />
      <PasswordField
        id="confirm-password"
        label="Xác nhận mật khẩu mới"
        value={confirmPassword}
        onChange={(event) => setConfirmPassword(event.target.value)}
        autoComplete="new-password"
      />
      <p className="settings-password-note">Mật khẩu mới phải có từ 8 đến 128 ký tự.</p>
      <button className="primary-button settings-password-submit" type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Đang đổi mật khẩu…' : 'Đổi mật khẩu'}
      </button>
    </form>
  )
}
