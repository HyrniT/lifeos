import { useEffect, useRef } from 'react'
import { notification as antdNotification } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import { api } from '@/app/api'
import { API_BASE_URL } from '@/app/baseQuery'
import { useDispatch } from 'react-redux'
import { ensureServiceWorker } from './push'
import type { AppNotification } from '@/types'

/**
 * Live notifications for an open tab, plus the service-worker wiring for closed ones.
 *
 * `EventSource` cannot send an Authorization header, and putting a bearer token in
 * the query string would leak it into access logs and browser history. So the SSE
 * stream is read with `fetch` and the wire format parsed by hand — about thirty
 * lines, and the token stays in a header where it belongs.
 *
 * If the stream drops (deploy, laptop sleep, proxy timeout) it reconnects with
 * backoff. Nothing is lost either way: the inbox is durable and Web Push covers
 * the case where no tab is open at all.
 */
export function NotificationStream() {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const token = useAppSelector((state) => state.auth.accessToken)
  const abortRef = useRef<AbortController | null>(null)
  const retryRef = useRef(0)

  // Register the worker as soon as there is a session, not when the user first
  // opens settings — otherwise a push arriving before that visit has nothing to
  // display it.
  useEffect(() => {
    if (token) void ensureServiceWorker()
  }, [token])

  // Tapping a push notification asks an already-open tab to navigate rather than
  // opening a second copy of the app.
  useEffect(() => {
    if (!('serviceWorker' in navigator)) return
    const onMessage = (event: MessageEvent) => {
      if (event.data?.type === 'notification-click' && typeof event.data.url === 'string') {
        navigate(event.data.url)
      }
    }
    navigator.serviceWorker.addEventListener('message', onMessage)
    return () => navigator.serviceWorker.removeEventListener('message', onMessage)
  }, [navigate])

  useEffect(() => {
    if (!token) return

    let cancelled = false

    const connect = async () => {
      const controller = new AbortController()
      abortRef.current = controller

      try {
        const response = await fetch(`${API_BASE_URL}/notifications/stream`, {
          headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
          signal: controller.signal,
        })

        if (!response.ok || !response.body) throw new Error(`stream ${response.status}`)

        retryRef.current = 0
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (!cancelled) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })

          // SSE frames are separated by a blank line.
          let boundary = buffer.indexOf('\n\n')
          while (boundary !== -1) {
            const frame = buffer.slice(0, boundary)
            buffer = buffer.slice(boundary + 2)
            handleFrame(frame)
            boundary = buffer.indexOf('\n\n')
          }
        }
      } catch {
        // Abort during unmount is expected and must not schedule a reconnect.
        if (cancelled) return
      }

      if (cancelled) return
      retryRef.current = Math.min(retryRef.current + 1, 6)
      const delay = Math.min(30_000, 1000 * 2 ** retryRef.current)
      window.setTimeout(() => {
        if (!cancelled) void connect()
      }, delay)
    }

    const handleFrame = (frame: string) => {
      const lines = frame.split('\n')
      let event = 'message'
      let data = ''

      lines.forEach((line) => {
        if (line.startsWith('event:')) event = line.slice(6).trim()
        else if (line.startsWith('data:')) data += line.slice(5).trim()
      })

      if (event !== 'notification' || !data) return

      try {
        const payload = JSON.parse(data) as AppNotification
        dispatch(api.util.invalidateTags(['Notification']))

        antdNotification.open({
          key: payload.id,
          message: payload.title,
          description: payload.body,
          placement: 'bottomRight',
          // Critical alerts stay until dismissed; everything else clears itself.
          duration: payload.severity === 'critical' ? 0 : 6,
          onClick: payload.deepLink
            ? () => {
                navigate(payload.deepLink!)
                antdNotification.destroy(payload.id)
              }
            : undefined,
          style: payload.deepLink ? { cursor: 'pointer' } : undefined,
        })
      } catch {
        // Malformed frame: drop it rather than tearing down a working stream.
      }
    }

    void connect()

    return () => {
      cancelled = true
      abortRef.current?.abort()
    }
  }, [token, dispatch, navigate])

  return null
}
