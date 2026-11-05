import {
  Archive, ArchiveRestore, BellOff, BellRing, Crown, Download, File, Image, LoaderCircle, LogOut,
  Mic, Pencil, Pin, PinOff, Search, UserMinus, UserPlus, X,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Avatar } from '../common/Avatar.jsx'
import { ConfirmDialog, Modal } from '../common/Modal.jsx'
import { PeoplePicker } from '../common/PeoplePicker.jsx'
import { useChatStore } from '../../features/conversations/chatStore.js'
import { toast } from '../../features/ui/toastStore.js'
import { mediaApi } from '../../services/api/mediaApi.js'
import { errorMessage } from '../../services/api/errors.js'
import { formatFileSize } from '../../utils/attachments.js'
import { conversationAvatar, conversationName, formatConversationTime, isOnline } from '../../utils/chat.js'

const MEDIA_TABS = [
  ['IMAGE', 'Ảnh', Image],
  ['FILE', 'Tệp', File],
  ['VOICE', 'Ghi âm', Mic],
]

function saveBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || 'download'
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1_000)
}

function MediaThumb({ item }) {
  const [url, setUrl] = useState('')
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    let objectUrl = ''
    const controller = new AbortController()
    mediaApi.downloadBlob(item.id, { signal: controller.signal })
      .then((blob) => { objectUrl = URL.createObjectURL(blob); setUrl(objectUrl) })
      .catch(() => {})
    return () => { controller.abort(); if (objectUrl) URL.revokeObjectURL(objectUrl) }
  }, [item.id])
  return (
    <a className="media-thumb" href={url || undefined} target="_blank" rel="noreferrer" aria-label={item.fileName}>
      {url && !failed ? <img src={url} alt={item.fileName} loading="lazy" onError={() => setFailed(true)} /> : <span className="media-thumb-placeholder"><Image size={18} /></span>}
    </a>
  )
}

function MediaSection({ conversationId }) {
  const [tab, setTab] = useState('IMAGE')
  const [state, setState] = useState({ status: 'loading', items: [], hasMore: false })
  const [stats, setStats] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    mediaApi.stats(conversationId, { signal: controller.signal }).then(setStats).catch(() => {})
    return () => controller.abort()
  }, [conversationId])

  function selectTab(id) {
    if (id === tab) return
    setState({ status: 'loading', items: [], hasMore: false })
    setTab(id)
  }

  useEffect(() => {
    const controller = new AbortController()
    mediaApi.list(conversationId, { category: tab, limit: 24, signal: controller.signal })
      .then((page) => setState({ status: 'success', items: page.items || [], hasMore: page.hasMore }))
      .catch((error) => {
        if (controller.signal.aborted) return
        setState({ status: 'error', items: [], hasMore: false, error: errorMessage(error) })
      })
    return () => controller.abort()
  }, [conversationId, tab])

  async function download(item) {
    try {
      saveBlob(await mediaApi.downloadBlob(item.id), item.fileName)
    } catch (error) {
      toast.error(errorMessage(error))
    }
  }

  const counts = { IMAGE: stats?.totalImages, FILE: stats?.totalFiles, VOICE: stats?.totalVoices }

  return (
    <section className="info-section">
      <div className="info-section-title">
        <h3>File phương tiện</h3>
        {stats && <small>{formatFileSize(stats.totalStorageBytes || 0)}</small>}
      </div>
      <div className="segmented segmented--small" role="tablist">
        {MEDIA_TABS.map(([id, label, Icon]) => (
          <button type="button" role="tab" key={id} aria-selected={tab === id} className={tab === id ? 'is-active' : ''} onClick={() => selectTab(id)}>
            <Icon size={14} /> {label}{Number.isFinite(counts[id]) && <small>{counts[id]}</small>}
          </button>
        ))}
      </div>
      {state.status === 'loading' && <div className="panel-state"><LoaderCircle className="spin" size={18} /></div>}
      {state.status === 'error' && <p className="panel-state panel-state--error">{state.error}</p>}
      {state.status === 'success' && !state.items.length && <p className="panel-state">Chưa có {tab === 'IMAGE' ? 'hình ảnh' : tab === 'FILE' ? 'tệp' : 'ghi âm'} nào.</p>}
      {tab === 'IMAGE' && state.items.length > 0 && (
        <div className="media-grid">{state.items.map((item) => <MediaThumb key={item.id} item={item} />)}</div>
      )}
      {tab !== 'IMAGE' && state.items.length > 0 && (
        <ul className="media-files">
          {state.items.map((item) => (
            <li key={item.id}>
              <span className="attachment-icon">{tab === 'VOICE' ? <Mic size={16} /> : <File size={16} />}</span>
              <span className="media-file-copy">
                <strong>{item.fileName}</strong>
                <small>{formatFileSize(item.fileSize || 0)} · {formatConversationTime(item.createdAt)}{item.uploadedBy?.displayName ? ` · ${item.uploadedBy.displayName}` : ''}</small>
              </span>
              <button type="button" className="icon-button" onClick={() => download(item)} aria-label={`Tải ${item.fileName}`}><Download size={15} /></button>
            </li>
          ))}
        </ul>
      )}
      {state.hasMore && <p className="muted-note">Đang hiển thị 24 mục mới nhất.</p>}
    </section>
  )
}

