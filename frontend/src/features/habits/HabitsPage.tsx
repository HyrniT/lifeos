import { useMemo, useState } from 'react'
import {
  Button,
  Dropdown,
  Empty,
  Input,
  Segmented,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { motion } from 'framer-motion'
import {
  Archive,
  ArchiveRestore,
  Check,
  Flame,
  MoreVertical,
  Pencil,
  Plus,
  Search,
  Trash2,
  Undo2,
} from 'lucide-react'
import {
  useArchiveHabitMutation,
  useCheckInMutation,
  useDeleteHabitMutation,
  useHabitsQuery,
  useUndoCheckInMutation,
} from '@/app/api'
import { DynamicIcon, EmptyState, PageHeader, PanelSkeleton, StaggerItem, StaggerList } from '@/components/ui'
import { HabitFormModal } from './HabitFormModal'
import { HabitDetailDrawer } from './HabitDetailDrawer'
import type { Habit } from '@/types'
import './habits.css'

type Filter = 'active' | 'today' | 'archived'

export function HabitsPage() {
  const [filter, setFilter] = useState<Filter>('active')
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Habit | null>(null)
  const [creating, setCreating] = useState(false)
  const [detailId, setDetailId] = useState<string | null>(null)

  const { data: habits = [], isLoading } = useHabitsQuery({ includeArchived: true })
  const [checkIn] = useCheckInMutation()
  const [undoCheckIn] = useUndoCheckInMutation()
  const [archive] = useArchiveHabitMutation()
  const [remove] = useDeleteHabitMutation()

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase()
    return habits
      .filter((habit) => {
        if (filter === 'archived') return habit.archived
        if (habit.archived) return false
        if (filter === 'today') return !habit.doneToday
        return true
      })
      .filter((habit) => !term || habit.name.toLowerCase().includes(term))
  }, [habits, filter, search])

  const toggle = async (habit: Habit) => {
    try {
      if (habit.doneToday) {
        await undoCheckIn({ id: habit.id }).unwrap()
        message.info(`Undone: ${habit.name}`)
      } else {
        const result = await checkIn({ id: habit.id }).unwrap()
        message.success(
          result.milestoneReached
            ? `${result.currentStreak}-day streak on ${habit.name}!`
            : `${habit.name} · +${result.xpAwarded} XP`,
        )
      }
    } catch {
      message.error('That did not save. Try again.')
    }
  }

  return (
    <>
      <PageHeader
        title="Habits"
        subtitle="What you repeat is what you become. Check in daily and the streaks take care of themselves."
        actions={
          <>
            <Input
              allowClear
              prefix={<Search size={15} style={{ color: 'var(--on-surface-muted)' }} />}
              placeholder="Search habits"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              style={{ width: 220 }}
            />
            <Segmented
              value={filter}
              onChange={(value) => setFilter(value as Filter)}
              options={[
                { label: 'All active', value: 'active' },
                { label: 'Not done', value: 'today' },
                { label: 'Archived', value: 'archived' },
              ]}
            />
            <Button type="primary" icon={<Plus size={16} />} onClick={() => setCreating(true)}>
              New habit
            </Button>
          </>
        }
      />

      {isLoading ? (
        <div className="lo-grid lo-grid--cards">
          {[0, 1, 2, 3, 4, 5].map((index) => (
            <PanelSkeleton key={index} rows={3} />
          ))}
        </div>
      ) : !visible.length ? (
        <EmptyState
          icon={<Flame size={22} />}
          title={
            filter === 'archived'
              ? 'No archived habits'
              : search
                ? 'No habits match that search'
                : 'No habits yet'
          }
          description={
            filter === 'archived'
              ? 'Habits you archive keep their history and can be restored at any time.'
              : 'Start with one. A habit you actually do beats five you plan to.'
          }
          action={
            filter !== 'archived' && (
              <Button type="primary" icon={<Plus size={15} />} onClick={() => setCreating(true)}>
                Create your first habit
              </Button>
            )
          }
        />
      ) : (
        <StaggerList>
          <div className="lo-grid lo-grid--cards">
            {visible.map((habit) => (
              <StaggerItem key={habit.id}>
                <motion.article
                  className={`lo-habit${habit.doneToday ? ' is-done' : ''}`}
                  whileHover={{ y: -3 }}
                  transition={{ duration: 0.18, ease: [0.2, 0, 0, 1] }}
                >
                  <header className="lo-habit__head">
                    <button
                      type="button"
                      className="lo-habit__icon"
                      onClick={() => setDetailId(habit.id)}
                      aria-label={`Open ${habit.name}`}
                    >
                      <DynamicIcon name={habit.icon} size={20} />
                    </button>

                    <div className="lo-habit__meta">
                      <button
                        type="button"
                        className="lo-habit__name"
                        onClick={() => setDetailId(habit.id)}
                      >
                        {habit.name}
                      </button>
                      <span className="lo-habit__sub">
                        {habit.frequency === 'DAILY'
                          ? 'Every day'
                          : habit.frequency === 'SPECIFIC_DAYS'
                            ? `${habit.daysOfWeek.length} day${habit.daysOfWeek.length === 1 ? '' : 's'} a week`
                            : habit.frequency === 'WEEKLY_TARGET'
                              ? `${habit.targetPerPeriod}× per week`
                              : habit.frequency === 'MONTHLY_TARGET'
                                ? `${habit.targetPerPeriod}× per month`
                                : habit.intervalDays === 1
                                  ? 'Every day'
                                  : `Every ${habit.intervalDays} days`}
                        {' · '}
                        {habit.difficulty.toLowerCase()}
                      </span>
                    </div>

                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: [
                          {
                            key: 'edit',
                            icon: <Pencil size={14} />,
                            label: 'Edit',
                            onClick: () => setEditing(habit),
                          },
                          {
                            key: 'archive',
                            icon: habit.archived ? <ArchiveRestore size={14} /> : <Archive size={14} />,
                            label: habit.archived ? 'Restore' : 'Archive',
                            onClick: () => archive({ id: habit.id, archived: !habit.archived }),
                          },
                          { type: 'divider' },
                          {
                            key: 'delete',
                            icon: <Trash2 size={14} />,
                            label: 'Delete',
                            danger: true,
                            onClick: () => {
                              remove(habit.id)
                              message.success(`Deleted ${habit.name}`)
                            },
                          },
                        ],
                      }}
                    >
                      <button type="button" className="lo-habit__more" aria-label="Habit options">
                        <MoreVertical size={16} />
                      </button>
                    </Dropdown>
                  </header>

                  <div className="lo-habit__stats">
                    <div className="lo-habit__stat">
                      <span className="lo-habit__stat-value tabular">{habit.currentStreak}</span>
                      <span className="lo-habit__stat-label">streak</span>
                    </div>
                    <div className="lo-habit__stat">
                      <span className="lo-habit__stat-value tabular">{habit.longestStreak}</span>
                      <span className="lo-habit__stat-label">best</span>
                    </div>
                    <div className="lo-habit__stat">
                      <span className="lo-habit__stat-value tabular">
                        {Math.round((habit.completionRate30d ?? 0) * 100)}%
                      </span>
                      <span className="lo-habit__stat-label">30d</span>
                    </div>
                    <div className="lo-habit__stat">
                      <span className="lo-habit__stat-value tabular">{habit.totalCheckIns}</span>
                      <span className="lo-habit__stat-label">total</span>
                    </div>
                  </div>

                  {/* Last 14 days at a glance — a filled square is a completed day. */}
                  <div className="lo-habit__dots" aria-hidden>
                    {Array.from({ length: 14 }, (_, index) => {
                      const filled = index >= 14 - Math.min(14, habit.currentStreak)
                      return (
                        <span
                          key={index}
                          className={`lo-habit__dot${filled ? ' is-filled' : ''}`}
                        />
                      )
                    })}
                  </div>

                  <footer className="lo-habit__foot">
                    {habit.currentStreak >= 7 && (
                      <Tag style={{ margin: 0 }}>
                        <Flame size={11} style={{ marginRight: 3, verticalAlign: -1 }} />
                        {habit.currentStreak} days
                      </Tag>
                    )}
                    <span style={{ flex: 1 }} />
                    {habit.archived ? (
                      <Button size="small" onClick={() => archive({ id: habit.id, archived: false })}>
                        Restore
                      </Button>
                    ) : (
                      <Tooltip title={habit.doneToday ? 'Undo today’s check-in' : 'Mark done for today'}>
                        <Button
                          type={habit.doneToday ? 'default' : 'primary'}
                          icon={habit.doneToday ? <Undo2 size={15} /> : <Check size={15} />}
                          onClick={() => toggle(habit)}
                        >
                          {habit.doneToday ? 'Done' : 'Check in'}
                        </Button>
                      </Tooltip>
                    )}
                  </footer>
                </motion.article>
              </StaggerItem>
            ))}
          </div>
        </StaggerList>
      )}

      <HabitFormModal
        open={creating || Boolean(editing)}
        habit={editing}
        onClose={() => {
          setCreating(false)
          setEditing(null)
        }}
      />

      <HabitDetailDrawer habitId={detailId} onClose={() => setDetailId(null)} />
    </>
  )
}
