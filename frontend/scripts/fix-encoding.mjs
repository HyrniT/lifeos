/**
 * Repairs UTF-8 text that was round-tripped through a Latin-1 read.
 *
 * PowerShell 5.1's Get-Content reads as ANSI unless told otherwise; writing the
 * result back as UTF-8 double-encodes every non-ASCII character. A whole-file
 * re-decode is not safe here (the BOM and any untouched characters break it), so
 * this maps the specific sequences the affected files actually contain.
 */
import { readdirSync, readFileSync, writeFileSync, statSync } from 'node:fs'
import { join, extname } from 'node:path'

const ROOTS = ['src', 'index.html', 'admin.html', '../backend', '../infra']
const EXTENSIONS = new Set(['.ts', '.tsx', '.css', '.java', '.yml', '.sql', '.md', '.html', '.conf'])

const REPLACEMENTS = [
  ['â€”', '—'], // em dash
  ['â€“', '–'], // en dash
  ['â€™', '’'], // right single quote
  ['â€œ', '“'], // left double quote
  ['â€', '”'], // right double quote
  ['â€¦', '…'], // ellipsis
  ['â†’', '→'], // right arrow
  ['Ã·', '÷'],       // division sign
  ['â‰¥', '≥'], // >=
  ['â', '❄'], // snowflake
  ['âš ', '⚠'], // warning sign
  ['Â·', '·'],       // middle dot
  ['Ã—', '×'],       // multiplication sign
  ['Â ', ' '],       // non-breaking space
]

let repaired = 0
let scanned = 0

function repair(path) {
  scanned++
  let text = readFileSync(path, 'utf8')
  const before = text
  for (const [bad, good] of REPLACEMENTS) {
    text = text.split(bad).join(good)
  }
  // Strip a UTF-8 BOM: harmless in most tools, but it breaks shell scripts and
  // shows up as a stray character in some editors.
  if (text.charCodeAt(0) === 0xfeff) text = text.slice(1)

  if (text !== before) {
    writeFileSync(path, text, 'utf8')
    console.log(`  repaired ${path}`)
    repaired++
  }
}

function walk(target) {
  let info
  try {
    info = statSync(target)
  } catch {
    return
  }
  if (info.isFile()) {
    if (EXTENSIONS.has(extname(target))) repair(target)
    return
  }
  for (const entry of readdirSync(target)) {
    if (['node_modules', 'target', 'dist', '.git', 'screenshots'].includes(entry)) continue
    walk(join(target, entry))
  }
}

ROOTS.forEach(walk)
console.log(`\nScanned ${scanned} files, repaired ${repaired}.`)
