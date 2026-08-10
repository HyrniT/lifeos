import { useEffect, useState } from 'react'
import { Alert, Button, Divider, Form, Input, Typography } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { CalendarCheck, ChartNoAxesCombined, Check, ShieldCheck, Wallet } from 'lucide-react'
import { useAppDispatch } from '@/app/hooks'
import { credentialsUpdated, twoFactorRequested } from './authSlice'
import {
  useAuthProvidersQuery,
  useLazyGoogleAuthUrlQuery,
  useLoginMutation,
  useVerifyTwoFactorMutation,
} from '@/app/api'
import { errorMessage } from '@/app/baseQuery'
import { isTwoFactorChallenge, type LoginResult } from '@/types'
import { GoogleMark } from './GoogleMark'
import '@/components/layout/shell.css'

export function LoginPage() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()

  const [login, { isLoading }] = useLoginMutation()
  const [verify, { isLoading: verifying }] = useVerifyTwoFactorMutation()
  const [fetchGoogleUrl, { isFetching: loadingGoogle }] = useLazyGoogleAuthUrlQuery()
  const { data: providers } = useAuthProvidersQuery()

  const [error, setError] = useState<string | null>(null)
  const [challenge, setChallenge] = useState<string | null>(null)

  useEffect(() => {
    document.title = 'Sign in · LifeOS'
  }, [])

  const onSubmit = async (values: { email: string; password: string }) => {
    setError(null)
    try {
      const result: LoginResult = await login(values).unwrap()
      if (isTwoFactorChallenge(result)) {
        setChallenge(result.challengeToken)
        dispatch(twoFactorRequested(result.challengeToken))
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
      dispatch(credentialsUpdated(tokens))
      navigate('/', { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'That code was not accepted'))
    }
  }

  const startGoogle = async () => {
    setError(null)
    try {
      const redirectUri = `${window.location.origin}/auth/google/callback`
      const result = await fetchGoogleUrl(redirectUri).unwrap()
      sessionStorage.setItem('lifeos.oauth.state', result.state)
      window.location.href = result.authorizationUrl
    } catch (err) {
      setError(errorMessage(err, 'Google sign-in is not available right now'))
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

          <motion.h2
            className="lo-auth__headline"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: [0.2, 0, 0, 1] }}
          >
            Your habits, money and plans in one place.
          </motion.h2>
          <p className="lo-auth__lede">
            Track what you do, what you spend and what you intend — then see how those three
            actually relate.
          </p>

          <ul className="lo-auth__points">
            {[
              { icon: CalendarCheck, text: 'Streaks, XP and levels that make consistency visible' },
              { icon: Wallet, text: 'Budgets that tell you what you can safely spend today' },
              { icon: ChartNoAxesCombined, text: 'Correlations across all three, from your own data' },
              { icon: ShieldCheck, text: 'Two-factor sign-in and rotating sessions by default' },
            ].map(({ icon: Icon, text }, index) => (
              <motion.li
                className="lo-auth__point"
                key={text}
                initial={{ opacity: 0, x: -12 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.15 + index * 0.08, duration: 0.4 }}
              >
                <Icon size={18} style={{ marginTop: 2, flexShrink: 0 }} />
                <span>{text}</span>
              </motion.li>
            ))}
          </ul>
        </div>

        <p style={{ opacity: 0.45, fontSize: 12, margin: 0 }}>
          Habits · Expenses · Planning · Analytics
        </p>
      </aside>

      <main className="lo-auth__main">
        <div className="lo-auth__card">
          {challenge ? (
            <>
              <h1 className="lo-auth__title">Two-factor verification</h1>
              <p className="lo-auth__sub">
                Enter the 6-digit code from your authenticator app, or one of your recovery codes.
              </p>

              {error && (
                <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />
              )}

              <Form layout="vertical" onFinish={onVerify} requiredMark={false}>
                <Form.Item
                  name="code"
                  label="Verification code"
                  rules={[{ required: true, message: 'Enter the code' }]}
                >
                  <Input
                    size="large"
                    autoFocus
                    autoComplete="one-time-code"
                    inputMode="numeric"
                    placeholder="123456"
                    style={{ letterSpacing: '0.35em', fontSize: 18, textAlign: 'center' }}
                  />
                </Form.Item>
                <Button type="primary" size="large" block htmlType="submit" loading={verifying}>
                  Verify and continue
                </Button>
                <Button type="text" block onClick={() => setChallenge(null)} style={{ marginTop: 8 }}>
                  Back to sign in
                </Button>
              </Form>
            </>
          ) : (
            <>
              <h1 className="lo-auth__title">Welcome back</h1>
              <p className="lo-auth__sub">Sign in to pick up where you left off.</p>

              {error && (
                <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />
              )}

              <Form layout="vertical" onFinish={onSubmit} requiredMark={false}>
                <Form.Item
                  name="email"
                  label="E-mail or username"
                  rules={[{ required: true, message: 'Enter your e-mail or username' }]}
                >
                  <Input size="large" autoComplete="username" placeholder="you@example.com" />
                </Form.Item>

                <Form.Item
                  name="password"
                  label="Password"
                  rules={[{ required: true, message: 'Enter your password' }]}
                >
                  <Input.Password size="large" autoComplete="current-password" placeholder="••••••••" />
                </Form.Item>

                <Button type="primary" size="large" block htmlType="submit" loading={isLoading}>
                  Sign in
                </Button>
              </Form>

              {providers?.google && (
                <>
                  <div className="lo-auth__divider">or</div>
                  <Button
                    size="large"
                    block
                    icon={<GoogleMark />}
                    loading={loadingGoogle}
                    onClick={startGoogle}
                  >
                    Continue with Google
                  </Button>
                </>
              )}

              <Divider style={{ margin: '24px 0 16px' }} />
              <Typography.Paragraph style={{ textAlign: 'center', margin: 0 }}>
                New here? <Link to="/register">Create an account</Link>
              </Typography.Paragraph>
            </>
          )}
        </div>
      </main>
    </div>
  )
}
