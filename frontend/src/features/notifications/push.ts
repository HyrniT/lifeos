import { API_BASE_URL } from '@/app/baseQuery'

/**
 * Web Push plumbing.
 *
 * Kept out of React because the sequence is genuinely imperative — register a
 * worker, ask permission, subscribe, tell the server — and each step can fail in
 * a way the user needs a specific sentence about.
 */

export type PushState =
  | 'unsupported'
  | 'denied'
  | 'default'
  | 'granted-unsubscribed'
  | 'subscribed'

export function isPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  )
}

/** Registers the worker once and resolves when it is ready to receive pushes. */
export async function ensureServiceWorker(): Promise<ServiceWorkerRegistration | null> {
  if (!isPushSupported()) return null
  try {
    const existing = await navigator.serviceWorker.getRegistration('/')
    if (existing) return await navigator.serviceWorker.ready
    await navigator.serviceWorker.register('/sw.js', { scope: '/' })
    return await navigator.serviceWorker.ready
  } catch {
    return null
  }
}

export async function currentPushState(): Promise<PushState> {
  if (!isPushSupported()) return 'unsupported'
  if (Notification.permission === 'denied') return 'denied'
  if (Notification.permission === 'default') return 'default'

  const registration = await ensureServiceWorker()
  if (!registration) return 'granted-unsubscribed'
  const subscription = await registration.pushManager.getSubscription()
  return subscription ? 'subscribed' : 'granted-unsubscribed'
}

/**
 * The VAPID public key arrives base64url-encoded; `applicationServerKey` wants
 * raw bytes.
 */
function urlBase64ToBytes(base64: string): ArrayBuffer {
  const padding = '='.repeat((4 - (base64.length % 4)) % 4)
  const normalised = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/')
  const raw = window.atob(normalised)
  // Allocate the ArrayBuffer explicitly: a plain Uint8Array is typed over
  // ArrayBufferLike, which the PushSubscriptionOptions signature will not accept.
  const buffer = new ArrayBuffer(raw.length)
  const view = new Uint8Array(buffer)
  for (let i = 0; i < raw.length; i++) view[i] = raw.charCodeAt(i)
  return buffer
}

function authHeaders(token: string): HeadersInit {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
}

export async function subscribeToPush(token: string): Promise<{ ok: boolean; reason?: string }> {
  if (!isPushSupported()) {
    return { ok: false, reason: 'This browser does not support push notifications.' }
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return {
      ok: false,
      reason:
        permission === 'denied'
          ? 'Notifications are blocked for this site. Re-enable them in your browser settings, then try again.'
          : 'Permission was dismissed.',
    }
  }

  const registration = await ensureServiceWorker()
  if (!registration) {
    return { ok: false, reason: 'The service worker could not be registered.' }
  }

  const keyResponse = await fetch(`${API_BASE_URL}/notifications/push/key`, {
    headers: authHeaders(token),
  })
  if (!keyResponse.ok) {
    return { ok: false, reason: 'Could not fetch the server key.' }
  }
  const { publicKey, available } = await keyResponse.json()
  if (!available) {
    return { ok: false, reason: 'Push delivery is switched off on this deployment.' }
  }

  // An existing subscription from a previous VAPID key is unusable and its
  // presence would stop `subscribe` returning a working one.
  const existing = await registration.pushManager.getSubscription()
  if (existing) await existing.unsubscribe()

  let subscription: PushSubscription
  try {
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToBytes(publicKey),
    })
  } catch (error) {
    return {
      ok: false,
      reason: `The browser refused the subscription: ${(error as Error).message}`,
    }
  }

  const json = subscription.toJSON() as {
    endpoint: string
    keys?: { p256dh?: string; auth?: string }
  }

  const saved = await fetch(`${API_BASE_URL}/notifications/push/subscribe`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({
      endpoint: json.endpoint,
      p256dh: json.keys?.p256dh,
      auth: json.keys?.auth,
    }),
  })

  if (!saved.ok) {
    // Do not leave the browser subscribed to a server that does not know about it.
    await subscription.unsubscribe()
    return { ok: false, reason: 'The server rejected the subscription.' }
  }
  return { ok: true }
}

export async function unsubscribeFromPush(token: string): Promise<boolean> {
  const registration = await ensureServiceWorker()
  if (!registration) return false

  const subscription = await registration.pushManager.getSubscription()
  if (!subscription) return true

  await fetch(`${API_BASE_URL}/notifications/push/unsubscribe`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ endpoint: subscription.endpoint }),
  }).catch(() => undefined)

  return subscription.unsubscribe()
}
