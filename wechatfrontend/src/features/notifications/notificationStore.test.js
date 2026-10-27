import { beforeEach, describe, expect, it, vi } from 'vitest'

const notificationApi = vi.hoisted(() => ({ list: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() }))
vi.mock('../../services/api/notificationApi.js', () => ({ notificationApi }))

import { useNotificationStore } from './notificationStore.js'

describe('notification store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useNotificationStore.getState().reset()
  })

  it('reconciles realtime notification counts and de-duplicates notifications', () => {
    useNotificationStore.getState().handleRealtimeEvent({
      eventType: 'notification.created',
      unreadCount: 2,
      payload: { notification: { id: 'n1', title: 'New message', read: false } },
    })
    useNotificationStore.getState().handleRealtimeEvent({
      eventType: 'notification.created',
      unreadCount: 2,
      payload: { notification: { id: 'n1', title: 'New message', read: false } },
    })

    expect(useNotificationStore.getState().unreadCount).toBe(2)
    expect(useNotificationStore.getState().notifications).toHaveLength(1)
  })

  it('replaces persisted notification state when reloaded', async () => {
    notificationApi.list.mockResolvedValue({
      notifications: [{ id: 'n2', title: 'Persisted notification', read: false }],
      unreadCount: 3,
    })

    await useNotificationStore.getState().load()

    expect(useNotificationStore.getState().notifications).toEqual([
      { id: 'n2', title: 'Persisted notification', read: false },
    ])
    expect(useNotificationStore.getState().unreadCount).toBe(3)
  })

  it('retries a stale reload instead of overwriting a concurrent realtime event', async () => {
    let resolveFirstLoad
    notificationApi.list
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirstLoad = resolve }))
      .mockResolvedValueOnce({
        notifications: [
          { id: 'n2', title: 'Realtime notification', read: false },
          { id: 'n1', title: 'Persisted notification', read: false },
        ],
        unreadCount: 2,
      })

    const loadPromise = useNotificationStore.getState().load()
    useNotificationStore.getState().handleRealtimeEvent({
      eventType: 'notification.created',
      unreadCount: 2,
      payload: { notification: { id: 'n2', title: 'Realtime notification', read: false } },
    })
    resolveFirstLoad({
      notifications: [{ id: 'n1', title: 'Persisted notification', read: false }],
      unreadCount: 1,
    })

    await loadPromise

    expect(notificationApi.list).toHaveBeenCalledTimes(2)
    expect(useNotificationStore.getState().notifications.map(({ id }) => id)).toEqual(['n2', 'n1'])
    expect(useNotificationStore.getState().unreadCount).toBe(2)
  })

  it('ignores a previous-session load after reset and a new load', async () => {
    let resolvePreviousLoad
    notificationApi.list
      .mockReturnValueOnce(new Promise((resolve) => { resolvePreviousLoad = resolve }))
      .mockResolvedValueOnce({
        notifications: [{ id: 'new', title: 'New session', read: false }],
        unreadCount: 1,
      })

    const previousLoad = useNotificationStore.getState().load()
    useNotificationStore.getState().reset()
    await useNotificationStore.getState().load()
    resolvePreviousLoad({
      notifications: [{ id: 'old', title: 'Previous session', read: false }],
      unreadCount: 1,
    })
    await previousLoad

    expect(useNotificationStore.getState().notifications.map(({ id }) => id)).toEqual(['new'])
  })

  it('marks one notification read without underflowing unread count', async () => {
    useNotificationStore.setState({ notifications: [{ id: 'n1', read: false }], unreadCount: 1 })
    notificationApi.markRead.mockResolvedValue({ id: 'n1', read: true })

    await useNotificationStore.getState().markRead('n1')

    expect(useNotificationStore.getState().notifications[0].read).toBe(true)
    expect(useNotificationStore.getState().unreadCount).toBe(0)
  })
})