function EditGroupDialog({ conversation, onClose }) {
  const updateConversation = useChatStore((state) => state.updateConversation)
  const [name, setName] = useState(conversation.name || '')
  const [avatarUrl, setAvatarUrl] = useState(conversation.avatarUrl || '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    if (!name.trim() && !avatarUrl.trim()) return setError('Hãy nhập tên nhóm hoặc ảnh đại diện.')
    setBusy(true)
    try {
      await updateConversation(conversation.id, { name: name.trim() || null, avatarUrl: avatarUrl.trim() || null })
      toast.success('Đã cập nhật nhóm')
      onClose()
    } catch (requestError) {
      setError(errorMessage(requestError))
      setBusy(false)
    }
  }

  return (
    <Modal title="Chỉnh sửa nhóm" onClose={onClose} size="small" footer={<>
      <button type="button" className="secondary-button" onClick={onClose}>Hủy</button>
      <button type="submit" form="edit-group-form" className="primary-button" disabled={busy}>{busy ? 'Đang lưu…' : 'Lưu'}</button>
    </>}>
      <form id="edit-group-form" className="auth-form auth-form--compact" onSubmit={submit}>
        {error && <div className="form-alert form-alert--error" role="alert">{error}</div>}
        <label className="field field--compact">Tên nhóm<input value={name} maxLength={100} onChange={(event) => setName(event.target.value)} /></label>
        <label className="field field--compact">Ảnh đại diện (URL)<input value={avatarUrl} maxLength={2048} onChange={(event) => setAvatarUrl(event.target.value)} placeholder="https://…" /></label>
      </form>
    </Modal>
  )
}

function AddMembersDialog({ conversation, friends, onClose }) {
  const addMembers = useChatStore((state) => state.addMembers)
  const [selected, setSelected] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit() {
    setBusy(true)
    try {
      await addMembers(conversation.id, selected)
      toast.success(`Đã thêm ${selected.length} thành viên`)
      onClose()
    } catch (requestError) {
      setError(errorMessage(requestError))
      setBusy(false)
    }
  }

  return (
    <Modal title="Thêm thành viên" onClose={onClose} footer={<>
      <button type="button" className="secondary-button" onClick={onClose}>Hủy</button>
      <button type="button" className="primary-button" onClick={submit} disabled={busy || !selected.length}>{busy ? 'Đang thêm…' : `Thêm (${selected.length})`}</button>
    </>}>
      {error && <div className="form-alert form-alert--error" role="alert">{error}</div>}
      <PeoplePicker
        people={friends}
        selected={selected}
        excludeIds={conversation.memberIds || []}
        onToggle={(id) => setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id])}
        emptyText="Tất cả bạn bè đã ở trong nhóm."
      />
    </Modal>
  )
}

