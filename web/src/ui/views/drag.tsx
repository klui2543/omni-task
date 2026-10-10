import { useEffect, useRef, useState } from 'preact/hooks'

// Dragging a task card to a column or a quadrant, with pointer events so a mouse and a finger work the same:
// a mouse drags as soon as it moves, a finger after a half-second hold (a quick swipe still scrolls), as
// Android's long-press. Drop places carry data-drop="<id>". A tap or a keyboard user has the arrow buttons.

const MOVE = 6
const HOLD = 350

export interface DragState {
  key: string
  title: string
  x: number
  y: number
  /** The id of the drop place under the pointer. */
  over: string | null
}

interface Pending {
  key: string
  title: string
  x: number
  y: number
  touch: boolean
  timer: number
}

const overAt = (x: number, y: number) => document.elementFromPoint(x, y)?.closest('[data-drop]')?.getAttribute('data-drop') ?? null

export function useTaskDrag(onDrop: (key: string, target: string) => void) {
  const [drag, setDrag] = useState<DragState | null>(null)
  const live = useRef<DragState | null>(null)
  const pending = useRef<Pending | null>(null)
  const quiet = useRef(0)
  const drop = useRef(onDrop)
  drop.current = onDrop

  const set = (d: DragState | null) => {
    live.current = d
    setDrag(d)
  }
  const forget = () => {
    if (pending.current) clearTimeout(pending.current.timer)
    pending.current = null
  }
  const begin = (p: Pending, x: number, y: number) => {
    forget()
    document.body.classList.add('dragging')
    set({ key: p.key, title: p.title, x, y, over: overAt(x, y) })
  }
  const end = (dropIt: boolean) => {
    forget()
    const d = live.current
    if (!d) return
    document.body.classList.remove('dragging')
    set(null)
    // The click that follows a drag must not open the card.
    quiet.current = Date.now() + 80
    if (dropIt && d.over) drop.current(d.key, d.over)
  }

  useEffect(() => {
    const move = (e: PointerEvent) => {
      if (live.current) {
        set({ ...live.current, x: e.clientX, y: e.clientY, over: overAt(e.clientX, e.clientY) })
        return
      }
      const p = pending.current
      if (!p || Math.hypot(e.clientX - p.x, e.clientY - p.y) <= MOVE) return
      // A finger that moves before the hold is scrolling; a mouse that moves is dragging.
      if (p.touch) forget()
      else begin(p, e.clientX, e.clientY)
    }
    const up = () => end(true)
    const cancel = () => end(false)
    // Once a hold has started the drag, the page must not scroll under the finger.
    const block = (e: TouchEvent) => {
      if (live.current && e.cancelable) e.preventDefault()
    }
    const click = (e: MouseEvent) => {
      if (Date.now() < quiet.current) {
        e.stopPropagation()
        e.preventDefault()
      }
    }
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape') cancel()
    }
    addEventListener('pointermove', move)
    addEventListener('pointerup', up)
    addEventListener('pointercancel', cancel)
    addEventListener('touchmove', block, { passive: false })
    addEventListener('click', click, true)
    addEventListener('keydown', key)
    return () => {
      removeEventListener('pointermove', move)
      removeEventListener('pointerup', up)
      removeEventListener('pointercancel', cancel)
      removeEventListener('touchmove', block)
      removeEventListener('click', click, true)
      removeEventListener('keydown', key)
      document.body.classList.remove('dragging')
      forget()
    }
  }, [])

  /** Props that make an element a drag handle for the task [key]. */
  const grip = (key: string, title: string) => ({
    onPointerDown: (e: PointerEvent) => {
      if (e.pointerType === 'mouse' && e.button !== 0) return
      if ((e.target as Element).closest('[data-nodrag]')) return
      forget()
      const p: Pending = { key, title, x: e.clientX, y: e.clientY, touch: e.pointerType !== 'mouse', timer: 0 }
      if (p.touch) p.timer = window.setTimeout(() => begin(p, p.x, p.y), HOLD)
      pending.current = p
    },
    onContextMenu: (e: Event) => e.preventDefault(),
    'data-grip': '',
  })

  return { drag, grip }
}

/** The title following the pointer while a card is dragged. */
export function DragGhost({ drag }: { drag: DragState | null }) {
  if (!drag) return null
  return <div class="vghost" style={{ left: `${drag.x + 14}px`, top: `${drag.y + 14}px` }} aria-hidden="true">{drag.title}</div>
}
