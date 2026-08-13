import { useState } from 'react'
import { Alert, Button, Form, Input, Progress, Typography } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { useAppDispatch } from '@/app/hooks'
import { credentialsUpdated } from './authSlice'
import { useRegisterMutation, useSeedFinanceDefaultsMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { BASE_CURRENCY } from '@/app/money'
import '@/components/layout/shell.css'

/**
 * Rough strength meter. It is a nudge, not a gate — the real policy (10+ chars
 * with a letter and a digit) is enforced by the backend, which is the only place
 * a rule can actually be relied on.
 */
function passwordScore(value: string): { percent: number; label: string } {
  if (!value) return { percent: 0, label: '' }
  let score = 0
  if (value.length >= 10) score += 30
  if (value.length >= 14) score += 15
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 20
  if (/\d/.test(value)) score += 15
  if (/[^A-Za-z0-9]/.test(value)) score += 20
  const percent = Math.min(100, score)
  const label = percent < 40 ? 'Weak' : percent < 70 ? 'Fair' : percent < 90 ? 'Good' : 'Strong'
  return { percent, label }
}

export function RegisterPage() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [register, { isLoading }] = useRegisterMutation()
  const [seedDefaults] = useSeedFinanceDefaultsMutation()
  const [error, setError] = useState<string | null>(null)
  const [password, setPassword] = useState('')

  const strength = passwordScore(password)

  const onSubmit = async (values: {
    email: string
    password: string
    displayName: string
  }) => {
    setError(null)
    try {
      const tokens = await register({
        ...values,
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        baseCurrency: BASE_CURRENCY,
      }).unwrap()
      dispatch(credentialsUpdated(tokens))

      // A brand-new account with no accounts or categories is a dead end; seed
      // the starter set so the money screens are usable immediately.
      try {
        await seedDefaults(BASE_CURRENCY).unwrap()
      } catch {
        // Non-fatal: the user can seed from the accounts screen later.
      }

      navigate('/', { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Could not create your account'))
    }
  }

  return (
    <div className="lo-auth">
      <aside className="lo-auth__aside lo-grain">
        <div>
          <div className="lo-brand" style={{ padding: 0, marginBottom: 48 }}>
            <span className="lo-brand__mark" style={{ background: '#fff', color: '#101010' }}>
              L
            </span>
            <span className="lo-brand__text">
              <span className="lo-brand__name" style={{ color: 'inherit' }}>
                LifeOS
              </span>
            </span>
          </div>
          <h2 className="lo-auth__headline">Start with one habit and one budget.</h2>
          <p className="lo-auth__lede">
            Everything else — streaks, levels, forecasts, correlations — builds itself out of what
            you log.
          </p>
        </div>
        <p style={{ opacity: 0.45, fontSize: 12, margin: 0 }}>Your data stays yours. Export any time.</p>
      </aside>

      <main className="lo-auth__main">
        <div className="lo-auth__card">
          <h1 className="lo-auth__title">Create your account</h1>
          <p className="lo-auth__sub">Takes about thirty seconds.</p>

          {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

          <Form
            layout="vertical"
            onFinish={onSubmit}
            requiredMark={false}
          >
            <Form.Item
              name="displayName"
              label="Name"
              rules={[{ required: true, message: 'What should we call you?' }]}
            >
              <Input size="large" autoComplete="name" placeholder="Alex Doe" />
            </Form.Item>

            <Form.Item
              name="email"
              label="E-mail"
              rules={[
                { required: true, message: 'Enter your e-mail' },
                { type: 'email', message: 'That does not look like an e-mail address' },
              ]}
            >
              <Input size="large" autoComplete="email" placeholder="you@example.com" />
            </Form.Item>

            <Form.Item
              name="password"
              label="Password"
              rules={[
                { required: true, message: 'Choose a password' },
                { min: 10, message: 'At least 10 characters' },
                {
                  pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/,
                  message: 'Include at least one letter and one digit',
                },
              ]}
            >
              <Input.Password
                size="large"
                autoComplete="new-password"
                placeholder="At least 10 characters"
                onChange={(event) => setPassword(event.target.value)}
              />
            </Form.Item>

            {password && (
              <div style={{ marginTop: -12, marginBottom: 16 }}>
                <Progress
                  percent={strength.percent}
                  showInfo={false}
                  size="small"
                  strokeColor="var(--on-surface)"
                  trailColor="var(--outline-variant)"
                />
                <span style={{ fontSize: 12, color: 'var(--on-surface-variant)' }}>
                  Strength: {strength.label}
                </span>
              </div>
            )}

            <Button type="primary" size="large" block htmlType="submit" loading={isLoading}>
              Create account
            </Button>
          </Form>

          <Typography.Paragraph style={{ textAlign: 'center', marginTop: 24, marginBottom: 0 }}>
            Already have an account? <Link to="/login">Sign in</Link>
          </Typography.Paragraph>
        </div>
      </main>
    </div>
  )
}
