import { useState } from 'preact/hooks'
import type { ListOut, Task } from '../../types'
import { RingMark, useTapOrHold } from './bits'

function Item(p: { task: Task; sub: string; selected: boolean; busy: boolean; onTick: () => void; onOpen: () => void }) {
  const hold = useTapOrHold(p.onTick, p.onOpen)
  const done = p.task.status === 'DONE'
  return (
    <li class={`pj-item${p.selected ? ' selected' : ''}`}>
      <button class="pj-item-main" role="checkbox" aria-checked={done} disabled={p.busy} aria-label={`${done ? 'ยกเลิกการติ๊ก' : 'ติ๊ก'} ${p.task.title}`} {...hold}>
        <span class="pj-item-ring"><RingMark task={p.task} /></span>
        <span class="grow stack">
          <span class={`ttitle${done || p.task.status === 'CANCELLED' ? ' struck' : ''}`}>{p.task.title}</span>
          {p.sub && <span class="small muted">{p.sub}</span>}
        </span>
      </button>
      <button class="link quiet pj-edit" aria-label={`แก้ ${p.task.title}`} onClick={p.onOpen}>แก้</button>
    </li>
  )
}

/** Inside a list: category chips, an add line, a way to pull tasks in, and the items (open first, done after). */
export function ListDetail(p: {
  list: ListOut
  task: (key: string) => Task | undefined
  selected?: string
  busy: boolean
  category: string | null
  onCategory: (c: string | null) => void
  onTick: (t: Task) => void
  onOpen: (t: Task) => void
  onAdd: (title: string, category: string | null) => void
  onInclude: () => void
  onIcon: () => void
}) {
  const [text, setText] = useState('')
  const l = p.list
  const items = l.items.filter((i) => p.category === null || i.tags.includes(p.category))
  const hint = p.category ? `เพิ่มใน ${p.category}` : `เพิ่มใน ${l.name}`
  const add = () => {
    if (!text.trim()) return
    p.onAdd(text.trim(), p.category)
    setText('')
  }
  return (
    <div class="pj-ld">
      <div class="pj-ld-left">
        <div class="row-gap">
          <button class="pj-tile big" aria-label="เปลี่ยนไอคอน" onClick={p.onIcon}>{l.emoji}</button>
          <span class="small muted">แตะไอคอนเพื่อเปลี่ยน</span>
        </div>
        {l.categories.length > 0 && (
          <div class="chips">
            <button class={`chip pj-cat${p.category === null ? ' on' : ''}`} aria-pressed={p.category === null} onClick={() => p.onCategory(null)}>ทั้งหมด</button>
            {l.categories.map((c) => (
              <button key={c} class={`chip pj-cat${p.category === c ? ' on' : ''}`} aria-pressed={p.category === c} onClick={() => p.onCategory(p.category === c ? null : c)}>{c}</button>
            ))}
          </div>
        )}
        <form class="pj-addline" onSubmit={(e) => { e.preventDefault(); add() }}>
          <input aria-label="เพิ่มรายการ" placeholder={hint} value={text} onInput={(e) => setText(e.currentTarget.value)} />
          <button class="pj-plus" aria-label="เพิ่ม" disabled={p.busy || !text.trim()}>+</button>
        </form>
        <button class="ghost pj-pull" onClick={p.onInclude}>ดึงงานที่มีอยู่แล้วเข้ามา</button>
      </div>
      <section class="group pj-ld-items">
        {items.length === 0 && <p class="muted pad">ยังว่างอยู่ เพิ่มสิ่งแรกได้เลย</p>}
        <ul class="plain">
          {items.map((i) => {
            const t = p.task(i.key)
            return t ? <Item key={i.key} task={t} sub={i.sub} selected={p.selected === i.key} busy={p.busy} onTick={() => p.onTick(t)} onOpen={() => p.onOpen(t)} /> : null
          })}
        </ul>
        <p class="small muted pj-foot">เก็บใน {l.path} และงานที่ติด #{l.tag} แตะเพื่อติ๊ก กดค้างเพื่อแก้</p>
      </section>
    </div>
  )
}
