import { useState } from 'react'
import { Alert, Segmented, Tag } from 'antd'
import dayjs from 'dayjs'
import { Activity, Coins, Flame, Link2, Timer } from 'lucide-react'
import { useLifeOverviewQuery } from '@/app/api'
import { useAppSelector } from '@/app/hooks'
import {
  BalanceRadar,
  StatTile,
  TrendChart,
  formatCurrency,
  formatMinutes,
  formatPercent,
  formatShortDate,
} from '@/components/charts'
import { EmptyState, PageHeader, PanelSkeleton, Section } from '@/components/ui'

const WINDOWS = [
  { label: '30 days', value: 30 },
  { label: '90 days', value: 90 },
  { label: '180 days', value: 180 },
  { label: '365 days', value: 365 },
]

export function AnalyticsPage() {
  const [days, setDays] = useState(90)
  const currency = useAppSelector((state) => state.auth.user?.baseCurrency ?? 'USD')

  const to = dayjs().format('YYYY-MM-DD')
  const from = dayjs().subtract(days - 1, 'day').format('YYYY-MM-DD')
  const { data, isLoading } = useLifeOverviewQuery({ from, to })

  const timeline = (data?.timeline ?? []).map((point) => ({
    date: point.date,
    habits: point.habitCheckIns,
    tasks: point.tasksCompleted,
    focusHours: Math.round((point.focusMinutes / 60) * 10) / 10,
    expense: point.expense,
  }))

  return (
    <>
      <PageHeader
        title="Analytics"
        subtitle="The part no single screen can tell you: how habits, money and work move together."
        actions={
          <Segmented
            value={days}
            onChange={(value) => setDays(value as number)}
            options={WINDOWS}
          />
        }
      />

      {isLoading ? (
        <PanelSkeleton rows={8} />
      ) : !data || data.timeline.every((point) => !point.habitCheckIns && !point.expense) ? (
        <EmptyState
          icon={<Activity size={22} />}
          title="Not enough history yet"
          description="Analytics needs a couple of weeks of check-ins and transactions before the patterns mean anything. Keep logging and this page fills itself in."
        />
      ) : (
        <>
          <div className="lo-grid lo-grid--stats">
            <StatTile
              label="Active days"
              value={`${data.activeDays}/${data.timeline.length}`}
              caption={`${formatPercent(data.habitConsistency)} consistency`}
              icon={<Flame size={17} />}
            />
            <StatTile
              label="Check-ins"
              value={data.totalCheckIns}
              caption={`${data.totalXp} XP earned`}
              icon={<Activity size={17} />}
            />
            <StatTile
              label="Net"
              value={formatCurrency(data.totalEarned - data.totalSpent, currency, true)}
              caption={`${formatCurrency(data.averageDailySpend, currency)}/day spent`}
              icon={<Coins size={17} />}
            />
            <StatTile
              label="Focused"
              value={formatMinutes(data.totalFocusMinutes)}
              caption={`${data.totalTasksCompleted} tasks completed`}
              icon={<Timer size={17} />}
            />
          </div>

          <Section>
            <div className="lo-grid lo-grid--split">
              <div className="lo-panel">
                <TrendChart
                  title="Everything on one timeline"
                  subtitle={`${dayjs(from).format('D MMM')} – ${dayjs(to).format('D MMM YYYY')}`}
                  summary={`Daily habit check-ins, completed tasks and focused hours between ${from} and ${to}.`}
                  hint="All three are counts on a comparable scale, so they share one axis. Spending is plotted separately below — putting money on a second axis here would let the crossover be placed anywhere."
                  data={timeline}
                  xKey="date"
                  xFormatter={formatShortDate}
                  series={[
                    { key: 'habits', label: 'Habit check-ins' },
                    { key: 'tasks', label: 'Tasks done' },
                    { key: 'focusHours', label: 'Focus hours', format: (v) => `${v}h` },
                  ]}
                  height={300}
                  tableColumns={[
                    { key: 'date', title: 'Date' },
                    { key: 'habits', title: 'Check-ins', align: 'right' },
                    { key: 'tasks', title: 'Tasks', align: 'right' },
                    { key: 'focusHours', title: 'Focus (h)', align: 'right' },
                  ]}
                  tableRows={timeline}
                />
              </div>

              <div className="lo-panel">
                <BalanceRadar
                  title="Life balance"
                  subtitle="Five areas, scored 0-100"
                  summary={`Balance scores across habits, money, productivity, focus and wellbeing over ${days} days.`}
                  hint="Each score normalises your own averages against a reasonable daily target — it compares you with you, not with anyone else."
                  scores={data.balanceScore}
                  height={300}
                />
              </div>
            </div>
          </Section>

          <Section>
            <div className="lo-panel">
              <TrendChart
                title="Daily spending"
                subtitle="Plotted on its own axis, deliberately"
                summary={`Daily spending between ${from} and ${to}, totalling ${formatCurrency(data.totalSpent, currency)}.`}
                data={timeline}
                xKey="date"
                xFormatter={formatShortDate}
                yFormatter={(value) => formatCurrency(value, currency, true)}
                series={[{ key: 'expense', label: 'Spent', format: (v) => formatCurrency(v, currency) }]}
                variant="area"
                height={220}
                tableColumns={[
                  { key: 'date', title: 'Date' },
                  { key: 'expense', title: 'Spent', align: 'right' },
                ]}
                tableRows={timeline.map((row) => ({
                  date: row.date,
                  expense: formatCurrency(row.expense, currency),
                }))}
              />
            </div>
          </Section>

          <Section
            title="Patterns in your data"
            description="Relationships found across your own logs. Correlation is not cause — these are places to look, not conclusions."
          >
            {!data.correlations.length ? (
              <Alert
                type="info"
                showIcon
                message="No strong patterns yet"
                description="A correlation needs at least a fortnight of overlapping data before it is worth reporting. Nothing has crossed that bar so far."
              />
            ) : (
              <div className="lo-grid lo-grid--cards">
                {data.correlations.map((correlation) => (
                  <div className="lo-panel" key={correlation.code}>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        marginBottom: 10,
                      }}
                    >
                      <Link2 size={16} />
                      <strong style={{ fontSize: 15 }}>{correlation.title}</strong>
                    </div>
                    <p style={{ margin: '0 0 12px', color: 'var(--on-surface-variant)', fontSize: 13 }}>
                      {correlation.message}
                    </p>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <Tag style={{ margin: 0 }}>r = {correlation.strength.toFixed(2)}</Tag>
                      <Tag style={{ margin: 0 }}>{correlation.sampleDays} days of data</Tag>
                      <Tag style={{ margin: 0 }}>{correlation.direction}</Tag>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Section>

          <Section>
            <div className="lo-panel">
              <p style={{ margin: 0, fontSize: 14 }}>
                Your strongest day is <strong>{data.strongestDay}</strong> and your weakest is{' '}
                <strong>{data.weakestDay}</strong>, measured by habit check-ins plus completed tasks.
                If something important keeps slipping, scheduling it on a{' '}
                {data.strongestDay.toLowerCase()} is the cheapest experiment available.
              </p>
            </div>
          </Section>
        </>
      )}
    </>
  )
}
