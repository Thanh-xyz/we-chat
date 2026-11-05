import { LoaderCircle, Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { messageApi } from '../../services/api/messageApi.js'
import { errorMessage } from '../../services/api/errors.js'
import { formatConversationTime } from '../../utils/chat.js'

function highlight(text, query) {
  if (!text || !query) return text
  const index = text.toLowerCase().indexOf(query.toLowerCase())
  if (index < 0) return text
  return <>{text.slice(0, index)}<mark>{text.slice(index, index + query.length)}</mark>{text.slice(index + query.length)}</>
}

export function MessageSearch({ conversationId, currentUserId, people, onJump, onClose }) {
  const [query, setQuery] = useState('')
  const [result, setState] = useState({ status: 'idle', items: [], error: '', query: '' })

  useEffect(() => {
    const q = query.trim()
    if (q.length < 2) return undefined
    const controller = new AbortController()
    const timer = setTimeout(async () => {
      setState((current) => ({ ...current, status: 'loading', error: '' }))
      try {
        const page = await messageApi.search(conversationId, q, { signal: controller.signal })
        setState({ status: 'success', items: page.items || [], error: '', query: q })
      } catch (error) {
        if (error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError') return
        setState({ status: 'error', items: [], error: errorMessage(error), query: q })
      }
    }, 300)
    return () => { clearTimeout(timer); controller.abort() }
  }, [conversationId, query])

  const q = query.trim()
  const state = q.length < 2 ? { status: 'idle', items: [], error: '' } : result

  return (
    <aside className="side-panel" aria-label="Tìm kiếm tin nhắn">
      <header className="side-panel-header">
        <h2>Tìm trong cuộc trò chuyện</h2>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Đóng tìm kiếm"><X size={18} /></button>
      </header>
      <label className="search-box side-panel-search">
        <Search size={17} />
        <input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Nhập từ khóa (tối thiểu 2 ký tự)" aria-label="Từ khóa tìm kiếm tin nhắn" />
        {state.status === 'loading' && <LoaderCircle className="spin" size={16} />}
      </label>
      <div className="side-panel-scroll">
        {state.status === 'error' && <p className="panel-state panel-state--error">{state.error}</p>}
        {state.status === 'success' && !state.items.length && <p className="panel-state">Không tìm thấy tin nhắn nào cho “{q}”.</p>}
        {state.status === 'idle' && <p className="panel-state">Kết quả sẽ hiển thị tại đây.</p>}
        {state.items.length > 0 && <p className="search-count">{state.items.length} kết quả</p>}
        <ul className="search-results">
          {state.items.map((message) => {
            const sender = message.senderId === currentUserId ? 'Bạn' : (people[message.senderId]?.displayName || people[message.senderId]?.username || 'Thành viên')
            return (
              <li key={message.id}>
                <button type="button" onClick={() => onJump(message)}>
                  <span className="search-result-meta"><strong>{sender}</strong><time>{formatConversationTime(message.createdAt)}</time></span>
                  <span className="search-result-text">{highlight(message.content, q)}</span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </aside>
  )
}
