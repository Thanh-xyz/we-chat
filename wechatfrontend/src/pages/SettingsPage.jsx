import { ArrowLeft, Bell, KeyRound, LogOut, Moon, Sun, UserRound } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '../components/common/Modal.jsx'
import { ChangePasswordForm } from '../components/settings/ChangePasswordForm.jsx'
import { NotificationPreferences } from '../components/settings/NotificationPreferences.jsx'
import { ProfileForm } from '../components/settings/ProfileForm.jsx'
import { useAuthStore } from '../features/auth/authStore.js'
import { useChatStore } from '../features/conversations/chatStore.js'
import { usePeopleStore } from '../features/people/peopleStore.js'
import { useRealtimeStore } from '../features/realtime/realtimeStore.js'
import { useSettingsStore } from '../features/settings/settingsStore.js'

const THEMES = [
  { value: 'light', label: 'Sáng', description: 'Giao diện sáng mặc định', Icon: Sun },
  { value: 'dark', label: 'Tối', description: 'Dịu mắt trong môi trường tối', Icon: Moon },
]

export function SettingsPage() {
  const theme = useSettingsStore((state) => state.theme)
  const setTheme = useSettingsStore((state) => state.setTheme)
  const logoutAll = useAuthStore((state) => state.logoutAll)
  const navigate = useNavigate()
  const [confirmLogoutAll, setConfirmLogoutAll] = useState(false)
  const [busy, setBusy] = useState(false)

  async function handleLogoutAll() {
    setBusy(true)
    useRealtimeStore.getState().disconnect()
    useChatStore.getState().reset()
    usePeopleStore.getState().reset()
    try {
      await logoutAll()
    } catch {
      // Local credentials are cleared by the store even when the request fails.
    }
    navigate('/login', { replace: true })
  }

  return (
    <main className="settings-page">
      <header className="settings-header">
        <Link className="icon-button settings-back" to="/app" aria-label="Quay lại trò chuyện">
          <ArrowLeft size={21} />
        </Link>
        <div>
          <p className="eyebrow">CHATSPACE</p>
          <h1>Cài đặt</h1>
          <p>Quản lý hồ sơ, giao diện, thông báo và bảo mật tài khoản.</p>
        </div>
      </header>

      <div className="settings-content">
        <section className="settings-card" aria-labelledby="settings-profile-title">
          <div className="settings-section-heading">
            <span className="settings-section-icon" aria-hidden="true"><UserRound size={20} /></span>
            <div>
              <h2 id="settings-profile-title">Hồ sơ</h2>
              <p>Tên và ảnh đại diện hiển thị với bạn bè của bạn.</p>
            </div>
          </div>
          <ProfileForm />
        </section>

        <section className="settings-card" aria-labelledby="settings-appearance-title">
          <div className="settings-section-heading">
            <span className="settings-section-icon" aria-hidden="true"><Sun size={20} /></span>
            <div>
              <h2 id="settings-appearance-title">Giao diện</h2>
              <p>Chọn cách ChatSpace hiển thị trên thiết bị này.</p>
            </div>
          </div>
          <fieldset className="settings-theme-fieldset">
            <legend>Chọn giao diện</legend>
            <div className="settings-theme-options">
              {THEMES.map(({ value, label, description, Icon }) => (
                <label className={`settings-theme-option ${theme === value ? 'is-selected' : ''}`} key={value}>
                  <input
                    type="radio"
                    name="theme"
                    value={value}
                    checked={theme === value}
                    onChange={() => setTheme(value)}
                  />
                  <Icon size={19} aria-hidden="true" />
                  <span>
                    <strong>{label}</strong>
                    <small>{description}</small>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        </section>

        <section className="settings-card" aria-labelledby="settings-notification-title">
          <div className="settings-section-heading">
            <span className="settings-section-icon" aria-hidden="true"><Bell size={19} /></span>
            <div>
              <h2 id="settings-notification-title">Thông báo</h2>
              <p>Chọn loại thông báo bạn muốn nhận.</p>
            </div>
          </div>
          <NotificationPreferences />
        </section>

        <section className="settings-card" aria-labelledby="settings-security-title">
          <div className="settings-section-heading">
            <span className="settings-section-icon settings-section-icon--security" aria-hidden="true"><KeyRound size={19} /></span>
            <div>
              <h2 id="settings-security-title">Bảo mật tài khoản</h2>
              <p>Đổi mật khẩu để bảo vệ tài khoản của bạn.</p>
            </div>
          </div>
          <div className="settings-change-password">
            <h3>Đổi mật khẩu</h3>
            <p>Sử dụng mật khẩu hiện tại để đặt mật khẩu mới.</p>
            <ChangePasswordForm />
          </div>
          <div className="settings-change-password settings-sessions">
            <div>
              <h3>Phiên đăng nhập</h3>
              <p>Đăng xuất khỏi tất cả thiết bị, kể cả thiết bị này.</p>
            </div>
            <button type="button" className="danger-button" onClick={() => setConfirmLogoutAll(true)}><LogOut size={16} /> Đăng xuất mọi thiết bị</button>
          </div>
        </section>
      </div>
      {confirmLogoutAll && (
        <ConfirmDialog
          title="Đăng xuất mọi thiết bị?"
          description="Mọi phiên đăng nhập sẽ bị thu hồi. Bạn cần đăng nhập lại trên từng thiết bị."
          confirmLabel="Đăng xuất tất cả"
          danger
          busy={busy}
          onConfirm={handleLogoutAll}
          onClose={() => setConfirmLogoutAll(false)}
        />
      )}
    </main>
  )
}
