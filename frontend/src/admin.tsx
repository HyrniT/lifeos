import { StrictMode, useEffect, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { App as AntApp, ConfigProvider, Result } from 'antd'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { store } from './app/store'
import { useAppDispatch, useAppSelector } from './app/hooks'
import { appKindSet } from './app/uiSlice'
import { selectIsAdmin, selectIsAuthenticated } from './features/auth/authSlice'
import { buildTheme } from './theme/antdTheme'
import { AdminShell } from './admin/AdminShell'
import { AdminLoginPage } from './admin/AdminLoginPage'
import { AdminOverviewPage } from './admin/AdminOverviewPage'
import { AdminUsersPage } from './admin/AdminUsersPage'
import { AdminAuditPage } from './admin/AdminAuditPage'
import { AdminSystemPage } from './admin/AdminSystemPage'
import './theme/tokens.css'

/**
 * The admin console is a separate build target served from /admin.html. It shares
 * the store, API client and design system with the user app but ships none of its
 * screens — and it checks the ADMIN role itself, on top of the gateway's check and
 * the service's @PreAuthorize.
 */
function RequireAdmin({ children }: { children: ReactNode }) {
  const authenticated = useAppSelector(selectIsAuthenticated)
  const isAdmin = useAppSelector(selectIsAdmin)

  if (!authenticated) return <Navigate to="/login" replace />
  if (!isAdmin) {
    return (
      <Result
        status="403"
        title="Administrator access required"
        subTitle="This account is signed in, but it does not hold the ADMIN role."
        extra={
          <a href="/" style={{ fontWeight: 600 }}>
            Go to the user app
          </a>
        }
      />
    )
  }
  return <>{children}</>
}

function ThemedAdminApp() {
  const dispatch = useAppDispatch()
  const theme = useAppSelector((state) => state.ui.theme)

  useEffect(() => {
    dispatch(appKindSet(true))
  }, [dispatch])

  return (
    <ConfigProvider theme={buildTheme(theme)}>
      <AntApp>
        {/* The console is served from /admin/. Deep links need a rewrite rule on
            whatever serves the build — see infra/nginx/web.conf, vercel.json and
            public/_redirects, plus the dev middleware in vite.config.ts. */}
        <BrowserRouter basename="/admin">
          <Routes>
            <Route path="/login" element={<AdminLoginPage />} />
            <Route
              element={
                <RequireAdmin>
                  <AdminShell />
                </RequireAdmin>
              }
            >
              <Route index element={<AdminOverviewPage />} />
              <Route path="users" element={<AdminUsersPage />} />
              <Route path="audit" element={<AdminAuditPage />} />
              <Route path="system" element={<AdminSystemPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  )
}

createRoot(document.getElementById('admin-root')!).render(
  <StrictMode>
    <Provider store={store}>
      <ThemedAdminApp />
    </Provider>
  </StrictMode>,
)
