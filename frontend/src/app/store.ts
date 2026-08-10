import { configureStore } from '@reduxjs/toolkit'
import { setupListeners } from '@reduxjs/toolkit/query'
import { api } from './api'
import authReducer from '@/features/auth/authSlice'
import uiReducer from './uiSlice'

export const store = configureStore({
  reducer: {
    [api.reducerPath]: api.reducer,
    auth: authReducer,
    ui: uiReducer,
  },
  middleware: (getDefault) =>
    getDefault({
      // The API cache holds server payloads only; running the serializable check
      // over every response is measurable overhead for no benefit.
      serializableCheck: { ignoredActions: [`${api.reducerPath}/executeQuery/fulfilled`] },
    }).concat(api.middleware),
  devTools: import.meta.env.DEV,
})

// Enables refetchOnFocus / refetchOnReconnect behaviour.
setupListeners(store.dispatch)

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
