import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { cleanProjectName, listIconThemes } from '../../core'
import type { ListOut, ProjectOut, Task } from '../../types'

/** A centred box over a dimmed page; a tap outside or Escape closes it. */
function Box(p: { label: string; onClose: () => void; children: preact.ComponentChildren }) {
  return (
    <div class="scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <section class="dialog pj-dialog" role="dialog" aria-modal="true" aria-label={p.label} onClick={(e) => e.stopPropagation()}>
        <h2>{p.label}</h2>
        {p.children}
      </section>
    </div>
  )
}

/** Renaming a project shows what will change before anything is written. */
export function RenameProjectDialog(p: { project: ProjectOut; busy: boolean; onRename: (name: string) => void; onClose: () => void }) {
  const old = p.project.name
  const [name, setName] = useState(old)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => { input.current?.focus(); input.current?.select() }, [])
  const clean = cleanProjectName(name)
  const ok = clean !== '' && clean !== old && !clean.includes('/')
  return (
    <Box label="เปลี่ยนชื่อโปรเจกต์" onClose={p.onClose}>
      <form class="pj-form" onSubmit={(e) => { e.preventDefault(); if (ok) p.onRename(clean) }}>
        <label class="pj-field">
          <span class="muted small">ชื่อใหม่</span>
          <input ref={input} class="pj-focus-input" aria-label="ชื่อใหม่" value={name} onInput={(e) => setName(e.currentTarget.value)} />
        </label>
        <div class="pj-preview">
          <span>#{old}</span><span class="muted">→</span><span class="accent-text">#{clean}</span>
          <span>#{old}/…</span><span class="muted">→</span><span class="accent-text">#{clean}/…</span>
        </div>
        <span class="muted small">
          แก้ {p.project.renameLines} บรรทัดงานใน {p.project.renameFiles} ไฟล์ รวมทุก branch ลำดับงาน ดาว และสถานะ branch ย้ายตามไปด้วย
        </span>
        {clean.includes('/') && <span class="error small">ชื่อโปรเจกต์มี / ไม่ได้</span>}
        <div class="dialog-actions">
          <button type="button" class="ghost" onClick={p.onClose}>ยกเลิก</button>
          <button class="primary" disabled={!ok || p.busy}>เปลี่ยนชื่อ</button>
        </div>
      </form>
    </Box>
  )
}

/** Name a new branch or task. */
export function NameDialog(p: { title: string; onDone: (name: string) => void; onClose: () => void }) {
  const [text, setText] = useState('')
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])
  return (
    <Box label={p.title} onClose={p.onClose}>
      <form class="pj-form" onSubmit={(e) => { e.preventDefault(); if (text.trim()) p.onDone(text) }}>
        <input ref={input} class="pj-focus-input" aria-label="พิมพ์ชื่อ" placeholder="พิมพ์ชื่อ" value={text} onInput={(e) => setText(e.currentTarget.value)} />
        <div class="dialog-actions">
          <button type="button" class="ghost" onClick={p.onClose}>ยกเลิก</button>
          <button class="primary" disabled={!text.trim()}>ตกลง</button>
        </div>
      </form>
    </Box>
  )
}

/** The emoji to choose from, by theme, and a box to type any other; the chosen one is outlined. */
export function IconGrid(p: { selected: string; onPick: (emoji: string) => void }) {
  const themes = useMemo(() => listIconThemes(), [])
  const [typed, setTyped] = useState('')
  const type = (text: string) => {
    const first = firstGlyph(text)
    setTyped(first)
    if (first && [...first].some((c) => c.codePointAt(0)! > 0x7f)) p.onPick(first)
  }
  return (
    <div class="pj-icons">
      {themes.map((t) => (
        <div key={t.label} class="stack">
          <span class="muted small">{t.label}</span>
          <div class="pj-icon-row">
            {t.emoji.map((e) => (
              <button key={e} type="button" class={`pj-icon${e === p.selected ? ' on' : ''}`} aria-pressed={e === p.selected} aria-label={e} onClick={() => p.onPick(e)}>{e}</button>
            ))}
          </div>
        </div>
      ))}
      <label class="stack">
        <span class="muted small">หรือพิมพ์ emoji เอง</span>
        <input class="field" aria-label="emoji ของคุณเอง" placeholder="พิมพ์หรือวาง emoji ตรงนี้" value={typed} onInput={(e) => type(e.currentTarget.value)} />
      </label>
    </div>
  )
}

/** The first character as people see it, so a flag or a family emoji stays whole. */
const firstGlyph = (text: string): string => {
  const t = text.trim()
  if (!t) return ''
  const Seg = (Intl as unknown as { Segmenter?: new (l?: string, o?: object) => { segment: (s: string) => Iterable<{ segment: string }> } }).Segmenter
  if (Seg) for (const part of new Seg(undefined, { granularity: 'grapheme' }).segment(t)) return part.segment
  return [...t][0]
}

