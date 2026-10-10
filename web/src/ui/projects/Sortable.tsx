import type { JSX } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'

/**
 * Rows the owner can drag up and down by their handle, as Android's arrange mode and ordered task list do: the
 * lifted row follows the pointer and swaps with a neighbour once it passes the neighbour's middle. The new order
 * is handed over when the pointer lifts. The handle also answers the arrow keys, one place per press.
 */
export function useSortable(keys: string[], onCommit: (order: string[]) => void) {
  const root = useRef<HTMLElement>(null)
  const [drag, setDrag] = useState<{ key: string; order: string[]; offset: number } | null>(null)
  const live = useRef<{ key: string; order: string[]; offset: number; lastY: number; moved: boolean } | null>(null)
  const commit = useRef(onCommit)
  commit.current = onCommit
  const stop = useRef<(() => void) | null>(null)
  useEffect(() => () => stop.current?.(), [])

  const height = (key?: string) => {
    if (!key) return 0
    const row = [...(root.current?.querySelectorAll<HTMLElement>('[data-sort-key]') ?? [])].find((e) => e.dataset.sortKey === key)
    return row?.getBoundingClientRect().height ?? 0
  }

  const down = (key: string) => (e: JSX.TargetedPointerEvent<HTMLElement>) => {
    if (e.button) return
    e.preventDefault()
    live.current = { key, order: [...keys], offset: 0, lastY: e.clientY, moved: false }
    setDrag({ key, order: live.current.order, offset: 0 })
    const move = (ev: PointerEvent) => {
      const s = live.current!
      s.offset += ev.clientY - s.lastY
      s.lastY = ev.clientY
      const at = s.order.indexOf(s.key)
      const below = s.order[at + 1]
      const above = s.order[at - 1]
      if (below && s.offset > height(below) / 2) {
        s.order = s.order.map((k, i) => (i === at ? below : i === at + 1 ? s.key : k))
        s.offset -= height(below)
        s.moved = true
      } else if (above && s.offset < -height(above) / 2) {
        s.order = s.order.map((k, i) => (i === at ? above : i === at - 1 ? s.key : k))
        s.offset += height(above)
        s.moved = true
      }
      setDrag({ key: s.key, order: s.order, offset: s.offset })
    }
    const up = (ev: PointerEvent) => {
      stop.current?.()
      const s = live.current
      live.current = null
      setDrag(null)
      if (s?.moved && ev.type === 'pointerup') commit.current(s.order)
    }
    stop.current = () => {
      removeEventListener('pointermove', move)
      removeEventListener('pointerup', up)
      removeEventListener('pointercancel', up)
      stop.current = null
    }
    addEventListener('pointermove', move)
    addEventListener('pointerup', up)
    addEventListener('pointercancel', up)
  }

  const keyDown = (key: string) => (e: JSX.TargetedKeyboardEvent<HTMLElement>) => {
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return
    e.preventDefault()
    const at = keys.indexOf(key)
    const to = at + (e.key === 'ArrowUp' ? -1 : 1)
    if (at < 0 || to < 0 || to >= keys.length) return
    const order = [...keys]
    order[at] = keys[to]
    order[to] = key
    commit.current(order)
  }

  return {
    ref: root,
    /** The order to draw: the one being dragged, or the saved one. */
    order: drag?.order ?? keys,
    lifted: drag?.key ?? null,
    /** Props for a row, so it can be found, and lifted while dragged. */
    row: (key: string) => ({
      'data-sort-key': key,
      style: drag?.key === key ? { transform: `translateY(${drag.offset}px)`, position: 'relative' as const, zIndex: 2 } : undefined,
    }),
    /** Props for the handle button. */
    handle: (key: string) => ({ onPointerDown: down(key), onKeyDown: keyDown(key) }),
  }
}
