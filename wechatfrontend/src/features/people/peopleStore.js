import { create } from 'zustand'
import { friendApi } from '../../services/api/friendApi.js'

function toPerson(source) {
  if (!source) return null
  const userId = source.userId || source.id
  if (!userId) return null
  return {
    userId,
    username: source.username || '',
    displayName: source.displayName || source.username || '',
    avatarUrl: source.avatarUrl || null,
    status: source.status || source.presenceStatus || null,
  }
}

/**
 * Local directory of people the current user can identify (self, friends, search hits).
 * The conversation API only returns member ids, so names are resolved from this cache.
 */
export const usePeopleStore = create((set, get) => ({
  byId: {},
  friendIds: [],
  status: 'idle',

  remember: (sources = []) => {
    const people = sources.map(toPerson).filter(Boolean)
    if (!people.length) return
    set((state) => {
      const byId = { ...state.byId }
      people.forEach((person) => { byId[person.userId] = { ...byId[person.userId], ...person } })
      return { byId }
    })
  },

  setPresence: (userId, status) => {
    if (!userId || !status) return
    set((state) => (state.byId[userId]
      ? { byId: { ...state.byId, [userId]: { ...state.byId[userId], status } } }
      : state))
  },

  loadFriends: async ({ signal } = {}) => {
    set({ status: 'loading' })
    try {
      const friends = await friendApi.list({ signal })
      get().remember(friends)
      set({ friendIds: friends.map((friend) => friend.userId), status: 'success' })
      return friends
    } catch (error) {
      set({ status: 'error' })
      throw error
    }
  },

  reset: () => set({ byId: {}, friendIds: [], status: 'idle' }),
}))

export function personName(person, fallback = 'Thành viên') {
  return person?.displayName || person?.username || fallback
}
