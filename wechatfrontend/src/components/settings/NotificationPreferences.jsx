import { LoaderCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import { notificationApi } from '../../services/api/notificationApi.js'
import { errorMessage } from '../../services/api/errors.js'
import { toast } from '../../features/ui/toastStore.js'

const OPTIONS = [
  ['messageEnabled', 'Tin nhắn mới', 'Khi có tin nhắn trong cuộc trò chuyện của bạn'],
  ['mentionEnabled', 'Lượt nhắc tên', 'Khi ai đó nhắc đến bạn'],
  ['reactionEnabled', 'Cảm xúc', 'Khi có người thả cảm xúc vào tin nhắn của bạn'],
  ['groupEnabled', 'Hoạt động nhóm', 'Thêm/xóa thành viên, đổi tên nhóm'],
  ['systemEnabled', 'Hệ thống', 'Thông báo bảo mật và tài khoản'],
  ['emailEnabled', 'Gửi qua email', 'Nhận bản sao thông báo qua email'],
]

export function NotificationPreferences() {
  const [preferences, setPreferences] = useState(null)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    notificationApi.preferences({ signal: controller.signal })
      .then(setPreferences)
      .catch((requestError) => { if (!controller.signal.aborted) setError(errorMessage(requestError)) })
    return () => controller.abort()
  }, [])

  async function toggle(key) {
    const next = !preferences[key]
    setSaving(key)
    setPreferences((current) => ({ ...current, [key]: next }))
    try {
      setPreferences(await notificationApi.updatePreferences({ [key]: next }))
    } catch (requestError) {
      setPreferences((current) => ({ ...current, [key]: !next }))
      toast.error(errorMessage(requestError))
    } finally {
      setSaving(null)
    }
  }

  if (error) return <p className="panel-state panel-state--error">{error}</p>
  if (!preferences) return <div className="panel-state"><LoaderCircle className="spin" size={18} /> Đang tải tùy chọn…</div>

  return (
    <ul className="toggle-list">
      {OPTIONS.map(([key, label, description]) => (
        <li key={key}>
          <span><strong>{label}</strong><small>{description}</small></span>
          <button
            type="button"
            role="switch"
            aria-checked={Boolean(preferences[key])}
            aria-label={label}
            className={`switch ${preferences[key] ? 'is-on' : ''}`}
            disabled={saving === key}
            onClick={() => toggle(key)}
          ><span /></button>
        </li>
      ))}
    </ul>
  )
}
