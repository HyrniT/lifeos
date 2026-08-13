/**
 * The IANA time-zone list, for the Settings picker.
 *
 * A time zone is not free text: it is an identifier from a fixed set, and a typo
 * ("Asia/Hochiminh", "GMT+7") is not a smaller mistake than a wrong choice — it
 * silently sends every reminder to the wrong hour. So the UI offers the real set
 * and never asks anyone to spell one from memory.
 *
 * `Intl.supportedValuesOf` gives us that set from the engine itself, so the list
 * ages with the browser rather than with this file. The fallback below is only
 * for engines that lack it (pre-2022); it covers the zones our users actually
 * live in rather than pretending to be complete.
 */

const FALLBACK_ZONES = [
  'Africa/Cairo', 'Africa/Johannesburg', 'Africa/Lagos', 'Africa/Nairobi',
  'America/Argentina/Buenos_Aires', 'America/Bogota', 'America/Chicago', 'America/Denver',
  'America/Los_Angeles', 'America/Mexico_City', 'America/New_York', 'America/Sao_Paulo',
  'America/Toronto', 'America/Vancouver',
  'Asia/Bangkok', 'Asia/Dubai', 'Asia/Ho_Chi_Minh', 'Asia/Hong_Kong', 'Asia/Jakarta',
  'Asia/Jerusalem', 'Asia/Kolkata', 'Asia/Kuala_Lumpur', 'Asia/Manila', 'Asia/Seoul',
  'Asia/Shanghai', 'Asia/Singapore', 'Asia/Taipei', 'Asia/Tokyo',
  'Australia/Melbourne', 'Australia/Perth', 'Australia/Sydney',
  'Europe/Amsterdam', 'Europe/Athens', 'Europe/Berlin', 'Europe/Brussels', 'Europe/Dublin',
  'Europe/Istanbul', 'Europe/Lisbon', 'Europe/London', 'Europe/Madrid', 'Europe/Moscow',
  'Europe/Oslo', 'Europe/Paris', 'Europe/Prague', 'Europe/Rome', 'Europe/Stockholm',
  'Europe/Vienna', 'Europe/Warsaw', 'Europe/Zurich',
  'Pacific/Auckland', 'Pacific/Honolulu',
  'UTC',
]

/**
 * Zones the IANA database renamed, old name first.
 *
 * This matters more than it looks. `Intl.supportedValuesOf` returns the engine's
 * *canonical* ids, and ICU still canonicalises several zones to the name they had
 * decades ago — on the engine we build against, Vietnam comes back as
 * `Asia/Saigon`, so a user searching "Ho Chi Minh" would find nothing at all.
 * Both spellings are accepted as input by every engine and by `ZoneId.of` on the
 * server, so we offer the modern name and keep the old one searchable.
 */
const RENAMED: Array<[legacy: string, current: string]> = [
  ['Africa/Asmera', 'Africa/Asmara'],
  ['America/Buenos_Aires', 'America/Argentina/Buenos_Aires'],
  ['America/Godthab', 'America/Nuuk'],
  ['Asia/Calcutta', 'Asia/Kolkata'],
  ['Asia/Dacca', 'Asia/Dhaka'],
  ['Asia/Katmandu', 'Asia/Kathmandu'],
  ['Asia/Rangoon', 'Asia/Yangon'],
  ['Asia/Saigon', 'Asia/Ho_Chi_Minh'],
  ['Asia/Ulan_Bator', 'Asia/Ulaanbaatar'],
  ['Atlantic/Faeroe', 'Atlantic/Faroe'],
  ['Europe/Kiev', 'Europe/Kyiv'],
  ['Pacific/Ponape', 'Pacific/Pohnpei'],
]

/**
 * Extra words people search by that are nowhere in the identifier — a country
 * name, or a city that shares a zone with the one IANA happened to name it after.
 */
const SEARCH_ALIASES: Record<string, string> = {
  'Asia/Ho_Chi_Minh': 'vietnam viet nam saigon hanoi ha noi hcmc',
  'Asia/Kolkata': 'india calcutta bangalore mumbai delhi',
  'Asia/Singapore': 'singapore',
  'Asia/Tokyo': 'japan',
  'Asia/Shanghai': 'china beijing peking',
  'Asia/Seoul': 'south korea',
  'Asia/Bangkok': 'thailand',
  'Asia/Jakarta': 'indonesia',
  'Asia/Manila': 'philippines',
  'Europe/Zurich': 'switzerland swiss geneva basel bern',
  'Europe/London': 'uk united kingdom england britain gb',
  'Europe/Paris': 'france',
  'Europe/Berlin': 'germany',
  'Europe/Madrid': 'spain',
  'Europe/Rome': 'italy',
  'Europe/Amsterdam': 'netherlands holland',
  'America/New_York': 'usa us eastern et est edt',
  'America/Chicago': 'usa us central ct cst cdt',
  'America/Denver': 'usa us mountain mt mst mdt',
  'America/Los_Angeles': 'usa us pacific pt pst pdt california san francisco',
  'America/Toronto': 'canada',
  'Australia/Sydney': 'australia',
  'Pacific/Auckland': 'new zealand',
  UTC: 'utc gmt universal',
}

