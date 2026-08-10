import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Checkbox,
  Divider,
  Select,
  Skeleton,
  Switch,
  TimePicker,
  Tooltip,
  message,
} from 'antd'
import dayjs from 'dayjs'
import { BellRing, Clock, Monitor, Moon, Send, Smartphone } from 'lucide-react'
import {
  useNotificationKindsQuery,
  useNotificationPreferencesQuery,
  useSendTestNotificationMutation,
  useUpdateNotificationPreferencesMutation,
} from '@/app/api'
import { useAppSelector } from '@/app/hooks'
import { errorMessage } from '@/app/baseQuery'
import {
  currentPushState,
  isPushSupported,
  subscribeToPush,
  unsubscribeFromPush,
  type PushState,
} from '@/features/notifications/push'
import type { NotificationPreferences } from '@/types'

/** Offered lead times must match what the scheduler actually emits. */
const LEAD_TIMES = [
  { value: 10080, label: '1 week before' },
  { value: 4320, label: '3 days before' },
  { value: 1440, label: '1 day before' },
  { value: 480, label: '8 hours before' },
  { value: 120, label: '2 hours before' },
  { value: 60, label: '1 hour before' },
  { value: 30, label: '30 minutes before' },
  { value: 15, label: '15 minutes before' },
]

function Row({
  icon,
  title,
  description,
  control,
}: {
  icon?: React.ReactNode
  title: string
  description?: string
  control: React.ReactNode
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '14px 0',
        borderBottom: '1px solid var(--outline-variant)',
      }}
    >
      {icon && <span style={{ color: 'var(--on-surface-variant)', flexShrink: 0 }}>{icon}</span>}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontWeight: 600, fontSize: 14 }}>{title}</div>
        {description && (
          <div style={{ fontSize: 12, color: 'var(--on-surface-variant)' }}>{description}</div>
        )}
      </div>
      <div style={{ flexShrink: 0 }}>{control}</div>
    </div>
  )
}

