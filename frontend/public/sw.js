/* =========================================================================
 *  LifeOS service worker
 *
 *  Its only job is Web Push: showing a notification when the app is closed, and
 *  focusing the right screen when the user taps it. It deliberately does NOT
 *  cache application assets — a stale cached bundle talking to a newer API is a
 *  far worse failure than a page that needs the network.
 * ========================================================================= */

const VERSION = 'lifeos-sw-v1'

self.addEventListener('install', (event) => {
  // Take over immediately rather than waiting for every old tab to close;
  // otherwise a user who just granted permission gets nothing until they quit
  // the browser.
  event.waitUntil(self.skipWaiting())
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

self.addEventListener('push', (event) => {
  let payload = {}
  try {
    payload = event.data ? event.data.json() : {}
  } catch {
    payload = { title: 'LifeOS', body: event.data ? event.data.text() : '' }
  }

  const title = payload.title || 'LifeOS'
  const options = {
    body: payload.body || '',
    icon: '/icon.svg',
    badge: '/icon.svg',
    // Same tag replaces an earlier notification instead of stacking — three
    // reminders about one task should not mean three entries in the tray.
    tag: payload.kind ? `${payload.kind}:${payload.id ?? ''}` : undefined,
    renotify: false,
    requireInteraction: payload.severity === 'critical',
    timestamp: Date.now(),
    data: {
      id: payload.id,
      kind: payload.kind,
      deepLink: payload.deepLink || '/',
    },
    actions: payload.deepLink ? [{ action: 'open', title: 'Open' }] : [],
  }

  event.waitUntil(self.registration.showNotification(title, options))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()

  const target = (event.notification.data && event.notification.data.deepLink) || '/'
  const url = new URL(target, self.location.origin).href

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      // Reuse an open tab where possible: opening a second copy of the app on
      // every notification is the fastest way to annoy someone.
      for (const client of clients) {
        if (client.url.startsWith(self.location.origin) && 'focus' in client) {
          client.postMessage({ type: 'notification-click', url: target })
          return client.focus()
        }
      }
      return self.clients.openWindow(url)
    }),
  )
})
