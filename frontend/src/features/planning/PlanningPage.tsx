import { useMemo, useState } from 'react'
import {
  Button,
  Checkbox,
  DatePicker,
  Dropdown,
  Form,
  Input,
  Modal,
  Segmented,
  Select,
  Tag,
  Tooltip,
  message,
} from 'antd'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { motion } from 'framer-motion'
import {
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  Inbox,
  ListTodo,
  MoreVertical,
  Plus,
  Trash2,
  X,
} from 'lucide-react'
import {
  useCreateTaskMutation,
  useDeleteTaskMutation,
  usePlanningStatisticsQuery,
  useProjectsQuery,
  useSetTaskStatusMutation,
  useTasksQuery,
} from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { BarSeriesChart, StatTile, TrendChart, formatMinutes, formatShortDate } from '@/components/charts'
import { EmptyState, PageHeader, PanelSkeleton, Section, StaggerItem, StaggerList } from '@/components/ui'
import type { Priority, Task, TaskStatus } from '@/types'

type View = 'today' | 'upcoming' | 'all' | 'done'

const PRIORITY_LABEL: Record<Priority, string> = {
  P1: 'Urgent & important',
  P2: 'Important',
  P3: 'Normal',
  P4: 'Someday',
}

const QUADRANT_LABEL: Record<string, string> = {
  Q1: 'Do now',
  Q2: 'Schedule',
  Q3: 'Delegate',
  Q4: 'Drop',
}

