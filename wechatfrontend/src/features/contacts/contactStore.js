import { create } from 'zustand'
import { friendApi } from '../../services/api/friendApi.js'
import { normalizeApiError } from '../../services/api/errors.js'

export const useContactStore = create((set, get) => ({
  friends: [],
  incoming: [],
  outgoing: [],
  blocked: [],
  searchResults: [],
  searchStatus: 'idle',
  searchError: null,
  summary: null,
  status: 'idle',
  error: null,
  searchRequestId: 0,

  loadAll: async ({ signal } = {}) => {
    set({ status: 'loading', error: null })
    try {
      const [friends, incoming, outgoing, blocked, summary] = await Promise.all([
        friendApi.list({ signal }),
        friendApi.incoming(),
        friendApi.outgoing(),
        friendApi.blocked(),
        friendApi.summary(),
      ])
      set({ friends, incoming, outgoing, blocked, summary, status: 'success' })
    } catch (error) {
      const normalized = normalizeApiError(error)
      set({ status: 'error', error: normalized })
      throw normalized
    }
  },

  search: async (query, { signal } = {}) => {
    const requestId = get().searchRequestId + 1
    set({ searchRequestId: requestId, searchError: null })
    if (!query.trim()) {
      set({ searchResults: [], searchStatus: 'idle' })
      return []
    }
    set({ searchStatus: 'loading' })
    try {
      const results = await friendApi.searchUsers(query.trim(), { signal })
      if (get().searchRequestId === requestId) set({ searchResults: results, searchStatus: results.length ? 'results' : 'empty' })
      return results
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (get().searchRequestId === requestId && normalized.code !== 'REQUEST_CANCELED') set({ searchStatus: 'error', searchError: normalized })
      throw normalized
    }
  },

  sendRequest: async (receiverId, message) => {
    const request = await friendApi.sendRequest(receiverId, message)
    set((state) => ({ outgoing: [request, ...state.outgoing] }))
    return request
  },
  accept: async (requestId) => {
    const request = await friendApi.acceptRequest(requestId)
    set((state) => ({ incoming: state.incoming.filter((item) => item.id !== requestId), friends: state.friends }))
    await get().loadAll()
    return request
  },
  decline: async (requestId) => {
    const request = await friendApi.declineRequest(requestId)
    set((state) => ({ incoming: state.incoming.filter((item) => item.id !== requestId) }))
    return request
  },
  cancel: async (requestId) => {
    const request = await friendApi.cancelRequest(requestId)
    set((state) => ({ outgoing: state.outgoing.filter((item) => item.id !== requestId) }))
    return request
  },
  unfriend: async (userId) => {
    await friendApi.unfriend(userId)
    set((state) => ({ friends: state.friends.filter((item) => item.userId !== userId) }))
  },
  block: async (userId, reason) => {
    const result = await friendApi.block(userId, reason)
    await get().loadAll()
    return result
  },
  unblock: async (userId) => {
    await friendApi.unblock(userId)
    set((state) => ({ blocked: state.blocked.filter((item) => item.userId !== userId) }))
  },
  reset: () => set({ friends: [], incoming: [], outgoing: [], blocked: [], searchResults: [], searchStatus: 'idle', searchError: null, summary: null, status: 'idle', error: null, searchRequestId: 0 }),
}))
