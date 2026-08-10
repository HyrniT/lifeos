import { useEffect } from 'react'
import { Checkbox, Form, Input, InputNumber, Modal, Select, TimePicker, message } from 'antd'
import dayjs from 'dayjs'
import { useCreateHabitMutation, useUpdateHabitMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { DynamicIcon } from '@/components/ui'
import type { Habit } from '@/types'

const ICON_CHOICES = [
  'target', 'dumbbell', 'book-open', 'droplets', 'moon', 'sun', 'brain',
  'heart-pulse', 'footprints', 'apple', 'code', 'music', 'palette', 'pen-line',
  'bike', 'coffee', 'leaf', 'wallet', 'phone-off', 'cigarette-off', 'utensils',
  'graduation-cap', 'languages', 'shower-head', 'bed', 'sparkles',
]

const DAYS = [
  { label: 'Mon', value: 1 },
  { label: 'Tue', value: 2 },
  { label: 'Wed', value: 3 },
  { label: 'Thu', value: 4 },
  { label: 'Fri', value: 5 },
  { label: 'Sat', value: 6 },
  { label: 'Sun', value: 7 },
]

export function HabitFormModal({
  open,
  habit,
  onClose,
}: {
  open: boolean
  habit: Habit | null
  onClose: () => void
}) {
  const [form] = Form.useForm()
  const [create, { isLoading: creating }] = useCreateHabitMutation()
  const [update, { isLoading: updating }] = useUpdateHabitMutation()

  const frequency = Form.useWatch('frequency', form)

  useEffect(() => {
    if (!open) return
    if (habit) {
      form.setFieldsValue({
        ...habit,
        reminderTime: habit.reminderTime ? dayjs(habit.reminderTime, 'HH:mm:ss') : null,
      })
    } else {
      form.resetFields()
      form.setFieldsValue({
        icon: 'target',
        type: 'BUILD',
        frequency: 'DAILY',
        difficulty: 'MEDIUM',
        unit: 'TIMES',
        targetPerPeriod: 1,
        targetValue: 1,
        intervalDays: 2,
        daysOfWeek: [1, 2, 3, 4, 5],
        category: 'general',
      })
    }
  }, [open, habit, form])

  const onSubmit = async (values: Record<string, unknown>) => {
    const payload = {
      ...values,
      reminderTime: values.reminderTime
        ? (values.reminderTime as dayjs.Dayjs).format('HH:mm:ss')
        : null,
      // Only send the shape the chosen frequency actually uses; sending stale
      // day lists would silently change how streaks are calculated.
      daysOfWeek: values.frequency === 'SPECIFIC_DAYS' ? values.daysOfWeek : [],
      intervalDays: values.frequency === 'INTERVAL' ? values.intervalDays : 1,
      targetPerPeriod:
        values.frequency === 'WEEKLY_TARGET' || values.frequency === 'MONTHLY_TARGET'
          ? values.targetPerPeriod
          : 1,
    }

    try {
      if (habit) {
        await update({ id: habit.id, patch: payload as Partial<Habit> }).unwrap()
        message.success('Habit updated')
      } else {
        await create(payload as Partial<Habit>).unwrap()
        message.success('Habit created')
      }
      onClose()
    } catch (err) {
      message.error(errorMessage(err, 'Could not save the habit'))
    }
  }

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={habit ? 'Edit habit' : 'New habit'}
      okText={habit ? 'Save changes' : 'Create habit'}
      confirmLoading={creating || updating}
      onOk={() => form.submit()}
      width={560}
      destroyOnHidden
    >
      <Form form={form} layout="vertical" onFinish={onSubmit} requiredMark={false}>
        <Form.Item
          name="name"
          label="Name"
          rules={[{ required: true, message: 'Give the habit a name' }]}
        >
          <Input placeholder="Read for 20 minutes" autoFocus maxLength={120} />
        </Form.Item>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="icon" label="Icon">
            <Select
              showSearch
              optionLabelProp="label"
              options={ICON_CHOICES.map((icon) => ({
                value: icon,
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                    <DynamicIcon name={icon} size={15} />
                    {icon.replace(/-/g, ' ')}
                  </span>
                ),
              }))}
            />
          </Form.Item>

          <Form.Item name="type" label="Type">
            <Select
              options={[
                { value: 'BUILD', label: 'Build — do more of this' },
                { value: 'QUIT', label: 'Quit — do less of this' },
              ]}
            />
          </Form.Item>
        </div>

        <Form.Item name="frequency" label="How often">
          <Select
            options={[
              { value: 'DAILY', label: 'Every day' },
              { value: 'SPECIFIC_DAYS', label: 'On specific weekdays' },
              { value: 'WEEKLY_TARGET', label: 'N times per week' },
              { value: 'MONTHLY_TARGET', label: 'N times per month' },
              { value: 'INTERVAL', label: 'Every N days' },
            ]}
          />
        </Form.Item>

        {frequency === 'SPECIFIC_DAYS' && (
          <Form.Item name="daysOfWeek" label="Which days">
            <Checkbox.Group options={DAYS} />
          </Form.Item>
        )}

        {(frequency === 'WEEKLY_TARGET' || frequency === 'MONTHLY_TARGET') && (
          <Form.Item
            name="targetPerPeriod"
            label={`Times per ${frequency === 'WEEKLY_TARGET' ? 'week' : 'month'}`}
          >
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
        )}

        {frequency === 'INTERVAL' && (
          <Form.Item name="intervalDays" label="Repeat every (days)">
            <InputNumber min={1} max={365} style={{ width: '100%' }} />
          </Form.Item>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
          <Form.Item name="unit" label="Unit">
            <Select
              options={[
                { value: 'TIMES', label: 'times' },
                { value: 'MINUTES', label: 'minutes' },
                { value: 'HOURS', label: 'hours' },
                { value: 'PAGES', label: 'pages' },
                { value: 'STEPS', label: 'steps' },
                { value: 'KILOMETRES', label: 'km' },
                { value: 'MILLILITRES', label: 'ml' },
                { value: 'GRAMS', label: 'g' },
                { value: 'CUSTOM', label: 'custom' },
              ]}
            />
          </Form.Item>

          <Form.Item name="targetValue" label="Target">
            <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="difficulty"
            label="Difficulty"
            tooltip="Drives how much XP a check-in is worth."
          >
            <Select
              options={[
                { value: 'TRIVIAL', label: 'Trivial · 5 XP' },
                { value: 'EASY', label: 'Easy · 10 XP' },
                { value: 'MEDIUM', label: 'Medium · 20 XP' },
                { value: 'HARD', label: 'Hard · 35 XP' },
                { value: 'EPIC', label: 'Epic · 60 XP' },
              ]}
            />
          </Form.Item>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item name="category" label="Category">
            <Select
              options={[
                'general', 'health', 'fitness', 'mind', 'learning',
                'work', 'money', 'social', 'creative', 'home',
              ].map((value) => ({ value, label: value }))}
            />
          </Form.Item>

          <Form.Item name="reminderTime" label="Daily reminder">
            <TimePicker format="HH:mm" style={{ width: '100%' }} minuteStep={5} />
          </Form.Item>
        </div>

        <Form.Item name="description" label="Notes">
          <Input.TextArea rows={2} maxLength={500} placeholder="Why does this matter to you?" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
