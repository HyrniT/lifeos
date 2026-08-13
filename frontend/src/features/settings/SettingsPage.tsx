import { useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import dayjs from 'dayjs'
import { KeyRound, Laptop, ShieldCheck, ShieldOff } from 'lucide-react'
import {
  useChangePasswordMutation,
  useConfirmTotpMutation,
  useDisableTotpMutation,
  useMeQuery,
  useRevokeSessionsMutation,
  useSessionsQuery,
  useSetupTotpMutation,
  useUpdateProfileMutation,
} from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { profileUpdated } from '@/features/auth/authSlice'
import { themeSet } from '@/app/uiSlice'
import { BASE_CURRENCY } from '@/app/money'
import { PageHeader } from '@/components/ui'
import { NotificationSettings } from './NotificationSettings'
import { deviceTimeZone, offsetLabel, timeZoneOptions } from './timezones'
import type { TotpSetup, UserView } from '@/types'

/** Wall-clock time in a zone right now, or '' if the zone is not a real one. */
function localTimeIn(zone: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, {
      timeZone: zone,
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date())
  } catch {
    return ''
  }
}

export function SettingsPage() {
  const dispatch = useAppDispatch()
  const theme = useAppSelector((state) => state.ui.theme)

  const { data: me } = useMeQuery()
  const { data: sessions = [] } = useSessionsQuery()
  const [updateProfile, { isLoading: savingProfile }] = useUpdateProfileMutation()
  const [changePassword, { isLoading: changingPassword }] = useChangePasswordMutation()
  const [revokeSessions] = useRevokeSessionsMutation()
  const [setupTotp, { isLoading: settingUp }] = useSetupTotpMutation()
  const [confirmTotp, { isLoading: confirming }] = useConfirmTotpMutation()
  const [disableTotp] = useDisableTotpMutation()

  const [totp, setTotp] = useState<TotpSetup | null>(null)
  const [totpCode, setTotpCode] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)

  const [profileForm] = Form.useForm<UserView>()
  const deviceZone = deviceTimeZone()
  // Several hundred zones, each needing an Intl formatter to price its offset:
  // built once rather than on every keystroke in the search box.
  const timezoneOptions = useMemo(() => timeZoneOptions(me?.timezone), [me?.timezone])
  const selectedZone = Form.useWatch('timezone', profileForm) ?? me?.timezone

  const onSaveProfile = async (values: Partial<UserView>) => {
    try {
      const updated = await updateProfile(values).unwrap()
      dispatch(profileUpdated(updated))
      message.success('Profile saved')
    } catch (err) {
      message.error(errorMessage(err, 'Could not save your profile'))
    }
  }

  const onChangePassword = async (values: { currentPassword: string; newPassword: string }) => {
    try {
      await changePassword(values).unwrap()
      message.success('Password changed. Other devices have been signed out.')
    } catch (err) {
      message.error(errorMessage(err, 'Could not change your password'))
    }
  }

  const startTotp = async () => {
    try {
      setTotp(await setupTotp().unwrap())
    } catch (err) {
      message.error(errorMessage(err, 'Could not start two-factor setup'))
    }
  }

  const finishTotp = async () => {
    try {
      const result = await confirmTotp({ code: totpCode }).unwrap()
      setRecoveryCodes(result.recoveryCodes)
      setTotp(null)
      setTotpCode('')
      message.success('Two-factor authentication is on')
    } catch (err) {
      message.error(errorMessage(err, 'That code was not accepted'))
    }
  }

  return (
    <>
      <PageHeader title="Settings" subtitle="Your profile, security and how the app looks." />

      <Tabs
        items={[
          // ------------------------------------------------------- profile
          {
            key: 'profile',
            label: 'Profile',
            children: (
              <div className="lo-panel" style={{ maxWidth: 560 }}>
                <Form
                  form={profileForm}
                  layout="vertical"
                  requiredMark={false}
                  initialValues={me}
                  key={me?.id}
                  onFinish={onSaveProfile}
                >
                  <Form.Item name="displayName" label="Display name">
                    <Input maxLength={120} />
                  </Form.Item>

                  <Form.Item label="E-mail">
                    <Input value={me?.email} disabled />
                  </Form.Item>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                    {/* Shown rather than hidden so the unit every figure on the
                        money screens is quoted in is never left to guesswork.
                        Read-only: LifeOS stores one currency. */}
                    <Form.Item
                      label="Currency"
                      extra={
                        <span style={{ fontSize: 12 }}>
                          Every amount in LifeOS is in {BASE_CURRENCY}.
                        </span>
                      }
                    >
                      <Input value={BASE_CURRENCY} disabled />
                    </Form.Item>
                    <Form.Item
                      name="timezone"
                      label="Time zone"
                      // Reminders fire in this zone, so the useful confirmation is
                      // not the identifier but the clock: "is it really 21:40
                      // where I am?" answers it without knowing IANA naming.
                      extra={
                        <span style={{ fontSize: 12 }}>
                          {selectedZone && localTimeIn(selectedZone)
                            ? `Now ${localTimeIn(selectedZone)} there · ${offsetLabel(selectedZone)}`
                            : 'Reminders and deadlines fire in this zone.'}
                          {selectedZone !== deviceZone && (
                            <>
                              {' · '}
                              <Typography.Link
                                onClick={() => profileForm.setFieldValue('timezone', deviceZone)}
                              >
                                use this device
                              </Typography.Link>
                            </>
                          )}
                        </span>
                      }
                    >
                      <Select
                        showSearch
                        placeholder="Search for a city or zone"
                        // Match our own haystack (identifier, spaced name and
                        // offset) instead of the rendered label, so "hochiminh"
                        // and "+07" both find Asia/Ho_Chi_Minh.
                        filterOption={(input, option) =>
                          (option?.search ?? '').includes(input.trim().toLowerCase())
                        }
                        options={timezoneOptions}
                        optionRender={(option) => (
                          <div style={{ display: 'flex', gap: 12, justifyContent: 'space-between' }}>
                            <span>{option.data.city}</span>
                            <span className="tabular" style={{ color: 'var(--on-surface-muted)' }}>
                              {option.data.offset}
                            </span>
                          </div>
                        )}
                      />
                    </Form.Item>
                  </div>

                  <Button type="primary" htmlType="submit" loading={savingProfile}>
                    Save changes
                  </Button>
                </Form>
              </div>
            ),
          },

          // ------------------------------------------------------ security
          {
            key: 'security',
            label: 'Security',
            children: (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 24, maxWidth: 640 }}>
                {/* ---- 2FA ---- */}
                <div className="lo-panel">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                    {me?.twoFactorEnabled ? <ShieldCheck size={20} /> : <ShieldOff size={20} />}
                    <div style={{ flex: 1 }}>
                      <strong style={{ fontSize: 15 }}>Two-factor authentication</strong>
                      <div style={{ fontSize: 13, color: 'var(--on-surface-variant)' }}>
                        {me?.twoFactorEnabled
                          ? 'On — a code from your authenticator is required at sign-in.'
                          : 'Off — a leaked password would be enough to get in.'}
                      </div>
                    </div>
                    <Tag color={me?.twoFactorEnabled ? 'success' : 'default'} style={{ margin: 0 }}>
                      {me?.twoFactorEnabled ? 'Enabled' : 'Disabled'}
                    </Tag>
                  </div>

                  {me?.twoFactorEnabled ? (
                    <Popconfirm
                      title="Turn off two-factor authentication?"
                      description="Your account will be protected by its password alone."
                      onConfirm={async () => {
                        const currentPassword = window.prompt('Confirm your current password')
                        if (!currentPassword) return
                        try {
                          await disableTotp({ currentPassword }).unwrap()
                          message.success('Two-factor authentication disabled')
                        } catch (err) {
                          message.error(errorMessage(err, 'Could not disable it'))
                        }
                      }}
                      okText="Turn off"
                      okButtonProps={{ danger: true }}
                    >
                      <Button danger>Turn off</Button>
                    </Popconfirm>
                  ) : (
                    <Button type="primary" loading={settingUp} onClick={startTotp}>
                      Set up two-factor
                    </Button>
                  )}
                </div>

                {/* ---- password ---- */}
                <div className="lo-panel">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
                    <KeyRound size={20} />
                    <div>
                      <strong style={{ fontSize: 15 }}>Password</strong>
                      <div style={{ fontSize: 13, color: 'var(--on-surface-variant)' }}>
                        Changing it signs out every other device.
                      </div>
                    </div>
                  </div>

                  <Form layout="vertical" requiredMark={false} onFinish={onChangePassword}>
                    <Form.Item
                      name="currentPassword"
                      label="Current password"
                      rules={[{ required: true, message: 'Enter your current password' }]}
                    >
                      <Input.Password autoComplete="current-password" />
                    </Form.Item>
                    <Form.Item
                      name="newPassword"
                      label="New password"
                      rules={[
                        { required: true, message: 'Choose a new password' },
                        { min: 10, message: 'At least 10 characters' },
                        {
                          pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
                          message: 'Include at least one letter and one digit',
                        },
                      ]}
                    >
                      <Input.Password autoComplete="new-password" />
                    </Form.Item>
                    <Button htmlType="submit" loading={changingPassword}>
                      Change password
                    </Button>
                  </Form>
                </div>

                {/* ---- sessions ---- */}
                <div className="lo-panel">
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      marginBottom: 16,
                      gap: 12,
                    }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <Laptop size={20} />
                      <div>
                        <strong style={{ fontSize: 15 }}>Active sessions</strong>
                        <div style={{ fontSize: 13, color: 'var(--on-surface-variant)' }}>
                          {sessions.length} device{sessions.length === 1 ? '' : 's'} signed in
                        </div>
                      </div>
                    </div>
                    <Popconfirm
                      title="Sign out everywhere?"
                      description="You will need to sign in again on every device, including this one."
                      onConfirm={() => revokeSessions()}
                      okText="Sign out all"
                      okButtonProps={{ danger: true }}
                    >
                      <Button danger size="small">
                        Sign out everywhere
                      </Button>
                    </Popconfirm>
                  </div>

                  <Table
                    size="small"
                    rowKey="id"
                    pagination={false}
                    dataSource={sessions}
                    scroll={{ x: 480 }}
                    columns={[
                      {
                        title: 'Device',
                        dataIndex: 'userAgent',
                        render: (value: string) => (
                          <span style={{ fontSize: 12 }}>{value ?? 'Unknown device'}</span>
                        ),
                        ellipsis: true,
                      },
                      { title: 'IP', dataIndex: 'ipAddress', width: 130 },
                      {
                        title: 'Signed in',
                        dataIndex: 'issuedAt',
                        width: 150,
                        render: (value: string) => dayjs(value).format('D MMM, HH:mm'),
                      },
                    ]}
                  />
                </div>
              </div>
            ),
          },

          // ------------------------------------------------- notifications
          {
            key: 'notifications',
            label: 'Notifications',
            children: <NotificationSettings />,
          },

          // ---------------------------------------------------- appearance
          {
            key: 'appearance',
            label: 'Appearance',
            children: (
              <div className="lo-panel" style={{ maxWidth: 560 }}>
                <div style={{ marginBottom: 24 }}>
                  <div style={{ fontWeight: 650, marginBottom: 4 }}>Theme</div>
                  <p style={{ margin: '0 0 12px', fontSize: 13, color: 'var(--on-surface-variant)' }}>
                    Both themes are drawn from the same monochrome ramps, re-anchored to their own
                    surface — dark mode is a designed palette, not an inverted one.
                  </p>
                  <Segmented
                    value={theme}
                    onChange={(value) => dispatch(themeSet(value as 'light' | 'dark'))}
                    options={[
                      { label: 'Light', value: 'light' },
                      { label: 'Dark', value: 'dark' },
                    ]}
                  />
                </div>

                <Alert
                  type="info"
                  showIcon
                  message="About the monochrome palette"
                  description="Every colour in the interface differs only in lightness, which means the charts read identically for every kind of colour vision and survive greyscale printing. Where three grey steps run out, series are separated by pattern, marker shape and direct labels instead."
                />
              </div>
            ),
          },
        ]}
      />

      {/* ---- 2FA enrolment ---- */}
      <Modal
        open={Boolean(totp)}
        onCancel={() => setTotp(null)}
        title="Set up two-factor authentication"
        okText="Verify and enable"
        confirmLoading={confirming}
        onOk={finishTotp}
        okButtonProps={{ disabled: totpCode.length !== 6 }}
      >
        <p style={{ color: 'var(--on-surface-variant)' }}>
          Scan this in Google Authenticator, Authy, 1Password or Microsoft Authenticator — or paste
          the key by hand.
        </p>

        {totp && (
          <>
            <div
              style={{
                display: 'grid',
                placeItems: 'center',
                padding: 16,
                background: '#ffffff',
                borderRadius: 12,
                marginBottom: 16,
              }}
            >
              {/* Rendered by a public QR endpoint; the secret never leaves as an image
                  we generate, and the otpauth URI below always works as a fallback. */}
              <img
                alt="Two-factor QR code"
                width={180}
                height={180}
                src={`https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(totp.otpauthUri)}`}
              />
            </div>

            <Typography.Paragraph copyable={{ text: totp.secret }} style={{ textAlign: 'center' }}>
              <code style={{ letterSpacing: '0.12em' }}>{totp.secret}</code>
            </Typography.Paragraph>

            <Input
              size="large"
              placeholder="123456"
              inputMode="numeric"
              maxLength={6}
              value={totpCode}
              onChange={(event) => setTotpCode(event.target.value.replace(/\D/g, ''))}
              style={{ letterSpacing: '0.35em', textAlign: 'center', fontSize: 18 }}
            />
          </>
        )}
      </Modal>

      {/* ---- recovery codes ---- */}
      <Modal
        open={Boolean(recoveryCodes)}
        onCancel={() => setRecoveryCodes(null)}
        onOk={() => setRecoveryCodes(null)}
        title="Save your recovery codes"
        okText="I have saved them"
        cancelButtonProps={{ style: { display: 'none' } }}
        closable={false}
        maskClosable={false}
      >
        <Alert
          type="warning"
          showIcon
          message="Shown once"
          description="Each code works a single time and gets you in if you lose your phone. Store them somewhere other than the device running your authenticator."
          style={{ marginBottom: 16 }}
        />
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '1fr 1fr',
            gap: 8,
            fontFamily: 'var(--font-mono)',
            fontSize: 14,
          }}
        >
          {recoveryCodes?.map((code) => (
            <code
              key={code}
              style={{
                padding: '8px 10px',
                background: 'var(--surface-container)',
                borderRadius: 8,
                textAlign: 'center',
              }}
            >
              {code}
            </code>
          ))}
        </div>
        <Typography.Paragraph
          copyable={{ text: recoveryCodes?.join('\n') }}
          style={{ marginTop: 12, textAlign: 'center' }}
        >
          Copy all codes
        </Typography.Paragraph>
      </Modal>
    </>
  )
}
