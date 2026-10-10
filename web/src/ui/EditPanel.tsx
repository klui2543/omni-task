import type { ComponentChildren } from 'preact'
import { useEffect, useState } from 'preact/hooks'
import { describeRule, today } from '../core'
import type { PageProps } from '../Home'
import { PRIORITIES, STATUSES, labelOf } from '../query'
import type { EditOp, Task } from '../types'
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

type Chip = 'DUE' | 'SCHEDULED' | 'REMIND' | 'REPEAT' | 'PRIORITY' | 'TAG'

/**
 * Everything about one task, as in Android's edit sheet: status, dates, reminder, repeat, priority, tags,
 * the description and the subtasks. A pane beside the list on wide screens, a sheet from the bottom on narrow ones.
 * Each change is written to the note at once.
 */
export function EditPanel(p: PageProps & { task: Task; onOpen: (t: Task) => void; onClose: () => void; /** The file name of the list note the task is an item of, when opened from a list. */ noteName?: string }) {
  const t = p.task
  const [open, setOpen] = useState<Chip | null>(null)
  const [folds, setFolds] = useState<Record<string, boolean>>({})
  const [desc, setDesc] = useState(t.description)
  const [sub, setSub] = useState('')
  const [askDelete, setAskDelete] = useState(false)
  useEffect(() => setDesc(t.description), [t.key, t.description])
  useEffect(() => { setOpen(null); setAskDelete(false) }, [t.key])

  const change = (op: EditOp) => p.run(() => p.vault.change(p.fresh(t), op))
  const all = p.snapshot?.tasks ?? []
  const subs = all.filter((c) => c.parent === t.key)
  const parent = t.parent ? all.find((c) => c.key === t.parent) : null
  const doneSubs = subs.filter((c) => c.status === 'DONE').length
  const tagsInUse = [...new Set(all.flatMap((c) => c.tags))].filter((g) => !g.startsWith('remind-at-') && !t.tags.includes(g)).slice(0, 10)
  const ownTags = t.tags.filter((g) => !g.startsWith('remind-at-'))

  const chips: [Chip, string, string][] = [
    ['DUE', 'ครบกำหนด', dayLabel(t.due)],
    ['SCHEDULED', 'นัดทำ', dayLabel(t.scheduled)],
    ['REMIND', 'เตือน', t.reminder ?? ''],
    ['REPEAT', 'วนซ้ำ', t.repeatText ?? ''],
    ['PRIORITY', 'ความสำคัญ', t.priority === 'NONE' ? '' : labelOf(PRIORITIES, t.priority)],
    ['TAG', 'Tag', ownTags.length ? ownTags.map((g) => '#' + g).join(' ') : ''],
  ]

  // The note the task is in: the task note, or a list note.
  const noteLabel = p.snapshot && t.key.startsWith(p.snapshot.fileId + '#') ? p.snapshot.path.split('/').pop() : p.noteName ?? 'โน้ตลิสต์'
  const saveDesc = () => { if (desc.trim() !== t.description.trim()) change({ op: 'describe', value: desc }) }

  return (
    <div class="edit">
      <div class="edit-top">
        <span class="muted small">{noteLabel} บรรทัด {t.lineIndex + 1}</span>
        <div class="row-gap">
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

      <label class="edit-desc">
        <span class="muted small">รายละเอียด</span>
        <textarea
          aria-label="รายละเอียด"
          placeholder="เพิ่มรายละเอียด"
          rows={Math.min(8, Math.max(2, desc.split('\n').length))}
          value={desc}
          onInput={(e) => setDesc(e.currentTarget.value)}
          onBlur={saveDesc}
        />
      </label>

      <div class="segmented" role="radiogroup" aria-label="สถานะ">
        {STATUSES.map(([s, label]) => (
          <button key={s} role="radio" aria-checked={t.status === s} onClick={() => t.status !== s && change({ op: 'status', value: s })}>{label}</button>
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
          {open === 'TAG' && <TagPick own={ownTags} suggest={tagsInUse} busy={p.busy} onAdd={(g) => change({ op: 'addTag', value: g })} onRemove={(g) => change({ op: 'removeTag', value: g })} />}
        </div>
      )}

      <div class="subtasks">
        <div class="subtasks-head">
          <span>งานย่อย{subs.length ? ` ${doneSubs}/${subs.length}` : ''}</span>
          {subs.length > 0 && <span class="bar-track"><span class="bar-fill" style={{ width: `${(doneSubs / subs.length) * 100}%` }} /></span>}
        </div>
        <ul class="plain">
          {subs.map((c) => (
            <li key={c.key} class={`sub-row${c.open ? '' : ' done'}`}>
              <Check task={c} onToggle={() => p.tick(c)} />
              <button class="sub-body" onClick={() => p.onOpen(c)}>
                <span class="ttitle">{c.title}</span>
                {metaOf(c).length > 0 && <span class="muted small">{metaOf(c).map((m) => m.text).join(', ')}</span>}
              </button>
              <span class="faint" aria-hidden="true">›</span>
            </li>
          ))}
        </ul>
        <form class="sub-add" onSubmit={(e) => { e.preventDefault(); if (sub.trim()) { const v = sub; setSub(''); change({ op: 'subtask', value: v }) } }}>
          <span class="accent-text" aria-hidden="true">+</span>
          <input aria-label="เพิ่มงานย่อย" placeholder="เพิ่มงานย่อย (พรุ่งนี้ 9:00 #tag ได้)" value={sub} onInput={(e) => setSub(e.currentTarget.value)} />
        </form>
      </div>

      <div class="folds">
        <Fold label="โน้ตที่เกี่ยวข้อง" summary={t.linkNames.length ? `${t.linkNames.length} โน้ต` : ''} open={!!folds.notes} onToggle={() => setFolds({ ...folds, notes: !folds.notes })}>
          {t.linkNames.length ? t.linkNames.map((n) => <div key={n} class="fold-row accent-text">{n}</div>) : <div class="muted small">ยังไม่มี</div>}
          <div class="muted small">เพิ่มหรือเอาลิงก์ออกได้ในแอป Android</div>
        </Fold>
        <Fold label="รูปแนบ" summary={t.attachmentNames.length ? `${t.attachmentNames.length} รูป` : ''} open={!!folds.images} onToggle={() => setFolds({ ...folds, images: !folds.images })}>
          {t.attachmentNames.length ? t.attachmentNames.map((n) => <div key={n} class="fold-row">{n}</div>) : <div class="muted small">ยังไม่มี</div>}
          <div class="muted small">แนบรูปได้ในแอป Android</div>
        </Fold>
        <Fold label="รายละเอียดอื่น" summary={t.created ? `สร้าง ${shortDate(t.created)}` : ''} open={!!folds.more} onToggle={() => setFolds({ ...folds, more: !folds.more })}>
          <label class="fold-row">
            <span class="fold-label">วันเริ่ม</span>
            <input type="date" class="field grow" aria-label="วันเริ่ม" value={t.start ?? ''}
              onChange={(e) => change({ op: 'date', field: 'START', value: e.currentTarget.value || null })} />
          </label>
          <div class="fold-row"><span class="fold-label">วันที่สร้าง</span><span class="muted">{t.created ? shortDate(t.created) : 'ไม่มี'}</span></div>
          <div class="fold-row"><span class="fold-label">โปรเจกต์</span><span class="muted">{t.project ?? 'ไม่มี'}</span></div>
        </Fold>
      </div>
    </div>
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

function TagPick(p: { own: string[]; suggest: string[]; busy: boolean; onAdd: (g: string) => void; onRemove: (g: string) => void }) {
  const [text, setText] = useState('')
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
      <form class="row-gap" onSubmit={(e) => { e.preventDefault(); if (text.trim()) { p.onAdd(text); setText('') } }}>
        <input class="field grow" aria-label="Tag ใหม่" placeholder="เพิ่ม Tag" value={text} onInput={(e) => setText(e.currentTarget.value)} />
        <button class="ghost" disabled={!text.trim()}>เพิ่ม</button>
      </form>
      {p.suggest.length > 0 && (
        <div class="chips">
          {p.suggest.map((g) => <button key={g} class="chip" onClick={() => p.onAdd(g)}>#{g}</button>)}
        </div>
      )}
    </>
  )
}