export function ConversationInfoPanel({ conversation, currentUserId, people, friends, onClose, onOpenSearch, onLeft }) {
  const togglePin = useChatStore((state) => state.togglePin)
  const toggleMute = useChatStore((state) => state.toggleMute)
  const toggleArchive = useChatStore((state) => state.toggleArchive)
  const removeMember = useChatStore((state) => state.removeMember)
  const leaveConversation = useChatStore((state) => state.leaveConversation)
  const [dialog, setDialog] = useState(null)
  const [busy, setBusy] = useState(false)

  const options = { currentUserId, people }
  const name = conversationName(conversation, options)
  const isGroup = conversation.type === 'GROUP'
  const isOwner = conversation.createdBy === currentUserId
  const members = useMemo(() => (conversation.memberIds || []).map((id) => ({
    id,
    person: people[id],
    me: id === currentUserId,
    owner: id === conversation.createdBy,
  })).sort((left, right) => Number(right.owner) - Number(left.owner) || Number(right.me) - Number(left.me)), [conversation, currentUserId, people])

  async function run(operation, success) {
    try {
      await operation()
      if (success) toast.success(success)
    } catch (error) {
      toast.error(errorMessage(error))
    }
  }

  async function confirmDialog() {
    setBusy(true)
    try {
      if (dialog.type === 'leave') {
        await leaveConversation(conversation.id)
        toast.success('Bạn đã rời nhóm')
        onLeft?.()
      } else if (dialog.type === 'remove') {
        await removeMember(conversation.id, dialog.userId)
        toast.success('Đã xóa thành viên khỏi nhóm')
      }
      setDialog(null)
    } catch (error) {
      toast.error(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  const quickActions = [
    { key: 'search', label: 'Tìm kiếm', Icon: Search, run: onOpenSearch },
    { key: 'pin', label: conversation.pinnedAt ? 'Bỏ ghim' : 'Ghim', Icon: conversation.pinnedAt ? PinOff : Pin, active: Boolean(conversation.pinnedAt), run: () => run(() => togglePin(conversation)) },
    { key: 'mute', label: conversation.muted ? 'Bật TB' : 'Tắt TB', Icon: conversation.muted ? BellRing : BellOff, active: conversation.muted, run: () => run(() => toggleMute(conversation)) },
    { key: 'archive', label: conversation.archivedAt ? 'Bỏ lưu trữ' : 'Lưu trữ', Icon: conversation.archivedAt ? ArchiveRestore : Archive, active: Boolean(conversation.archivedAt), run: () => run(() => toggleArchive(conversation), conversation.archivedAt ? 'Đã bỏ lưu trữ' : 'Đã lưu trữ cuộc trò chuyện') },
  ]

  return (
    <aside className="side-panel info-panel" aria-label="Thông tin cuộc trò chuyện">
      <header className="side-panel-header">
        <h2>Thông tin</h2>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Đóng thông tin"><X size={18} /></button>
      </header>
      <div className="side-panel-scroll">
        <div className="info-hero">
          <Avatar name={name} src={conversationAvatar(conversation, options)} size="xlarge" group={isGroup} />
          <h3>{name}</h3>
          <p>{isGroup ? `Nhóm · ${conversation.memberIds?.length || 0} thành viên` : 'Trò chuyện trực tiếp'}</p>
          {isGroup && <button type="button" className="text-button" onClick={() => setDialog({ type: 'edit' })}><Pencil size={13} /> Chỉnh sửa nhóm</button>}
        </div>

        <div className="quick-actions">
          {quickActions.map(({ key, label, Icon, active, run: action }) => (
            <button type="button" key={key} className={active ? 'is-active' : ''} onClick={action}>
              <span><Icon size={18} /></span>{label}
            </button>
          ))}
        </div>

        {isGroup && (
          <section className="info-section">
            <div className="info-section-title">
              <h3>Thành viên</h3>
              <button type="button" className="text-button" onClick={() => setDialog({ type: 'add' })}><UserPlus size={14} /> Thêm</button>
            </div>
            <ul className="member-list">
              {members.map(({ id, person, me, owner }) => {
                const label = me ? 'Bạn' : person?.displayName || person?.username || 'Thành viên'
                return (
                  <li key={id}>
                    <Avatar name={person?.displayName || person?.username || label} src={person?.avatarUrl} size="small" online={!me && isOnline(person?.status)} />
                    <span className="member-copy">
                      <strong>{label}</strong>
                      <small>{owner ? <><Crown size={11} /> Trưởng nhóm</> : person?.username ? `@${person.username}` : 'Chưa kết bạn'}</small>
                    </span>
                    {isOwner && !me && (
                      <button type="button" className="icon-button" onClick={() => setDialog({ type: 'remove', userId: id, label })} aria-label={`Xóa ${label} khỏi nhóm`} title="Xóa khỏi nhóm"><UserMinus size={15} /></button>
                    )}
                  </li>
                )
              })}
            </ul>
          </section>
        )}

        <MediaSection conversationId={conversation.id} />

        {isGroup && (
          <section className="info-section">
            <button type="button" className="danger-row" onClick={() => setDialog({ type: 'leave' })}><LogOut size={16} /> Rời nhóm</button>
          </section>
        )}
      </div>

      {dialog?.type === 'edit' && <EditGroupDialog conversation={conversation} onClose={() => setDialog(null)} />}
      {dialog?.type === 'add' && <AddMembersDialog conversation={conversation} friends={friends} onClose={() => setDialog(null)} />}
      {dialog?.type === 'leave' && (
        <ConfirmDialog title="Rời nhóm?" description="Bạn sẽ không nhận được tin nhắn mới từ nhóm này nữa." confirmLabel="Rời nhóm" danger busy={busy} onConfirm={confirmDialog} onClose={() => setDialog(null)} />
      )}
      {dialog?.type === 'remove' && (
        <ConfirmDialog title={`Xóa ${dialog.label}?`} description="Thành viên này sẽ bị xóa khỏi nhóm." confirmLabel="Xóa khỏi nhóm" danger busy={busy} onConfirm={confirmDialog} onClose={() => setDialog(null)} />
      )}
    </aside>
  )
}