export function IconPickerDialog(p: { selected: string; onPick: (emoji: string) => void; onClose: () => void }) {
  return (
    <Box label="เลือกไอคอน" onClose={p.onClose}>
      <div class="pj-scroll"><IconGrid selected={p.selected} onPick={p.onPick} /></div>
      <div class="dialog-actions"><button class="ghost" onClick={p.onClose}>ปิด</button></div>
    </Box>
  )
}

/** Name, icon and categories for a new list note. */
export function CreateListDialog(p: { busy: boolean; onCreate: (name: string, icon: string, categories: string[]) => void; onClose: () => void }) {
  const [name, setName] = useState('')
  const [icon, setIcon] = useState('📋')
  const [cats, setCats] = useState('')
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])
  return (
    <Box label="สร้างรายการใหม่" onClose={p.onClose}>
      <form class="pj-form" onSubmit={(e) => { e.preventDefault(); if (name.trim()) p.onCreate(name, icon, cats.split(',').map((c) => c.trim()).filter(Boolean)) }}>
        <div class="pj-scroll pj-form">
          <label class="pj-field">
            <span class="muted small">ชื่อ</span>
            <input ref={input} class="pj-focus-input" aria-label="ชื่อรายการ" placeholder="ชื่อ เช่น หนังสือที่อยากอ่าน" value={name} onInput={(e) => setName(e.currentTarget.value)} />
          </label>
          <span class="muted small">ไอคอน</span>
          <IconGrid selected={icon} onPick={setIcon} />
          <label class="pj-field">
            <span class="muted small">หมวดย่อย คั่นด้วยจุลภาค (ไม่ใส่ก็ได้)</span>
            <input aria-label="หมวดย่อย" placeholder="เช่น นิยาย, ธุรกิจ, สุขภาพ" value={cats} onInput={(e) => setCats(e.currentTarget.value)} />
          </label>
        </div>
        <div class="dialog-actions">
          <button type="button" class="ghost" onClick={p.onClose}>ยกเลิก</button>
          <button class="primary" disabled={!name.trim() || p.busy}>สร้าง</button>
        </div>
      </form>
    </Box>
  )
}

/** Pick any number of open tasks from the vault to tag into the list; they stay where they are. */
export function IncludeDialog(p: {
  list: ListOut
  pool: Task[]
  startCategory: string | null
  busy: boolean
  onAdd: (picked: Task[], category: string | null) => void
  onClose: () => void
}) {
  const [search, setSearch] = useState('')
  const [picked, setPicked] = useState<string[]>([])
  const [category, setCategory] = useState<string | null>(p.startCategory)
  const q = search.trim().toLowerCase()
  const shown = p.pool
    .filter((t) => !q || t.title.toLowerCase().includes(q) || t.tags.some((g) => g.toLowerCase().includes(q.replace(/^#/, ''))))
    .sort((a, b) => a.title.toLowerCase().localeCompare(b.title.toLowerCase()))
    .slice(0, 150)
  const n = picked.length
  return (
    <Box label={`ดึงงานเข้า ${p.list.name}`} onClose={p.onClose}>
      <span class="muted small">งานยังอยู่ที่เดิม แค่ติด #{p.list.tag} เพิ่ม</span>
      <label class="dsearch">
        <input aria-label="ค้นชื่องานหรือ #แท็ก" placeholder="ค้นชื่องานหรือ #แท็ก" value={search} onInput={(e) => setSearch(e.currentTarget.value)} />
      </label>
      {p.list.categories.length > 0 && (
        <div class="chips">
          <button type="button" class={`chip${category === null ? ' on' : ''}`} aria-pressed={category === null} onClick={() => setCategory(null)}>ไม่ใส่หมวด</button>
          {p.list.categories.map((c) => (
            <button type="button" key={c} class={`chip${category === c ? ' on' : ''}`} aria-pressed={category === c} onClick={() => setCategory(category === c ? null : c)}>{c}</button>
          ))}
        </div>
      )}
      <div class="pj-scroll">
        {shown.length === 0 && <span class="muted pad">ไม่พบงาน</span>}
        {shown.map((t) => {
          const on = picked.includes(t.key)
          const tags = t.tags.filter((g) => !g.startsWith('remind-at-')).map((g) => '#' + g).join(' ')
          return (
            <button key={t.key} type="button" class={`pj-pick${on ? ' on' : ''}`} role="checkbox" aria-checked={on} onClick={() => setPicked(on ? picked.filter((k) => k !== t.key) : [...picked, t.key])}>
              <span class="pj-box">{on ? '✓' : ''}</span>
              <span class="grow">{t.title}</span>
              <span class="small accent-text ellipsis">{tags}</span>
            </button>
          )
        })}
      </div>
      <button class="primary pj-wide" disabled={n === 0 || p.busy} onClick={() => p.onAdd(p.pool.filter((t) => picked.includes(t.key)), category)}>
        {n === 0 ? 'เลือกงานที่จะเพิ่ม' : `เพิ่ม ${n} งาน`}
      </button>
    </Box>
  )
}
