import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Spin } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAppDispatch } from '@/app/hooks'
import { credentialsUpdated } from './authSlice'
import { useGoogleCallbackMutation } from '@/app/api'
import { errorMessage } from '@/app/baseQuery'

export function GoogleCallbackPage() {
  const [params] = useSearchParams()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()
  const [exchange] = useGoogleCallbackMutation()
  const [error, setError] = useState<string | null>(null)
  // React 18 StrictMode mounts effects twice in development; an authorization
  // code is single-use, so the second attempt would fail with a confusing error.
  const exchanged = useRef(false)

  useEffect(() => {
    if (exchanged.current) return
    exchanged.current = true

    const code = params.get('code')
    const state = params.get('state')
    const googleError = params.get('error')

    if (googleError) {
      setError(
        googleError === 'access_denied'
          ? 'You cancelled the Google sign-in.'
          : `Google returned an error: ${googleError}`,
      )
      return
    }
    if (!code || !state) {
      setError('The sign-in link was incomplete. Please try again.')
      return
    }

    const expectedState = sessionStorage.getItem('lifeos.oauth.state')
    if (expectedState && expectedState !== state) {
      // The backend checks this too; failing here avoids a pointless round trip.
      setError('Security check failed (state mismatch). Please start the sign-in again.')
      return
    }
    sessionStorage.removeItem('lifeos.oauth.state')

    exchange({ code, state, redirectUri: `${window.location.origin}/auth/google/callback` })
      .unwrap()
      .then((tokens) => {
        dispatch(credentialsUpdated(tokens))
        navigate('/', { replace: true })
      })
      .catch((err) => setError(errorMessage(err, 'Could not complete the Google sign-in')))
  }, [params, exchange, dispatch, navigate])

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
      <div style={{ maxWidth: 420, width: '100%', textAlign: 'center' }}>
        {error ? (
          <>
            <Alert type="error" showIcon message="Sign-in failed" description={error} />
            <Button type="primary" style={{ marginTop: 16 }} onClick={() => navigate('/login')}>
              Back to sign in
            </Button>
          </>
        ) : (
          <>
            <Spin size="large" />
            <p style={{ marginTop: 20, color: 'var(--on-surface-variant)' }}>
              Finishing your Google sign-in…
            </p>
          </>
        )}
      </div>
    </div>
  )
}
