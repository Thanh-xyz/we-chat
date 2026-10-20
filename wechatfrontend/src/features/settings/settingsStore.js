import { create } from 'zustand'

const THEME_STORAGE_KEY = 'wechat.theme'
const THEMES = ['light', 'dark']

function readThemePreference() {
  try {
    const storedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
    return THEMES.includes(storedTheme) ? storedTheme : 'light'
  } catch {
    return 'light'
  }
}

function applyTheme(theme) {
  if (typeof document !== 'undefined') {
    document.documentElement.dataset.theme = theme
  }
}

const initialTheme = readThemePreference()

export function initializeTheme() {
  const theme = readThemePreference()
  applyTheme(theme)
  return theme
}

export const useSettingsStore = create((set) => ({
  theme: initialTheme,
  setTheme: (theme) => {
    const nextTheme = THEMES.includes(theme) ? theme : 'light'
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
    } catch {
      // Keep the current page usable when browser storage is unavailable.
    }
    applyTheme(nextTheme)
    set({ theme: nextTheme })
  },
}))
