import { beforeEach, describe, expect, it, vi } from 'vitest'

async function loadSettingsStore(storedTheme) {
  vi.resetModules()
  window.localStorage.clear()
  delete document.documentElement.dataset.theme
  if (storedTheme) window.localStorage.setItem('wechat.theme', storedTheme)
  return import('./settingsStore.js')
}

describe('settingsStore', () => {
  beforeEach(() => {
    window.localStorage.clear()
    delete document.documentElement.dataset.theme
  })

  it('defaults to light and initializes the root theme before rendering', async () => {
    const { initializeTheme, useSettingsStore } = await loadSettingsStore()

    expect(useSettingsStore.getState().theme).toBe('light')
    expect(initializeTheme()).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('loads and keeps the saved theme after a page reload', async () => {
    const firstLoad = await loadSettingsStore('dark')

    expect(firstLoad.initializeTheme()).toBe('dark')
    expect(firstLoad.useSettingsStore.getState().theme).toBe('dark')

    const reloaded = await loadSettingsStore('dark')
    expect(reloaded.initializeTheme()).toBe('dark')
    expect(reloaded.useSettingsStore.getState().theme).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('updates the theme and persists it when the user changes it', async () => {
    const { useSettingsStore } = await loadSettingsStore()

    useSettingsStore.getState().setTheme('dark')

    expect(useSettingsStore.getState().theme).toBe('dark')
    expect(window.localStorage.getItem('wechat.theme')).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('falls back to light for an unsupported saved value', async () => {
    const { initializeTheme, useSettingsStore } = await loadSettingsStore('blue')

    expect(useSettingsStore.getState().theme).toBe('light')
    expect(initializeTheme()).toBe('light')
    expect(document.documentElement.dataset.theme).toBe('light')
  })
})