/** Every zone the engine knows, or the curated set if it cannot tell us. */
export function supportedTimeZones(): string[] {
  const supportedValuesOf = (
    Intl as unknown as { supportedValuesOf?: (key: string) => string[] }
  ).supportedValuesOf
  if (typeof supportedValuesOf === 'function') {
    try {
      const zones = supportedValuesOf('timeZone')
      if (zones.length) return zones
    } catch {
      /* fall through to the curated list */
    }
  }
  return FALLBACK_ZONES
}

/** The zone the browser is in — the sensible default for a new account. */
export function deviceTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}

export function isValidTimeZone(zone: string): boolean {
  if (!zone) return false
  try {
    new Intl.DateTimeFormat(undefined, { timeZone: zone })
    return true
  } catch {
    return false
  }
}

/**
 * Current UTC offset of a zone, as "UTC+07:00".
 *
 * Read from the formatter rather than a table because offsets move: half the
 * world changes them twice a year, and a few countries change them by decree.
 */
export function offsetLabel(zone: string, at: Date = new Date()): string {
  for (const timeZoneName of ['longOffset', 'shortOffset'] as const) {
    try {
      const parts = new Intl.DateTimeFormat('en-US', { timeZone: zone, timeZoneName }).formatToParts(at)
      const name = parts.find((part) => part.type === 'timeZoneName')?.value
      if (name) return name.replace(/^GMT/, 'UTC') === 'UTC' ? 'UTC+00:00' : name.replace(/^GMT/, 'UTC')
    } catch {
      /* try the next style, then give up */
    }
  }
  return ''
}

/** Minutes east of UTC, used only to sort the list into a sensible order. */
function offsetMinutes(zone: string, at: Date = new Date()): number {
  const match = /^UTC([+-])(\d{2}):(\d{2})$/.exec(offsetLabel(zone, at))
  if (!match) return 0
  const sign = match[1] === '-' ? -1 : 1
  return sign * (Number(match[2]) * 60 + Number(match[3]))
}

export interface TimeZoneOption {
  value: string
  /** Shown in the closed Select. */
  label: string
  /** "Asia/Ho Chi Minh" — the identifier, minus the underscores nobody types. */
  city: string
  /** "UTC+07:00" */
  offset: string
  /** What Select's `showSearch` matches against. */
  search: string
}

/**
 * The picker's options, ordered west to east so scrolling the list walks the
 * globe instead of the alphabet. `current` is appended when it is not a zone the
 * engine recognises, so a value already stored on the account stays visible
 * instead of silently vanishing from the form.
 */
export function timeZoneOptions(current?: string | null): TimeZoneOption[] {
  // Prefer the modern spelling wherever this engine accepts it, so what we store
  // is the name the user recognises rather than the one ICU kept for stability.
  const preferred = new Map<string, string>()
  const legacyOf = new Map<string, string>()
  for (const [legacy, modern] of RENAMED) {
    if (isValidTimeZone(modern)) {
      preferred.set(legacy, modern)
      legacyOf.set(modern, legacy)
    }
  }

  // `supportedValuesOf` lists geographic zones only — it returns no UTC entry at
  // all, on any engine we tested. UTC is this app's default for a new account,
  // so leaving it out would mean a user could not re-select the zone they were
  // given, and could not deliberately choose UTC at all.
  const zones = ['UTC', ...supportedTimeZones()]
    .filter(isValidTimeZone)
    .map((zone) => preferred.get(zone) ?? zone)
  const known = new Set(zones)
  const all = current && !known.has(current) ? [...zones, current] : zones

  return [...new Set(all)]
    .map((zone) => {
      const offset = offsetLabel(zone)
      const city = zone.replace(/_/g, ' ')
      const extra = [legacyOf.get(zone), SEARCH_ALIASES[zone]].filter(Boolean).join(' ')
      return {
        value: zone,
        label: city,
        city,
        offset,
        // Identifier, spaced name, offset, the old name and any country words —
        // so "hochiminh", "Ho Chi Minh", "Saigon", "vietnam" and "+07" all land
        // on the same option. The offset is indexed *without* its "UTC" prefix:
        // with it, typing "utc" matched all 418 zones and buried the one zone
        // actually called UTC.
        search: `${city} ${zone} ${zone.replace(/[_/]/g, '')} ${offset.replace('UTC', '')} ${extra}`.toLowerCase(),
        sort: offsetMinutes(zone),
      }
    })
    .sort((a, b) => a.sort - b.sort || a.city.localeCompare(b.city))
    .map(({ sort: _sort, ...option }) => option)
}
