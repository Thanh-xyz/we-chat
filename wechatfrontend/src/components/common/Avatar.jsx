import { initials } from '../../utils/chat.js'

const PALETTE = 6

function hueIndex(value = '') {
  let hash = 0
  for (let index = 0; index < value.length; index += 1) hash = (hash * 31 + value.charCodeAt(index)) >>> 0
  return hash % PALETTE
}

export function Avatar({ name, src, size = 'medium', online = false, group = false }) {
  return (
    <span className={`avatar avatar--${size} avatar--tone-${hueIndex(name)} ${group ? 'avatar--group' : ''}`} aria-hidden="true">
      {src ? <img src={src} alt="" /> : <span>{initials(name)}</span>}
      {online && <span className="avatar-presence" />}
    </span>
  )
}
