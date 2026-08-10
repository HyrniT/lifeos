import { useDispatch, useSelector, type TypedUseSelectorHook } from 'react-redux'
import { useEffect, useState } from 'react'
import type { AppDispatch, RootState } from './store'

export const useAppDispatch: () => AppDispatch = useDispatch
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector

/** Matches a media query and re-renders on change. */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(
    () => typeof window !== 'undefined' && window.matchMedia(query).matches,
  )

  useEffect(() => {
    const list = window.matchMedia(query)
    const onChange = (event: MediaQueryListEvent) => setMatches(event.matches)
    setMatches(list.matches)
    list.addEventListener('change', onChange)
    return () => list.removeEventListener('change', onChange)
  }, [query])

  return matches
}

export const useIsMobile = () => useMediaQuery('(max-width: 768px)')
export const useIsTablet = () => useMediaQuery('(max-width: 1024px)')

/**
 * Re-reads the chart CSS variables whenever the theme attribute flips.
 *
 * Charts read their colours from CSS custom properties, which React does not
 * observe — without this the first render after a theme switch would paint the
 * previous theme's greys onto the new surface.
 */
export function useThemeVersion(): number {
  const [version, setVersion] = useState(0)

  useEffect(() => {
    const observer = new MutationObserver((mutations) => {
      if (mutations.some((m) => m.attributeName === 'data-theme')) {
        setVersion((v) => v + 1)
      }
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => observer.disconnect()
  }, [])

  return version
}
