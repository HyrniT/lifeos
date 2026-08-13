/**
 * Renders the real app in Chromium, signs in, walks every screen and captures a
 * screenshot of each. Fails loudly on any console error or unhandled rejection —
 * a build that compiles can still render a blank page.
 *
 *   node scripts/visual-check.mjs
 */
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

const BASE = process.env.LIFEOS_WEB ?? 'http://localhost:5273'
const API = process.env.LIFEOS_API ?? 'http://localhost:9080/api'
const OUT = resolve(process.cwd(), 'screenshots')
mkdirSync(OUT, { recursive: true })

const problems = []
let shots = 0

async function seedAccount() {
  const stamp = Date.now()
  const email = `visual+${stamp}@lifeos.test`
  const password = 'VisualCheck12345'

  const register = await fetch(`${API}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName: 'Vera Visual', baseCurrency: 'USD' }),
  })
  if (!register.ok) throw new Error(`register failed: ${register.status}`)
  const { accessToken } = await register.json()
  const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` }

  // Enough content that every screen has something real to draw.
  const habits = [
    { name: 'Read 20 pages', icon: 'book-open', difficulty: 'HARD', unit: 'PAGES', targetValue: 20 },
    { name: 'Morning run', icon: 'footprints', difficulty: 'EPIC', unit: 'KILOMETRES', targetValue: 5 },
    { name: 'Meditate', icon: 'brain', difficulty: 'EASY', unit: 'MINUTES', targetValue: 10 },
    { name: 'Drink water', icon: 'droplets', difficulty: 'TRIVIAL', unit: 'MILLILITRES', targetValue: 2000 },
    { name: 'No late-night phone', icon: 'phone-off', difficulty: 'MEDIUM', type: 'QUIT' },
  ]
  for (const habit of habits) {
    const created = await (
      await fetch(`${API}/habits`, { method: 'POST', headers: auth, body: JSON.stringify(habit) })
    ).json()
    // Back-fill a fortnight so streaks, heatmaps and completion rates are non-trivial.
    for (let daysAgo = 13; daysAgo >= 0; daysAgo--) {
      if (daysAgo % 4 === 3) continue
      const date = new Date(Date.now() - daysAgo * 86400000).toISOString().slice(0, 10)
      await fetch(`${API}/habits/${created.id}/check-in`, {
        method: 'POST',
        headers: auth,
        body: JSON.stringify({ date, value: habit.targetValue ?? 1, mood: 3 + (daysAgo % 3) }),
      })
    }
  }

  await fetch(`${API}/accounts/seed-defaults?currency=USD`, { method: 'POST', headers: auth })
  const accounts = await (await fetch(`${API}/accounts`, { headers: auth })).json()
  const categories = await (await fetch(`${API}/categories`, { headers: auth })).json()
  const expenseCats = categories.filter((c) => c.kind === 'EXPENSE')
  const incomeCats = categories.filter((c) => c.kind === 'INCOME')

  const merchants = ['Corner Cafe', 'Metro Transit', 'Green Grocer', 'Bookshop', 'Gym', 'Cinema']
  for (let daysAgo = 28; daysAgo >= 0; daysAgo--) {
    const date = new Date(Date.now() - daysAgo * 86400000).toISOString().slice(0, 10)
    const count = 1 + (daysAgo % 3)
    for (let i = 0; i < count; i++) {
      const category = expenseCats[(daysAgo + i) % expenseCats.length]
      await fetch(`${API}/expenses`, {
        method: 'POST',
        headers: auth,
        body: JSON.stringify({
          accountId: accounts[(daysAgo + i) % accounts.length].id,
          categoryId: category.id,
          amount: Math.round((8 + ((daysAgo * 7 + i * 13) % 90)) * 100) / 100,
          type: 'EXPENSE',
          occurredOn: date,
          merchant: merchants[(daysAgo + i) % merchants.length],
        }),
      })
    }
    if (daysAgo % 14 === 0) {
      await fetch(`${API}/expenses`, {
        method: 'POST',
        headers: auth,
        body: JSON.stringify({
          accountId: accounts[1].id,
          categoryId: incomeCats[0].id,
          amount: 2400,
          type: 'INCOME',
          occurredOn: date,
          merchant: 'Salary',
        }),
      })
    }
  }

  for (const budget of [
    { name: 'Eating out', categoryId: expenseCats[0].id, amount: 300, period: 'MONTHLY' },
    { name: 'Everything', amount: 1800, period: 'MONTHLY' },
  ]) {
    await fetch(`${API}/budgets`, { method: 'POST', headers: auth, body: JSON.stringify(budget) })
  }

  const project = await (
    await fetch(`${API}/projects`, {
      method: 'POST',
      headers: auth,
      body: JSON.stringify({ name: 'Website relaunch', icon: 'rocket' }),
    })
  ).json()

  const tasks = [
    { title: 'Draft the launch announcement', priority: 'P1', offset: 0 },
    { title: 'Review analytics dashboard', priority: 'P2', offset: 0 },
    { title: 'Fix the mobile navigation', priority: 'P1', offset: -2 },
    { title: 'Write release notes', priority: 'P3', offset: 3 },
    { title: 'Book the venue', priority: 'P2', offset: 7 },
    { title: 'Archive old assets', priority: 'P4', offset: 14 },
  ]
  for (const task of tasks) {
    const due = new Date(Date.now() + task.offset * 86400000).toISOString().slice(0, 10)
    await fetch(`${API}/tasks`, {
      method: 'POST',
      headers: auth,
      body: JSON.stringify({
        title: task.title,
        priority: task.priority,
        dueDate: due,
        projectId: project.id,
        estimateMinutes: 45,
      }),
    })
  }

  const created = await (await fetch(`${API}/tasks`, { headers: auth })).json()
  for (const task of created.slice(0, 3)) {
    await fetch(`${API}/tasks/${task.id}/status?status=DONE`, { method: 'POST', headers: auth })
  }

  for (const goal of [
    { title: 'Read 24 books', targetValue: 24, currentValue: 9, unit: 'books', icon: 'book-open' },
    { title: 'Run 500 km', targetValue: 500, currentValue: 180, unit: 'km', icon: 'footprints' },
    { title: 'Save 10,000', targetValue: 10000, currentValue: 6400, unit: 'USD', icon: 'trophy' },
  ]) {
    const target = new Date(Date.now() + 120 * 86400000).toISOString().slice(0, 10)
    const start = new Date(Date.now() - 90 * 86400000).toISOString().slice(0, 10)
    await fetch(`${API}/goals`, {
      method: 'POST',
      headers: auth,
      body: JSON.stringify({ ...goal, startDate: start, targetDate: target }),
    })
  }

  for (let daysAgo = 6; daysAgo >= 0; daysAgo--) {
    const session = await (
      await fetch(`${API}/focus/start`, {
        method: 'POST',
        headers: auth,
        body: JSON.stringify({ type: 'POMODORO', plannedMinutes: 25 }),
      })
    ).json()
    await fetch(`${API}/focus/${session.id}/end`, {
      method: 'POST',
      headers: auth,
      body: JSON.stringify({ focusScore: 4, completed: true }),
    })
  }

  await fetch(`${API}/journal`, {
    method: 'PUT',
    headers: auth,
    body: JSON.stringify({ mood: 4, energy: 4, highlights: 'Shipped the dashboard' }),
  })

  // The project id comes back too: the task list has a per-project view that can
  // only be reached with a real id in the query string.
  return { email, password, projectId: project.id }
}

