import {
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from '@reduxjs/toolkit/query'
import { Mutex } from './mutex'
import { credentialsUpdated, signedOut } from '@/features/auth/authSlice'
import type { RootState } from './store'
import type { TokenPair } from '@/types'

export const API_BASE_URL: string = (import.meta.env.VITE_API_BASE_URL as string) || '/api'

const rawBaseQuery = fetchBaseQuery({
  baseUrl: API_BASE_URL,
  prepareHeaders: (headers, { getState }) => {
    const token = (getState() as RootState).auth.accessToken
    if (token) headers.set('Authorization', `Bearer ${token}`)
    headers.set('Accept', 'application/json')
    return headers
  },
})

// One refresh at a time. Without this, a dashboard that fires eight queries at
// once would fire eight refreshes on expiry — and since refresh tokens rotate,
// seven of them would be replays and the backend would kill the whole session.
const refreshMutex = new Mutex()

export const baseQueryWithReauth: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  await refreshMutex.waitForUnlock()
  let result = await rawBaseQuery(args, api, extraOptions)

  if (result.error?.status !== 401) return result

  const state = api.getState() as RootState
  const refreshToken = state.auth.refreshToken
  if (!refreshToken) {
    api.dispatch(signedOut())
    return result
  }

  if (refreshMutex.isLocked()) {
    // Another call is already refreshing; wait for it and retry with the new token.
    await refreshMutex.waitForUnlock()
    return rawBaseQuery(args, api, extraOptions)
  }

  const release = await refreshMutex.acquire()
  try {
    const refreshResult = await rawBaseQuery(
      { url: '/auth/refresh', method: 'POST', body: { refreshToken } },
      api,
      extraOptions,
    )

    if (refreshResult.data) {
      api.dispatch(credentialsUpdated(refreshResult.data as TokenPair))
      result = await rawBaseQuery(args, api, extraOptions)
    } else {
      api.dispatch(signedOut())
    }
  } finally {
    release()
  }

  return result
}

/** Pulls a human-readable message out of whatever shape the error arrived in. */
export function errorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (!error) return fallback
  const err = error as FetchBaseQueryError & { data?: { message?: string; code?: string } }

  if (err.status === 'FETCH_ERROR') {
    return 'Cannot reach the server. Check that the backend is running.'
  }
  if (err.status === 429 || err.data?.code === 'ACCOUNT_LOCKED' || err.data?.code === 'IP_THROTTLED') {
    return err.data?.message ?? 'Too many attempts. Please wait a moment and try again.'
  }
  if (err.data?.message) return err.data.message
  if (typeof err.status === 'number') return `Request failed (${err.status})`
  return fallback
}
