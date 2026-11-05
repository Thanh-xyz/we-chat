import { MessageSquarePlus, UsersRound } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Modal } from '../common/Modal.jsx'
import { PeoplePicker } from '../common/PeoplePicker.jsx'
import { errorMessage } from '../../services/api/errors.js'

export function NewConversationDialog({ friends, onCreate, onClose, onOpenContacts }) {
  const [mode, setMode] = useState('DIRECT')
  const [selected, setSelected] = useState([])
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const valid = useMemo(() => (mode === 'DIRECT' ? selected.length === 1 : selected.length >= 2), [mode, selected])

  function switchMode(next) {
    setMode(next)
    setSelected((current) => (next === 'DIRECT' ? current.slice(0, 1) : current))
    setError('')
  }

  function toggle(userId) {
    setSelected((current) => {
      if (mode === 'DIRECT') return current[0] === userId ? [] : [userId]
      return current.includes(userId) ? current.filter((id) => id !== userId) : [...current, userId]
    })
  }

  async function submit() {
    if (!valid || busy) return
    setBusy(true)
    setError('')
    try {
      await onCreate({ type: mode, memberIds: selected, name: mode === 'GROUP' ? name.trim() || null : null })
    } catch (requestError) {
      setError(errorMessage(requestError))
      setBusy(false)
    }
  }

  return (
    <Modal
      title="Cuộc trò chuyện mới"
      description="Chỉ có thể nhắn tin với người đã là bạn bè."
      onClose={onClose}
      footer={<>
        <button type="button" className="secondary-button" onClick={onClose}>Hủy</button>
        <button type="button" className="primary-button" onClick={submit} disabled={!valid || busy}>
          {busy ? 'Đang tạo…' : mode === 'DIRECT' ? 'Bắt đầu trò chuyện' : `Tạo nhóm (${selected.length + 1})`}
        </button>
      </>}
    >
      <div className="segmented" role="tablist" aria-label="Loại cuộc trò chuyện">
        <button type="button" role="tab" aria-selected={mode === 'DIRECT'} className={mode === 'DIRECT' ? 'is-active' : ''} onClick={() => switchMode('DIRECT')}>
          <MessageSquarePlus size={16} /> Trò chuyện 1-1
        </button>
        <button type="button" role="tab" aria-selected={mode === 'GROUP'} className={mode === 'GROUP' ? 'is-active' : ''} onClick={() => switchMode('GROUP')}>
          <UsersRound size={16} /> Nhóm
        </button>
      </div>
      {mode === 'GROUP' && (
        <label className="field field--compact">
          Tên nhóm <small>(không bắt buộc)</small>
          <input value={name} onChange={(event) => setName(event.target.value)} maxLength={100} placeholder="Ví dụ: Nhóm dự án" />
        </label>
      )}
      {mode === 'GROUP' && <p className="picker-hint">Chọn ít nhất 2 người bạn · đã chọn {selected.length}</p>}
      {error && <div className="form-alert form-alert--error" role="alert">{error}</div>}
      <PeoplePicker
        people={friends}
        selected={selected}
        onToggle={toggle}
        multiple={mode === 'GROUP'}
        emptyText="Bạn chưa có bạn bè. Hãy kết bạn trong Danh bạ trước."
      />
      {!friends.length && <button type="button" className="secondary-button picker-cta" onClick={onOpenContacts}>Mở Danh bạ</button>}
    </Modal>
  )
}
