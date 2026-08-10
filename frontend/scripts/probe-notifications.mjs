import { chromium } from 'playwright'

const BASE = 'http://localhost:5273'
const API = 'http://localhost:9080/api'

const stamp = Date.now()
const email = `notif+${stamp}@lifeos.test`
const password = 'NotifCheck12345'

const reg = await (
  await fetch(`${API}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName: 'Nora Notify', baseCurrency: 'USD' }),
  })
).json()
const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${reg.accessToken}` }

// A task whose 2-hour warning is already due, so the scheduler produces a real
// reminder rather than the screen showing an empty bell.
const deadline = new Date(Date.now() + 115 * 60 * 1000)
await fetch(`${API}/tasks`, {
  method: 'POST',
  headers: auth,
  body: JSON.stringify({
    title: 'Submit the quarterly report',
    priority: 'P1',
    dueDate: deadline.toISOString().slice(0, 10),
    dueTime: deadline.toISOString().slice(11, 19),
  }),
})
await fetch(`${API}/notifications/test`, { method: 'POST', headers: auth })

// Give the scheduler a tick to produce the deadline reminder.
await new Promise((r) => setTimeout(r, 20000))

const problems = []
const browser = await chromium.launch()
const context = await browser.newContext({
  viewport: { width: 1440, height: 1000 },
  // Grant up front: the permission prompt is modal and would block the run.
  permissions: ['notifications'],
})
const page = await context.newPage()
page.on('console', (m) => {
  if (m.type() === 'error') problems.push(m.text().slice(0, 200))
})
page.on('pageerror', (e) => problems.push(e.message.slice(0, 200)))

await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
await page.fill('input[autocomplete="username"]', email)
await page.fill('input[autocomplete="current-password"]', password)
await page.click('button[type="submit"]')
await page.waitForURL(`${BASE}/`, { timeout: 20000 })
await page.waitForTimeout(2500)

// ---- the bell, with real reminders in it ----
await page.click('button[aria-label^="Notifications"]')
await page.waitForTimeout(1800)
await page.screenshot({ path: 'screenshots/notif-01-bell.png' })
console.log('captured bell drawer')
await page.keyboard.press('Escape')
await page.waitForTimeout(600)

// ---- the settings tab ----
await page.goto(`${BASE}/settings`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(1500)
await page.click('div[role="tab"]:has-text("Notifications")')
await page.waitForTimeout(2500)
await page.screenshot({ path: 'screenshots/notif-02-settings.png', fullPage: true })
console.log('captured notification settings')

// ---- is the service worker actually registered? ----
const swState = await page.evaluate(async () => {
  const registration = await navigator.serviceWorker.getRegistration('/')
  return {
    registered: Boolean(registration),
    scope: registration?.scope ?? null,
    active: Boolean(registration?.active),
  }
})
console.log('service worker:', JSON.stringify(swState))

const prefs = await (await fetch(`${API}/notifications/preferences`, { headers: auth })).json()
console.log('preferences:', JSON.stringify({
  leadTimeMinutes: prefs.leadTimeMinutes,
  quietHours: `${prefs.quietFrom}-${prefs.quietTo}`,
  pushAvailable: prefs.pushAvailable,
}))

const inbox = await (await fetch(`${API}/notifications`, { headers: auth })).json()
console.log('inbox kinds:', inbox.map((n) => n.kind).join(', ') || '(empty)')

await browser.close()

if (problems.length) {
  console.log(`\n${problems.length} console problem(s):`)
  ;[...new Set(problems)].slice(0, 10).forEach((p) => console.log('  ' + p))
  process.exit(1)
}
console.log('\nNo console errors.')
