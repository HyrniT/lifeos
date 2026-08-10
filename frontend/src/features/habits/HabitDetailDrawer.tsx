import { Drawer, Skeleton, Tag } from 'antd'
import { TrendingDown, TrendingUp, Minus } from 'lucide-react'
import { useHabitInsightsQuery, useHabitQuery } from '@/app/api'
import { BarSeriesChart, ContributionHeatmap, formatPercent } from '@/components/charts'
import { DynamicIcon } from '@/components/ui'
import './habits.css'

export function HabitDetailDrawer({
  habitId,
  onClose,
}: {
  habitId: string | null
  onClose: () => void
}) {
  const { data: habit } = useHabitQuery(habitId!, { skip: !habitId })
  const { data: insights, isLoading } = useHabitInsightsQuery(habitId!, { skip: !habitId })

  const trendIcon =
    insights?.trend === 'up' ? (
      <TrendingUp size={14} />
    ) : insights?.trend === 'down' ? (
      <TrendingDown size={14} />
    ) : (
      <Minus size={14} />
    )

  const weekdayRows = Object.entries(insights?.weekdayCompletion ?? {}).map(([day, rate]) => ({
    day,
    rate: Math.round(rate * 100),
  }))

  return (
    <Drawer
      open={Boolean(habitId)}
      onClose={onClose}
      width={640}
      title={habit?.name ?? 'Habit'}
      styles={{ body: { paddingTop: 20 } }}
    >
      {isLoading || !habit || !insights ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <>
          <div className="lo-habit-detail__hero">
            <span
              style={{
                display: 'grid',
                placeItems: 'center',
                width: 56,
                height: 56,
                borderRadius: 16,
                background: 'var(--surface-container)',
              }}
            >
              <DynamicIcon name={habit.icon} size={26} />
            </span>
            <div style={{ minWidth: 0 }}>
              <h3 style={{ margin: 0, fontSize: 'var(--title-lg)', fontWeight: 720 }}>
                {habit.name}
              </h3>
              <p style={{ margin: '2px 0 8px', color: 'var(--on-surface-variant)', fontSize: 13 }}>
                {habit.description || 'No notes yet.'}
              </p>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <Tag style={{ margin: 0 }}>{habit.category}</Tag>
                <Tag style={{ margin: 0 }}>{habit.difficulty.toLowerCase()}</Tag>
                <Tag style={{ margin: 0, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  {trendIcon} {insights.trend}
                </Tag>
              </div>
            </div>
          </div>

          <div className="lo-habit-detail__grid">
            {[
              { label: 'Current streak', value: insights.currentStreak },
              { label: 'Longest streak', value: insights.longestStreak },
              { label: 'Total check-ins', value: insights.totalCheckIns },
              { label: 'Last 7 days', value: formatPercent(insights.completionRate7d) },
              { label: 'Last 30 days', value: formatPercent(insights.completionRate30d) },
              { label: 'Last 90 days', value: formatPercent(insights.completionRate90d) },
            ].map((cell) => (
              <div className="lo-habit-detail__cell" key={cell.label}>
                <div className="lo-habit-detail__cell-value tabular">{cell.value}</div>
                <div className="lo-habit-detail__cell-label">{cell.label}</div>
              </div>
            ))}
          </div>

          <div style={{ marginBottom: 28 }}>
            <BarSeriesChart
              title="Which days you actually do it"
              subtitle="Completion rate by weekday, last 90 days"
              summary={`Completion rate by weekday over 90 days. Best day ${insights.bestDay}, weakest ${insights.worstDay}.`}
              hint="Measured against the days the habit was scheduled, not calendar days."
              data={weekdayRows}
              xKey="day"
              series={[{ key: 'rate', label: 'Completion', format: (v) => `${v}%` }]}
              yFormatter={(value) => `${value}%`}
              height={220}
              tableColumns={[
                { key: 'day', title: 'Day' },
                { key: 'rate', title: 'Completion %', align: 'right' },
              ]}
              tableRows={weekdayRows}
            />
            <p style={{ marginTop: 10, fontSize: 13, color: 'var(--on-surface-variant)' }}>
              Strongest on <strong>{insights.bestDay}</strong>, weakest on{' '}
              <strong>{insights.worstDay}</strong>
              {insights.averageMood != null && ` · average mood ${insights.averageMood}/5`}.
            </p>
          </div>

          <ContributionHeatmap
            title="History"
            subtitle="Last six months"
            summary={`Daily completion heatmap for ${habit.name} over the last 180 days.`}
            cells={insights.heatmap}
            unitLabel="completion"
            height={130}
          />
        </>
      )}
    </Drawer>
  )
}
