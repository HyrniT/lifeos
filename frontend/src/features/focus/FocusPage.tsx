import { useEffect, useMemo, useRef, useState } from 'react'
import { Button, Rate, Segmented, Select, Statistic, message } from 'antd'
import dayjs from 'dayjs'
import { Pause, Play, Square, Timer } from 'lucide-react'
import {
  useCurrentFocusQuery,
  useEndFocusMutation,
  useFocusHistoryQuery,
  usePlanningStatisticsQuery,
  useStartFocusMutation,
  useTasksQuery,
} from '@/app/api'
import { BarSeriesChart, ProgressRing, StatTile, formatMinutes } from '@/components/charts'
import { PageHeader, Section } from '@/components/ui'
import type { SessionType } from '@/types'

const PRESETS: { label: string; value: SessionType; minutes: number }[] = [
  { label: 'Pomodoro · 25m', value: 'POMODORO', minutes: 25 },
  { label: 'Deep work · 90m', value: 'DEEP_WORK', minutes: 90 },
  { label: 'Short break · 5m', value: 'SHORT_BREAK', minutes: 5 },
  { label: 'Long break · 15m', value: 'LONG_BREAK', minutes: 15 },
]

export function FocusPage() {
  const [preset, setPreset] = useState<SessionType>('POMODORO')
  const [taskId, setTaskId] = useState<string | undefined>()
  const [score, setScore] = useState(4)
  const [elapsed, setElapsed] = useState(0)
  const tickRef = useRef<number | null>(null)

  const { data: current, refetch } = useCurrentFocusQuery(undefined, { pollingInterval: 60_000 })
  const { data: tasks = [] } = useTasksQuery({ status: 'TODO' })
  const { data: history = [] } = useFocusHistoryQuery({
    from: dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
    to: dayjs().format('YYYY-MM-DD'),
  })
  const { data: stats } = usePlanningStatisticsQuery(30)
  const [start, { isLoading: starting }] = useStartFocusMutation()
  const [end, { isLoading: ending }] = useEndFocusMutation()

  const plannedMinutes = PRESETS.find((p) => p.value === preset)?.minutes ?? 25

  // The timer is derived from the server's start time rather than counted in the
  // browser, so a refresh, a sleeping laptop or a second tab all agree.
  useEffect(() => {
    if (!current?.startedAt) {
      setElapsed(0)
      if (tickRef.current) window.clearInterval(tickRef.current)
      return
    }
    const update = () => {
      setElapsed(Math.max(0, Math.floor((Date.now() - new Date(current.startedAt).getTime()) / 1000)))
    }
    update()
    tickRef.current = window.setInterval(update, 1000)
    return () => {
      if (tickRef.current) window.clearInterval(tickRef.current)
    }
  }, [current?.startedAt])

  const running = Boolean(current && !current.endedAt)
  const target = (current?.plannedMinutes ?? plannedMinutes) * 60
  const progress = target > 0 ? Math.min(1, elapsed / target) : 0

  const clock = useMemo(() => {
    const minutes = Math.floor(elapsed / 60)
    const seconds = elapsed % 60
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }, [elapsed])

  const hourRows = Object.entries(stats?.focusByHour ?? {})
    .map(([hour, minutes]) => ({ hour: `${hour}:00`, minutes }))
    .filter((row) => row.minutes > 0)

  const dailyRows = useMemo(() => {
    const byDate = new Map<string, number>()
    history
      .filter((session) => session.completed)
      .forEach((session) =>
        byDate.set(session.sessionDate, (byDate.get(session.sessionDate) ?? 0) + session.actualMinutes),
      )
    return Array.from(byDate.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, minutes]) => ({ date: dayjs(date).format('D MMM'), minutes }))
  }, [history])

  const onStart = async () => {
    try {
      await start({ taskId, type: preset, plannedMinutes }).unwrap()
      message.success('Timer started')
    } catch {
      message.error('Could not start the timer')
    }
  }

  const onStop = async (completed: boolean) => {
    if (!current) return
    try {
      const result = await end({ id: current.id, focusScore: score, completed }).unwrap()
      message.success(
        result.completed
          ? `Session logged · ${formatMinutes(result.actualMinutes)}`
          : 'Session ended early',
      )
      refetch()
    } catch {
      message.error('Could not end the session')
    }
  }

  return (
    <>
      <PageHeader
        title="Focus"
        subtitle="Time is the only resource you cannot get more of. Track where it goes."
      />

      <div className="lo-grid lo-grid--stats">
        <StatTile
          label="This week"
          value={formatMinutes(stats?.focusMinutesLast7d ?? 0)}
          icon={<Timer size={17} />}
        />
        <StatTile label="Last 30 days" value={formatMinutes(stats?.focusMinutesLast30d ?? 0)} />
        <StatTile label="Sessions" value={stats?.focusSessionsTotal ?? 0} />
        <StatTile
          label="Average session"
          value={formatMinutes(stats?.averageSessionMinutes ?? 0)}
          caption={`Peak day: ${stats?.mostProductiveDay ?? '—'}`}
        />
      </div>

      <Section>
        <div className="lo-panel" style={{ display: 'grid', placeItems: 'center', padding: 40 }}>
          <ProgressRing
            value={progress}
            size={216}
            thickness={14}
            label={<span style={{ fontSize: 44, letterSpacing: '-0.04em' }}>{clock}</span>}
            caption={running ? current?.type.replace('_', ' ').toLowerCase() : 'ready'}
          />

          <div style={{ marginTop: 28, width: '100%', maxWidth: 420 }}>
            {!running ? (
              <>
                <Segmented
                  block
                  value={preset}
                  onChange={(value) => setPreset(value as SessionType)}
                  options={PRESETS.map((p) => ({ label: p.label, value: p.value }))}
                  style={{ marginBottom: 16 }}
                />
                <Select
                  allowClear
                  style={{ width: '100%', marginBottom: 16 }}
                  placeholder="Attach to a task (optional)"
                  value={taskId}
                  onChange={setTaskId}
                  showSearch
                  optionFilterProp="label"
                  options={tasks.map((task) => ({ value: task.id, label: task.title }))}
                />
                <Button
                  type="primary"
                  size="large"
                  block
                  icon={<Play size={17} />}
                  loading={starting}
                  onClick={onStart}
                >
                  Start {plannedMinutes} minutes
                </Button>
              </>
            ) : (
              <>
                <div style={{ textAlign: 'center', marginBottom: 16 }}>
                  <span style={{ fontSize: 13, color: 'var(--on-surface-variant)' }}>
                    How focused did this feel?
                  </span>
                  <div>
                    <Rate value={score} onChange={setScore} />
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 12 }}>
                  <Button
                    size="large"
                    block
                    icon={<Pause size={17} />}
                    loading={ending}
                    onClick={() => onStop(false)}
                  >
                    End early
                  </Button>
                  <Button
                    type="primary"
                    size="large"
                    block
                    icon={<Square size={16} />}
                    loading={ending}
                    onClick={() => onStop(true)}
                  >
                    Complete
                  </Button>
                </div>
              </>
            )}
          </div>
        </div>
      </Section>

      <Section title="When you focus best">
        <div className="lo-grid lo-grid--halves">
          <div className="lo-panel">
            <BarSeriesChart
              title="By hour of day"
              subtitle="Total focused minutes, last 30 days"
              summary="Focused minutes grouped by the hour the session started, over the last 30 days."
              hint="Times are shown in UTC as recorded by the server."
              data={hourRows}
              xKey="hour"
              series={[{ key: 'minutes', label: 'Minutes', format: formatMinutes }]}
              yFormatter={(value) => `${value}m`}
              height={260}
              tableColumns={[
                { key: 'hour', title: 'Hour' },
                { key: 'minutes', title: 'Minutes', align: 'right' },
              ]}
              tableRows={hourRows}
            />
          </div>

          <div className="lo-panel">
            <BarSeriesChart
              title="Daily focus"
              subtitle="Completed sessions only"
              summary="Minutes of completed focus per day over the last 30 days."
              data={dailyRows}
              xKey="date"
              series={[{ key: 'minutes', label: 'Minutes', format: formatMinutes }]}
              yFormatter={(value) => `${value}m`}
              height={260}
              tableColumns={[
                { key: 'date', title: 'Date' },
                { key: 'minutes', title: 'Minutes', align: 'right' },
              ]}
              tableRows={dailyRows}
            />
          </div>
        </div>
      </Section>
    </>
  )
}
