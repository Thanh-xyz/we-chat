import { Check, Search } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Avatar } from './Avatar.jsx'
import { isOnline } from '../../utils/chat.js'

/** Selectable list of known people (usually friends). */
export function PeoplePicker({ people, selected, onToggle, multiple = true, excludeIds = [], emptyText = 'Chưa có bạn bè nào để chọn.' }) {
  const [query, setQuery] = useState('')
  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return people
      .filter((person) => !excludeIds.includes(person.userId))
      .filter((person) => !q || `${person.displayName} ${person.username}`.toLowerCase().includes(q))
  }, [excludeIds, people, query])

  return (
    <div className="people-picker">
      <label className="search-box people-picker-search">
        <Search size={17} />
        <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm theo tên hoặc username" aria-label="Tìm bạn bè" />
      </label>
      {!visible.length ? (
        <p className="people-picker-empty">{people.length ? 'Không tìm thấy kết quả phù hợp.' : emptyText}</p>
      ) : (
        <ul className="people-picker-list" role="listbox" aria-multiselectable={multiple}>
          {visible.map((person) => {
            const isSelected = selected.includes(person.userId)
            return (
              <li key={person.userId}>
                <button
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className={`people-picker-item ${isSelected ? 'is-selected' : ''}`}
                  onClick={() => onToggle(person.userId)}
                >
                  <Avatar name={person.displayName || person.username} src={person.avatarUrl} online={isOnline(person.status)} />
                  <span className="people-picker-copy"><strong>{person.displayName || person.username}</strong><small>@{person.username}</small></span>
                  <span className={`picker-check ${multiple ? '' : 'picker-check--radio'}`}>{isSelected && <Check size={13} strokeWidth={3} />}</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
