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

/**
 * A sideways swipe pages the range, as on Android: more than 64 px, and mostly sideways. A finger swipes (touch
 * events), and so does a mouse or pen dragged with its button held (pointer events); the click that ends such a
 * drag opens nothing, and a link dragged with the mouse does not start the browser's own drag.
 */
export function useSwipe(onNext: () => void, onPrev: () => void) {
  const start = useRef<{ x: number; y: number } | null>(null)
  const swiped = useRef(false)
  const act = useRef({ onNext, onPrev })
  act.current = { onNext, onPrev }
  const end = (x: number, y: number) => {
    const s = start.current
    start.current = null
    if (!s) return false
    const dx = x - s.x
    const dy = y - s.y
    if (Math.abs(dx) <= 64 || Math.abs(dx) <= Math.abs(dy) * 1.5) return false
    if (dx < 0) act.current.onNext()
    else act.current.onPrev()
    return true
  }
  return {
    onTouchStart: (e: TouchEvent) => {
      start.current = { x: e.touches[0].clientX, y: e.touches[0].clientY }
    },
    onTouchEnd: (e: TouchEvent) => {
      end(e.changedTouches[0].clientX, e.changedTouches[0].clientY)
    },
    onPointerDown: (e: PointerEvent) => {
      swiped.current = false
      if (e.pointerType !== 'touch' && e.button === 0) start.current = { x: e.clientX, y: e.clientY }
    },
    onPointerUp: (e: PointerEvent) => {
      if (e.pointerType !== 'touch' && end(e.clientX, e.clientY)) swiped.current = true
    },
    onClickCapture: (e: MouseEvent) => {
      if (!swiped.current) return
      swiped.current = false
      e.stopPropagation()
      e.preventDefault()
    },
    onDragStart: (e: DragEvent) => e.preventDefault(),
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
