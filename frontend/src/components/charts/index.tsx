import { memo, useMemo, type ReactNode } from 'react'
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Pie,
  PieChart,
  Radar,
  RadarChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ArrowDownRight, ArrowRight, ArrowUpRight } from 'lucide-react'
import {
  ChartFrame,
  ChartPatterns,
  ChartTooltipCard,
  formatCompact,
  useChartTokens,
  useSeriesStyles,
  type ChartFrameProps,
  type LegendItem,
  type TooltipRow,
} from './chartPrimitives'
import { sequentialStep } from '@/theme/chartTheme'
import './charts.css'

export * from './chartPrimitives'

type FrameProps = Omit<ChartFrameProps, 'children'>

export interface SeriesDef {
  key: string
  label: string
  format?: (value: number) => string
}

interface XYProps extends Omit<FrameProps, 'legend'> {
  /**
   * Deliberately `object[]` rather than `Record<string, unknown>[]`: the callers
   * pass typed API rows (TrendPoint, CashFlowPoint…), and an interface without an
   * index signature is not assignable to a Record. Recharts itself types this as
   * `any[]`.
   */
  data: readonly object[]
  xKey: string
  series: SeriesDef[]
  xFormatter?: (value: string) => string
  yFormatter?: (value: number) => string
  /** Draws a dashed zero line — worth it on any net/delta chart. */
  showZeroLine?: boolean
}

/* ==========================================================================
   Trend — line or area over time.

   One y-axis, always. Two measures at different scales get two charts, never a
   second axis: a dual-axis chart lets you place the crossover wherever you like,
   which means the reader learns nothing they can trust.
   ========================================================================== */
export const TrendChart = memo(function TrendChart({
  data,
  xKey,
  series,
  xFormatter,
  yFormatter = formatCompact,
  showZeroLine,
  variant = 'line',
  ...frame
}: XYProps & { variant?: 'line' | 'area' }) {
  const tokens = useChartTokens()
  const styles = useSeriesStyles(series.length)

  const legend: LegendItem[] = series.map((definition, index) => ({
    label: definition.label,
    style: styles[index],
  }))

  const rows = data as Record<string, unknown>[]
  const empty = !rows.length || rows.every((row) => series.every((s) => !Number(row[s.key])))

  const Chart = variant === 'area' ? AreaChart : LineChart

  return (
    <ChartFrame {...frame} legend={legend} empty={empty}>
      <ResponsiveContainer width="100%" height="100%">
        <Chart data={rows} margin={{ top: 8, right: 12, bottom: 4, left: -12 }}>
          {variant === 'area' && (
            <defs>
              {styles.map((style, index) => (
                <linearGradient
                  key={index}
                  id={`lo-area-${index}`}
                  x1="0"
                  y1="0"
                  x2="0"
                  y2="1"
                >
                  <stop offset="0%" stopColor={style.color} stopOpacity={0.22} />
                  <stop offset="100%" stopColor={style.color} stopOpacity={0.02} />
                </linearGradient>
              ))}
            </defs>
          )}

          <CartesianGrid vertical={false} stroke={tokens.grid} strokeWidth={1} />
          <XAxis
            dataKey={xKey}
            tickFormatter={xFormatter}
            tickLine={false}
            axisLine={{ stroke: tokens.axis }}
            tick={{ fill: tokens.label, fontSize: 11 }}
            minTickGap={24}
          />
          <YAxis
            tickFormatter={yFormatter}
            tickLine={false}
            axisLine={false}
            tick={{ fill: tokens.label, fontSize: 11 }}
            width={56}
          />
          {showZeroLine && (
            <ReferenceLine y={0} stroke={tokens.axis} strokeDasharray="4 4" strokeWidth={1} />
          )}

          <Tooltip
            cursor={{ stroke: tokens.crosshair, strokeWidth: 1, strokeDasharray: '4 4' }}
            content={({ active, payload, label }) => {
              if (!active || !payload?.length) return null
              const rows: TooltipRow[] = payload.map((item) => {
                const definition = series.find((s) => s.key === item.dataKey)
                const value = Number(item.value ?? 0)
                return {
                  label: definition?.label ?? String(item.dataKey),
                  value: definition?.format ? definition.format(value) : yFormatter(value),
                  color: item.color as string,
                }
              })
              return (
                <ChartTooltipCard
                  title={xFormatter ? xFormatter(String(label)) : String(label)}
                  rows={rows}
                />
              )
            }}
          />

          {series.map((definition, index) =>
            variant === 'area' ? (
              <Area
                key={definition.key}
                type="monotone"
                dataKey={definition.key}
                name={definition.label}
                stroke={styles[index].color}
                strokeWidth={2}
                strokeDasharray={styles[index].dash}
                fill={`url(#lo-area-${index})`}
                dot={false}
                activeDot={{ r: 5, strokeWidth: 2, stroke: tokens.surface }}
                isAnimationActive
                animationDuration={450}
              />
            ) : (
              <Line
                key={definition.key}
                type="monotone"
                dataKey={definition.key}
                name={definition.label}
                stroke={styles[index].color}
                strokeWidth={2}
                strokeDasharray={styles[index].dash}
                dot={false}
                activeDot={{ r: 5, strokeWidth: 2, stroke: tokens.surface }}
                isAnimationActive
                animationDuration={450}
              />
            ),
          )}
        </Chart>
      </ResponsiveContainer>
    </ChartFrame>
  )
})

