import { create } from 'zustand'
import { notificationApi } from '../../services/api/notificationApi.js'
import { normalizeApiError } from '../../services/api/errors.js'

const MAX_LOAD_ATTEMPTS = 2

export const useNotificationStore = create((set, get) => ({
  notifications: [],
  unreadCount: 0,
  status: 'idle',
  error: null,
  open: false,
  notificationRevision: 0,
  notificationRequestId: 0,

  load: async ({ signal } = {}) => {
    const requestId = get().notificationRequestId + 1
    set({ notificationRequestId: requestId, status: 'loading', error: null })
    try {
      let data
      for (let attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt += 1) {
        const revision = get().notificationRevision
        data = await notificationApi.list({ signal })
        if (get().notificationRequestId !== requestId) return data
        if (get().notificationRevision !== revision) continue
        set({ notifications: data.notifications, unreadCount: data.unreadCount, status: 'success' })
        return data
      }
      set({ status: 'success' })
      return data
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (get().notificationRequestId !== requestId) return null
      if (normalized.code === 'REQUEST_CANCELED') return null
      set({ status: 'error', error: normalized })
      throw normalized
    }
  },

  setOpen: (open) => set({ open }),

  handleRealtimeEvent: (event) => {
    set((state) => {
      const notification = event?.payload?.notification
      const hasUnreadCount = Number.isFinite(event?.unreadCount)
      const created = event?.eventType === 'notification.created' && notification
      const deleted = event?.eventType === 'notification.deleted' && event.notificationId
      if (!hasUnreadCount && !created && !deleted) return {}

      let notifications = state.notifications
      if (created) {
        notifications = [notification, ...notifications.filter((item) => item.id !== notification.id)].slice(0, 50)
      } else if (deleted) {
        notifications = notifications.filter((item) => item.id !== event.notificationId)
      }
      return {
        notifications,
        unreadCount: hasUnreadCount ? event.unreadCount : state.unreadCount,
        notificationRevision: state.notificationRevision + 1,
      }
    })
  },

  markRead: async (notificationId) => {
    const notification = await notificationApi.markRead(notificationId)
    set((state) => ({
      notifications: state.notifications.map((item) => item.id === notificationId ? { ...item, ...notification, read: true } : item),
      unreadCount: Math.max(0, state.unreadCount - (state.notifications.find((item) => item.id === notificationId)?.read ? 0 : 1)),
      notificationRevision: state.notificationRevision + 1,
    }))
    return notification
  },

  markAllRead: async () => {
    const result = await notificationApi.markAllRead()
    set((state) => ({
      notifications: state.notifications.map((item) => ({ ...item, read: true })),
      unreadCount: result.count,
      notificationRevision: state.notificationRevision + 1,
    }))
    return result
  },

  remove: async (notificationId) => {
    await notificationApi.remove(notificationId)
    set((state) => ({
      notifications: state.notifications.filter((item) => item.id !== notificationId),
      notificationRevision: state.notificationRevision + 1,
    }))
  },

  reset: () => set((state) => ({
    notifications: [],
    unreadCount: 0,
    status: 'idle',
    error: null,
    open: false,
    notificationRevision: 0,
    notificationRequestId: state.notificationRequestId + 1,
  })),
}))
