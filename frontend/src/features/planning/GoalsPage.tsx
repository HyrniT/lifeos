import { useState } from 'react'
import { Button, Form, Input, InputNumber, Modal, Progress, Select, Tag, message } from 'antd'
import { DatePicker } from 'antd'
import dayjs from 'dayjs'
import { Flag, Plus, Target } from 'lucide-react'
import { useCreateGoalMutation, useGoalsQuery, useUpdateGoalMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { DynamicIcon, EmptyState, PageHeader, PanelSkeleton, StaggerItem, StaggerList } from '@/components/ui'
import type { Goal, GoalStatus } from '@/types'

const PACE_COPY: Record<string, string> = {
  ahead: 'Ahead of schedule',
  'on-track': 'On track',
  behind: 'Behind schedule',
  unknown: 'No deadline set',
}

/* Short form for the corner tag. `unknown` is deliberately absent: it is a
   sentinel for "no deadline", not a state worth showing, and the footer already
   says so in words. */
const PACE_TAG: Record<string, string> = {
  ahead: 'ahead',
  'on-track': 'on track',
  behind: 'behind',
}

const STATUS_TAG: Partial<Record<GoalStatus, string>> = {
  ACHIEVED: 'achieved',
  PAUSED: 'paused',
  ABANDONED: 'abandoned',
}

export function GoalsPage() {
  const [open, setOpen] = useState(false)
  const [form] = Form.useForm()
  const { data: goals = [], isLoading } = useGoalsQuery()
  const [create, { isLoading: creating }] = useCreateGoalMutation()
  const [update] = useUpdateGoalMutation()

  const onCreate = async (values: Record<string, unknown>) => {
    try {
      await create({
        ...values,
        startDate: values.startDate ? (values.startDate as dayjs.Dayjs).format('YYYY-MM-DD') : null,
        targetDate: values.targetDate ? (values.targetDate as dayjs.Dayjs).format('YYYY-MM-DD') : null,
      } as Partial<Goal>).unwrap()
      message.success('Goal created')
      form.resetFields()
      setOpen(false)
    } catch (err) {
      message.error(errorMessage(err, 'Could not create the goal'))
    }
  }

  const bumpProgress = (goal: Goal, delta: number) => {
    const next = Math.max(0, Math.min(goal.targetValue, goal.currentValue + delta))
    update({ id: goal.id, patch: { currentValue: next } as Partial<Goal> })
  }

  return (
    <>
      <PageHeader
        title="Goals"
        subtitle="Measurable outcomes with a deadline. Progress is a number, not a feeling."
        actions={
          <Button type="primary" icon={<Plus size={16} />} onClick={() => setOpen(true)}>
            New goal
          </Button>
        }
      />

      {isLoading ? (
        <div className="lo-grid lo-grid--cards">
          {[0, 1, 2].map((index) => (
            <PanelSkeleton key={index} rows={3} />
          ))}
        </div>
      ) : !goals.length ? (
        <EmptyState
          icon={<Target size={22} />}
          title="No goals yet"
          description="A goal is a target value with a date. “Read 24 books by December” works; “read more” does not."
          action={
            <Button type="primary" icon={<Plus size={15} />} onClick={() => setOpen(true)}>
              Set your first goal
            </Button>
          }
        />
      ) : (
        <StaggerList>
          <div className="lo-grid lo-grid--cards">
            {goals.map((goal) => (
              <StaggerItem key={goal.id}>
                <div className="lo-panel" style={{ height: '100%' }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 14 }}>
                    <span
                      style={{
                        display: 'grid',
                        placeItems: 'center',
                        width: 40,
                        height: 40,
                        borderRadius: 12,
                        background: 'var(--surface-container)',
                        flexShrink: 0,
                      }}
                    >
                      <DynamicIcon name={goal.icon} size={18} />
                    </span>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 15 }}>{goal.title}</div>
                      <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
                        {goal.category}
                        {goal.targetDate && ` · due ${dayjs(goal.targetDate).format('D MMM YYYY')}`}
                      </div>
                    </div>
                    {/* A finished or parked goal is described by its status; only a
                        live one is described by its pace. Neither is shown when
                        there is nothing meaningful to say. */}
                    {(STATUS_TAG[goal.status] ?? PACE_TAG[goal.pace]) && (
                      <Tag
                        style={{ margin: 0 }}
                        color={
                          goal.status === 'ACHIEVED'
                            ? 'success'
                            : goal.status === 'ACTIVE' && goal.pace === 'behind'
                              ? 'warning'
                              : 'default'
                        }
                      >
                        {STATUS_TAG[goal.status] ?? PACE_TAG[goal.pace]}
                      </Tag>
                    )}
                  </div>

                  <Progress
                    percent={Math.round(goal.progress * 100)}
                    strokeColor="var(--on-surface)"
                    trailColor="var(--outline-variant)"
                  />

                  {/* Time-elapsed marker: progress alone does not say whether you
                      are keeping up with the clock. */}
                  {goal.timeElapsed != null && (
                    <div
                      style={{
                        position: 'relative',
                        height: 4,
                        marginTop: -4,
                        marginBottom: 8,
                      }}
                    >
                      <span
                        style={{
                          position: 'absolute',
                          left: `${Math.min(100, goal.timeElapsed * 100)}%`,
                          top: -8,
                          width: 2,
                          height: 14,
                          background: 'var(--on-surface-muted)',
                        }}
                        title={`${Math.round(goal.timeElapsed * 100)}% of the time has passed`}
                      />
                    </div>
                  )}

                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      fontSize: 13,
                      marginTop: 10,
                    }}
                  >
                    <span className="tabular">
                      {goal.currentValue} / {goal.targetValue} {goal.unit}
                    </span>
                    <span style={{ color: 'var(--on-surface-variant)' }}>
                      {goal.daysRemaining != null && goal.daysRemaining >= 0
                        ? `${goal.daysRemaining}d left`
                        : PACE_COPY[goal.pace]}
                    </span>
                  </div>

                  {goal.linkedTasks > 0 && (
                    <div style={{ fontSize: 12, color: 'var(--on-surface-muted)', marginTop: 6 }}>
                      {goal.linkedTasksDone}/{goal.linkedTasks} linked tasks done
                    </div>
                  )}

                  {goal.status === 'ACTIVE' && (
                    <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
                      <Button size="small" onClick={() => bumpProgress(goal, -1)}>
                        −1 {goal.unit}
                      </Button>
                      <Button size="small" type="primary" onClick={() => bumpProgress(goal, 1)}>
                        +1 {goal.unit}
                      </Button>
                    </div>
                  )}
                </div>
              </StaggerItem>
            ))}
          </div>
        </StaggerList>
      )}

      <Modal
        open={open}
        onCancel={() => setOpen(false)}
        title="New goal"
        okText="Create goal"
        confirmLoading={creating}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={onCreate}
          requiredMark={false}
          initialValues={{
            icon: 'flag',
            category: 'personal',
            unit: 'steps',
            targetValue: 10,
            currentValue: 0,
            startDate: dayjs(),
          }}
        >
          <Form.Item name="title" label="Goal" rules={[{ required: true, message: 'Name the goal' }]}>
            <Input placeholder="Read 24 books" autoFocus maxLength={200} />
          </Form.Item>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
            <Form.Item name="targetValue" label="Target">
              <InputNumber style={{ width: '100%' }} min={1} />
            </Form.Item>
            <Form.Item name="currentValue" label="Already done">
              <InputNumber style={{ width: '100%' }} min={0} />
            </Form.Item>
            <Form.Item name="unit" label="Unit">
              <Input placeholder="books" maxLength={32} />
            </Form.Item>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="startDate" label="Start">
              <DatePicker style={{ width: '100%' }} format="D MMM YYYY" />
            </Form.Item>
            <Form.Item name="targetDate" label="Deadline">
              <DatePicker style={{ width: '100%' }} format="D MMM YYYY" />
            </Form.Item>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <Form.Item name="category" label="Area">
              <Select
                options={['personal', 'health', 'career', 'finance', 'learning', 'relationships'].map(
                  (value) => ({ value, label: value }),
                )}
              />
            </Form.Item>
            <Form.Item name="icon" label="Icon">
              <Select
                options={['flag', 'target', 'trophy', 'rocket', 'mountain', 'compass', 'book-open'].map(
                  (icon) => ({
                    value: icon,
                    label: (
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                        <DynamicIcon name={icon} size={14} />
                        {icon}
                      </span>
                    ),
                  }),
                )}
              />
            </Form.Item>
          </div>

          <Form.Item name="description" label="Why this matters">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
