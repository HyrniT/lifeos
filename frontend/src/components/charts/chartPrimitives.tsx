import { useMemo, useState, type ReactNode } from 'react'
import { Segmented, Table, Tooltip as AntTooltip } from 'antd'
import { BarChart3, Table2, Info } from 'lucide-react'
import { useThemeVersion } from '@/app/hooks'
import { readChartTokens, seriesStyle, type ChartTokens, type SeriesStyle } from '@/theme/chartTheme'
import './charts.css'

/** Re-reads the CSS colour variables whenever the theme flips. */
export function useChartTokens(): ChartTokens {
  const version = useThemeVersion()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  return useMemo(() => readChartTokens(), [version])
}

export function useSeriesStyles(count: number): SeriesStyle[] {
  const tokens = useChartTokens()
  return useMemo(
    () => Array.from({ length: count }, (_, index) => seriesStyle(index, tokens)),
    [count, tokens],
  )
}

/* ==========================================================================
   Pattern defs — the second encoding.

   With no hue available, three grey steps is the honest ceiling for solid
   fills. Series four onwards reuse those greys with a texture, which stays
   distinguishable in greyscale print and under any colour vision deficiency.

   These live in their own hidden <svg> rather than inside the chart. Recharts'
   PieChart filters its children down to the element types it recognises and
   silently drops a bare <defs>, so the patterns never reached the document and
   every patterned slice rendered as nothing at all. SVG paint references resolve
   document-wide, so defining them once outside the chart works everywhere.
   ========================================================================== */
export function ChartPatterns({ tokens }: { tokens: ChartTokens }) {
  return (
    <svg
      width="0"
      height="0"
      aria-hidden="true"
      focusable="false"
      style={{ position: 'absolute', pointerEvents: 'none' }}
    >
      <defs>
        {tokens.categorical.map((color, index) => (
          <pattern
            key={`hatch-${index}`}
            id={`lo-pattern-hatch-${index + 3}`}
            patternUnits="userSpaceOnUse"
            width="6"
            height="6"
            patternTransform="rotate(45)"
          >
            {/* A tinted ground, not the plain surface: it keeps a patterned area
                reading as a filled region even in a thin slice. */}
            <rect width="6" height="6" fill={color} fillOpacity="0.16" />
            <line x1="0" y1="0" x2="0" y2="6" stroke={color} strokeWidth="3" />
          </pattern>
        ))}
        {tokens.categorical.map((color, index) => (
          <pattern
            key={`dots-${index}`}
            id={`lo-pattern-dots-${index + 3}`}
            patternUnits="userSpaceOnUse"
            width="6"
            height="6"
          >
            <rect width="6" height="6" fill={color} fillOpacity="0.16" />
            <circle cx="3" cy="3" r="1.7" fill={color} />
          </pattern>
        ))}
        {tokens.categorical.map((color, index) => (
          <pattern
            key={`cross-${index}`}
            id={`lo-pattern-cross-${index + 3}`}
            patternUnits="userSpaceOnUse"
            width="7"
            height="7"
          >
            <rect width="7" height="7" fill={color} fillOpacity="0.14" />
            <path d="M0 0 L7 7 M7 0 L0 7" stroke={color} strokeWidth="1.4" />
          </pattern>
        ))}
      </defs>
    </svg>
  )
}

/* ==========================================================================
   Legend — present whenever there are two or more series, so identity is never
   carried by colour alone.
   ========================================================================== */
export interface LegendItem {
  label: string
  style: SeriesStyle
  value?: string
}

export function ChartLegend({ items }: { items: LegendItem[] }) {
  if (items.length < 2) return null
  return (
    <ul className="lo-chart__legend" role="list">
      {items.map((item) => (
        <li key={item.label} className="lo-chart__legend-item">
          <span
            className="lo-chart__swatch"
            style={{
              background: item.style.pattern === 'solid' ? item.style.color : 'transparent',
              borderColor: item.style.color,
              backgroundImage:
                item.style.pattern === 'hatch'
                  ? `repeating-linear-gradient(45deg, ${item.style.color} 0 2px, transparent 2px 5px)`
                  : item.style.pattern === 'dots'
                    ? `radial-gradient(${item.style.color} 1.4px, transparent 1.5px)`
                    : item.style.pattern === 'cross'
                      ? `repeating-linear-gradient(45deg, ${item.style.color} 0 1px, transparent 1px 4px), repeating-linear-gradient(-45deg, ${item.style.color} 0 1px, transparent 1px 4px)`
                      : undefined,
              backgroundSize: item.style.pattern === 'dots' ? '5px 5px' : undefined,
            }}
            aria-hidden
          />
          <span className="lo-chart__legend-label">{item.label}</span>
          {item.value && <span className="lo-chart__legend-value tabular">{item.value}</span>}
        </li>
      ))}
    </ul>
  )
}

/* ==========================================================================
   Tooltip — every chart ships one. Values wear ink tokens; the swatch beside
   them carries the series identity.
   ========================================================================== */
export interface TooltipRow {
  label: string
  value: string
  color?: string
  emphasis?: boolean
}

