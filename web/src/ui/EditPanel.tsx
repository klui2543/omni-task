import type { ComponentChildren } from 'preact'
import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { describeRule, kindPicker, today } from '../core'
import { markReviewed } from '../focusState'
import { customKindTags, kindsStore } from '../kinds'
import type { PageProps } from '../Home'
import { PRIORITIES, STATUSES, labelOf } from '../query'
import type { EditOp, KindPicker, Task } from '../types'
import { fileName, needsChoice, prepare, sizeLabel, type Prepared } from '../image'
import { linkText, resolveLink, searchNotes } from '../vault'
import { KindManager } from './KindManager'
import { Check, metaOf } from './TaskRow'

/** A date [days] after [iso], as YYYY-MM-DD. */
const plus = (iso: string, days: number) => {
  const d = new Date(iso + 'T00:00')
  d.setDate(d.getDate() + days)
  return d.toLocaleDateString('en-CA')
}
const weekday = (iso: string) => new Date(iso + 'T00:00').getDay()
const shortDate = (iso: string) => new Date(iso + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })

/** The quick dates of Android's date chips: today, tomorrow, this Saturday, next Monday. */
const quickDates = (): [string, string][] => {
  const t = today()
  return [
    ['วันนี้', t],
    ['พรุ่งนี้', plus(t, 1)],
    ['เสาร์นี้', plus(t, (6 - weekday(t) + 7) % 7)],
    ['สัปดาห์หน้า', plus(t, ((8 - weekday(t)) % 7) || 7)],
  ]
}
const dayLabel = (iso: string | null) => {
  if (!iso) return ''
  const t = today()
  return iso === t ? 'วันนี้' : iso === plus(t, 1) ? 'พรุ่งนี้' : shortDate(iso)
}

const REPEATS: [string, string][] = [
  ['ทุกวัน', 'every day'], ['ทุกวันทำงาน', 'every weekday'], ['ทุกสัปดาห์', 'every week'],
  ['ทุก 2 สัปดาห์', 'every 2 weeks'], ['ทุกเดือน', 'every month'], ['ทุกปี', 'every year'],
]
const TIMES = ['08:00', '12:00', '17:30', '20:00']

type Chip = 'DUE' | 'SCHEDULED' | 'REMIND' | 'REPEAT' | 'PRIORITY' | 'KIND' | 'TAG'

/** The title of the first open task [t] waits on (⛔), as Android's "waits on"; null when it waits on nothing open. */
const waitingOn = (t: Task, all: Task[]) =>
  t.dependsOn?.length ? all.find((c) => c.id && c.open && t.dependsOn!.includes(c.id))?.title ?? null : null

