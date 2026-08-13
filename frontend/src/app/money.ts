/**
 * The one currency this app deals in, and how it is written.
 *
 * LifeOS is single-currency by design, so nothing here takes a currency code:
 * every stored amount is in the same unit and is directly comparable with every
 * other one.
 *
 * Formatting follows Vietnamese convention rather than the browser's locale:
 * dot for thousands, symbol after the number, no space — `123.000₫`. The locale
 * is pinned so the same amount reads identically on a phone set to English and
 * a laptop set to Vietnamese; only the currency's own conventions matter here.
 */
export const BASE_CURRENCY = 'VND'

/** Pinned, not the browser's — see the note above. */
const MONEY_LOCALE = 'vi-VN'

/**
 * `Intl` puts a non-breaking space before the symbol ("123.000 ₫"). Vietnamese
 * writing runs it on, so that one gap is closed rather than the number rebuilt
 * by hand — grouping, negatives and the compact scale suffix all stay the
 * library's job. Only the space before the symbol goes: the suffix keeps its
 * own, so large sums read "1,5 Tr₫" rather than the cramped "1,5Tr₫".
 */
function tighten(formatted: string): string {
  return formatted.replace(/[\s ]+₫/, '₫')
}

const standard = new Intl.NumberFormat(MONEY_LOCALE, {
  style: 'currency',
  currency: BASE_CURRENCY,
})

const compactFormat = new Intl.NumberFormat(MONEY_LOCALE, {
  style: 'currency',
  currency: BASE_CURRENCY,
  notation: 'compact',
  maximumFractionDigits: 1,
})

/**
 * `123.000₫`, or `12,5 Tr₫` when compact.
 *
 * Compact is for axis labels and stat tiles, where a full VND figure is wide
 * enough to break a layout; it uses the Vietnamese short scale (N, Tr, T).
 */
export function formatMoney(value: number, compact = false): string {
  const safe = Number.isFinite(value) ? value : 0
  // Below ten thousand the compact form saves nothing and reads worse.
  const useCompact = compact && Math.abs(safe) >= 10_000
  return tighten((useCompact ? compactFormat : standard).format(safe))
}

/** The bare number, for inputs and anywhere the symbol is already on screen. */
export function formatMoneyNumber(value: number): string {
  const safe = Number.isFinite(value) ? value : 0
  return new Intl.NumberFormat(MONEY_LOCALE, { maximumFractionDigits: 0 }).format(safe)
}