export function PlanningPage() {
  const [composerOpen, setComposerOpen] = useState(false)
  const [form] = Form.useForm()

  // A project card links here with ?project=<id>. The filter lives in the URL
  // rather than in component state so the link can be shared, bookmarked and
  // backed out of.
  const [searchParams, setSearchParams] = useSearchParams()
  const projectId = searchParams.get('project') ?? undefined

  // Arriving from a project card opens on every open task, not on today's slice:
  // a project's work is spread over weeks, so "Today" would answer a click on
  // "3/6 tasks done" with an empty screen.
  const [view, setView] = useState<View>(projectId ? 'all' : 'today')

  // Filtering server-side rather than in the browser: the endpoint already takes
  // projectId, and it keeps the two lists from disagreeing about what belongs.
  const { data: tasks = [], isLoading } = useTasksQuery(projectId ? { projectId } : undefined)
  const { data: projects = [] } = useProjectsQuery()
  const activeProject = projects.find((project) => project.id === projectId)
  const { data: stats } = usePlanningStatisticsQuery(30)
  const [createTask, { isLoading: creating }] = useCreateTaskMutation()
  const [setStatus] = useSetTaskStatusMutation()
  const [removeTask] = useDeleteTaskMutation()

  const today = dayjs().format('YYYY-MM-DD')

  const visible = useMemo(() => {
    const open = tasks.filter((task) => task.status !== 'DONE' && task.status !== 'CANCELLED')
    switch (view) {
      case 'today':
        return open.filter(
          (task) =>
            task.overdue ||
            task.dueDate === today ||
            task.scheduledFor === today ||
            (!task.dueDate && task.priority === 'P1'),
        )
      case 'upcoming':
        return open.filter((task) => task.dueDate && task.dueDate > today)
      case 'done':
        return tasks.filter((task) => task.status === 'DONE')
      default:
        return open
    }
  }, [tasks, view, today])

  const quadrantRows = Object.entries(stats?.byQuadrant ?? {}).map(([key, count]) => ({
    quadrant: QUADRANT_LABEL[key] ?? key,
    count,
  }))

  const timeline = (stats?.timeline ?? []).map((point) => ({
    date: point.date,
    completed: point.completed,
    focus: point.focusMinutes,
  }))

  const onCreate = async (values: Record<string, unknown>) => {
    try {
      await createTask({
        ...values,
        dueDate: values.dueDate ? (values.dueDate as dayjs.Dayjs).format('YYYY-MM-DD') : null,
      } as Partial<Task>).unwrap()
      message.success('Task added')
      form.resetFields()
      setComposerOpen(false)
    } catch (err) {
      message.error(errorMessage(err, 'Could not add the task'))
    }
  }

  const toggleTask = (task: Task) => {
    const next: TaskStatus = task.status === 'DONE' ? 'TODO' : 'DONE'
    setStatus({ id: task.id, status: next })
  }

  return (
    <>
      <PageHeader
        title="Tasks"
        subtitle="What needs doing, when it is due, and how much of it you actually finish."
        actions={
          <>
            <Segmented
              value={view}
              onChange={(value) => setView(value as View)}
              options={[
                { label: 'Today', value: 'today' },
                { label: 'Upcoming', value: 'upcoming' },
                { label: 'All open', value: 'all' },
                { label: 'Done', value: 'done' },
              ]}
            />
            <Button type="primary" icon={<Plus size={16} />} onClick={() => setComposerOpen(true)}>
              New task
            </Button>
          </>
        }
      />

      <div className="lo-grid lo-grid--stats">
        <StatTile label="Open" value={stats?.tasksOpen ?? 0} icon={<ListTodo size={17} />} />
        <StatTile
          label="Overdue"
          value={stats?.tasksOverdue ?? 0}
          caption={stats?.tasksOverdue ? 'Needs attention' : 'All clear'}
          icon={<AlertTriangle size={17} />}
        />
        <StatTile
          label="Done last 7 days"
          value={stats?.tasksCompletedLast7d ?? 0}
          caption={`${Math.round((stats?.completionRate30d ?? 0) * 100)}% completion rate`}
          icon={<CheckCircle2 size={17} />}
        />
        <StatTile
          label="Focus last 7 days"
          value={formatMinutes(stats?.focusMinutesLast7d ?? 0)}
          caption={`Best day: ${stats?.mostProductiveDay ?? '—'}`}
          icon={<CalendarDays size={17} />}
        />
      </div>

      <Section>
        {projectId && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
              flexWrap: 'wrap',
              marginBottom: 12,
              padding: '10px 14px',
              borderRadius: 'var(--radius-md)',
              background: 'var(--surface-container-low)',
              border: '1px solid var(--outline-variant)',
              fontSize: 13,
            }}
          >
            <span>
              Showing only <strong>{activeProject?.name ?? 'one project'}</strong>. The tiles above
              still count every project.
            </span>
            <Button
              size="small"
              icon={<X size={14} />}
              onClick={() => setSearchParams({}, { replace: true })}
            >
              Clear filter
            </Button>
          </div>
        )}

        {isLoading ? (
          <PanelSkeleton rows={6} />
        ) : !visible.length ? (
          <EmptyState
            icon={<Inbox size={22} />}
            title={view === 'done' ? 'Nothing completed yet' : 'Nothing here'}
            description={
              projectId
                ? 'Nothing in this project matches the current view. Clear the filter to see everything.'
                : view === 'today'
                  ? 'No tasks due today. Either you are on top of things, or nothing has a date yet.'
                  : 'Add a task and it will appear in the right view automatically.'
            }
            action={
              view !== 'done' && (
                <Button type="primary" icon={<Plus size={15} />} onClick={() => setComposerOpen(true)}>
                  Add a task
                </Button>
              )
            }
          />
        ) : (
          <div className="lo-panel" style={{ padding: '8px 16px' }}>
            <StaggerList>
              {visible.map((task) => (
                <StaggerItem key={task.id}>
                  <motion.div
                    whileHover={{ x: 2 }}
                    transition={{ duration: 0.14 }}
                    style={{
                      display: 'flex',
                      alignItems: 'flex-start',
                      gap: 12,
                      padding: '14px 4px',
                      borderBottom: '1px solid var(--outline-variant)',
                    }}
                  >
                    <Checkbox
                      checked={task.status === 'DONE'}
                      onChange={() => toggleTask(task)}
                      style={{ marginTop: 2 }}
                      aria-label={`Toggle ${task.title}`}
                    />

                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div
                        style={{
                          fontWeight: 620,
                          fontSize: 15,
                          textDecoration: task.status === 'DONE' ? 'line-through' : 'none',
                          opacity: task.status === 'DONE' ? 0.5 : 1,
                        }}
                      >
                        {task.title}
                      </div>

                      <div
                        style={{
                          display: 'flex',
                          gap: 8,
                          flexWrap: 'wrap',
                          marginTop: 4,
                          fontSize: 12,
                          color: 'var(--on-surface-muted)',
                          alignItems: 'center',
                        }}
                      >
                        {task.dueDate && (
                          <span style={{ color: task.overdue ? 'var(--status-critical)' : undefined }}>
                            {task.overdue ? '⚠ ' : ''}
                            {dayjs(task.dueDate).format('D MMM')}
                          </span>
                        )}
                        {task.projectName && <span>· {task.projectName}</span>}
                        {task.subtaskCount > 0 && (
                          <span>
                            · {task.subtasksDone}/{task.subtaskCount} subtasks
                          </span>
                        )}
                        {task.estimateMinutes && <span>· est {formatMinutes(task.estimateMinutes)}</span>}
                        {task.actualMinutes > 0 && <span>· spent {formatMinutes(task.actualMinutes)}</span>}
                        {task.tags.map((tag) => (
                          <Tag key={tag} style={{ margin: 0, fontSize: 11 }}>
                            {tag}
                          </Tag>
                        ))}
                      </div>
                    </div>

                    <Tooltip title={PRIORITY_LABEL[task.priority]}>
                      <Tag
                        style={{
                          margin: 0,
                          fontWeight: 700,
                          borderColor:
                            task.priority === 'P1' ? 'var(--on-surface)' : 'var(--outline-variant)',
                        }}
                      >
                        {task.priority}
                      </Tag>
                    </Tooltip>

                    <Dropdown
                      trigger={['click']}
                      menu={{
                        items: [
                          {
                            key: 'progress',
                            label: 'Mark in progress',
                            onClick: () => setStatus({ id: task.id, status: 'IN_PROGRESS' }),
                          },
                          {
                            key: 'cancel',
                            label: 'Cancel task',
                            onClick: () => setStatus({ id: task.id, status: 'CANCELLED' }),
                          },
                          { type: 'divider' },
                          {
                            key: 'delete',
                            icon: <Trash2 size={14} />,
                            label: 'Delete',
                            danger: true,
                            onClick: () => removeTask(task.id),
                          },
                        ],
                      }}
                    >
                      <Button type="text" size="small" icon={<MoreVertical size={15} />} />
                    </Dropdown>
                  </motion.div>
                </StaggerItem>
              ))}
            </StaggerList>
          </div>
        )}
      </Section>

      <Section title="How you actually work">
        <div className="lo-grid lo-grid--halves">
          <div className="lo-panel">
            <TrendChart
              title="Throughput"
              subtitle="Tasks completed and minutes focused, last 30 days"
              summary="Daily count of completed tasks alongside minutes of focused work over the last 30 days."
              hint="Two measures on one axis only because both are counts of roughly the same magnitude; if they diverge, read the table."
              data={timeline}
              xKey="date"
              xFormatter={formatShortDate}
              series={[
                { key: 'completed', label: 'Tasks done' },
                { key: 'focus', label: 'Focus minutes', format: (v) => formatMinutes(v) },
              ]}
              height={260}
              tableColumns={[
                { key: 'date', title: 'Date' },
                { key: 'completed', title: 'Tasks done', align: 'right' },
                { key: 'focus', title: 'Focus minutes', align: 'right' },
              ]}
              tableRows={timeline}
            />
          </div>

          <div className="lo-panel">
            <BarSeriesChart
              title="Eisenhower split"
              subtitle="Where your open tasks sit"
              summary="Open tasks grouped into the four Eisenhower quadrants."
              hint="Derived from priority and due date, so it can never disagree with them."
              data={quadrantRows}
              xKey="quadrant"
              series={[{ key: 'count', label: 'Open tasks' }]}
              layout="horizontal"
              height={260}
              tableColumns={[
                { key: 'quadrant', title: 'Quadrant' },
                { key: 'count', title: 'Open tasks', align: 'right' },
              ]}
              tableRows={quadrantRows}
            />
          </div>
        </div>
      </Section>

      <Modal
        open={composerOpen}
        onCancel={() => setComposerOpen(false)}
        title="New task"
        okText="Add task"
        confirmLoading={creating}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={onCreate}
          requiredMark={false}
          // While a project filter is on, a new task belongs to that project unless
          // the user says otherwise — otherwise it is created and then vanishes.
          initialValues={{ priority: 'P3', status: 'TODO', recurrence: 'NONE', projectId }}
        >
          <Form.Item name="title" label="Task" rules={[{ required: true, message: 'What needs doing?' }]}>
            <Input placeholder="Draft the quarterly review" autoFocus maxLength={200} />
          </Form.Item>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="dueDate" label="Due">
              <DatePicker style={{ width: '100%' }} format="D MMM YYYY" />
            </Form.Item>
            <Form.Item name="priority" label="Priority">
              <Select
                options={(Object.keys(PRIORITY_LABEL) as Priority[]).map((value) => ({
                  value,
                  label: `${value} · ${PRIORITY_LABEL[value]}`,
                }))}
              />
            </Form.Item>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="projectId" label="Project">
              <Select
                allowClear
                placeholder="None"
                options={projects.map((project) => ({ value: project.id, label: project.name }))}
              />
            </Form.Item>
            <Form.Item name="recurrence" label="Repeat">
              <Select
                options={[
                  { value: 'NONE', label: 'Does not repeat' },
                  { value: 'DAILY', label: 'Daily' },
                  { value: 'WEEKDAYS', label: 'Weekdays' },
                  { value: 'WEEKLY', label: 'Weekly' },
                  { value: 'BIWEEKLY', label: 'Every two weeks' },
                  { value: 'MONTHLY', label: 'Monthly' },
                  { value: 'YEARLY', label: 'Yearly' },
                ]}
              />
            </Form.Item>
          </div>

          <Form.Item name="notes" label="Notes">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