export function NotificationSettings() {
  const token = useAppSelector((state) => state.auth.accessToken)
  const { data: prefs, isLoading } = useNotificationPreferencesQuery()
  const { data: kinds = [] } = useNotificationKindsQuery()
  const [update] = useUpdateNotificationPreferencesMutation()
  const [sendTest, { isLoading: testing }] = useSendTestNotificationMutation()

  const [pushState, setPushState] = useState<PushState>('default')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    void currentPushState().then(setPushState)
  }, [])

  const save = async (patch: Partial<NotificationPreferences>) => {
    try {
      await update(patch).unwrap()
    } catch (err) {
      message.error(errorMessage(err, 'Could not save that setting'))
    }
  }

  const togglePush = async () => {
    if (!token) return
    setBusy(true)
    try {
      if (pushState === 'subscribed') {
        await unsubscribeFromPush(token)
        message.success('This device will no longer receive push notifications')
      } else {
        const result = await subscribeToPush(token)
        if (!result.ok) {
          message.error(result.reason ?? 'Could not enable push notifications')
        } else {
          message.success('This device is now registered for notifications')
        }
      }
      setPushState(await currentPushState())
    } finally {
      setBusy(false)
    }
  }

  const runTest = async () => {
    try {
      const result = await sendTest().unwrap()
      if (result.pushDevices === 0) {
        message.warning(
          'Sent in-app only — no device is registered for push yet. Turn on "This device" above.',
        )
      } else {
        message.success(`Sent to ${result.pushDevices} device(s). Close this tab to test properly.`)
      }
    } catch (err) {
      message.error(errorMessage(err, 'Could not send the test notification'))
    }
  }

  if (isLoading || !prefs) {
    return (
      <div className="lo-panel" style={{ maxWidth: 640 }}>
        <Skeleton active paragraph={{ rows: 8 }} />
      </div>
    )
  }

  const muted = new Set(prefs.mutedKinds ?? [])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 640 }}>
      {/* ------------------------------------------------------- channels -- */}
      <div className="lo-panel">
        <h3 style={{ margin: '0 0 4px', fontSize: 'var(--title-md)', fontWeight: 700 }}>
          How you get told
        </h3>
        <p style={{ margin: '0 0 8px', fontSize: 13, color: 'var(--on-surface-variant)' }}>
          In-app alerts only arrive while a tab is open. Push is what reaches you when the app is
          closed — which is the whole point of a deadline reminder.
        </p>

        {!isPushSupported() && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 12 }}
            message="This browser cannot receive push notifications"
            description="Safari on iOS only supports them once the app has been added to the Home Screen."
          />
        )}
        {pushState === 'denied' && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="Notifications are blocked for this site"
            description="The browser will not ask again. Allow notifications in the site settings (the icon beside the address bar), then reload."
          />
        )}
        {!prefs.pushAvailable && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="Push delivery is not configured on the server"
            description="Set LIFEOS_PUSH_VAPID_PUBLIC_KEY and LIFEOS_PUSH_VAPID_PRIVATE_KEY on notification-service."
          />
        )}

        <Row
          icon={<Monitor size={17} />}
          title="In-app alerts"
          description="Toasts and the bell, while the app is open"
          control={
            <Switch
              checked={prefs.inAppEnabled}
              onChange={(v) => save({ inAppEnabled: v })}
            />
          }
        />
        <Row
          icon={<Smartphone size={17} />}
          title="Push notifications"
          description={`Delivered with the app closed · ${prefs.pushDevices} device${prefs.pushDevices === 1 ? '' : 's'} registered`}
          control={
            <Switch
              checked={prefs.pushEnabled}
              disabled={!prefs.pushAvailable}
              onChange={(v) => save({ pushEnabled: v })}
            />
          }
        />
        <Row
          icon={<BellRing size={17} />}
          title="This device"
          description={
            pushState === 'subscribed'
              ? 'Registered to receive push here'
              : 'Not registered yet — this is the step people miss'
          }
          control={
            <Button
              size="small"
              type={pushState === 'subscribed' ? 'default' : 'primary'}
              loading={busy}
              disabled={!isPushSupported() || pushState === 'denied' || !prefs.pushAvailable}
              onClick={togglePush}
            >
              {pushState === 'subscribed' ? 'Unregister' : 'Enable on this device'}
            </Button>
          }
        />

        <div style={{ marginTop: 16 }}>
          <Button icon={<Send size={15} />} loading={testing} onClick={runTest}>
            Send a test notification
          </Button>
        </div>
      </div>

      {/* ------------------------------------------------------ deadlines -- */}
      <div className="lo-panel">
        <h3 style={{ margin: '0 0 4px', fontSize: 'var(--title-md)', fontWeight: 700 }}>
          Before a deadline
        </h3>
        <p style={{ margin: '0 0 14px', fontSize: 13, color: 'var(--on-surface-variant)' }}>
          Reminders fire in your local time ({prefs.timezone}). A task with no time of day is
          treated as due at 18:00.
        </p>

        <div style={{ marginBottom: 8, fontWeight: 600, fontSize: 14 }}>Warn me</div>
        <Checkbox.Group
          value={prefs.leadTimeMinutes}
          onChange={(values) => save({ leadTimeMinutes: values as number[] })}
          style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}
          options={LEAD_TIMES}
        />

        <Divider style={{ margin: '18px 0 0' }} />

        <Row
          icon={<Clock size={17} />}
          title="At the deadline itself"
          control={
            <Switch
              checked={prefs.remindAtDeadline}
              onChange={(v) => save({ remindAtDeadline: v })}
            />
          }
        />
        <Row
          title="Once a day while overdue"
          description="At 09:00, for up to a week"
          control={
            <Switch
              checked={prefs.remindWhenOverdue}
              onChange={(v) => save({ remindWhenOverdue: v })}
            />
          }
        />
        <Row
          title="Morning summary"
          description="One digest of what is due today"
          control={
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <TimePicker
                size="small"
                format="HH:mm"
                minuteStep={15}
                allowClear={false}
                disabled={!prefs.dailySummaryEnabled}
                value={dayjs(prefs.dailySummaryTime, 'HH:mm:ss')}
                onChange={(value) =>
                  value && save({ dailySummaryTime: value.format('HH:mm:ss') })
                }
                style={{ width: 92 }}
              />
              <Switch
                checked={prefs.dailySummaryEnabled}
                onChange={(v) => save({ dailySummaryEnabled: v })}
              />
            </div>
          }
        />
      </div>

      {/* ---------------------------------------------------- quiet hours -- */}
      <div className="lo-panel">
        <h3 style={{ margin: '0 0 4px', fontSize: 'var(--title-md)', fontWeight: 700 }}>
          Quiet hours
        </h3>
        <p style={{ margin: '0 0 8px', fontSize: 13, color: 'var(--on-surface-variant)' }}>
          Nothing is dropped during quiet hours — it is held and delivered when they end. A streak
          about to break still gets through.
        </p>

        <Row
          icon={<Moon size={17} />}
          title="Hold notifications overnight"
          control={
            <Switch
              checked={prefs.quietHoursEnabled}
              onChange={(v) => save({ quietHoursEnabled: v })}
            />
          }
        />
        <Row
          title="From / until"
          control={
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <TimePicker
                size="small"
                format="HH:mm"
                minuteStep={30}
                allowClear={false}
                disabled={!prefs.quietHoursEnabled}
                value={dayjs(prefs.quietFrom, 'HH:mm:ss')}
                onChange={(value) => value && save({ quietFrom: value.format('HH:mm:ss') })}
                style={{ width: 84 }}
              />
              <span style={{ color: 'var(--on-surface-muted)' }}>→</span>
              <TimePicker
                size="small"
                format="HH:mm"
                minuteStep={30}
                allowClear={false}
                disabled={!prefs.quietHoursEnabled}
                value={dayjs(prefs.quietTo, 'HH:mm:ss')}
                onChange={(value) => value && save({ quietTo: value.format('HH:mm:ss') })}
                style={{ width: 84 }}
              />
            </div>
          }
        />
      </div>

      {/* ---------------------------------------------------------- kinds -- */}
      <div className="lo-panel">
        <h3 style={{ margin: '0 0 4px', fontSize: 'var(--title-md)', fontWeight: 700 }}>
          What to tell me about
        </h3>
        <p style={{ margin: '0 0 8px', fontSize: 13, color: 'var(--on-surface-variant)' }}>
          Switch off anything that is not useful. Everything else stays on.
        </p>

        {kinds.map((kind) => (
          <Row
            key={kind.code}
            title={kind.label}
            description={kind.description}
            control={
              <Switch
                checked={!muted.has(kind.code)}
                onChange={(enabled) => {
                  const next = new Set(muted)
                  if (enabled) next.delete(kind.code)
                  else next.add(kind.code)
                  save({ mutedKinds: Array.from(next) })
                }}
              />
            }
          />
        ))}
      </div>
    </div>
  )
}
