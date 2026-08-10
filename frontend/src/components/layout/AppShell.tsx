import { useEffect, useMemo, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar, Badge, Drawer, Dropdown, Empty, List, Tooltip } from 'antd'
import { motion } from 'framer-motion'
import {
  Bell,
  CalendarCheck,
  ChartNoAxesCombined,
  ChevronsLeft,
  ChevronsRight,
  FolderKanban,
  LayoutGrid,
  ListTodo,
  LogOut,
  Menu as MenuIcon,
  Moon,
  Settings,
  Sun,
  Target,
  Timer,
  Trophy,
  Wallet,
} from 'lucide-react'
import { useAppDispatch, useAppSelector, useIsMobile } from '@/app/hooks'
import { sidebarToggled, themeToggled } from '@/app/uiSlice'
import { signedOut } from '@/features/auth/authSlice'
import {
  useGameStatsQuery,
  useLogoutMutation,
  useMarkAllNotificationsReadMutation,
  useMarkNotificationReadMutation,
  useNotificationsQuery,
} from '@/app/api'
import { NotificationStream } from '@/features/notifications/NotificationStream'
import { DynamicIcon } from '@/components/ui'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import './shell.css'

// Relative timestamps in the notification list ("3 minutes ago").
dayjs.extend(relativeTime)

interface NavItem {
  to: string
  label: string
  icon: typeof LayoutGrid
  end?: boolean
}

const PRIMARY_NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutGrid, end: true },
  { to: '/habits', label: 'Habits', icon: CalendarCheck },
  { to: '/money', label: 'Money', icon: Wallet },
  { to: '/planning', label: 'Tasks', icon: ListTodo },
  { to: '/goals', label: 'Goals', icon: Target },
  { to: '/projects', label: 'Projects', icon: FolderKanban },
  { to: '/focus', label: 'Focus', icon: Timer },
]

const SECONDARY_NAV: NavItem[] = [
  { to: '/analytics', label: 'Analytics', icon: ChartNoAxesCombined },
  { to: '/achievements', label: 'Achievements', icon: Trophy },
  { to: '/settings', label: 'Settings', icon: Settings },
]

const MOBILE_NAV: NavItem[] = [
  { to: '/', label: 'Home', icon: LayoutGrid, end: true },
  { to: '/habits', label: 'Habits', icon: CalendarCheck },
  { to: '/money', label: 'Money', icon: Wallet },
  { to: '/planning', label: 'Tasks', icon: ListTodo },
  { to: '/analytics', label: 'Stats', icon: ChartNoAxesCombined },
]

const TITLES: Record<string, string> = {
  '/': 'Today',
  '/habits': 'Habits',
  '/money': 'Money',
  '/planning': 'Tasks',
  '/goals': 'Goals',
  '/projects': 'Projects',
  '/focus': 'Focus',
  '/analytics': 'Analytics',
  '/achievements': 'Achievements',
  '/settings': 'Settings',
}

