import { useLayoutEffect, useRef, useState } from 'preact/hooks'

/**
 * Moving and zooming a map of [width] by [height] inside its box, shared by the mind map and Ygdrasil: drag to move,
 * two fingers or Ctrl + wheel to zoom, the wheel alone to scroll. Until the owner zooms or drags, the map keeps
 * fitting its box; [fit] hands it back to that. A drag never counts as a tap on what is under it.
 */
export function usePanZoom(width: number, height: number, minScale = 0.3) {
  const box = useRef<HTMLDivElement>(null)
  const [scale, setScale] = useState(1)
  const [pan, setPan] = useState({ x: 0, y: 0 })
  const view = useRef({ scale: 1, pan: { x: 0, y: 0 } })
  view.current = { scale, pan }
  const touched = useRef(false)
  const apply = (s: number, pn: { x: number; y: number }) => {
    touched.current = true
    view.current = { scale: s, pan: pn }
    setScale(s)
    setPan(pn)
  }
  const size = useRef({ w: 0, h: 0 })
  const fit = () => {
    const { w, h } = size.current
    if (!w || !h) return
    const pad = 24
    const s = Math.min(1.2, (w - pad * 2) / width, (h - pad * 2) / height)
    const k = Math.min(2.5, Math.max(minScale, s))
    apply(k, { x: (w - width * k) / 2, y: (h - height * k) / 2 })
    touched.current = false
  }
  const fitRef = useRef(fit)
  fitRef.current = fit
  const zoomBy = (f: number, atPoint = { x: size.current.w / 2, y: size.current.h / 2 }) => {
    const { scale: s0, pan: p0 } = view.current
    const s = Math.min(2.5, Math.max(minScale, s0 * f))
    apply(s, { x: atPoint.x - (atPoint.x - p0.x) * (s / s0), y: atPoint.y - (atPoint.y - p0.y) * (s / s0) })
  }

  // Fitted before the first frame is drawn (a layout effect, so it never shows at scale 1 first), and again whenever
  // the box or the map changes size until the owner moves it. Wheel gestures are read here since the browser's own are passive.
  useLayoutEffect(() => {
    const el = box.current!
    const measure = () => {
      size.current = { w: el.clientWidth, h: el.clientHeight }
      if (!touched.current && size.current.w) fitRef.current()
    }
    measure()
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    const wheel = (e: WheelEvent) => {
      e.preventDefault()
      const r = el.getBoundingClientRect()
      if (e.ctrlKey || e.metaKey) zoomBy(Math.exp(-e.deltaY * 0.01), { x: e.clientX - r.left, y: e.clientY - r.top })
      else apply(view.current.scale, { x: view.current.pan.x - e.deltaX, y: view.current.pan.y - e.deltaY })
    }
    el.addEventListener('wheel', wheel, { passive: false })
    return () => { ro.disconnect(); el.removeEventListener('wheel', wheel) }
  }, [])
  useLayoutEffect(() => { if (!touched.current) fitRef.current() }, [width, height])

  // While something on the map is being carried (a task dragged to another branch), the map holds still.
  const frozen = useRef(false)
  const pointers = useRef(new Map<number, { x: number; y: number; sx: number; sy: number }>())
  const moved = useRef(false)
  const pinch = useRef(0)
  const down = (e: PointerEvent) => {
    if (e.button) return
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY })
    moved.current = false
    if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()]
      pinch.current = Math.hypot(a.x - b.x, a.y - b.y)
    }
  }
  const move = (e: PointerEvent) => {
    const cur = pointers.current.get(e.pointerId)
    if (!cur || frozen.current) return
    const r = box.current!.getBoundingClientRect()
    const dx = e.clientX - cur.x
    const dy = e.clientY - cur.y
    cur.x = e.clientX
    cur.y = e.clientY
    if (!moved.current && Math.hypot(cur.x - cur.sx, cur.y - cur.sy) > 4) {
      moved.current = true
      box.current!.setPointerCapture(e.pointerId)
    }
    if (!moved.current) return
    if (pointers.current.size >= 2) {
      const [a, b] = [...pointers.current.values()]
      const d = Math.hypot(a.x - b.x, a.y - b.y)
      if (pinch.current > 0) zoomBy(d / pinch.current, { x: (a.x + b.x) / 2 - r.left, y: (a.y + b.y) / 2 - r.top })
      pinch.current = d
    } else {
      apply(view.current.scale, { x: view.current.pan.x + dx, y: view.current.pan.y + dy })
    }
  }
  const up = (e: PointerEvent) => {
    pointers.current.delete(e.pointerId)
    pinch.current = 0
  }

  return {
    scale,
    zoomBy,
    fit,
    freeze: (on: boolean) => { frozen.current = on },
    /** Spread on the box that holds the map. */
    boxProps: {
      ref: box,
      onPointerDown: down,
      onPointerMove: move,
      onPointerUp: up,
      onPointerCancel: up,
      onClickCapture: (e: Event) => { if (moved.current) { e.stopPropagation(); e.preventDefault(); moved.current = false } },
    },
    /** The style of the layer that holds everything on the map. */
    layerStyle: { width: `${width}px`, height: `${height}px`, transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})` },
  }
}