/** A tag as Android's TaskLine.normalizeTag writes it: no "#", spaces made dashes. */
const normalizeTag = (text: string) => text.trim().replace(/^#/, '').trim().replace(/[\s#]+/g, '-')

/**
 * Everything about one task, as in Android's edit sheet: status, dates, reminder, repeat, priority, tags,
 * the description and the subtasks. A pane beside the list on wide screens, a sheet from the bottom on narrow ones.
 * Each change is written to the note at once.
 */
export function EditPanel(p: PageProps & { task: Task; onOpen: (t: Task) => void; onClose: () => void; /** The file name of the list note the task is an item of, when opened from a list. */ noteName?: string }) {
  const t = p.task
  const [open, setOpen] = useState<Chip | null>(null)
  const [folds, setFolds] = useState<Record<string, boolean>>({})
  const [sub, setSub] = useState('')
  const [askDelete, setAskDelete] = useState(false)
  // The kinds were changed in the manager: the field shows the new choices.
  const [, setKindsRev] = useState(0)
  // A different task closes what was open. Not on the first render: that effect runs after paint, and a tap on a
  // chip landing before it was undone (the panel looked as if it ignored the tap).
  const shown = useRef(t.key)
  useEffect(() => {
    if (shown.current === t.key) return
    shown.current = t.key
    setOpen(null)
    setAskDelete(false)
  }, [t.key])

  const change = (op: EditOp) => p.run(() => p.vault.change(p.fresh(t), op))
  const all = p.snapshot?.tasks ?? []
  const subs = all.filter((c) => c.parent === t.key)
  const parent = t.parent ? all.find((c) => c.key === t.parent) : null
  const doneSubs = subs.filter((c) => c.status === 'DONE').length
  // The first open task this one waits on (⛔), as Android's "waits on" field.
  const waitsOn = waitingOn(t, all)
  // Every tag in the vault (most used first), for the Tag field to search, as Android's add-tag dialog.
  const tagsInUse = useMemo(() => {
    const count = new Map<string, number>()
    all.forEach((c) => c.tags.forEach((g) => { if (!g.startsWith('remind-at-')) count.set(g, (count.get(g) ?? 0) + 1) }))
    return [...count.entries()].sort((a, b) => b[1] - a[1]).map(([g]) => g)
  }, [all]).filter((g) => !t.tags.includes(g))
  // The names on other waiting tasks (#รอ/name), for "who is waiting".
  const waitNames = useMemo(() => [...new Set(all.flatMap((c) => c.tags).filter((g) => g.startsWith('รอ/') && g.length > 3).map((g) => g.slice(3)))].sort((a, b) => a.localeCompare(b, 'th')), [all])
  // Tick logic for "done" from the status row too, as Android's setStatus: a repeating task moves on to its next
  // round, open subtasks are asked about, and a finished task is offered the archive.
  const setStatus = (s: Task['status']) => {
    if (t.status === s) return
    if (s === 'DONE' && t.open) p.tick(t)
    else change({ op: 'status', value: s })
  }

  // The kinds on offer come from this device's choices (hidden ones are left out unless the task has that kind).
  const kinds = kindPicker(kindsStore.get(), t.raw)
  const kindLabel = kinds.customTag
    ? kinds.custom.find((c) => c.tag === kinds.customTag)?.label ?? ''
    : kinds.current === 'NORMAL' ? '' : kinds.builtin.find((b) => b.id === kinds.current)?.label ?? ''
  const ownTags = t.tags.filter((g) => !g.startsWith('remind-at-') && !isKindTag(g, kinds.customTag))

  const chips: [Chip, string, string][] = [
    ['DUE', 'ครบกำหนด', dayLabel(t.due)],
    ['SCHEDULED', 'นัดทำ', dayLabel(t.scheduled)],
    ['REMIND', 'เตือน', t.reminder ?? ''],
    ['REPEAT', 'วนซ้ำ', t.repeatText ?? ''],
    ['PRIORITY', 'ความสำคัญ', t.priority === 'NONE' ? '' : labelOf(PRIORITIES, t.priority)],
    ['KIND', 'ประเภท', kindLabel],
    ['TAG', 'Tag', ownTags.length ? ownTags.map((g) => '#' + g).join(' ') : ''],
  ]

  // The note the task is in: the task note, or a list note.
  const noteLabel = t.note ? t.note.split('/').pop() : p.snapshot && t.key.startsWith(p.snapshot.fileId + '#') ? p.snapshot.path.split('/').pop() : p.noteName ?? 'โน้ตลิสต์'
  // A finished task (project work too) can go to the archive by hand; not a subtask or a list's item, as on Android.
  const archivable = !t.open && !t.parent && !t.list && !p.noteName

  return (
    <div class="edit">
      <div class="edit-top">
        <span class="muted small">{noteLabel} บรรทัด {t.lineIndex + 1}</span>
        <div class="row-gap">
          {archivable && <button class="ghost small-btn" onClick={() => { p.onClose(); p.archive(t) }}>เก็บเข้าคลัง</button>}
          <button class="ghost small-btn danger" onClick={() => setAskDelete(true)}>ลบงาน</button>
          <button class="ghost small-btn" onClick={p.onClose}>ปิด</button>
        </div>
      </div>
      {askDelete && (
        <div class="confirm" role="alertdialog" aria-label="ลบงานนี้?">
          <span class="grow">ลบงานนี้{subs.length ? ` พร้อมงานย่อย ${subs.length} งาน` : ''}?</span>
          <button class="link quiet" onClick={() => setAskDelete(false)}>ยกเลิก</button>
          <button class="link danger" onClick={() => { setAskDelete(false); p.onClose(); p.remove(t) }}>ลบ</button>
        </div>
      )}

      {parent && (
        <button class="link parent-link" onClick={() => p.onOpen(parent)}>‹ {parent.title}</button>
      )}
      <div class="edit-title">
        <Check task={t} onToggle={() => p.tick(t)} />
        <h2 class={t.open ? '' : 'done'}>{t.title}</h2>
      </div>

      <Description {...p} task={t} onSave={(text) => change({ op: 'describe', value: text })} />

      <div class="segmented" role="radiogroup" aria-label="สถานะ">
        {STATUSES.map(([s, label]) => (
          <button key={s} role="radio" aria-checked={t.status === s} onClick={() => setStatus(s)}>{label}</button>
        ))}
      </div>

      <div class="chips">
        {chips.map(([id, name, value]) => (
          <button key={id} class={`chip field-chip${open === id ? ' on' : ''}`} aria-expanded={open === id} onClick={() => setOpen(open === id ? null : id)}>
            <span class="muted">{name}</span>{value && <strong> {value}</strong>}
          </button>
        ))}
      </div>

      {open && (
        <div class="chip-panel">
          {(open === 'DUE' || open === 'SCHEDULED') && (
            <DatePick title={open === 'DUE' ? 'ครบกำหนด' : 'นัดทำ'} value={open === 'DUE' ? t.due : t.scheduled} busy={p.busy}
              onPick={(v) => change({ op: 'date', field: open, value: v })} />
          )}
          {open === 'REMIND' && <RemindPick task={t} busy={p.busy} onChange={change} />}
          {open === 'REPEAT' && <RepeatPick rule={t.recurrence} busy={p.busy} onPick={(v) => change({ op: 'recurrence', value: v })} />}
          {open === 'PRIORITY' && (
            <>
              <span class="muted small">ความสำคัญ</span>
              <div class="chips">
                {PRIORITIES.map(([k, label]) => (
                  <button key={k} class={`chip${t.priority === k ? ' on' : ''}`} aria-pressed={t.priority === k} onClick={() => change({ op: 'priority', value: k })}>{label}</button>
                ))}
              </div>
            </>
          )}
          {open === 'KIND' && (
            <KindPick picker={kinds} task={t} busy={p.busy} names={waitNames} onKindsChanged={() => setKindsRev((n) => n + 1)}
              onChange={(op) => {
                // A new kind counts as a review of the task, as on Android (and Focus's review card).
                if (op.op === 'kind') markReviewed(t.title)
                change(op)
              }} />
          )}
          {open === 'TAG' && <TagPick own={ownTags} suggest={tagsInUse} busy={p.busy} onAdd={(g) => change({ op: 'addTag', value: g })} onRemove={(g) => change({ op: 'removeTag', value: g })} />}
        </div>
      )}

      <div class="subtasks">
        <div class="subtasks-head">
          <span>งานย่อย{subs.length ? ` ${doneSubs}/${subs.length}` : ''}</span>
          {subs.length > 0 && <span class="bar-track"><span class="bar-fill" style={{ width: `${(doneSubs / subs.length) * 100}%` }} /></span>}
        </div>
        <SubtaskList subs={subs} all={all} onOpen={p.onOpen} onTick={p.tick}
          onOrder={(order) => change({ op: 'order', value: JSON.stringify(order.map((c) => p.fresh(c).raw)) })} />
        <form class="sub-add" onSubmit={(e) => { e.preventDefault(); if (sub.trim()) { const v = sub; setSub(''); change({ op: 'subtask', value: v }) } }}>
          <span class="accent-text" aria-hidden="true">+</span>
          <input aria-label="เพิ่มงานย่อย" placeholder="เพิ่มงานย่อย (พรุ่งนี้ 9:00 #tag ได้)" value={sub} onInput={(e) => setSub(e.currentTarget.value)} />
        </form>
      </div>

      <div class="folds">
        <Fold label="โน้ตที่เกี่ยวข้อง" summary={t.linkNames.length ? `${t.linkNames.length} โน้ต` : ''} open={!!folds.notes} onToggle={() => setFolds({ ...folds, notes: !folds.notes })}>
          <LinkedNotes {...p} task={t} onChange={change} />
        </Fold>
        <Fold label="รูปแนบ" summary={t.attachmentNames.length ? `${t.attachmentNames.length} รูป` : ''} open={!!folds.images} onToggle={() => setFolds({ ...folds, images: !folds.images })}>
          <Images {...p} task={t} />
        </Fold>
        <Fold label="รายละเอียดอื่น" summary={t.created ? `สร้าง ${shortDate(t.created)}` : ''} open={!!folds.more} onToggle={() => setFolds({ ...folds, more: !folds.more })}>
          <label class="fold-row">
            <span class="fold-label">วันเริ่ม</span>
            <input type="date" class="field grow" aria-label="วันเริ่ม" value={t.start ?? ''}
              onChange={(e) => change({ op: 'date', field: 'START', value: e.currentTarget.value || null })} />
          </label>
          <div class="fold-row"><span class="fold-label">วันที่สร้าง</span><span class="muted">{t.created ? shortDate(t.created) : 'ไม่มี'}</span></div>
          <div class="fold-row"><span class="fold-label">โปรเจกต์</span><span class="muted">{t.project ?? 'ไม่มี'}</span></div>
          <div class="fold-row"><span class="fold-label">ต้องรอ</span><span class="muted">{waitsOn ?? 'ไม่มี'}</span></div>
        </Fold>
      </div>
    </div>
  )
}

/**
 * The description under the title, as Android's: shown as text, tapped to write, then kept with Save or let go with
 * Cancel. A long one (over 280 characters or 6 lines) can move to a note of its own while it is being written.
 */
function Description(p: PageProps & { task: Task; onSave: (text: string) => void }) {
  const t = p.task
  const [editing, setEditing] = useState(false)
  const [text, setText] = useState(t.description)
  useEffect(() => { setEditing(false); setText(t.description) }, [t.key])
  useEffect(() => { if (!editing) setText(t.description) }, [t.description])
  const long = text.length > 280 || text.split('\n').length > 6
  const move = () => {
    const words = text
    setEditing(false)
    p.run(async () => {
      const r = await p.vault.moveDescription(p.fresh(t), words)
      return r.ok ? { ...r, notice: `ย้ายรายละเอียดไปที่ ${r.path} แล้ว` } : r
    })
  }
  return (
    <div class={`edit-desc${editing ? ' editing' : ''}`}>
      <div class="desc-head">
        <span class="muted small grow">รายละเอียด</span>
        {editing && long && <button class="link small" onClick={move}>ย้ายไปโน้ตแยก</button>}
      </div>
      {editing ? (
        <>
          <textarea
            aria-label="รายละเอียด"
            placeholder="เขียนรายละเอียดงาน บรรทัดละเรื่อง"
            rows={Math.min(10, Math.max(3, text.split('\n').length))}
            value={text}
            autoFocus
            onInput={(e) => setText(e.currentTarget.value)}
          />
          <div class="dialog-actions">
            <button class="ghost" onClick={() => { setText(t.description); setEditing(false) }}>ยกเลิก</button>
            <button class="primary" disabled={p.busy} onClick={() => { if (text.trim() !== t.description.trim()) p.onSave(text); setEditing(false) }}>บันทึก</button>
          </div>
        </>
      ) : (
        <button class={`desc-text${t.description ? '' : ' faint'}`} title="แตะเพื่อแก้" onClick={() => setEditing(true)}>
          {t.description || 'แตะเพื่อเขียนรายละเอียด'}
        </button>
      )}
    </div>
  )
}

/**
 * The direct subtasks, numbered 1, 2, 3 in the order to do them, as Android's ordered list: the next open one's number
 * is filled, a done one's is green, and one still waiting on open work (⛔) is greyed with whom it waits on. Each can
 * be dragged by its handle (or moved with the arrow keys on it) into a new order, which is written when let go.
 */
function SubtaskList(p: { subs: Task[]; all: Task[]; onOpen: (t: Task) => void; onTick: (t: Task) => void; onOrder: (order: Task[]) => void }) {
  const [order, setOrder] = useState<Task[] | null>(null)
  const [held, setHeld] = useState<string | null>(null)
  const list = useRef<HTMLUListElement>(null)
  const shown = order ?? p.subs
  // A new read of the note shows what was written.
  useEffect(() => setOrder(null), [p.subs.map((c) => c.key + c.raw).join('\n')])
  const commit = (next: Task[]) => {
    if (next.some((c, i) => c.key !== p.subs[i]?.key)) p.onOrder(next)
    else setOrder(null)
  }
  const moveTo = (from: Task[], key: string, to: number) => {
    const next = from.filter((c) => c.key !== key)
    next.splice(Math.max(0, Math.min(to, next.length)), 0, from.find((c) => c.key === key)!)
    return next
  }
  const at = (y: number) => {
    const rows = [...(list.current?.children ?? [])] as HTMLElement[]
    const i = rows.findIndex((r) => { const b = r.getBoundingClientRect(); return y < b.top + b.height / 2 })
    return i < 0 ? rows.length - 1 : i
  }
  const next = shown.find((c) => c.open)
  return (
    <ul class="plain" ref={list}>
      {shown.map((c, i) => {
        const done = c.status === 'DONE'
        const waits = done ? null : waitingOn(c, p.all)
        const meta = [...(waits ? [`รอ ${waits}`] : []), ...metaOf(c).map((m) => m.text)].join(', ')
        return (
        <li key={c.key} class={`sub-row${c.open ? '' : ' done'}${held === c.key ? ' held' : ''}`}>
          {p.subs.length > 1 && (
            <button class="sub-handle" aria-label={`ลากเพื่อเรียง ${c.title}`} title="ลากเพื่อเรียง"
              onPointerDown={(e) => { e.preventDefault(); e.currentTarget.setPointerCapture(e.pointerId); setHeld(c.key); setOrder(shown) }}
              onPointerMove={(e) => {
                if (held !== c.key || !order) return
                const to = at(e.clientY)
                if (order[to]?.key !== c.key) setOrder(moveTo(order, c.key, to))
              }}
              onPointerUp={() => { if (held === c.key && order) commit(order); setHeld(null) }}
              onPointerCancel={() => { setHeld(null); setOrder(null) }}
              onKeyDown={(e) => {
                const step = e.key === 'ArrowUp' ? -1 : e.key === 'ArrowDown' ? 1 : 0
                if (!step || i + step < 0 || i + step >= shown.length) return
                e.preventDefault()
                const next = moveTo(shown, c.key, i + step)
                setOrder(next)
                commit(next)
              }}>⋮⋮</button>
          )}
          <span class={`pj-n sub-n${c === next ? ' next' : done ? ' done' : ''}`} aria-label={c === next ? `ลำดับ ${i + 1} ทำถัดไป` : `ลำดับ ${i + 1}`}>{i + 1}</span>
          <Check task={c} onToggle={() => p.onTick(c)} />
          <button class="sub-body" onClick={() => p.onOpen(c)}>
            <span class={`ttitle${waits ? ' blocked' : ''}`}>{c.title}</span>
            {meta && <span class={`small ${waits ? 'amber-text' : c === next ? 'accent-text' : 'muted'}`}>{meta}</span>}
          </button>
          <span class="faint" aria-hidden="true">›</span>
        </li>
        )
      })}
    </ul>
  )
}

/**
 * The notes linked under the task: each opens in Obsidian and says which folder it is in (or, in amber, that the note
 * is not in the vault yet); a link on its own line can be taken off (one written in the task's text stays), and any
 * note of the vault can be added.
 */
function LinkedNotes(p: PageProps & { task: Task; onChange: (op: EditOp) => void }) {
  const t = p.task
  const [vaultName, setVaultName] = useState<string | null>(null)
  const [picking, setPicking] = useState(false)
  const [paths, setPaths] = useState<string[] | null>(null)
  const [query, setQuery] = useState('')
  const [failed, setFailed] = useState(false)
  useEffect(() => { p.vault.vaultName().then(setVaultName, () => undefined) }, [p.vault])
  // The vault's notes are read when the fold opens, to say where each linked note is.
  useEffect(() => {
    if (paths) return
    setFailed(false)
    p.vault.notePaths().then(setPaths, () => setFailed(true))
  }, [picking])
  const href = (name: string) =>
    `obsidian://open?${vaultName ? `vault=${encodeURIComponent(vaultName)}&` : ''}file=${encodeURIComponent(name)}`
  const linked = new Set(t.linkNames)
  const matches = paths ? searchNotes(query, paths).filter((path) => !linked.has(linkText(path, paths))) : []
  const where = (link: string) => {
    if (!paths) return null
    const path = resolveLink(link, paths)
    if (path === null) return <span class="amber-text small">ยังไม่มีโน้ตนี้</span>
    return <span class="faint small ellipsis">{path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : 'ราก Vault'}</span>
  }
  return (
    <>
      {t.linkNames.length ? t.linkNames.map((n) => (
        <div key={n} class="fold-row link-row">
          <span class="grow stack link-name">
            <a class="accent-text ellipsis" href={href(n)} title="เปิดใน Obsidian">{n.split('/').pop()}</a>
            {where(n)}
          </span>
          {(t.linkLines ?? []).includes(n) && <button class="chip-x" aria-label={`เอาลิงก์ ${n} ออก`} onClick={() => p.onChange({ op: 'unlink', value: n })}>×</button>}
        </div>
      )) : <div class="muted small">ยังไม่มี</div>}
      {!picking ? (
        <button class="link" onClick={() => setPicking(true)}>+ ลิงก์โน้ต</button>
      ) : (
        <div class="note-pick" role="group" aria-label="เลือกโน้ต">
          <div class="row-gap">
            <input class="field grow" type="search" aria-label="ค้นหาโน้ต" placeholder="ค้นหาโน้ตใน Vault" value={query} autoFocus onInput={(e) => setQuery(e.currentTarget.value)} />
            <button class="ghost" onClick={() => { setPicking(false); setQuery('') }}>ปิด</button>
          </div>
          {failed ? <div class="error small">อ่านรายชื่อโน้ตไม่ได้ ลองอีกครั้ง</div>
            : !paths ? <div class="muted small">กำลังอ่านโน้ตใน Vault</div>
            : matches.length === 0 ? <div class="muted small">ไม่พบโน้ต</div>
            : (
              <ul class="plain note-results">
                {matches.map((path) => (
                  <li key={path}>
                    <button class="note-result" onClick={() => { p.onChange({ op: 'link', value: linkText(path, paths) }); setPicking(false); setQuery('') }}>
                      <span class="ellipsis">{path.split('/').pop()!.replace(/\.md$/i, '')}</span>
                      <span class="faint small ellipsis">{path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : 'ราก Vault'}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
        </div>
      )}
    </>
  )
}

/** One attached image, read from the Attachments folder. */
function Thumb(p: PageProps & { name: string; onView: () => void }) {
  const [src, setSrc] = useState<string | null | undefined>(undefined)
  useEffect(() => {
    let live = true
    p.vault.image(p.name).then((u) => live && setSrc(u), () => live && setSrc(null))
    return () => { live = false }
  }, [p.name])
  return (
    <button class="thumb" aria-label={`ดูรูป ${p.name}`} onClick={p.onView}>
      {src ? <img src={src} alt="" /> : <span class="faint small">{src === null ? 'ไม่พบไฟล์' : '...'}</span>}
    </button>
  )
}

/** The images under the task: shown small, opened large, taken off (the file goes to Drive's trash), and new ones attached as WebP. */
function Images(p: PageProps & { task: Task }) {
  const t = p.task
  const [viewing, setViewing] = useState<string | null>(null)
  const [full, setFull] = useState<string | null>(null)
  const [askRemove, setAskRemove] = useState(false)
  const [choice, setChoice] = useState<Prepared | null>(null)
  const [error, setError] = useState('')
  const [reading, setReading] = useState(false)
  useEffect(() => {
    setFull(null)
    setAskRemove(false)
    if (viewing) p.vault.image(viewing).then(setFull, () => undefined)
  }, [viewing])
  const save = (blob: Blob) => {
    setChoice(null)
    const name = fileName()
    p.run(async () => {
      const r = await p.vault.attach(p.fresh(t), name, blob)
      return r.ok ? { ...r, notice: `แนบรูปแล้ว (${name}, ${sizeLabel(blob.size)})` } : r
    })
  }
  const pick = async (file: File | undefined) => {
    if (!file) return
    setError('')
    setReading(true)
    try {
      const prepared = await prepare(file)
      if (needsChoice(prepared)) setChoice(prepared)
      else save(prepared.full)
    } catch {
      setError('อ่านรูปนี้ไม่ได้')
    } finally {
      setReading(false)
    }
  }
  return (
    <>
      {t.attachmentNames.length > 0 && (
        <div class="thumbs">
          {t.attachmentNames.map((n) => <Thumb key={n} {...p} name={n} onView={() => setViewing(n)} />)}
        </div>
      )}
      <label class="link attach">
        <input type="file" accept="image/*" aria-label="แนบรูป" class="sr-only" onChange={(e) => { const f = e.currentTarget.files?.[0]; e.currentTarget.value = ''; pick(f) }} />
        {reading ? 'กำลังเตรียมรูป' : '+ แนบรูป'}
      </label>
      <div class="muted small">บันทึกเป็น WebP ในโฟลเดอร์ Attachments</div>
      {error && <div class="error small">{error}</div>}
      {choice && (
        <div class="confirm img-choice" role="alertdialog" aria-label="ขนาดรูป">
          <span class="grow">รูปนี้ใหญ่ ({choice.width} x {choice.height}) จะเก็บแบบไหน?</span>
          <button class="link" onClick={() => save(choice.reduced!)}>ย่อ ({sizeLabel(choice.reduced!.size)})</button>
          <button class="link quiet" onClick={() => save(choice.full)}>เต็ม ({sizeLabel(choice.full.size)})</button>
          <button class="link quiet" onClick={() => setChoice(null)}>ยกเลิก</button>
        </div>
      )}
      {viewing && (
        <div class="viewer" role="dialog" aria-label={`รูป ${viewing}`} onClick={(e) => { if (e.target === e.currentTarget) setViewing(null) }}>
          <div class="viewer-box">
            {full ? <img src={full} alt={viewing} /> : <span class="muted">กำลังโหลดรูป</span>}
            <div class="viewer-bar">
              <span class="muted small grow ellipsis">{viewing}</span>
              {askRemove ? (
                <>
                  <span class="small">ลบรูปนี้?</span>
                  <button class="link quiet" onClick={() => setAskRemove(false)}>ยกเลิก</button>
                  <button class="link danger" onClick={() => { const n = viewing; setViewing(null); p.run(() => p.vault.detach(p.fresh(t), n)) }}>ลบ</button>
                </>
              ) : (
                <button class="link danger" onClick={() => setAskRemove(true)}>ลบรูป</button>
              )}
              <button class="link" onClick={() => setViewing(null)}>ปิด</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function Fold(p: { label: string; summary: string; open: boolean; onToggle: () => void; children: ComponentChildren }) {
  return (
    <div class="fold">
      <button class="fold-head" aria-expanded={p.open} onClick={p.onToggle}>
        <span class="grow">{p.label}</span>
        <span class="muted small">{p.summary}</span>
        <span class={`caret${p.open ? ' open' : ''}`}>›</span>
      </button>
      {p.open && <div class="fold-body">{p.children}</div>}
    </div>
  )
}

function DatePick(p: { title: string; value: string | null; busy: boolean; onPick: (v: string | null) => void }) {
  return (
    <>
      <span class="muted small">{p.title}</span>
      <div class="chips">
        {quickDates().map(([label, iso]) => (
          <button key={label} class={`chip${p.value === iso ? ' on' : ''}`} aria-pressed={p.value === iso} onClick={() => p.onPick(iso)}>{label}</button>
        ))}
        <label class="chip date-chip">
          <span>เลือกวัน</span>
          <input type="date" aria-label={`เลือกวัน${p.title}`} value={p.value ?? ''} onChange={(e) => e.currentTarget.value && p.onPick(e.currentTarget.value)} />
        </label>
        {p.value && <button class="chip warn" onClick={() => p.onPick(null)}>ไม่มี</button>}
      </div>
    </>
  )
}

function RemindPick(p: { task: Task; busy: boolean; onChange: (op: EditOp) => void }) {
  const t = p.task
  const [on, setOn] = useState<'DUE' | 'SCHEDULED'>(t.reminderOn ?? (t.due || !t.scheduled ? 'DUE' : 'SCHEDULED'))
  const day = on === 'DUE' ? t.due : t.scheduled
  const pick = (v: string | null) => p.onChange({ op: 'reminder', value: v, on })
  return (
    <>
      <span class="muted small">เตือน</span>
      <div class="segmented small-seg" role="radiogroup" aria-label="เตือนตาม">
        <button role="radio" aria-checked={on === 'DUE'} onClick={() => setOn('DUE')}>วันครบกำหนด</button>
        <button role="radio" aria-checked={on === 'SCHEDULED'} onClick={() => setOn('SCHEDULED')}>วันนัดทำ</button>
      </div>
      <div class="chips">
        {TIMES.map((h) => (
          <button key={h} class={`chip${t.reminder === h && t.reminderOn === on ? ' on' : ''}`} onClick={() => pick(h)}>{h}</button>
        ))}
        <label class="chip date-chip">
          <span>เลือกเวลา</span>
          <input type="time" aria-label="เลือกเวลาเตือน" value={t.reminder ?? ''} onChange={(e) => e.currentTarget.value && pick(e.currentTarget.value)} />
        </label>
        {t.reminder && <button class="chip warn" onClick={() => pick(null)}>ไม่เตือน</button>}
      </div>
      {!day && <span class="muted small">ยังไม่มี{on === 'DUE' ? 'วันครบกำหนด' : 'วันนัดทำ'} เตือนจะดังเมื่อมีวันแล้ว</span>}
    </>
  )
}

/** The six usual rules, or one typed the way the Tasks plugin reads it, checked as it is typed. */
function RepeatPick(p: { rule: string | null; busy: boolean; onPick: (v: string | null) => void }) {
  const preset = REPEATS.find(([, r]) => r === p.rule)
  const [custom, setCustom] = useState(p.rule && !preset ? p.rule.replace(/ when done$/, '') : '')
  const [typing, setTyping] = useState(!!p.rule && !preset)
  const [whenDone, setWhenDone] = useState(!!p.rule?.endsWith(' when done'))
  const typed = custom.trim() + (whenDone ? ' when done' : '')
  const words = custom.trim() ? describeRule(typed) : null
  return (
    <>
      <span class="muted small">วนซ้ำ</span>
      <div class="chips">
        {REPEATS.map(([label, r]) => (
          <button key={r} class={`chip${p.rule === r ? ' on' : ''}`} aria-pressed={p.rule === r} onClick={() => p.onPick(r)}>{label}</button>
        ))}
        <button class={`chip${typing ? ' on' : ''}`} onClick={() => setTyping(!typing)}>แบบอื่น</button>
        {p.rule && <button class="chip warn" onClick={() => p.onPick(null)}>ไม่วนซ้ำ</button>}
      </div>
      {typing && (
        <form class="repeat-form" onSubmit={(e) => { e.preventDefault(); if (words) p.onPick(typed) }}>
          <label class="stack">
            <span class="muted small">พิมพ์แบบปลั๊กอิน Tasks</span>
            <input class="field" aria-label="กฎวนซ้ำ" placeholder="every week on Monday, Thursday" value={custom} onInput={(e) => setCustom(e.currentTarget.value)} />
          </label>
          {custom.trim() && <span class={words ? 'lime-text' : 'error'}>{words ?? 'ปลั๊กอิน Tasks อ่านแบบนี้ไม่ได้'}</span>}
          <label class="check-line">
            <input type="checkbox" checked={whenDone} onChange={(e) => setWhenDone(e.currentTarget.checked)} />
            <span>นับจากวันที่ทำเสร็จ</span>
          </label>
          <div class="dialog-actions"><button class="primary" disabled={!words}>บันทึก</button></div>
        </form>
      )}
    </>
  )
}

/** Kind tags (#รอ, #อนาคต, #สักวัน, #รอ/name and the owner's own) are shown by the kind field, not as plain tags. */
const isKindTag = (tag: string, own: string | null) =>
  ['รอ', 'อนาคต', 'สักวัน'].some((k) => tag === k || tag.startsWith(k + '/')) || (!!own && tag.toLowerCase() === own.toLowerCase())

/**
 * The "ประเภท" field of Android's edit sheet: a choice stored as a tag; waiting asks who (offering the names on other
 * waiting tasks), and the kinds are managed in a dialog right here.
 */
function KindPick(p: { picker: KindPicker; task: Task; busy: boolean; names: string[]; onChange: (op: EditOp) => void; onKindsChanged: () => void }) {
  const k = p.picker
  const [asking, setAsking] = useState(false)
  const [managing, setManaging] = useState(false)
  const [who, setWho] = useState(k.who ?? '')
  const custom = customKindTags()
  useEffect(() => { setAsking(false); setWho(k.who ?? '') }, [p.task.key, k.current])
  const wait = (name: string) => { setAsking(false); p.onChange({ op: 'kind', value: 'WAITING', who: name.trim(), custom }) }
  const names = p.names.filter((n) => !who.trim() || n.toLowerCase().includes(who.trim().toLowerCase()))
  return (
    <>
      <span class="muted small">ประเภทงาน</span>
      <div class="chips" role="group" aria-label="ประเภทงาน">
        {k.builtin.map((b) => {
          const on = !k.customTag && k.current === b.id
          return (
            <button key={b.id} class={`chip${on ? ' on' : ''}`} aria-pressed={on} disabled={p.busy}
              onClick={() => { if (b.id === 'WAITING') setAsking(true); else if (!on) { setAsking(false); p.onChange({ op: 'kind', value: b.id, custom }) } }}>{b.label}</button>
          )
        })}
        {k.custom.map((c) => (
          <button key={c.tag} class={`chip${k.customTag === c.tag ? ' on' : ''}`} aria-pressed={k.customTag === c.tag} disabled={p.busy}
            onClick={() => { if (k.customTag !== c.tag) { setAsking(false); p.onChange({ op: 'customKind', value: c.tag, custom }) } }}>{c.label}</button>
        ))}
        <button class="chip" onClick={() => setManaging(true)}>จัดการประเภท</button>
      </div>
      {asking && (
        <>
          <form class="row-gap" onSubmit={(e) => { e.preventDefault(); wait(who) }}>
            <input class="field grow" aria-label="ใครรองานนี้" placeholder="ชื่อ (เว้นว่างได้)" value={who} onInput={(e) => setWho(e.currentTarget.value)} autoFocus />
            <button class="primary" disabled={p.busy}>ตั้งเป็นมีคนรอ</button>
          </form>
          {names.length > 0 && (
            <div class="chips pick-list" role="group" aria-label="ชื่อที่เคยใช้">
              {names.map((n) => <button key={n} class="chip" disabled={p.busy} onClick={() => wait(n)}>{n}</button>)}
            </div>
          )}
        </>
      )}
      {k.hint && <span class="muted small">{k.hint}</span>}
      {managing && <KindManager onChange={p.onKindsChanged} onClose={() => setManaging(false)} />}
    </>
  )
}

/** The task's own tags, and a box that searches every tag in the vault (or adds a new one), as Android's add-tag dialog. */
function TagPick(p: { own: string[]; suggest: string[]; busy: boolean; onAdd: (g: string) => void; onRemove: (g: string) => void }) {
  const [text, setText] = useState('')
  const query = normalizeTag(text).toLowerCase()
  const matches = p.suggest.filter((g) => !query || g.toLowerCase().includes(query))
  return (
    <>
      <span class="muted small">Tag</span>
      <div class="chips">
        {p.own.map((g) => (
          <span key={g} class="chip on saved">
            <span class="chip-main">#{g}</span>
            <button class="chip-x" aria-label={`เอา #${g} ออก`} onClick={() => p.onRemove(g)}>×</button>
          </span>
        ))}
      </div>
      <form class="row-gap" onSubmit={(e) => { e.preventDefault(); if (normalizeTag(text)) { p.onAdd(normalizeTag(text)); setText('') } }}>
        <input class="field grow" aria-label="Tag ใหม่" placeholder="ค้นหาหรือเพิ่ม Tag" value={text} onInput={(e) => setText(e.currentTarget.value)} />
        <button class="ghost" disabled={!normalizeTag(text)}>เพิ่ม</button>
      </form>
      {matches.length > 0 ? (
        <div class="chips pick-list" role="group" aria-label="Tag ในโน้ต">
          {matches.map((g) => <button key={g} class="chip" onClick={() => { p.onAdd(g); setText('') }}>#{g}</button>)}
        </div>
      ) : query && <span class="muted small">ยังไม่มี Tag นี้ในโน้ต กดเพิ่มเพื่อใช้เป็น Tag ใหม่</span>}
    </>
  )
}