export function ChartTooltipCard({ title, rows }: { title: string; rows: TooltipRow[] }) {
  return (
    <div className="lo-chart__tooltip" role="tooltip">
      <div className="lo-chart__tooltip-title">{title}</div>
      {rows.map((row) => (
        <div
          key={row.label}
          className={`lo-chart__tooltip-row${row.emphasis ? ' is-emphasis' : ''}`}
        >
          {row.color && (
            <span className="lo-chart__tooltip-dot" style={{ background: row.color }} aria-hidden />
          )}
          <span className="lo-chart__tooltip-label">{row.label}</span>
          <span className="lo-chart__tooltip-value tabular">{row.value}</span>
        </div>
      ))}
    </div>
  )
}

/* ==========================================================================
   Frame — title, optional legend, and the table view that every chart owes its
   keyboard and screen-reader users.
   ========================================================================== */
export interface TableColumn {
  key: string
  title: string
  align?: 'left' | 'right'
}

export interface ChartFrameProps {
  title: string
  subtitle?: string
  hint?: string
  legend?: LegendItem[]
  actions?: ReactNode
  /** Sentence describing the chart for assistive tech; required. */
  summary: string
  tableColumns?: TableColumn[]
  tableRows?: readonly object[]
  height?: number
  children: ReactNode
  empty?: boolean
  emptyLabel?: string
}

export function ChartFrame({
  title,
  subtitle,
  hint,
  legend,
  actions,
  summary,
  tableColumns,
  tableRows,
  height = 280,
  children,
  empty,
  emptyLabel = 'Nothing to show yet',
}: ChartFrameProps) {
  const [view, setView] = useState<'chart' | 'table'>('chart')
  const tokens = useChartTokens()
  const canShowTable = Boolean(tableColumns?.length && tableRows?.length)

  return (
    <section className="lo-chart" aria-label={title}>
      {/* Mounted per frame. Several copies share the same ids, which is harmless
          because the definitions are identical and theme-driven together. */}
      <ChartPatterns tokens={tokens} />
      <header className="lo-chart__header">
        <div className="lo-chart__heading">
          <h3 className="lo-chart__title">
            {title}
            {hint && (
              <AntTooltip title={hint}>
                <span className="lo-chart__hint" tabIndex={0} role="note" aria-label={hint}>
                  <Info size={13} strokeWidth={2} />
                </span>
              </AntTooltip>
            )}
          </h3>
          {subtitle && <p className="lo-chart__subtitle">{subtitle}</p>}
        </div>
        <div className="lo-chart__actions">
          {actions}
          {canShowTable && (
            <Segmented
              size="small"
              value={view}
              onChange={(value) => setView(value as 'chart' | 'table')}
              options={[
                { value: 'chart', icon: <BarChart3 size={14} />, title: 'Chart view' },
                { value: 'table', icon: <Table2 size={14} />, title: 'Table view' },
              ]}
            />
          )}
        </div>
      </header>

      <p className="sr-only">{summary}</p>

      {empty ? (
        <div className="lo-chart__empty" style={{ height }}>
          <span>{emptyLabel}</span>
        </div>
      ) : view === 'chart' ? (
        <>
          <div className="lo-chart__canvas" style={{ height }}>
            {children}
          </div>
          {legend && <ChartLegend items={legend} />}
        </>
      ) : (
        <div className="lo-chart__table lo-scroll-x">
          <Table
            size="small"
            pagination={tableRows && tableRows.length > 12 ? { pageSize: 12 } : false}
            rowKey={(_row, index) => String(index)}
            dataSource={tableRows as Record<string, unknown>[]}
            columns={(tableColumns ?? []).map((column) => ({
              key: column.key,
              dataIndex: column.key,
              title: column.title,
              align: column.align ?? 'left',
              className: column.align === 'right' ? 'tabular' : undefined,
            }))}
          />
        </div>
      )}
    </section>
  )
}

/* ==========================================================================
   Formatters
   ========================================================================== */
export function formatCurrency(value: number, currency = 'USD', compact = false): string {
  const abs = Math.abs(value)
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      notation: compact && abs >= 10_000 ? 'compact' : 'standard',
      maximumFractionDigits: compact && abs >= 10_000 ? 1 : abs >= 1000 ? 0 : 2,
    }).format(value)
  } catch {
    return `${currency} ${value.toFixed(2)}`
  }
}

export function formatCompact(value: number): string {
  return new Intl.NumberFormat(undefined, {
    notation: Math.abs(value) >= 10_000 ? 'compact' : 'standard',
    maximumFractionDigits: 1,
  }).format(value)
}

export function formatPercent(ratio: number, digits = 0): string {
  return `${(ratio * 100).toFixed(digits)}%`
}

export function formatMinutes(minutes: number): string {
  // Round to whole minutes *first*: rounding the remainder on its own turns
  // 119.7 into "1h 60m", and 59.6 into "60m" instead of "1h".
  const total = Math.round(minutes)
  if (total < 60) return `${total}m`
  const hours = Math.floor(total / 60)
  const rest = total % 60
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`
}

export function formatShortDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}
