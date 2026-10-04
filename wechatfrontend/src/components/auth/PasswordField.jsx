import { Eye, EyeOff } from 'lucide-react'
import { useState } from 'react'

export function PasswordField({ id, label, value, onChange, autoComplete, minLength = 8 }) {
  const [visible, setVisible] = useState(false)
  return (
    <label className="field" htmlFor={id}>
      <span>{label}</span>
      <span className="input-with-action">
        <input
          id={id}
          name={id}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={onChange}
          autoComplete={autoComplete}
          minLength={minLength}
          maxLength={128}
          required
        />
        <button
          type="button"
          className="icon-button input-action"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        >
          {visible ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </span>
    </label>
  )
}
