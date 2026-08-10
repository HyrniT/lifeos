import { useState } from 'react'
import { Alert, Button, Form, Input } from 'antd'
import { useNavigate } from 'react-router-dom'
import { ShieldAlert } from 'lucide-react'
import { useAppDispatch } from '@/app/hooks'
import { credentialsUpdated } from '@/features/auth/authSlice'
import { useLoginMutation, useVerifyTwoFactorMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { isTwoFactorChallenge, type LoginResult } from '@/types'
import '@/components/layout/shell.css'

export function AdminLoginPage() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [login, { isLoading }] = useLoginMutation()
  const [verify, { isLoading: verifying }] = useVerifyTwoFactorMutation()
  const [error, setError] = useState<string | null>(null)
  const [challenge, setChallenge] = useState<string | null>(null)

  const onSubmit = async (values: { email: string; password: string }) => {
    setError(null)
    try {
      const result: LoginResult = await login(values).unwrap()
      if (isTwoFactorChallenge(result)) {
        setChallenge(result.challengeToken)
        return
      }
      // The gateway also enforces this, but failing here gives a clear message
      // instead of a wall of 403s once inside.
      if (!result.user.roles.includes('ADMIN')) {
        setError('That account does not have administrator access.')
        return
      }
      dispatch(credentialsUpdated(result))
      navigate('/', { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Could not sign you in'))
    }
  }

  const onVerify = async (values: { code: string }) => {
    if (!challenge) return
    setError(null)
    try {
      const tokens = await verify({ challengeToken: challenge, code: values.code }).unwrap()
      if (!tokens.user.roles.includes('ADMIN')) {
        setError('That account does not have administrator access.')
        return
      }
      dispatch(credentialsUpdated(tokens))
      navigate('/', { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'That code was not accepted'))
    }
  }

  return (
    <div
      style={{
        minHeight: '100dvh',
        display: 'grid',
        placeItems: 'center',
        padding: 24,
        background: 'var(--surface)',
      }}
    >
      <div style={{ width: '100%', maxWidth: 380 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
          <span className="lo-brand__mark" style={{ width: 42, height: 42, fontSize: 18 }}>
            L
          </span>
          <div>
            <div style={{ fontWeight: 750, fontSize: 17, letterSpacing: '-0.02em' }}>
              LifeOS Admin
            </div>
            <div style={{ fontSize: 12, color: 'var(--on-surface-muted)' }}>
              Restricted — administrators only
            </div>
          </div>
        </div>

        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

        {challenge ? (
          <Form layout="vertical" onFinish={onVerify} requiredMark={false}>
            <Form.Item
              name="code"
              label="Verification code"
              rules={[{ required: true, message: 'Enter the code' }]}
            >
              <Input
                size="large"
                autoFocus
                inputMode="numeric"
                maxLength={6}
                placeholder="123456"
                style={{ letterSpacing: '0.35em', textAlign: 'center', fontSize: 18 }}
              />
            </Form.Item>
            <Button type="primary" size="large" block htmlType="submit" loading={verifying}>
              Verify
            </Button>
          </Form>
        ) : (
          <Form layout="vertical" onFinish={onSubmit} requiredMark={false}>
            <Form.Item
              name="email"
              label="Username or e-mail"
              rules={[{ required: true, message: 'Enter your username' }]}
            >
              <Input size="large" autoFocus autoComplete="username" placeholder="admin" />
            </Form.Item>
            <Form.Item
              name="password"
              label="Password"
              rules={[{ required: true, message: 'Enter your password' }]}
            >
              <Input.Password size="large" autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" size="large" block htmlType="submit" loading={isLoading}>
              Sign in
            </Button>
          </Form>
        )}

        <Alert
          type="warning"
          showIcon
          icon={<ShieldAlert size={16} />}
          style={{ marginTop: 24 }}
          message="Default credentials"
          description="A fresh deployment seeds admin / admin. Change it before this console is reachable from the internet — the seed can also be turned off with LIFEOS_ADMIN_SEED_ENABLED=false."
        />
      </div>
    </div>
  )
}
