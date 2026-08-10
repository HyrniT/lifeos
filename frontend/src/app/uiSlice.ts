import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { ThemeMode } from '@/theme/antdTheme'

const THEME_KEY = 'lifeos.theme'
const ADMIN_THEME_KEY = 'lifeos.admin.theme'
const DENSITY_KEY = 'lifeos.density'

export type Density = 'comfortable' | 'compact'

interface UiState {
  theme: ThemeMode
  density: Density
  sidebarCollapsed: boolean
  /** Set once at boot so the admin console can persist its own theme choice. */
  isAdminApp: boolean
}

function readTheme(key: string, fallback: ThemeMode): ThemeMode {
  try {
    const stored = localStorage.getItem(key)
    if (stored === 'light' || stored === 'dark') return stored
    if (key === THEME_KEY) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    }
  } catch {
    /* storage blocked */
  }
  return fallback
}

const initialState: UiState = {
  theme: readTheme(THEME_KEY, 'light'),
  density: (() => {
    try {
      return (localStorage.getItem(DENSITY_KEY) as Density) || 'comfortable'
    } catch {
      return 'comfortable'
    }
  })(),
  sidebarCollapsed: false,
  isAdminApp: false,
}

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    appKindSet(state, action: PayloadAction<boolean>) {
      state.isAdminApp = action.payload
      state.theme = readTheme(action.payload ? ADMIN_THEME_KEY : THEME_KEY, action.payload ? 'dark' : 'light')
      document.documentElement.setAttribute('data-theme', state.theme)
    },
    themeToggled(state) {
      state.theme = state.theme === 'dark' ? 'light' : 'dark'
      document.documentElement.setAttribute('data-theme', state.theme)
      try {
        localStorage.setItem(state.isAdminApp ? ADMIN_THEME_KEY : THEME_KEY, state.theme)
      } catch {
        /* storage blocked */
      }
    },
    themeSet(state, action: PayloadAction<ThemeMode>) {
      state.theme = action.payload
      document.documentElement.setAttribute('data-theme', state.theme)
      try {
        localStorage.setItem(state.isAdminApp ? ADMIN_THEME_KEY : THEME_KEY, state.theme)
      } catch {
        /* storage blocked */
      }
    },
    densitySet(state, action: PayloadAction<Density>) {
      state.density = action.payload
      try {
        localStorage.setItem(DENSITY_KEY, action.payload)
      } catch {
        /* storage blocked */
      }
    },
    sidebarToggled(state) {
      state.sidebarCollapsed = !state.sidebarCollapsed
    },
    sidebarSet(state, action: PayloadAction<boolean>) {
      state.sidebarCollapsed = action.payload
    },
  },
})

export const { appKindSet, themeToggled, themeSet, densitySet, sidebarToggled, sidebarSet } =
  uiSlice.actions

export default uiSlice.reducer
