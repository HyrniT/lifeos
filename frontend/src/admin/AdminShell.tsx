import { useEffect } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Dropdown, Tag, Tooltip } from 'antd'
import {
  Activity,
  LayoutDashboard,
  LogOut,
  Moon,
  ScrollText,
  Server,
  Sun,
  Users,
} from 'lucide-react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { themeToggled } from '@/app/uiSlice'
import { signedOut } from '@/features/auth/authSlice'
import { useLogoutMutation } from '@/app/api'
import '@/components/layout/shell.css'

const NAV = [
  { to: '/', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/users', label: 'Users', icon: Users },
  { to: '/audit', label: 'Audit log', icon: ScrollText },
  { to: '/system', label: 'System', icon: Server },
]

const TITLES: Record<string, string> = {
  '/': 'Platform overview',
  '/users': 'User management',
  '/audit': 'Security audit log',
  '/system': 'System health',
}

export function AdminShell() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const theme = useAppSelector((state) => state.ui.theme)
  const user = useAppSelector((state) => state.auth.user)
  const refreshToken = useAppSelector((state) => state.auth.refreshToken)
  const [logout] = useLogoutMutation()

  const title = TITLES[location.pathname] ?? 'Admin'

  useEffect(() => {
    document.title = `${title} · LifeOS Admin`
  }, [title])

  const signOut = async () => {
    try {
      if (refreshToken) await logout({ refreshToken }).unwrap()
    } catch {
      // Local state is cleared regardless.
    }
    dispatch(signedOut())
    navigate('/login', { replace: true })
  }

  return (
    <div className="lo-shell">
      <aside className="lo-sidebar">
        <div className="lo-brand">
          <span className="lo-brand__mark">L</span>
          <span className="lo-brand__text">
            <span className="lo-brand__name">LifeOS</span>
            <span className="lo-brand__tag">admin console</span>
          </span>
        </div>

        <nav className="lo-nav" aria-label="Admin sections">
          {NAV.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `lo-nav__link${isActive ? ' is-active' : ''}`}
              >
                <span className="lo-nav__icon">
                  <Icon size={19} />
                </span>
                <span>{item.label}</span>
              </NavLink>
            )
          })}
        </nav>

        <div className="lo-sidebar__foot">
          <a
            href="/"
            className="lo-nav__link"
            target="_blank"
            rel="noopener noreferrer"
            style={{ justifyContent: 'flex-start' }}
          >
            <span className="lo-nav__icon">
              <Activity size={19} />
            </span>
            <span>Open user app ↗</span>
          </a>
        </div>
      </aside>

      <div className="lo-main">
        <header className="lo-topbar">
          <h1 className="lo-topbar__title">{title}</h1>
          <Tag style={{ marginLeft: 12 }}>ADMIN</Tag>
          <div className="lo-topbar__spacer" />

          <div className="lo-topbar__actions">
            <Tooltip title={theme === 'dark' ? 'Switch to light' : 'Switch to dark'}>
              <button
                type="button"
                className="lo-iconbtn"
                onClick={() => dispatch(themeToggled())}
                aria-label="Toggle theme"
              >
                {theme === 'dark' ? <Sun size={19} /> : <Moon size={19} />}
              </button>
            </Tooltip>

            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  {
                    key: 'who',
                    disabled: true,
                    label: (
                      <div style={{ minWidth: 170 }}>
                        <div style={{ fontWeight: 650 }}>{user?.displayName}</div>
                        <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                          {user?.email}
                        </div>
                      </div>
                    ),
                  },
                  { type: 'divider' },
                  {
                    key: 'logout',
                    icon: <LogOut size={15} />,
                    label: 'Sign out',
                    danger: true,
                    onClick: signOut,
                  },
                ],
              }}
            >
              <button type="button" className="lo-iconbtn" aria-label="Account">
                <Avatar
                  size={30}
                  style={{ background: 'var(--on-surface)', color: 'var(--surface)', fontWeight: 700 }}
                >
                  {user?.displayName?.charAt(0).toUpperCase() ?? 'A'}
                </Avatar>
              </button>
            </Dropdown>
          </div>
        </header>

        <main className="lo-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
