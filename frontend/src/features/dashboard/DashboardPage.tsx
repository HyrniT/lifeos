import { useMemo } from 'react'
import { Button, Checkbox, Empty, Progress, Tag, Tooltip, message } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import dayjs from 'dayjs'
import {
  Bolt,
  CalendarCheck,
  Coins,
  Flame,
  ListTodo,
  Plus,
  Sparkles,
  Timer,
  TrendingDown,
  TrendingUp,
  Wallet,
} from 'lucide-react'
import {
  useAgendaQuery,
  useCheckInMutation,
  useExpenseStatisticsQuery,
  useHabitHeatmapQuery,
  useHabitsTodayQuery,
  useSetTaskStatusMutation,
} from '@/app/api'
import { useAppSelector } from '@/app/hooks'
import {
  ContributionHeatmap,
  ProgressRing,
  StatTile,
  TrendChart,
  formatCurrency,
  formatMinutes,
  formatShortDate,
} from '@/components/charts'
import { DynamicIcon, EmptyState, FadeIn, PanelSkeleton, Section, StaggerItem, StaggerList } from '@/components/ui'

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 5) return 'Still up'
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

export function DashboardPage() {
  const navigate = useNavigate()
  const user = useAppSelector((state) => state.auth.user)

  const today = dayjs()
  const monthStart = today.startOf('month').format('YYYY-MM-DD')
  const todayIso = today.format('YYYY-MM-DD')

  const { data: habitsToday, isLoading: loadingHabits } = useHabitsTodayQuery()
  const { data: agenda, isLoading: loadingAgenda } = useAgendaQuery()
  const { data: money, isLoading: loadingMoney } = useExpenseStatisticsQuery({
    from: monthStart,
    to: todayIso,
  })
  const { data: heatmap = [] } = useHabitHeatmapQuery({
    from: today.subtract(180, 'day').format('YYYY-MM-DD'),
    to: todayIso,
  })

  const [checkIn, { isLoading: checkingIn }] = useCheckInMutation()
  const [setTaskStatus] = useSetTaskStatusMutation()

  const stats = habitsToday?.stats
  const currency = money?.overview.currency ?? user?.baseCurrency ?? 'USD'

  const cashFlow = useMemo(
    () =>
      (money?.cashFlow ?? []).map((point) => ({
        date: point.date,
        expense: point.expense,
        income: point.income,
      })),
    [money],
  )

  const spentToday = useMemo(() => {
    const entry = money?.cashFlow?.find((point) => point.date === todayIso)
    return entry?.expense ?? 0
  }, [money, todayIso])

  const openTasks = (agenda?.overdue.length ?? 0) + (agenda?.dueToday.length ?? 0)

  const handleCheckIn = async (habitId: string, name: string) => {
    try {
      const result = await checkIn({ id: habitId }).unwrap()
      if (result.newAchievements.length) {
        message.success(`${name} done — achievement unlocked!`)
      } else if (result.milestoneReached) {
        message.success(`${result.currentStreak}-day streak on ${name}!`)
      } else {
        message.success(`${name} · +${result.xpAwarded} XP`)
      }
    } catch {
      message.error('Could not save that check-in')
    }
  }

  return (
    <>
      {/* ---------------------------------------------------------- hero --- */}
      <FadeIn>
        <div
          className="lo-panel lo-grain"
          style={{
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
            gap: 28,
            flexWrap: 'wrap',
            background: 'var(--surface-container-lowest)',
            marginBottom: 24,
          }}
        >
          <ProgressRing
            value={habitsToday?.completionRate ?? 0}
            size={124}
            thickness={11}
            label={
              habitsToday ? `${habitsToday.completed}/${habitsToday.totalDue}` : '—'
            }
            caption="today"
          />

          <div style={{ flex: 1, minWidth: 240 }}>
            <p
              style={{
                margin: 0,
                fontSize: 13,
                letterSpacing: '0.08em',
                textTransform: 'uppercase',
                color: 'var(--on-surface-muted)',
                fontWeight: 650,
              }}
            >
              {today.format('dddd, D MMMM')}
            </p>
            <h2
              style={{
                margin: '4px 0 10px',
                fontSize: 'var(--headline-md)',
                fontWeight: 780,
                letterSpacing: '-0.03em',
              }}
            >
              {greeting()}, {user?.displayName?.split(' ')[0] ?? 'there'}.
            </h2>

            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <Tag
                icon={<Flame size={12} style={{ marginRight: 4 }} />}
                style={{ display: 'inline-flex', alignItems: 'center', padding: '3px 12px' }}
              >
                {stats?.currentDayStreak ?? 0}-day streak
              </Tag>
              <Tag
                icon={<Sparkles size={12} style={{ marginRight: 4 }} />}
                style={{ display: 'inline-flex', alignItems: 'center', padding: '3px 12px' }}
              >
                Level {stats?.level ?? 1}
              </Tag>
              <Tag
                icon={<Coins size={12} style={{ marginRight: 4 }} />}
                style={{ display: 'inline-flex', alignItems: 'center', padding: '3px 12px' }}
              >
                {stats?.coins ?? 0} coins
              </Tag>
              {(stats?.streakFreezes ?? 0) > 0 && (
                <Tooltip title="A streak freeze covers one missed day automatically.">
                  <Tag style={{ padding: '3px 12px' }}>
                    ❄ {stats?.streakFreezes} freeze{stats?.streakFreezes === 1 ? '' : 's'}
                  </Tag>
                </Tooltip>
              )}
            </div>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <Button type="primary" icon={<Plus size={16} />} onClick={() => navigate('/habits')}>
              New habit
            </Button>
            <Button icon={<Wallet size={16} />} onClick={() => navigate('/money')}>
              Add expense
            </Button>
          </div>
        </div>
      </FadeIn>

      {/* --------------------------------------------------------- tiles --- */}
      <div className="lo-grid lo-grid--stats">
        <StatTile
          label="Habits today"
          value={habitsToday ? `${habitsToday.completed}/${habitsToday.totalDue}` : '—'}
          caption={
            habitsToday && habitsToday.totalDue > 0
              ? `${Math.round(habitsToday.completionRate * 100)}% complete`
              : 'Nothing scheduled'
          }
          icon={<CalendarCheck size={17} />}
        />
        <StatTile
          label="Spent today"
          value={formatCurrency(spentToday, currency)}
          delta={money?.overview.changeVsPreviousPeriod}
          deltaLabel="vs previous period"
          invertDelta
          icon={<Wallet size={17} />}
        />
        <StatTile
          label="Open tasks"
          value={openTasks}
          caption={
            (agenda?.overdue.length ?? 0) > 0
              ? `${agenda?.overdue.length} overdue`
              : 'Nothing overdue'
          }
          icon={<ListTodo size={17} />}
        />
        <StatTile
          label="Focus today"
          value={formatMinutes(agenda?.focusMinutesToday ?? 0)}
          caption={`${habitsToday?.xpEarnedToday ?? 0} XP earned`}
          icon={<Timer size={17} />}
        />
      </div>

      {/* ------------------------------------------------------ two-column - */}
      <Section>
        <div className="lo-grid lo-grid--split">
          {/* ---- habits due today ---- */}
          <div className="lo-panel">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 16,
              }}
            >
              <h3 style={{ margin: 0, fontSize: 'var(--title-md)', fontWeight: 700 }}>
                Due today
              </h3>
              <Link to="/habits" style={{ fontSize: 13, fontWeight: 600 }}>
                All habits →
              </Link>
            </div>

            {loadingHabits ? (
              <PanelSkeleton rows={3} />
            ) : !habitsToday?.due.length ? (
              <EmptyState
                icon={<CalendarCheck size={22} />}
                title="No habits scheduled today"
                description="Add one and it will show up here on the days it is due."
                action={
                  <Button type="primary" icon={<Plus size={15} />} onClick={() => navigate('/habits')}>
                    Create a habit
                  </Button>
                }
              />
            ) : (
              <StaggerList>
                {habitsToday.due.map((habit) => (
                  <StaggerItem key={habit.id}>
                    <motion.div
                      whileHover={{ x: 2 }}
                      transition={{ duration: 0.15 }}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 14,
                        padding: '12px 4px',
                        borderBottom: '1px solid var(--outline-variant)',
                      }}
                    >
                      <Checkbox
                        checked={habit.doneToday}
                        disabled={checkingIn || habit.doneToday}
                        onChange={() => handleCheckIn(habit.id, habit.name)}
                        aria-label={`Mark ${habit.name} complete`}
                      />
                      <span
                        style={{
                          display: 'grid',
                          placeItems: 'center',
                          width: 36,
                          height: 36,
                          borderRadius: 11,
                          background: 'var(--surface-container)',
                          color: 'var(--on-surface-variant)',
                          flexShrink: 0,
                        }}
                      >
                        <DynamicIcon name={habit.icon} size={17} />
                      </span>

                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div
                          style={{
                            fontWeight: 620,
                            textDecoration: habit.doneToday ? 'line-through' : 'none',
                            opacity: habit.doneToday ? 0.5 : 1,
                          }}
                        >
                          {habit.name}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                          {habit.currentStreak > 0
                            ? `${habit.currentStreak}-day streak`
                            : 'Start a streak today'}
                          {habit.targetValue && habit.targetValue > 1
                            ? ` · target ${habit.targetValue} ${habit.unitLabel ?? habit.unit.toLowerCase()}`
                            : ''}
                        </div>
                      </div>

                      {habit.currentStreak >= 7 && (
                        <Tag style={{ margin: 0 }}>
                          <Flame size={11} style={{ marginRight: 3, verticalAlign: -1 }} />
                          {habit.currentStreak}
                        </Tag>
                      )}
                    </motion.div>
                  </StaggerItem>
                ))}
              </StaggerList>
            )}
          </div>

          {/* ---- agenda ---- */}
          <div className="lo-panel">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: 16,
              }}
            >
              <h3 style={{ margin: 0, fontSize: 'var(--title-md)', fontWeight: 700 }}>Agenda</h3>
              <Link to="/planning" style={{ fontSize: 13, fontWeight: 600 }}>
                All tasks →
              </Link>
            </div>

            {loadingAgenda ? (
              <PanelSkeleton rows={3} />
            ) : openTasks === 0 ? (
              <Empty description="Nothing due today" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                {[...(agenda?.overdue ?? []), ...(agenda?.dueToday ?? [])]
                  .slice(0, 7)
                  .map((task) => (
                    <div
                      key={task.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        padding: '10px 2px',
                        borderBottom: '1px solid var(--outline-variant)',
                      }}
                    >
                      <Checkbox
                        onChange={() => setTaskStatus({ id: task.id, status: 'DONE' })}
                        aria-label={`Complete ${task.title}`}
                      />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div
                          style={{
                            fontWeight: 600,
                            fontSize: 14,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                        >
                          {task.title}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                          {task.overdue ? 'Overdue' : 'Today'}
                          {task.projectName ? ` · ${task.projectName}` : ''}
                        </div>
                      </div>
                      <Tag style={{ margin: 0, fontSize: 11 }}>{task.priority}</Tag>
                    </div>
                  ))}
              </div>
            )}
          </div>
        </div>
      </Section>

      {/* -------------------------------------------------------- money --- */}
      <Section>
        <div className="lo-grid lo-grid--halves">
          <div className="lo-panel">
            {loadingMoney ? (
              <PanelSkeleton rows={5} height={340} />
            ) : (
              <TrendChart
                title="Cash flow this month"
                subtitle={`${dayjs(monthStart).format('D MMM')} – ${today.format('D MMM')}`}
                summary={`Daily income and expense from ${monthStart} to ${todayIso}. Total spent ${formatCurrency(money?.overview.totalExpense ?? 0, currency)}, total earned ${formatCurrency(money?.overview.totalIncome ?? 0, currency)}.`}
                hint="Transfers between your own accounts are excluded — moving money is not spending."
                data={cashFlow}
                xKey="date"
                xFormatter={formatShortDate}
                yFormatter={(value) => formatCurrency(value, currency, true)}
                series={[
                  { key: 'expense', label: 'Spent' },
                  { key: 'income', label: 'Earned' },
                ]}
                variant="area"
                height={280}
                tableColumns={[
                  { key: 'date', title: 'Date' },
                  { key: 'expense', title: 'Spent', align: 'right' },
                  { key: 'income', title: 'Earned', align: 'right' },
                ]}
                tableRows={cashFlow.map((row) => ({
                  date: row.date,
                  expense: formatCurrency(row.expense, currency),
                  income: formatCurrency(row.income, currency),
                }))}
              />
            )}
          </div>

          <div className="lo-panel">
            <h3 style={{ margin: '0 0 4px', fontSize: 'var(--title-md)', fontWeight: 700 }}>
              This month
            </h3>
            <p style={{ margin: '0 0 20px', fontSize: 13, color: 'var(--on-surface-variant)' }}>
              Net {formatCurrency(money?.overview.net ?? 0, currency)} · savings rate{' '}
              {Math.round((money?.overview.savingsRate ?? 0) * 100)}%
            </p>

            {(money?.budgets ?? []).slice(0, 4).map((budget) => (
              <div key={budget.id} style={{ marginBottom: 18 }}>
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontSize: 13,
                    marginBottom: 6,
                  }}
                >
                  <span style={{ fontWeight: 620 }}>{budget.name}</span>
                  <span className="tabular" style={{ color: 'var(--on-surface-variant)' }}>
                    {formatCurrency(budget.spent, currency)} / {formatCurrency(budget.amount, currency)}
                  </span>
                </div>
                <Progress
                  percent={Math.min(100, Math.round(budget.usedRatio * 100))}
                  showInfo={false}
                  size="small"
                  strokeColor={
                    budget.state === 'EXCEEDED'
                      ? 'var(--status-critical)'
                      : budget.state === 'WARNING'
                        ? 'var(--status-warning)'
                        : 'var(--on-surface)'
                  }
                  trailColor="var(--outline-variant)"
                />
                <div style={{ fontSize: 12, color: 'var(--on-surface-muted)', marginTop: 4 }}>
                  {budget.state === 'EXCEEDED'
                    ? `Over by ${formatCurrency(Math.abs(budget.remaining), currency)}`
                    : `${formatCurrency(budget.safeDailySpend, currency)}/day for ${budget.daysLeft} more days`}
                </div>
              </div>
            ))}

            {!money?.budgets.length && (
              <Empty
                description="No budgets yet"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                style={{ margin: '32px 0' }}
              >
                <Button size="small" onClick={() => navigate('/money')}>
                  Set one up
                </Button>
              </Empty>
            )}

            {(money?.insights ?? []).slice(0, 2).map((insight) => (
              <div
                key={insight.code}
                style={{
                  marginTop: 14,
                  padding: '10px 12px',
                  borderRadius: 'var(--radius-md)',
                  background: 'var(--surface-container)',
                  fontSize: 13,
                  display: 'flex',
                  gap: 8,
                }}
              >
                {insight.severity === 'positive' ? (
                  <TrendingUp size={16} style={{ flexShrink: 0, marginTop: 2 }} />
                ) : insight.severity === 'critical' || insight.severity === 'warning' ? (
                  <TrendingDown size={16} style={{ flexShrink: 0, marginTop: 2 }} />
                ) : (
                  <Bolt size={16} style={{ flexShrink: 0, marginTop: 2 }} />
                )}
                <div>
                  <strong style={{ display: 'block' }}>{insight.title}</strong>
                  <span style={{ color: 'var(--on-surface-variant)' }}>{insight.message}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </Section>

      {/* ------------------------------------------------------ heatmap --- */}
      <Section>
        <div className="lo-panel">
          <ContributionHeatmap
            title="Consistency"
            subtitle="Every check-in across every habit, last six months"
            summary={`Contribution heatmap of habit check-ins over the last 180 days. Darker cells mean more check-ins that day.`}
            cells={heatmap}
            unitLabel="check-in"
            height={140}
          />
        </div>
      </Section>
    </>
  )
}
