import { useState } from 'preact/hooks'
import type { CountdownChoice, Kind, Pick } from '../types'
import { KIND_LABEL } from '../focusState'

/** The shell every Focus dialog shares: centred on wide screens, a sheet from the bottom on narrow ones. */
export function Sheet(p: { title: string; onClose: () => void; children: preact.ComponentChildren }) {
  return (
    <div class="scrim sheet-scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <section class="dialog tall" role="dialog" aria-modal="true" aria-label={p.title} onClick={(e) => e.stopPropagation()}>
        <h2>{p.title}</h2>
        {p.children}
      </section>
    </div>
  )
}

const Search = (p: { value: string; onInput: (v: string) => void; label?: string }) => (
  <label class="dsearch">
    <input aria-label={p.label ?? 'ค้นหางาน'} placeholder={p.label ?? 'ค้นหางาน'} autofocus value={p.value} onInput={(e) => p.onInput(e.currentTarget.value)} />
  </label>
)

export type ReviewAction = 'THIS_WEEK' | 'FUTURE' | 'SOMEDAY' | 'KEEP' | 'DROP'

/** One task at a time: decide, and it leaves the queue until its next review. */
export function ReviewDialog(p: {
  left: number
  handled: number
  task: { title: string; kind: Kind; age?: number; last?: string } | null
  onAct: (a: ReviewAction) => void
  onOpen: () => void
  onClose: () => void
}) {
  const t = p.task
  const lastDate = t?.last ? new Date(t.last + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' }) : null
  return (
    <Sheet title={t ? `ทบทวนงาน (เหลือ ${p.left})` : 'ทบทวนครบแล้ว'} onClose={p.onClose}>
      {!t ? (
        <>
          <p class="muted">{p.handled > 0 ? `จัดการไป ${p.handled} งาน งานที่เหลือจะกลับมาเมื่อถึงรอบ` : 'ไม่มีงานค้างทบทวน'}</p>
          <div class="dialog-actions"><button class="ghost" onClick={p.onClose}>ปิด</button></div>
        </>
      ) : (
        <>
          <div class="review-task">
            <button class="review-title" onClick={p.onOpen}>{t.title}</button>
            <span class="muted small">
              {[t.kind !== 'NORMAL' ? KIND_LABEL[t.kind] : null, t.age != null ? `สร้างมา ${t.age} วัน` : null, lastDate ? `ทบทวนล่าสุด ${lastDate}` : null]
                .filter(Boolean).join(', ')}
            </span>
          </div>
          <span class="muted">ยังอยากทำไหม</span>
          <div class="review-actions">
            <button class="rv lime" onClick={() => p.onAct('THIS_WEEK')}>ทำเสาร์นี้</button>
            {t.kind !== 'FUTURE' && <button class="rv accent" onClick={() => p.onAct('FUTURE')}>ลงทุนอนาคต</button>}
            {t.kind !== 'SOMEDAY' && <button class="rv amber" onClick={() => p.onAct('SOMEDAY')}>พักไว้ก่อน</button>}
            <button class="rv plain" onClick={() => p.onAct('KEEP')}>เก็บไว้แบบเดิม</button>
            <button class="rv red" onClick={() => p.onAct('DROP')}>ทิ้ง</button>
          </div>
          <span class="muted small">ทิ้ง = ทำเครื่องหมายยกเลิก [-] บรรทัดยังอยู่ในไฟล์</span>
          <div class="dialog-actions"><button class="ghost" onClick={p.onClose}>พอก่อน</button></div>
        </>
      )}
    </Sheet>
  )
}

/**
 * What Focus counts down to: a tab for tasks and one for calendar events, so the two do not mix, any number ticked in
 * either. A tick saves at once; the ticked ones lead their tab.
 */
export function CountdownDialog(p: {
  tasks: CountdownChoice[]
  events: CountdownChoice[]
  calendar: boolean
  onToggle: (c: CountdownChoice) => void
  onClear: () => void
  onClose: () => void
}) {
  const [text, setText] = useState('')
  const [tab, setTab] = useState<'tasks' | 'events'>('tasks')
  const tasksOn = p.tasks.filter((c) => c.on).length
  const eventsOn = p.events.filter((c) => c.on).length
  const q = text.trim().toLowerCase()
  const list = (tab === 'events' ? p.events : p.tasks).filter((c) => !q || c.title.toLowerCase().includes(q))
  const empty = tab === 'tasks' ? 'ไม่พบงานที่มีวัน' : p.calendar ? 'ไม่พบนัดในปีข้างหน้า' : 'ยังไม่ได้เชื่อมต่อ Google Calendar'
  return (
    <Sheet title={tasksOn + eventsOn > 0 ? `นับถอยหลัง (เลือกแล้ว ${tasksOn + eventsOn})` : 'นับถอยหลัง'} onClose={p.onClose}>
      <div class="segmented small-seg" role="tablist">
        <button role="tab" aria-checked={tab === 'tasks'} onClick={() => setTab('tasks')}>งาน{tasksOn > 0 && <span class="cd-count">{tasksOn}</span>}</button>
        <button role="tab" aria-checked={tab === 'events'} onClick={() => setTab('events')}>ปฏิทิน{eventsOn > 0 && <span class="cd-count">{eventsOn}</span>}</button>
      </div>
      <Search value={text} onInput={setText} label={tab === 'events' ? 'ค้นหานัด' : 'ค้นหางาน'} />
      <div class="dlist">
        {list.length === 0 && <p class="muted pad">{empty}</p>}
        {list.map((c, i) => {
          const prev = list[i - 1]
          // The ticked ones under "chosen", then the rest under their group (a month, for events).
          const head = c.on ? (prev ? null : 'เลือกไว้') : !prev || prev.on || prev.group !== c.group ? c.group : null
          return [
            head && <span key={`h${i}`} class="muted small dhead">{head}</span>,
            <button key={c.pin} class="drow" role="checkbox" aria-checked={c.on} onClick={() => p.onToggle(c)}>
              <span class={`dot-ring square${c.on ? ' on' : ''}`} />
              <span class="dtitle"><span class="ellipsis cd-title">{c.title}</span>{c.sub && <span class="muted small">{c.sub}</span>}</span>
              <span class={`small${c.late ? ' late' : ' muted'}`}>{c.text}</span>
            </button>,
          ]
        })}
      </div>
      <div class="dialog-actions">
        {tasksOn + eventsOn > 0 && <button class="ghost danger" onClick={p.onClear}>ล้างทั้งหมด</button>}
        <button class="primary" onClick={p.onClose}>เสร็จ</button>
      </div>
    </Sheet>
  )
}

/** Picks which tasks count as future work: open work without a deadline. */
export function FutureDialog(p: { chosen: Pick[]; candidates: Pick[]; busy: boolean; onSet: (key: string, on: boolean) => void; onClose: () => void }) {
  const [text, setText] = useState('')
  const match = (c: Pick) => !text.trim() || c.title.toLowerCase().includes(text.trim().toLowerCase())
  return (
    <Sheet title="เลือกงานลงทุนอนาคต" onClose={p.onClose}>
      <p class="small">งานที่ไม่มีเดดไลน์แต่สำคัญกับชีวิต หน้าโฟกัสจะหยิบขึ้นมาวันละงาน</p>
      <Search value={text} onInput={setText} />
      <div class="dlist">
        {p.chosen.length > 0 && <span class="muted small dhead">เลือกไว้แล้ว {p.chosen.length} งาน (แตะเพื่อเอาออก)</span>}
        {p.chosen.filter(match).map((c) => <PickRow key={c.key} pick={c} on busy={p.busy} onClick={() => p.onSet(c.key, false)} />)}
        {p.chosen.length > 0 && <span class="muted small dhead">งานอื่นที่ไม่มีเดดไลน์</span>}
        {p.candidates.filter(match).map((c) => <PickRow key={c.key} pick={c} on={false} busy={p.busy} onClick={() => p.onSet(c.key, true)} />)}
        {p.candidates.length === 0 && p.chosen.length === 0 && <p class="muted pad">ไม่มีงานที่ไม่มีเดดไลน์</p>}
      </div>
      <div class="dialog-actions"><button class="primary" onClick={p.onClose}>เสร็จ</button></div>
    </Sheet>
  )
}

function PickRow(p: { pick: Pick; on: boolean; busy: boolean; onClick: () => void }) {
  return (
    <button class="drow" role="checkbox" aria-checked={p.on} disabled={p.busy} onClick={p.onClick}>
      <span class={`dot-ring${p.on ? ' on' : ''}`} />
      <span class="dtitle">{p.pick.title}</span>
      <span class="muted small">{p.pick.sub}</span>
    </button>
  )
}

/**
 * Tonight's bedtime and wake time, when they are not the usual ones (those are in the profile note). A wake time picked
 * here stays as it is: a morning event no longer pulls it earlier, as on Android.
 */
export function BedtimeDialog(p: {
  time: string
  wake: string
  wakePicked: boolean
  /** [wake]: the time picked, null to go back to the usual one, undefined to leave it as it was. */
  onSave: (time: string, wake: string | null | undefined) => void
  onClose: () => void
}) {
  const [time, setTime] = useState(p.time)
  const [wake, setWake] = useState(p.wake)
  const moved = wake !== p.wake
  return (
    <Sheet title="คืนนี้" onClose={p.onClose}>
      <label class="field">
        <span class="muted small">นอนกี่โมง</span>
        <input type="time" aria-label="เวลานอน" value={time} onInput={(e) => setTime(e.currentTarget.value)} />
      </label>
      <label class="field">
        <span class="muted small">ตื่นกี่โมง</span>
        <input type="time" aria-label="เวลาตื่น" value={wake} onInput={(e) => setWake(e.currentTarget.value)} />
      </label>
      <p class="muted small">ใช้แค่คืนนี้ พรุ่งนี้กลับเป็นเวลาปกติ{p.wakePicked ? '' : ' เวลาตื่นที่ตั้งเองจะไม่ขยับตามนัดตอนเช้า'}</p>
      <div class="dialog-actions">
        {p.wakePicked && <button class="link" onClick={() => { p.onSave(time, null); p.onClose() }}>ใช้เวลาตื่นปกติ</button>}
        <span class="grow" />
        <button class="ghost" onClick={p.onClose}>ยกเลิก</button>
        <button class="primary" disabled={!time || !wake} onClick={() => { p.onSave(time, moved ? wake : undefined); p.onClose() }}>บันทึก</button>
      </div>
    </Sheet>
  )
}