export function AppShell() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const location = useLocation()
  const isMobile = useIsMobile()

  const collapsed = useAppSelector((state) => state.ui.sidebarCollapsed)
  const theme = useAppSelector((state) => state.ui.theme)
  const user = useAppSelector((state) => state.auth.user)
  const refreshToken = useAppSelector((state) => state.auth.refreshToken)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [notificationsOpen, setNotificationsOpen] = useState(false)

  const { data: stats } = useGameStatsQuery()
  const { data: notifications = [] } = useNotificationsQuery(undefined, { pollingInterval: 120_000 })
  const [markAllRead] = useMarkAllNotificationsReadMutation()
  const [markRead] = useMarkNotificationReadMutation()
  const [logout] = useLogoutMutation()

  const unread = notifications.filter((n) => !n.read).length

  const title = useMemo(() => {
    const exact = TITLES[location.pathname]
    if (exact) return exact
    const base = `/${location.pathname.split('/')[1] ?? ''}`
    return TITLES[base] ?? 'LifeOS'
  }, [location.pathname])

  useEffect(() => {
    document.title = `${title} · LifeOS`
  }, [title])

  // Route change closes the mobile drawer; otherwise it stays open over the new page.
  useEffect(() => {
    setDrawerOpen(false)
  }, [location.pathname])

  const handleSignOut = async () => {
    try {
      if (refreshToken) await logout({ refreshToken }).unwrap()
    } catch {
      // The local session is cleared either way — a failed call must not trap
      // the user in a signed-in state.
    }
    dispatch(signedOut())
    navigate('/login', { replace: true })
  }

  const renderNav = (items: NavItem[], showLabels: boolean) =>
    items.map((item) => {
      const Icon = item.icon
      return (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) => `lo-nav__link${isActive ? ' is-active' : ''}`}
          title={showLabels ? undefined : item.label}
        >
          <span className="lo-nav__icon">
            <Icon size={19} strokeWidth={2} />
          </span>
          {showLabels && <span>{item.label}</span>}
        </NavLink>
      )
    })

  const sidebarBody = (showLabels: boolean) => (
    <>
      <NavLink to="/" className="lo-brand">
        <span className="lo-brand__mark">L</span>
        {showLabels && (
          <span className="lo-brand__text">
            <span className="lo-brand__name">LifeOS</span>
            <span className="lo-brand__tag">habits · money · plans</span>
          </span>
        )}
      </NavLink>

      <nav className="lo-nav" aria-label="Main">
        {renderNav(PRIMARY_NAV, showLabels)}
        {showLabels && <div className="lo-nav__section">Insights</div>}
        {renderNav(SECONDARY_NAV, showLabels)}
      </nav>

      <div className="lo-sidebar__foot">
        {showLabels && stats && (
          <div className="lo-level">
            <span className="lo-level__badge">{stats.level}</span>
            <div className="lo-level__meta">
              <div className="lo-level__title">
                {stats.xpIntoLevel}/{stats.xpForNextLevel} XP
              </div>
              <div className="lo-level__bar">
                <motion.div
                  className="lo-level__fill"
                  initial={{ width: 0 }}
                  animate={{ width: `${Math.round(stats.levelProgress * 100)}%` }}
                  transition={{ duration: 0.6, ease: [0.2, 0, 0, 1] }}
                />
              </div>
            </div>
          </div>
        )}

        {!isMobile && (
          <button
            type="button"
            className="lo-iconbtn"
            onClick={() => dispatch(sidebarToggled())}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            style={{ alignSelf: showLabels ? 'flex-end' : 'center' }}
          >
            {collapsed ? <ChevronsRight size={18} /> : <ChevronsLeft size={18} />}
          </button>
        )}
      </div>
    </>
  )

  return (
    <div className="lo-shell">
      <NotificationStream />

      {!isMobile && (
        <aside className={`lo-sidebar${collapsed ? ' lo-sidebar--collapsed' : ''}`}>
          {sidebarBody(!collapsed)}
        </aside>
      )}

      <Drawer
        placement="left"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={280}
        closable={false}
        styles={{ body: { padding: '16px 12px', display: 'flex', flexDirection: 'column' } }}
      >
        {sidebarBody(true)}
      </Drawer>

      <div className="lo-main">
        <header className="lo-topbar">
          {isMobile && (
            <button
              type="button"
              className="lo-iconbtn"
              onClick={() => setDrawerOpen(true)}
              aria-label="Open navigation"
            >
              <MenuIcon size={20} />
            </button>
          )}

          <h1 className="lo-topbar__title">{title}</h1>
          <div className="lo-topbar__spacer" />

          <div className="lo-topbar__actions">
            <Tooltip title={theme === 'dark' ? 'Switch to light' : 'Switch to dark'}>
              <button
                type="button"
                className="lo-iconbtn"
                onClick={() => dispatch(themeToggled())}
                aria-label="Toggle colour theme"
              >
                {theme === 'dark' ? <Sun size={19} /> : <Moon size={19} />}
              </button>
            </Tooltip>

            <button
              type="button"
              className="lo-iconbtn"
              onClick={() => setNotificationsOpen(true)}
              aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}
            >
              <Bell size={19} />
              {unread > 0 && <span className="lo-iconbtn__dot">{unread > 9 ? '9+' : unread}</span>}
            </button>

            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  {
                    key: 'profile',
                    label: (
                      <div style={{ padding: '4px 0', minWidth: 180 }}>
                        <div style={{ fontWeight: 650 }}>{user?.displayName}</div>
                        <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                          {user?.email}
                        </div>
                      </div>
                    ),
                    disabled: true,
                  },
                  { type: 'divider' },
                  {
                    key: 'settings',
                    icon: <Settings size={15} />,
                    label: 'Settings',
                    onClick: () => navigate('/settings'),
                  },
                  ...(user?.roles?.includes('ADMIN')
                    ? [
                        {
                          key: 'admin',
                          icon: <LayoutGrid size={15} />,
                          label: 'Admin console',
                          onClick: () => window.open('/admin/', '_blank', 'noopener'),
                        },
                      ]
                    : []),
                  { type: 'divider' as const },
                  {
                    key: 'logout',
                    icon: <LogOut size={15} />,
                    label: 'Sign out',
                    danger: true,
                    onClick: handleSignOut,
                  },
                ],
              }}
            >
              <button type="button" className="lo-iconbtn" aria-label="Account menu">
                <Avatar
                  size={30}
                  src={user?.avatarUrl ?? undefined}
                  style={{
                    background: 'var(--on-surface)',
                    color: 'var(--surface)',
                    fontWeight: 700,
                    fontSize: 13,
                  }}
                >
                  {user?.displayName?.charAt(0).toUpperCase() ?? '?'}
                </Avatar>
              </button>
            </Dropdown>
          </div>
        </header>

        <main className="lo-content">
          <Outlet />
        </main>
      </div>

      {isMobile && (
        <nav className="lo-bottomnav" aria-label="Main">
          {MOBILE_NAV.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `lo-bottomnav__link${isActive ? ' is-active' : ''}`}
              >
                <Icon size={19} strokeWidth={2} />
                <span>{item.label}</span>
              </NavLink>
            )
          })}
        </nav>
      )}

      <Drawer
        title="Notifications"
        placement="right"
        width={400}
        open={notificationsOpen}
        onClose={() => setNotificationsOpen(false)}
        extra={
          unread > 0 && (
            <button
              type="button"
              onClick={() => markAllRead()}
              style={{
                border: 'none',
                background: 'transparent',
                color: 'var(--on-surface)',
                cursor: 'pointer',
                fontWeight: 600,
                fontSize: 13,
              }}
            >
              Mark all read
            </button>
          )
        }
      >
        {notifications.length === 0 ? (
          <Empty description="Nothing here yet" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <List
            dataSource={notifications}
            renderItem={(item) => (
              <List.Item
                style={{
                  opacity: item.read ? 0.55 : 1,
                  paddingInline: 0,
                  cursor: item.deepLink ? 'pointer' : 'default',
                }}
                onClick={() => {
                  // Reading it and acting on it are the same gesture; making the
                  // user dismiss separately just leaves a stale unread badge.
                  if (!item.read) markRead(item.id)
                  if (item.deepLink) {
                    setNotificationsOpen(false)
                    navigate(item.deepLink)
                  }
                }}
              >
                <List.Item.Meta
                  avatar={
                    <Badge dot={!item.read} offset={[-2, 4]}>
                      <span
                        style={{
                          display: 'grid',
                          placeItems: 'center',
                          width: 34,
                          height: 34,
                          borderRadius: 10,
                          background: 'var(--surface-container)',
                        }}
                      >
                        <DynamicIcon name={item.icon} size={16} />
                      </span>
                    </Badge>
                  }
                  title={<span style={{ fontSize: 14, fontWeight: 650 }}>{item.title}</span>}
                  description={
                    <>
                      <span style={{ fontSize: 13, color: 'var(--on-surface-variant)' }}>
                        {item.body}
                      </span>
                      <div style={{ fontSize: 11, color: 'var(--on-surface-faint)', marginTop: 2 }}>
                        {dayjs(item.createdAt).fromNow()}
                      </div>
                    </>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Drawer>
    </div>
  )
}
