import { ArrowLeft, KeyRound, Moon, Sun } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ChangePasswordForm } from '../components/settings/ChangePasswordForm.jsx'
import { useSettingsStore } from '../features/settings/settingsStore.js'

const THEMES = [
  { value: 'light', label: 'Sáng', description: 'Giao diện sáng mặc định', Icon: Sun },
  { value: 'dark', label: 'Tối', description: 'Dịu mắt trong môi trường tối', Icon: Moon },
]

export function SettingsPage() {
  const theme = useSettingsStore((state) => state.theme)
  const setTheme = useSettingsStore((state) => state.setTheme)

  return (
    <main className="settings-page">
      <header className="settings-header">
        <Link className="icon-button settings-back" to="/app" aria-label="Quay lại trò chuyện">
          <ArrowLeft size={21} />
        </Link>
        <div>
          <p className="eyebrow">CHATSPACE</p>
          <h1>Cài đặt</h1>
          <p>Tuỳ chỉnh giao diện và bảo mật tài khoản của bạn.</p>
        </div>
      </header>

      <div className="settings-content">
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
        </section>
      </div>
    </main>
  )
}