async function shoot(page, name, { full = true } = {}) {
  await page.waitForTimeout(2600) // let data load and chart animations settle
  await page.screenshot({ path: `${OUT}/${String(++shots).padStart(2, '0')}-${name}.png`, fullPage: full })
  console.log(`  captured ${name}`)
}

async function main() {
  console.log('Seeding a demo account through the API...')
  const { email, password, projectId } = await seedAccount()
  console.log(`  ${email}`)

  const browser = await chromium.launch()

  for (const [label, viewport] of [
    ['desktop', { width: 1440, height: 960 }],
    ['mobile', { width: 414, height: 896 }],
  ]) {
    const context = await browser.newContext({ viewport, deviceScaleFactor: 1 })
    const page = await context.newPage()

    page.on('console', (msg) => {
      if (msg.type() === 'error') problems.push(`[${label}] console: ${msg.text().slice(0, 200)}`)
    })
    page.on('pageerror', (err) => problems.push(`[${label}] pageerror: ${err.message.slice(0, 200)}`))

    console.log(`\n${label} (${viewport.width}x${viewport.height})`)

    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    if (label === 'desktop') await shoot(page, 'login')

    await page.fill('input#email, input[autocomplete="username"]', email)
    await page.fill('input[autocomplete="current-password"]', password)
    await page.click('button[type="submit"]')
    await page.waitForURL(`${BASE}/`, { timeout: 20000 })
        await shoot(page, `${label}-dashboard`)

    for (const [path, name] of [
      ['/habits', 'habits'],
      ['/money', 'money'],
      ['/planning', 'tasks'],
      ['/goals', 'goals'],
      ['/projects', 'projects'],
      [`/planning?project=${projectId}`, 'tasks-one-project'],
      ['/focus', 'focus'],
      ['/analytics', 'analytics'],
      ['/achievements', 'achievements'],
      ['/settings', 'settings'],
    ]) {
      await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' })
      await shoot(page, `${label}-${name}`)
    }

    if (label === 'desktop') {
      // Dark theme on the busiest screen.
      await page.goto(`${BASE}/analytics`, { waitUntil: 'domcontentloaded' })
      await page.evaluate(() => {
        localStorage.setItem('lifeos.theme', 'dark')
        document.documentElement.setAttribute('data-theme', 'dark')
      })
      await page.reload({ waitUntil: 'domcontentloaded' })
      await shoot(page, 'analytics-dark')

      await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
      await shoot(page, 'dashboard-dark')
    }

    await context.close()
  }

  // ---- admin console ----
  console.log('\nadmin console')
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } })
  const page = await context.newPage()
  page.on('console', (msg) => {
    if (msg.type() === 'error') problems.push(`[admin] console: ${msg.text().slice(0, 200)}`)
  })
  page.on('pageerror', (err) => problems.push(`[admin] pageerror: ${err.message.slice(0, 200)}`))

  await page.goto(`${BASE}/admin/login`, { waitUntil: 'domcontentloaded' })
  await shoot(page, 'admin-login')
  await page.fill('input[autocomplete="username"]', 'admin')
  await page.fill('input[autocomplete="current-password"]', 'admin')
  await page.click('button[type="submit"]')
  await page.waitForTimeout(3500)
    await shoot(page, 'admin-overview')

  for (const [path, name] of [
    ['/admin/users', 'admin-users'],
    ['/admin/audit', 'admin-audit'],
    ['/admin/system', 'admin-system'],
  ]) {
    await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' })
    await shoot(page, name)
  }

  await context.close()
  await browser.close()

  console.log(`\n${shots} screenshots written to ${OUT}`)
  // React 18 StrictMode and Ant Design emit known development-only warnings; only
  // genuine errors are collected above.
  if (problems.length) {
    console.log(`\n${problems.length} problem(s):`)
    ;[...new Set(problems)].slice(0, 20).forEach((p) => console.log(`  ${p}`))
    process.exit(1)
  }
  console.log('No console errors on any screen.')
}

main().catch((err) => {
  console.error('visual check failed:', err)
  process.exit(1)
})
