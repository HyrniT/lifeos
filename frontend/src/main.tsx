import { StrictMode, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { Provider } from 'react-redux'
import { App as AntApp, ConfigProvider } from 'antd'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { store } from './app/store'
import { useAppDispatch, useAppSelector } from './app/hooks'
import { appKindSet } from './app/uiSlice'
import { selectIsAuthenticated } from './features/auth/authSlice'
import { buildTheme } from './theme/antdTheme'
import { AppShell } from './components/layout/AppShell'
import { LoginPage } from './features/auth/LoginPage'
import { RegisterPage } from './features/auth/RegisterPage'
import { GoogleCallbackPage } from './features/auth/GoogleCallbackPage'
import { lazyPage } from './components/ui'
import './theme/tokens.css'

const DashboardPage = lazyPage(() => import('./features/dashboard/DashboardPage'), 'DashboardPage')
const HabitsPage = lazyPage(() => import('./features/habits/HabitsPage'), 'HabitsPage')
const MoneyPage = lazyPage(() => import('./features/money/MoneyPage'), 'MoneyPage')
const PlanningPage = lazyPage(() => import('./features/planning/PlanningPage'), 'PlanningPage')
const GoalsPage = lazyPage(() => import('./features/planning/GoalsPage'), 'GoalsPage')
const ProjectsPage = lazyPage(() => import('./features/planning/ProjectsPage'), 'ProjectsPage')
const FocusPage = lazyPage(() => import('./features/focus/FocusPage'), 'FocusPage')
const AnalyticsPage = lazyPage(() => import('./features/analytics/AnalyticsPage'), 'AnalyticsPage')
const AchievementsPage = lazyPage(
  () => import('./features/achievements/AchievementsPage'),
  'AchievementsPage',
)
const SettingsPage = lazyPage(() => import('./features/settings/SettingsPage'), 'SettingsPage')

function RequireAuth({ children }: { children: React.ReactNode }) {
  const authenticated = useAppSelector(selectIsAuthenticated)
  const location = useLocation()
  if (!authenticated) {
    // `state.from` lets the login page send the user back where they were headed.
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return <>{children}</>
}

function RedirectIfAuthenticated({ children }: { children: React.ReactNode }) {
  const authenticated = useAppSelector(selectIsAuthenticated)
  return authenticated ? <Navigate to="/" replace /> : <>{children}</>
}

function ThemedApp() {
  const dispatch = useAppDispatch()
  const theme = useAppSelector((state) => state.ui.theme)

  useEffect(() => {
    dispatch(appKindSet(false))
  }, [dispatch])

  return (
    <ConfigProvider theme={buildTheme(theme)}>
      <AntApp>
        <BrowserRouter>
          <Routes>
            <Route
              path="/login"
              element={
                <RedirectIfAuthenticated>
                  <LoginPage />
                </RedirectIfAuthenticated>
              }
            />
            <Route
              path="/register"
              element={
                <RedirectIfAuthenticated>
                  <RegisterPage />
                </RedirectIfAuthenticated>
              }
            />
            <Route path="/auth/google/callback" element={<GoogleCallbackPage />} />

            <Route
              element={
                <RequireAuth>
                  <AppShell />
                </RequireAuth>
              }
            >
              <Route index element={<DashboardPage />} />
              <Route path="habits" element={<HabitsPage />} />
              <Route path="money" element={<MoneyPage />} />
              <Route path="planning" element={<PlanningPage />} />
              <Route path="goals" element={<GoalsPage />} />
              <Route path="projects" element={<ProjectsPage />} />
              <Route path="focus" element={<FocusPage />} />
              <Route path="analytics" element={<AnalyticsPage />} />
              <Route path="achievements" element={<AchievementsPage />} />
              <Route path="settings" element={<SettingsPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <ThemedApp />
    </Provider>
  </StrictMode>,
)
