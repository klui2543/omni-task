import { useEffect, useRef, useState } from 'preact/hooks'

/** The width of an element, kept up to date; 0 until it is on screen. */
export function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    setWidth(el.clientWidth)
    if (typeof ResizeObserver === 'undefined') return
    const watch = new ResizeObserver(() => setWidth(el.clientWidth))
    watch.observe(el)
    return () => watch.disconnect()
  }, [])
  return [ref, width] as const
}

/** A sideways swipe of a finger pages the range, as on Android: more than 64 px, and mostly sideways. */
export function useSwipe(onNext: () => void, onPrev: () => void) {
  const start = useRef<{ x: number; y: number } | null>(null)
  const act = useRef({ onNext, onPrev })
  act.current = { onNext, onPrev }
  return {
    onTouchStart: (e: TouchEvent) => {
      start.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }
    },
    onTouchEnd: (e: TouchEvent) => {
      const s = start.current
      start.current = null
      if (!s) return
      const dx = e.changedTouches[0].clientX - s.x
      const dy = e.changedTouches[0].clientY - s.y
      if (Math.abs(dx) > 64 && Math.abs(dx) > Math.abs(dy) * 1.5) (dx < 0 ? act.current.onNext : act.current.onPrev)()
    },
  }
}

/** Remembers a choice on this device. */
export function useStored<T extends string>(key: string, fallback: T, allowed: readonly T[]) {
  const [value, setValue] = useState<T>(() => {
    try {
      const v = localStorage.getItem(key) as T | null
      return v && allowed.includes(v) ? v : fallback
    } catch {
      return fallback
    }
  })
  return [
    value,
    (v: T) => {
      setValue(v)
      try { localStorage.setItem(key, v) } catch { /* just not kept */ }
    },
  ] as const
}
