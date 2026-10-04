import { Cloud, CloudOff, LoaderCircle } from 'lucide-react'

const COPY = {
  connected: 'Đã kết nối',
  connecting: 'Đang kết nối',
  reconnecting: 'Đang kết nối lại',
  offline: 'Ngoại tuyến',
}

export function ConnectionBadge({ status, onRetry }) {
  const Icon = status === 'connected' ? Cloud : status === 'offline' ? CloudOff : LoaderCircle
  const content = <><Icon size={13} className={status === 'connecting' || status === 'reconnecting' ? 'spin' : ''} /><span>{COPY[status] || COPY.offline}</span></>
  if (status === 'offline') {
    return <button type="button" className={`connection-badge connection-badge--${status}`} onClick={onRetry} title="Thử kết nối lại">{content}</button>
  }
  return <span className={`connection-badge connection-badge--${status}`}>{content}</span>
}
