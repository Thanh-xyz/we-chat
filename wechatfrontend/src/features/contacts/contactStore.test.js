import { beforeEach, describe, expect, it, vi } from 'vitest'

const friendApi = vi.hoisted(() => ({
  searchUsers: vi.fn(),
  list: vi.fn(),
  incoming: vi.fn(),
  outgoing: vi.fn(),
  blocked: vi.fn(),
  summary: vi.fn(),
}))

vi.mock('../../services/api/friendApi.js', () => ({ friendApi }))

import { useContactStore } from './contactStore.js'

describe('contact store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useContactStore.getState().reset()
  })

  it('loads the real friend collections in parallel', async () => {
    friendApi.list.mockResolvedValue([{ userId: 'u1' }])
    friendApi.incoming.mockResolvedValue([])
    friendApi.outgoing.mockResolvedValue([])
    friendApi.blocked.mockResolvedValue([])
    friendApi.summary.mockResolvedValue({ friendCount: 1 })

    await useContactStore.getState().loadAll()

    expect(useContactStore.getState().friends).toEqual([{ userId: 'u1' }])
    expect(useContactStore.getState().summary.friendCount).toBe(1)
  })

  it('ignores a stale search response', async () => {
    let resolveFirst
    const first = new Promise((resolve) => { resolveFirst = resolve })
    friendApi.searchUsers.mockReturnValueOnce(first).mockResolvedValueOnce([{ userId: 'new' }])

    const firstSearch = useContactStore.getState().search('old')
    const secondSearch = useContactStore.getState().search('new')
    await secondSearch
    resolveFirst([{ userId: 'old' }])
    await firstSearch

    expect(useContactStore.getState().searchResults).toEqual([{ userId: 'new' }])
    expect(useContactStore.getState().searchStatus).toBe('results')
  })
})
