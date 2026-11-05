import { create } from 'zustand'

let nextId = 1

export const useToastStore = create((set, get) => ({
  toasts: [],
  push: (message, tone = 'info', timeout = 3_800) => {
    const id = nextId++
    set((state) => ({ toasts: [...state.toasts.slice(-3), { id, message, tone }] }))
    if (timeout) setTimeout(() => get().dismiss(id), timeout)
    return id
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((toast) => toast.id !== id) })),
}))

export const toast = {
  info: (message) => useToastStore.getState().push(message, 'info'),
  success: (message) => useToastStore.getState().push(message, 'success'),
  error: (message) => useToastStore.getState().push(message, 'error', 5_000),
}
