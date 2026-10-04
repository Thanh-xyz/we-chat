import { ArrowLeft, Ban, Check, Clock3, LoaderCircle, Search, UserPlus, Users, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar } from '../components/common/Avatar.jsx'
import { FormAlert } from '../components/auth/AuthLayout.jsx'
import { useContactStore } from '../features/contacts/contactStore.js'
import { errorMessage } from '../services/api/errors.js'

const TABS = [
  ['people', 'Mọi người'],
  ['friends', 'Bạn bè'],
  ['incoming', 'Lời mời đến'],
  ['outgoing', 'Đã gửi'],
  ['blocked', 'Đã chặn'],
]

function UserSummary({ user }) {
  return <><Avatar name={user.displayName || user.username} src={user.avatarUrl} /><span className="contact-copy"><strong>{user.displayName || user.username}</strong><small>@{user.username}{user.status ? ` · ${user.status}` : ''}</small></span></>
}

export function ContactsPage() {
  const navigate = useNavigate()
  const friends = useContactStore((state) => state.friends)
  const incoming = useContactStore((state) => state.incoming)
  const outgoing = useContactStore((state) => state.outgoing)
  const blocked = useContactStore((state) => state.blocked)
  const searchResults = useContactStore((state) => state.searchResults)
  const summary = useContactStore((state) => state.summary)
  const status = useContactStore((state) => state.status)
  const loadAll = useContactStore((state) => state.loadAll)
  const searchUsers = useContactStore((state) => state.search)
  const sendRequest = useContactStore((state) => state.sendRequest)
  const accept = useContactStore((state) => state.accept)
  const decline = useContactStore((state) => state.decline)
  const cancel = useContactStore((state) => state.cancel)
  const unfriend = useContactStore((state) => state.unfriend)
  const block = useContactStore((state) => state.block)
  const unblock = useContactStore((state) => state.unblock)
  const reset = useContactStore((state) => state.reset)
  const [tab, setTab] = useState('people')
  const [query, setQuery] = useState('')
  const [message, setMessage] = useState('')
  const [actionError, setActionError] = useState('')
  const [busyId, setBusyId] = useState(null)

  useEffect(() => {
    loadAll().catch(() => {})
    return () => reset()
  }, [loadAll, reset])

  const search = useCallback((value) => {
    const controller = new AbortController()
    const timer = setTimeout(() => searchUsers(value, { signal: controller.signal }).catch((error) => {
      if (error.code !== 'REQUEST_CANCELED') setActionError(errorMessage(error))
    }), 350)
    return () => { clearTimeout(timer); controller.abort() }
  }, [searchUsers])

  useEffect(() => search(query), [query, search])

  const rows = useMemo(() => {
    if (tab === 'friends') return friends.map((friend) => ({ type: 'friend', id: friend.userId, user: friend }))
    if (tab === 'incoming') return incoming.map((request) => ({ type: 'incoming', id: request.id, request, user: request.requester }))
    if (tab === 'outgoing') return outgoing.map((request) => ({ type: 'outgoing', id: request.id, request, user: request.receiver }))
    if (tab === 'blocked') return blocked.map((user) => ({ type: 'blocked', id: user.userId, user }))
    return searchResults.map((user) => ({ type: 'person', id: user.userId, user }))
  }, [blocked, friends, incoming, outgoing, searchResults, tab])

  async function action(id, operation) {
    setBusyId(id)
    setActionError('')
    try { await operation() } catch (error) { setActionError(errorMessage(error)) } finally { setBusyId(null) }
  }

  function relationAction(row) {
    if (row.type === 'person') {
      const relation = row.user.relationStatus
      if (relation === 'NONE') return <button className="small-action" onClick={() => action(row.id, () => sendRequest(row.id, message.trim() || null))}><UserPlus size={14} /> Kết bạn</button>
      if (relation === 'FRIEND') return <span className="relation-label"><Check size={14} /> Bạn bè</span>
      if (relation === 'INCOMING_REQUEST') return <span className="relation-label"><Clock3 size={14} /> Có lời mời đến</span>
      return <span className="relation-label"><Clock3 size={14} /> Đã gửi</span>
    }
    if (row.type === 'incoming') return <span className="contact-actions"><button className="small-action" onClick={() => action(row.id, () => accept(row.id))}><Check size={14} /> Chấp nhận</button><button className="small-action small-action--muted" onClick={() => action(row.id, () => decline(row.id))}><X size={14} /> Từ chối</button></span>
    if (row.type === 'outgoing') return <button className="small-action small-action--muted" onClick={() => action(row.id, () => cancel(row.id))}><X size={14} /> Hủy lời mời</button>
    if (row.type === 'friend') return <span className="contact-actions"><button className="small-action small-action--muted" onClick={() => action(row.id, () => unfriend(row.id))}>Bỏ kết bạn</button><button className="small-action small-action--danger" onClick={() => action(row.id, () => block(row.id, 'Blocked from Contacts'))}><Ban size={14} /> Chặn</button></span>
    return <button className="small-action" onClick={() => action(row.id, () => unblock(row.id))}>Bỏ chặn</button>
  }

  return (
    <main className="contacts-page">
      <header className="contacts-header">
        <button className="icon-button" onClick={() => navigate('/app')} aria-label="Quay lại chat"><ArrowLeft size={21} /></button>
        <span className="brand-mark"><Users size={20} /></span>
        <div><h1>Danh bạ</h1><small>{summary ? `${summary.friendCount} bạn bè` : 'Kết nối với mọi người'}</small></div>
      </header>
      <div className="contacts-body">
        <div className="contacts-toolbar">
          <label className="search-box contacts-search" htmlFor="people-search"><Search size={18} /><input id="people-search" placeholder="Tìm người dùng" value={query} onChange={(event) => setQuery(event.target.value)} /></label>
          {tab === 'people' && <input className="request-message" aria-label="Lời nhắn kết bạn" placeholder="Lời nhắn (không bắt buộc)" value={message} onChange={(event) => setMessage(event.target.value)} maxLength={255} />}
        </div>
        <nav className="contact-tabs" aria-label="Danh mục danh bạ">
          {TABS.map(([id, label]) => <button key={id} className={tab === id ? 'is-active' : ''} onClick={() => setTab(id)}>{label}{id === 'incoming' && incoming.length > 0 && <b>{incoming.length}</b>}</button>)}
        </nav>
        {actionError && <FormAlert>{actionError}</FormAlert>}
        {status === 'loading' && !friends.length ? <div className="contacts-state"><LoaderCircle className="spin" size={24} /> Đang tải danh bạ…</div> : !rows.length ? <div className="contacts-state"><Users size={32} /><strong>{tab === 'people' && !query ? 'Tìm kiếm người dùng' : 'Chưa có dữ liệu'}</strong><span>{tab === 'people' && !query ? 'Nhập tên hoặc username để bắt đầu.' : 'Khi có dữ liệu thật từ backend, dữ liệu sẽ hiển thị ở đây.'}</span></div> : <div className="contact-list">{rows.map((row) => <article className="contact-card" key={row.id}><UserSummary user={row.user} /><div>{relationAction(row)}</div>{busyId === row.id && <LoaderCircle className="contact-busy spin" size={17} />}</article>)}</div>}
      </div>
    </main>
  )
}