/* ==========================================================================
   Bars — grouped or stacked.

   Data ends are rounded 4px and anchored to the baseline; stacked segments get a
   2px surface-coloured stroke so adjacent tones never bleed into one another.
   ========================================================================== */
export const BarSeriesChart = memo(function BarSeriesChart({
  data,
  xKey,
  series,
  xFormatter,
  yFormatter = formatCompact,
  stacked = false,
  layout = 'vertical',
  ...frame
}: XYProps & { stacked?: boolean; layout?: 'vertical' | 'horizontal' }) {
  const tokens = useChartTokens()
  const styles = useSeriesStyles(series.length)
  const isHorizontal = layout === 'horizontal'

  const legend: LegendItem[] = series.map((definition, index) => ({
    label: definition.label,
    style: styles[index],
  }))

  const rows = data as Record<string, unknown>[]
  const empty = !rows.length || rows.every((row) => series.every((s) => !Number(row[s.key])))

  return (
    <ChartFrame {...frame} legend={legend} empty={empty}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={rows}
          layout={isHorizontal ? 'vertical' : 'horizontal'}
          margin={{ top: 8, right: 16, bottom: 4, left: isHorizontal ? 8 : -12 }}
          barGap={2}
          barCategoryGap={isHorizontal ? '22%' : '28%'}
        >
          <CartesianGrid
            horizontal={!isHorizontal}
            vertical={isHorizontal}
            stroke={tokens.grid}
          />

          {isHorizontal ? (
            <>
              <XAxis
                type="number"
                tickFormatter={yFormatter}
                tickLine={false}
                axisLine={false}
                tick={{ fill: tokens.label, fontSize: 11 }}
              />
              <YAxis
                type="category"
                dataKey={xKey}
                tickFormatter={xFormatter}
                tickLine={false}
                axisLine={false}
                width={112}
                tick={{ fill: tokens.label, fontSize: 11 }}
              />
            </>
          ) : (
            <>
              <XAxis
                dataKey={xKey}
                tickFormatter={xFormatter}
                tickLine={false}
                axisLine={{ stroke: tokens.axis }}
                tick={{ fill: tokens.label, fontSize: 11 }}
                minTickGap={12}
              />
              <YAxis
                tickFormatter={yFormatter}
                tickLine={false}
                axisLine={false}
                width={56}
                tick={{ fill: tokens.label, fontSize: 11 }}
              />
            </>
          )}

          <Tooltip
            cursor={{ fill: tokens.grid, fillOpacity: 0.55 }}
            content={({ active, payload, label }) => {
              if (!active || !payload?.length) return null
              const rows: TooltipRow[] = payload.map((item) => {
                const definition = series.find((s) => s.key === item.dataKey)
                const value = Number(item.value ?? 0)
                return {
                  label: definition?.label ?? String(item.dataKey),
                  value: definition?.format ? definition.format(value) : yFormatter(value),
                  color: item.color as string,
                }
              })
              if (stacked && rows.length > 1) {
                const total = payload.reduce((sum, item) => sum + Number(item.value ?? 0), 0)
                rows.push({ label: 'Total', value: yFormatter(total), emphasis: true })
              }
              return (
                <ChartTooltipCard
                  title={xFormatter ? xFormatter(String(label)) : String(label)}
                  rows={rows}
                />
              )
            }}
          />

          {series.map((definition, index) => (
            <Bar
              key={definition.key}
              dataKey={definition.key}
              name={definition.label}
              stackId={stacked ? 'stack' : undefined}
              fill={styles[index].fill}
              // The surface-coloured stroke is what produces the 2px gap between
              // stacked segments and adjacent bars.
              stroke={tokens.surface}
              strokeWidth={stacked ? 2 : 0}
              radius={
                isHorizontal
                  ? stacked
                    ? [0, 0, 0, 0]
                    : [0, 4, 4, 0]
                  : stacked
                    ? [0, 0, 0, 0]
                    : [4, 4, 0, 0]
              }
              isAnimationActive
              animationDuration={450}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
})

/* ==========================================================================
   Donut — part-to-whole.

   Only used when the parts genuinely sum to a meaningful whole, capped at five
   slices plus "Other"; beyond that a ranked bar chart reads better and this
   component would be the wrong choice.
   ========================================================================== */
export interface DonutSlice {
  label: string
  value: number
  icon?: string
}

export const DonutBreakdown = memo(function DonutBreakdown({
  slices,
  valueFormatter = formatCompact,
  centreLabel,
  centreValue,
  ...frame
}: FrameProps & {
  slices: DonutSlice[]
  valueFormatter?: (value: number) => string
  centreLabel?: string
  centreValue?: string
}) {
  const tokens = useChartTokens()

  const prepared = useMemo(() => {
    const sorted = [...slices].sort((a, b) => b.value - a.value)
    if (sorted.length <= 6) return sorted
    const head = sorted.slice(0, 5)
    const tail = sorted.slice(5)
    return [...head, { label: 'Other', value: tail.reduce((sum, s) => sum + s.value, 0) }]
  }, [slices])

  const styles = useSeriesStyles(prepared.length)
  const total = prepared.reduce((sum, slice) => sum + slice.value, 0)

  const legend: LegendItem[] = prepared.map((slice, index) => ({
    label: slice.label,
    style: styles[index],
    value: total ? `${Math.round((slice.value / total) * 100)}%` : '0%',
  }))

  return (
    <ChartFrame {...frame} legend={legend} empty={!total}>
      <div style={{ position: 'relative', height: '100%' }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Tooltip
              content={({ active, payload }) => {
                if (!active || !payload?.length) return null
                const slice = payload[0]
                const value = Number(slice.value ?? 0)
                return (
                  <ChartTooltipCard
                    title={String(slice.name)}
                    rows={[
                      { label: 'Amount', value: valueFormatter(value) },
                      {
                        label: 'Share',
                        value: total ? `${((value / total) * 100).toFixed(1)}%` : '0%',
                      },
                    ]}
                  />
                )
              }}
            />
            <Pie
              data={prepared}
              dataKey="value"
              nameKey="label"
              innerRadius="62%"
              outerRadius="92%"
              // A 2px gap between slices, same rule as stacked bars.
              paddingAngle={1.6}
              stroke={tokens.surface}
              strokeWidth={2}
              isAnimationActive
              animationDuration={450}
            >
              {prepared.map((_slice, index) => (
                <Cell key={index} fill={styles[index].fill} />
              ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>

        {(centreValue || centreLabel) && (
          <div className="lo-ring__label">
            {centreValue && <span className="lo-ring__value tabular">{centreValue}</span>}
            {centreLabel && <span className="lo-ring__caption">{centreLabel}</span>}
          </div>
        )}
      </div>
    </ChartFrame>
  )
})

/* ==========================================================================
   Radar — the life-balance view. Five fixed axes, one series.
   ========================================================================== */
export const BalanceRadar = memo(function BalanceRadar({
  scores,
  ...frame
}: FrameProps & { scores: Record<string, number> }) {
  const tokens = useChartTokens()
  const data = Object.entries(scores).map(([key, value]) => ({
    axis: key.charAt(0).toUpperCase() + key.slice(1),
    value: Math.round(value * 100),
  }))

  return (
    <ChartFrame {...frame} empty={!data.length}>
      <ResponsiveContainer width="100%" height="100%">
        <RadarChart data={data} outerRadius="72%">
          <PolarGrid stroke={tokens.grid} />
          <PolarAngleAxis dataKey="axis" tick={{ fill: tokens.label, fontSize: 11 }} />
          <PolarRadiusAxis domain={[0, 100]} tick={false} axisLine={false} />
          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null
              return (
                <ChartTooltipCard
                  title={String(payload[0].payload.axis)}
                  rows={[{ label: 'Score', value: `${payload[0].value}/100` }]}
                />
              )
            }}
          />
          <Radar
            dataKey="value"
            stroke={tokens.categorical[0]}
            strokeWidth={2}
            fill={tokens.categorical[0]}
            fillOpacity={0.14}
            isAnimationActive
            animationDuration={500}
          />
        </RadarChart>
      </ResponsiveContainer>
    </ChartFrame>
  )
})

/* ==========================================================================
   Contribution heatmap — magnitude over a year, on the sequential ramp.
   ========================================================================== */
export interface HeatCell {
  date: string
  count: number
  intensity: number
}

export const ContributionHeatmap = memo(function ContributionHeatmap({
  cells,
  unitLabel = 'check-in',
  ...frame
}: FrameProps & { cells: HeatCell[]; unitLabel?: string }) {
  const tokens = useChartTokens()

  const weeks = useMemo(() => {
    if (!cells.length) return []
    const result: HeatCell[][] = []
    let current: HeatCell[] = []

    // Pad the first week so every column starts on a Monday.
    const firstDay = new Date(cells[0].date).getDay()
    const leading = (firstDay + 6) % 7
    for (let i = 0; i < leading; i++) {
      current.push({ date: '', count: -1, intensity: 0 })
    }

    cells.forEach((cell) => {
      current.push(cell)
      if (current.length === 7) {
        result.push(current)
        current = []
      }
    })
    if (current.length) result.push(current)
    return result
  }, [cells])

  const totalDays = cells.filter((c) => c.count > 0).length
  const totalCount = cells.reduce((sum, c) => sum + Math.max(0, c.count), 0)

  return (
    <ChartFrame
      {...frame}
      empty={!cells.length}
      actions={
        <div className="lo-heatmap__scale" aria-label="Intensity scale, less to more">
          <span>Less</span>
          {tokens.sequential.map((color) => (
            <span key={color} className="lo-heatmap__scale-cell" style={{ background: color }} />
          ))}
          <span>More</span>
        </div>
      }
    >
      <div className="lo-scroll-x" style={{ paddingTop: 4 }}>
        <div className="lo-heatmap" role="img" aria-label={frame.summary}>
          {weeks.map((week, weekIndex) => (
            <div className="lo-heatmap__week" key={weekIndex}>
              {week.map((cell, dayIndex) =>
                cell.count < 0 ? (
                  <span
                    key={dayIndex}
                    className="lo-heatmap__cell"
                    style={{ background: 'transparent', border: 'none' }}
                  />
                ) : (
                  <span
                    key={dayIndex}
                    className="lo-heatmap__cell"
                    tabIndex={0}
                    role="button"
                    style={{ background: sequentialStep(cell.intensity, tokens) }}
                    title={`${cell.date} · ${cell.count} ${unitLabel}${cell.count === 1 ? '' : 's'}`}
                    aria-label={`${cell.date}: ${cell.count} ${unitLabel}${cell.count === 1 ? '' : 's'}`}
                  />
                ),
              )}
            </div>
          ))}
        </div>
      </div>
      <p className="lo-chart__subtitle" style={{ marginTop: 8 }}>
        {totalCount} {unitLabel}s across {totalDays} active {totalDays === 1 ? 'day' : 'days'}
      </p>
    </ChartFrame>
  )
})

/* ==========================================================================
   Progress ring — a single ratio, rendered as one number and one arc.
   ========================================================================== */
export function ProgressRing({
  value,
  size = 132,
  thickness = 10,
  label,
  caption,
  trackColor,
  arcColor,
}: {
  value: number
  size?: number
  thickness?: number
  label?: ReactNode
  caption?: string
  trackColor?: string
  arcColor?: string
}) {
  const tokens = useChartTokens()
  const clamped = Math.max(0, Math.min(1, Number.isFinite(value) ? value : 0))
  const radius = (size - thickness) / 2
  const circumference = 2 * Math.PI * radius

  return (
    <div className="lo-ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} role="img" aria-label={`${Math.round(clamped * 100)}% complete`}>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={trackColor ?? tokens.grid}
          strokeWidth={thickness}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={arcColor ?? tokens.categorical[0]}
          strokeWidth={thickness}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - clamped)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          style={{ transition: 'stroke-dashoffset 600ms cubic-bezier(0.2,0,0,1)' }}
        />
      </svg>
      <div className="lo-ring__label">
        <span className="lo-ring__value tabular">{label ?? `${Math.round(clamped * 100)}%`}</span>
        {caption && <span className="lo-ring__caption">{caption}</span>}
      </div>
    </div>
  )
}

/* ==========================================================================
   Sparkline — trend shape only, no axes. Never carries a value on its own.
   ========================================================================== */
export function Sparkline({
  data,
  dataKey = 'value',
  height = 34,
}: {
  data: Record<string, unknown>[]
  dataKey?: string
  height?: number
}) {
  const tokens = useChartTokens()
  if (!data.length) return null

  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="lo-spark" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={tokens.categorical[0]} stopOpacity={0.24} />
            <stop offset="100%" stopColor={tokens.categorical[0]} stopOpacity={0} />
          </linearGradient>
        </defs>
        <Area
          type="monotone"
          dataKey={dataKey}
          stroke={tokens.categorical[0]}
          strokeWidth={2}
          fill="url(#lo-spark)"
          dot={false}
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}

/* ==========================================================================
   Stat tile — a headline number. Not a chart, and deliberately so: one value
   with a comparison is clearer than any plot of a single number.
   ========================================================================== */
export function StatTile({
  label,
  value,
  caption,
  delta,
  deltaLabel,
  icon,
  spark,
  invertDelta = false,
}: {
  label: string
  value: ReactNode
  caption?: string
  /** Fractional change, e.g. 0.12 for +12%. */
  delta?: number | null
  deltaLabel?: string
  icon?: ReactNode
  spark?: Record<string, unknown>[]
  /** For spending, a rise is bad — flips which direction reads as positive. */
  invertDelta?: boolean
}) {
  const hasDelta = typeof delta === 'number' && Number.isFinite(delta)
  const rising = hasDelta && delta! > 0.005
  const falling = hasDelta && delta! < -0.005
  const good = invertDelta ? falling : rising
  const bad = invertDelta ? rising : falling

  const deltaClass = !hasDelta || (!rising && !falling)
    ? 'lo-stat__delta--flat'
    : good
      ? 'lo-stat__delta--up'
      : bad
        ? 'lo-stat__delta--down'
        : 'lo-stat__delta--flat'

  const DeltaIcon = rising ? ArrowUpRight : falling ? ArrowDownRight : ArrowRight

  return (
    <div className="lo-stat">
      <div className="lo-stat__head">
        <span className="lo-stat__label">{label}</span>
        {icon && <span className="lo-stat__icon">{icon}</span>}
      </div>

      <div className="lo-stat__value tabular">{value}</div>

      {(caption || hasDelta) && (
        <div className="lo-stat__foot">
          {hasDelta && (
            <span className={`lo-stat__delta ${deltaClass}`}>
              <DeltaIcon size={12} strokeWidth={2.5} aria-hidden />
              {Math.abs(delta! * 100).toFixed(0)}%
            </span>
          )}
          {(deltaLabel || caption) && <span>{deltaLabel ?? caption}</span>}
        </div>
      )}

      {spark && spark.length > 1 && (
        <div className="lo-stat__spark">
          <Sparkline data={spark} />
        </div>
      )}
    </div>
  )
}
