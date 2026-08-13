import { Suspense, lazy, memo, useMemo, type ComponentType, type ReactNode } from 'react'
import { Skeleton, Spin } from 'antd'
import { motion } from 'framer-motion'
import * as Lucide from 'lucide-react'
import type { LucideProps } from 'lucide-react'

/* ==========================================================================
   Dynamic icon

   Habit and category icons are stored as kebab-case names ("heart-pulse"), so
   the icon set has to be resolvable at runtime. Lucide's named exports are
   PascalCase, hence the conversion.
   ========================================================================== */
const iconCache = new Map<string, ComponentType<LucideProps>>()

function toPascalCase(name: string): string {
  return name
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join('')
}

export const DynamicIcon = memo(function DynamicIcon({
  name,
  size = 18,
  strokeWidth = 2,
  ...rest
}: { name?: string | null } & LucideProps) {
  const Icon = useMemo(() => {
    const key = name ?? 'circle'
    if (iconCache.has(key)) return iconCache.get(key)!
    const pascal = toPascalCase(key)
    const resolved =
      (Lucide as unknown as Record<string, ComponentType<LucideProps>>)[pascal] ?? Lucide.Circle
    iconCache.set(key, resolved)
    return resolved
  }, [name])

  return <Icon size={size} strokeWidth={strokeWidth} {...rest} />
})

/* ==========================================================================
   Page header
   ========================================================================== */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string
  subtitle?: string
  actions?: ReactNode
}) {
  return (
    <div className="lo-page-head">
      <div>
        <h2 className="lo-page-head__title">{title}</h2>
        {subtitle && <p className="lo-page-head__subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="lo-page-head__actions">{actions}</div>}
    </div>
  )
}

/* ==========================================================================
   Empty state — always offers the next action rather than just saying "empty".
   ========================================================================== */
export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: [0.2, 0, 0, 1] }}
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        padding: '56px 24px',
        textAlign: 'center',
        border: '1px dashed var(--outline-variant)',
        borderRadius: 'var(--radius-lg)',
        background: 'var(--surface-container-low)',
      }}
    >
      {icon && (
        <span
          style={{
            display: 'grid',
            placeItems: 'center',
            width: 52,
            height: 52,
            borderRadius: 16,
            background: 'var(--surface-container-high)',
            color: 'var(--on-surface-variant)',
          }}
        >
          {icon}
        </span>
      )}
      <div>
        <div style={{ fontSize: 16, fontWeight: 700, letterSpacing: '-0.01em' }}>{title}</div>
        {description && (
          <p
            style={{
              margin: '6px auto 0',
              maxWidth: '42ch',
              color: 'var(--on-surface-variant)',
              fontSize: 14,
            }}
          >
            {description}
          </p>
        )}
      </div>
      {action}
    </motion.div>
  )
}

/* ==========================================================================
   Loading
   ========================================================================== */
export function PanelSkeleton({ rows = 4, height }: { rows?: number; height?: number }) {
  return (
    <div className="lo-panel" style={height ? { height } : undefined}>
      <Skeleton active paragraph={{ rows }} />
    </div>
  )
}

export function PageLoader({ label = 'Loading' }: { label?: string }) {
  return (
    <div style={{ display: 'grid', placeItems: 'center', minHeight: '50vh', gap: 12 }}>
      <Spin size="large" />
      <span style={{ color: 'var(--on-surface-variant)', fontSize: 13 }}>{label}…</span>
    </div>
  )
}

/* ==========================================================================
   Motion helpers — one shared easing so the whole app moves the same way.
   ========================================================================== */
export { MoneyInput } from './MoneyInput'

export const EASE = [0.2, 0, 0, 1] as const

export function FadeIn({
  children,
  delay = 0,
  y = 10,
}: {
  children: ReactNode
  delay?: number
  y?: number
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, delay, ease: EASE }}
    >
      {children}
    </motion.div>
  )
}

export function StaggerList({ children }: { children: ReactNode }) {
  return (
    <motion.div
      initial="hidden"
      animate="show"
      variants={{
        hidden: {},
        show: { transition: { staggerChildren: 0.045 } },
      }}
    >
      {children}
    </motion.div>
  )
}

export function StaggerItem({ children }: { children: ReactNode }) {
  return (
    <motion.div
      variants={{
        hidden: { opacity: 0, y: 12 },
        show: { opacity: 1, y: 0, transition: { duration: 0.32, ease: EASE } },
      }}
    >
      {children}
    </motion.div>
  )
}

/* ==========================================================================
   Section wrapper
   ========================================================================== */
export function Section({
  title,
  description,
  actions,
  children,
}: {
  title?: string
  description?: string
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="lo-section">
      {(title || actions) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'space-between',
            gap: 16,
            marginBottom: 16,
            flexWrap: 'wrap',
          }}
        >
          <div>
            {title && (
              <h3
                style={{
                  margin: 0,
                  fontSize: 'var(--title-lg)',
                  fontWeight: 700,
                  letterSpacing: '-0.02em',
                }}
              >
                {title}
              </h3>
            )}
            {description && (
              <p
                style={{
                  margin: '2px 0 0',
                  color: 'var(--on-surface-variant)',
                  fontSize: 'var(--body-sm)',
                }}
              >
                {description}
              </p>
            )}
          </div>
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

/**
 * Wraps a lazily-loaded route in a consistent fallback.
 *
 * The pages are named exports rather than default ones, so the module is mapped
 * to the `{ default }` shape `React.lazy` expects.
 */
export function lazyPage(
  loader: () => Promise<Record<string, unknown>>,
  name: string,
): ComponentType {
  const Component = lazy(async () => {
    const module = await loader()
    return { default: module[name] as ComponentType }
  })

  return function LazyPage() {
    return (
      <Suspense fallback={<PageLoader />}>
        <Component />
      </Suspense>
    )
  }
}
