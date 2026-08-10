import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { TokenPair, UserView } from '@/types'

const STORAGE_KEY = 'lifeos.session'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: UserView | null
  /** Held between the two legs of a 2FA sign-in; never persisted. */
  twoFactorChallenge: string | null
  initialised: boolean
}

interface PersistedSession {
  accessToken: string
  refreshToken: string
  user: UserView
}

function loadSession(): PersistedSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PersistedSession
    if (!parsed.accessToken || !parsed.refreshToken) return null
    return parsed
  } catch {
    return null
  }
}

function saveSession(session: PersistedSession | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Private-browsing mode: the session simply does not survive a reload.
  }
}

const restored = loadSession()

const initialState: AuthState = {
  accessToken: restored?.accessToken ?? null,
  refreshToken: restored?.refreshToken ?? null,
  user: restored?.user ?? null,
  twoFactorChallenge: null,
  initialised: true,
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    credentialsUpdated(state, action: PayloadAction<TokenPair>) {
      const { accessToken, refreshToken, user } = action.payload
      state.accessToken = accessToken
      state.refreshToken = refreshToken
      state.user = user
      state.twoFactorChallenge = null
      saveSession({ accessToken, refreshToken, user })
    },
    twoFactorRequested(state, action: PayloadAction<string>) {
      state.twoFactorChallenge = action.payload
    },
    twoFactorCancelled(state) {
      state.twoFactorChallenge = null
    },
    profileUpdated(state, action: PayloadAction<UserView>) {
      state.user = action.payload
      if (state.accessToken && state.refreshToken) {
        saveSession({
          accessToken: state.accessToken,
          refreshToken: state.refreshToken,
          user: action.payload,
        })
      }
    },
    signedOut(state) {
      state.accessToken = null
      state.refreshToken = null
      state.user = null
      state.twoFactorChallenge = null
      saveSession(null)
    },
  },
})

export const {
  credentialsUpdated,
  twoFactorRequested,
  twoFactorCancelled,
  profileUpdated,
  signedOut,
} = authSlice.actions

export default authSlice.reducer

export const selectIsAuthenticated = (state: { auth: AuthState }) => Boolean(state.auth.accessToken)
export const selectCurrentUser = (state: { auth: AuthState }) => state.auth.user
export const selectIsAdmin = (state: { auth: AuthState }) =>
  Boolean(state.auth.user?.roles?.includes('ADMIN'))
