import { chromium } from 'playwright'

const BASE = 'http://localhost:5273'
const API = 'http://localhost:9080/api'

const stamp = Date.now()
const email = `probe+${stamp}@lifeos.test`
const password = 'ProbeCheck12345'

const reg = await (
  await fetch(`${API}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName: 'Probe', baseCurrency: 'USD' }),
  })
).json()
const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${reg.accessToken}` }

await fetch(`${API}/accounts/seed-defaults?currency=USD`, { method: 'POST', headers: auth })
const accounts = await (await fetch(`${API}/accounts`, { headers: auth })).json()
const cats = (await (await fetch(`${API}/categories`, { headers: auth })).json()).filter(
  (c) => c.kind === 'EXPENSE',
)
// Six categories so the donut uses all three solid tones plus all three patterns.
for (let i = 0; i < 6; i++) {
  await fetch(`${API}/expenses`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({
      accountId: accounts[0].id,
      categoryId: cats[i].id,
      amount: 100 - i * 10,
      type: 'EXPENSE',
      merchant: `Probe ${i}`,
    }),
  })
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1440, height: 960 } })
await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
await page.fill('input[autocomplete="username"]', email)
await page.fill('input[autocomplete="current-password"]', password)
await page.click('button[type="submit"]')
await page.waitForURL(`${BASE}/`, { timeout: 20000 })
await page.goto(`${BASE}/money`, { waitUntil: 'domcontentloaded' })
await page.waitForTimeout(3500)

const report = await page.evaluate(() => {
  const sectors = [...document.querySelectorAll('.recharts-pie-sector path')]
  const defs = [...document.querySelectorAll('pattern')].map((p) => p.id)
  return {
    sectorCount: sectors.length,
    fills: sectors.map((s) => s.getAttribute('fill')),
    patternIdsInDom: defs,
    // Does each referenced pattern actually resolve?
    resolves: sectors.map((s) => {
      const fill = s.getAttribute('fill') ?? ''
      const match = fill.match(/url\(#(.+?)\)/)
      return match ? Boolean(document.getElementById(match[1])) : 'solid'
    }),
  }
})

console.log(JSON.stringify(report, null, 2))
await page.screenshot({ path: 'screenshots/probe-donut.png', clip: { x: 860, y: 400, width: 560, height: 470 } })
await browser.close()
