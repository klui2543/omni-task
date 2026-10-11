import { useRef, useState } from 'preact/hooks'
import { Sheet } from './FocusDialogs'

/**
 * A small card that shows one of [items] at a time: swipe sideways for the next (the dots also jump), the head counts
 * "1/3", and tapping the head opens all of them in a dialog. Tapping a task opens it. Adding to the card lives in the
 * dialog ([more]), so the card keeps one size next to its neighbour.
 */
export function PagedCard<T>(p: {
  title: string
  items: T[]
  empty: string
  /** The two lines a task takes: its title (a button that opens it) and one caption line. */
  render: (item: T, open: () => void) => preact.ComponentChildren
  onOpen: (item: T) => void
  more?: (close: () => void) => preact.ComponentChildren
}) {
  const [page, setPage] = useState(0)
  const [all, setAll] = useState(false)
  const strip = useRef<HTMLDivElement>(null)
  const n = p.items.length
  const shown = Math.min(page, Math.max(0, n - 1))
  const go = (i: number) => strip.current?.scrollTo({ left: i * strip.current.clientWidth, behavior: 'smooth' })
  const count = n > 1 ? `${shown + 1}/${n}` : n > 0 ? String(n) : ''

  return (
    <section class="fcard pcard">
      <button class="pcard-head" aria-haspopup="dialog" onClick={() => setAll(true)}>
        <span class="strong grow">{p.title}</span>
        {count && <span class="pcount">{count}</span>}
      </button>
      {n === 0 ? (
        <>
          <div class="pempty"><span class="pitem-title">{p.empty}</span><span class="pitem-sub" /></div>
          <div class="pdots" />
        </>
      ) : (
        <>
          <div class="pager" ref={strip} onScroll={(e) => setPage(Math.round(e.currentTarget.scrollLeft / Math.max(1, e.currentTarget.clientWidth)))}>
            {p.items.map((it, i) => <div key={i} class="pitem">{p.render(it, () => p.onOpen(it))}</div>)}
          </div>
          {/* The dots row is always there (empty for one task), so both cards keep one height. */}
          <div class="pdots">
            {n > 1 && p.items.map((_, i) => <button key={i} aria-label={`${p.title} ${i + 1} จาก ${n}`} aria-current={i === shown} onClick={() => go(i)} />)}
          </div>
        </>
      )}
      {all && (
        <Sheet title={`${p.title} ทั้งหมด`} onClose={() => setAll(false)}>
          <div class="alldlg-list">
            {n === 0 && <span class="muted">{p.empty}</span>}
            {p.items.map((it, i) => (
              <div key={i} class="alldlg-row">{p.render(it, () => { setAll(false); p.onOpen(it) })}</div>
            ))}
          </div>
          {p.more && <div class="alldlg-foot">{p.more(() => setAll(false))}</div>}
          <div class="dialog-actions"><button class="ghost" onClick={() => setAll(false)}>ปิด</button></div>
        </Sheet>
      )}
    </section>
  )
}
