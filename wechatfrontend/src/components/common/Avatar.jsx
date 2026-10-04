import { initials } from '../../utils/chat.js'

export function Avatar({ name, src, size = 'medium', online = false }) {
  return (
    <span className={`avatar avatar--${size}`} aria-hidden="true">
      {src ? <img src={src} alt="" /> : <span>{initials(name)}</span>}
      {online && <span className="avatar-presence" />}
    </span>
  )
}
