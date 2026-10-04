import { beforeEach, describe, expect, it, vi } from 'vitest'

const notificationApi = vi.hoisted(() => ({ markRead: vi.fn(), markAllRead: vi.fn() }))
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

  it('marks one notification read without underflowing unread count', async () => {
    useNotificationStore.setState({ notifications: [{ id: 'n1', read: false }], unreadCount: 1 })
    notificationApi.markRead.mockResolvedValue({ id: 'n1', read: true })

    await useNotificationStore.getState().markRead('n1')

    expect(useNotificationStore.getState().notifications[0].read).toBe(true)
    expect(useNotificationStore.getState().unreadCount).toBe(0)
  })
})
