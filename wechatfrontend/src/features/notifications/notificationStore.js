import { create } from 'zustand'
import { notificationApi } from '../../services/api/notificationApi.js'
import { normalizeApiError } from '../../services/api/errors.js'

export const useNotificationStore = create((set) => ({
  notifications: [],
  unreadCount: 0,
  status: 'idle',
  error: null,
  open: false,

  load: async ({ signal } = {}) => {
    set({ status: 'loading', error: null })
    try {
      const data = await notificationApi.list({ signal })
      set({ notifications: data.notifications, unreadCount: data.unreadCount, status: 'success' })
      return data
    } catch (error) {
      const normalized = normalizeApiError(error)
      if (normalized.code === 'REQUEST_CANCELED') return null
      set({ status: 'error', error: normalized })
      throw normalized
    }
  },

  setOpen: (open) => set({ open }),

  handleRealtimeEvent: (event) => {
    if (Number.isFinite(event?.unreadCount)) set({ unreadCount: event.unreadCount })
    const notification = event?.payload?.notification
    if (event?.eventType === 'notification.created' && notification) {
      set((state) => ({ notifications: [notification, ...state.notifications.filter((item) => item.id !== notification.id)].slice(0, 50) }))
    }
    if (event?.eventType === 'notification.deleted' && event.notificationId) {
      set((state) => ({ notifications: state.notifications.filter((item) => item.id !== event.notificationId) }))
    }
  },

  markRead: async (notificationId) => {
    const notification = await notificationApi.markRead(notificationId)
    set((state) => ({
      notifications: state.notifications.map((item) => item.id === notificationId ? { ...item, ...notification, read: true } : item),
      unreadCount: Math.max(0, state.unreadCount - (state.notifications.find((item) => item.id === notificationId)?.read ? 0 : 1)),
    }))
    return notification
  },

  markAllRead: async () => {
    const result = await notificationApi.markAllRead()
    set((state) => ({ notifications: state.notifications.map((item) => ({ ...item, read: true })), unreadCount: result.count }))
    return result
  },

  remove: async (notificationId) => {
    await notificationApi.remove(notificationId)
    set((state) => ({
      notifications: state.notifications.filter((item) => item.id !== notificationId),
    }))
  },

  reset: () => set({ notifications: [], unreadCount: 0, status: 'idle', error: null, open: false }),
}))
