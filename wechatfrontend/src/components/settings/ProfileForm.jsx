import { useState } from 'react'
import { Avatar } from '../common/Avatar.jsx'
import { FormAlert } from '../auth/AuthLayout.jsx'
import { useAuthStore } from '../../features/auth/authStore.js'
import { toast } from '../../features/ui/toastStore.js'
import { errorMessage } from '../../services/api/errors.js'

export function ProfileForm() {
  const user = useAuthStore((state) => state.user)
  const updateProfile = useAuthStore((state) => state.updateProfile)
  const [displayName, setDisplayName] = useState(user?.displayName || '')
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl || '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (!user) return null
  const dirty = displayName.trim() !== (user.displayName || '') || avatarUrl.trim() !== (user.avatarUrl || '')

  async function submit(event) {
    event.preventDefault()
    if (!displayName.trim()) return setError('Tên hiển thị không được để trống.')
    if (avatarUrl.trim() && !/^https?:\/\//i.test(avatarUrl.trim())) return setError('Ảnh đại diện phải là URL bắt đầu bằng http(s)://')
    setBusy(true)
    setError('')
    try {
      await updateProfile({ displayName: displayName.trim(), avatarUrl: avatarUrl.trim() || null })
      toast.success('Đã cập nhật hồ sơ')
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="profile-form" onSubmit={submit}>
      <div className="profile-preview">
        <Avatar name={displayName || user.username} src={avatarUrl.trim() || null} size="xlarge" />
        <span>
          <strong>{displayName || user.username}</strong>
          <small>@{user.username}{user.email ? ` · ${user.email}` : ''}</small>
          {user.emailVerified === false && <em className="pill pill--warn">Email chưa xác minh</em>}
          {user.emailVerified && <em className="pill pill--ok">Email đã xác minh</em>}
        </span>
      </div>
      {error && <FormAlert>{error}</FormAlert>}
      <div className="settings-grid">
        <label className="field field--compact">Tên hiển thị
          <input value={displayName} maxLength={120} onChange={(event) => setDisplayName(event.target.value)} />
        </label>
        <label className="field field--compact">Ảnh đại diện (URL) <small>Để trống nếu dùng chữ cái đầu</small>
          <input value={avatarUrl} maxLength={2048} placeholder="https://…" onChange={(event) => setAvatarUrl(event.target.value)} />
        </label>
      </div>
      <button type="submit" className="primary-button settings-password-submit" disabled={busy || !dirty}>{busy ? 'Đang lưu…' : 'Lưu hồ sơ'}</button>
    </form>
  )
}
