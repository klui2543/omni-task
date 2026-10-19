import { useEffect, useRef } from 'preact/hooks'
import { host } from '../host'

/** Places that already use a sideways drag or scroll for something else; a drag that begins in one is theirs. */
const OWN_DRAG = [
  'input, textarea, select, [contenteditable="true"]',
  '[data-no-menu-swipe]',
  // Kanban columns, the filter chips, the view pager and the tool bars scroll sideways.
  '.kcols, .pager, .as-chips, .layout-bar',
  // The calendar and the Gantt chart page the range with a sideways swipe; the maps pan and zoom.
  '.tcard, .gcard, .pj-map',
].join(',')

/** True when the touch began on something that scrolls or drags sideways by itself. */
function ownsDrag(from: Element | null, stop: Element): boolean {
  for (let el = from; el && el !== stop; el = el.parentElement) {
    if (el.matches(OWN_DRAG)) return true
    if (el.scrollWidth > el.clientWidth + 1) {
      const x = getComputedStyle(el).overflowX
      if (x === 'auto' || x === 'scroll') return true
    }
  }
  return false
}

const clamp = (v: number) => Math.max(0, Math.min(1, v))
/** The finger has to move this far before it is told apart as sideways or up and down. */
const SLOP = 8
/** A flick faster than this (px per ms) opens or closes the menu whatever the distance. */
const FLICK = 0.5

/**
 * The side menu follows the finger: a drag to the right from anywhere on the page pulls it out, a drag to the left
 * pushes it back in. Let go past halfway (or flick) and it goes on to the end, otherwise it springs back. While the
 * finger is down the shell carries data-drag and the variable --om-p (0 closed, 1 open) that the menu is placed by;
 * after it the variable is dropped and [open] decides, so CSS animates the rest.
 *
 * [shell]: the element the drag is read on. [setOpen]: the menu's open state. [enabled]: only in the plugin's tab.
 */
export function useMenuDrag(shell: { current: HTMLElement | null }, open: boolean, setOpen: (open: boolean) => void, enabled: boolean) {
  const openRef = useRef(open)
  openRef.current = open
  const set = useRef(setOpen)
  set.current = setOpen

  useEffect(() => {
    const root = shell.current
    if (!enabled || !root) return
    const menu = () => root.querySelector<HTMLElement>('.om-drawer')
    let drag: { id: number; x: number; y: number; p0: number; w: number; sideways: boolean; last: { x: number; t: number }[] } | null = null
    let ate = false

    const down = (e: PointerEvent) => {
      // A finger or a pen only: a mouse keeps its own sideways drags (text, lists) and has the menu button.
      if (e.pointerType === 'mouse' || !e.isPrimary || drag) return
      const target = e.target as Element
      const w = menu()?.offsetWidth ?? 0
      if (!w) return
      if (openRef.current) {
        drag = { id: e.pointerId, x: e.clientX, y: e.clientY, p0: 1, w, sideways: false, last: [{ x: e.clientX, t: e.timeStamp }] }
        return
      }
      // Closed: only on the page itself (not over a sheet or dialog), and not where something else drags sideways.
      if (!target.closest('.content') || ownsDrag(target, root)) return
      drag = { id: e.pointerId, x: e.clientX, y: e.clientY, p0: 0, w, sideways: false, last: [{ x: e.clientX, t: e.timeStamp }] }
    }

    const move = (e: PointerEvent) => {
      if (!drag || e.pointerId !== drag.id) return
      const dx = e.clientX - drag.x
      const dy = e.clientY - drag.y
      if (!drag.sideways) {
        if (Math.abs(dx) < SLOP && Math.abs(dy) < SLOP) return
        // Up and down is the page scrolling. Closed, only to the right opens; open, only to the left closes.
        const wrongWay = drag.p0 === 0 ? dx < 0 : dx > 0
        if (Math.abs(dx) <= Math.abs(dy) || wrongWay) { drag = null; return }
        // A card lifted by a long press is being carried: that drag is its own.
        if (host.body().classList.contains('dragging')) { drag = null; return }
        drag.sideways = true
        root.dataset.drag = ''
        try { root.setPointerCapture(e.pointerId) } catch { /* the pointer is already gone */ }
      }
      root.style.setProperty('--om-p', String(clamp(drag.p0 + dx / drag.w)))
      drag.last.push({ x: e.clientX, t: e.timeStamp })
      if (drag.last.length > 5) drag.last.shift()
    }

    const end = (e: PointerEvent) => {
      if (!drag || e.pointerId !== drag.id) return
      const d = drag
      drag = null
      if (!d.sideways) return
      const p = clamp(Number(root.style.getPropertyValue('--om-p')) || 0)
      const a = d.last[0]
      const b = d.last[d.last.length - 1]
      const v = b.t > a.t ? (b.x - a.x) / (b.t - a.t) : 0
      const next = v > FLICK ? true : v < -FLICK ? false : p > 0.5
      delete root.dataset.drag
      root.style.removeProperty('--om-p')
      set.current(next)
      // The tap that ends a drag opens nothing underneath.
      ate = true
      setTimeout(() => { ate = false }, 0)
    }

    const click = (e: MouseEvent) => {
      if (!ate) return
      ate = false
      e.stopPropagation()
      e.preventDefault()
    }

    root.addEventListener('pointerdown', down)
    root.addEventListener('pointermove', move)
    root.addEventListener('pointerup', end)
    root.addEventListener('pointercancel', end)
    root.addEventListener('click', click, true)
    return () => {
      root.removeEventListener('pointerdown', down)
      root.removeEventListener('pointermove', move)
      root.removeEventListener('pointerup', end)
      root.removeEventListener('pointercancel', end)
      root.removeEventListener('click', click, true)
      delete root.dataset.drag
      root.style.removeProperty('--om-p')
    }
  }, [enabled, shell])
}
