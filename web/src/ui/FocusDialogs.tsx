import { useState } from 'preact/hooks'
import type { Kind, Pick } from '../types'
import { KIND_LABEL } from '../focusState'

/** The shell every Focus dialog shares: centred on wide screens, a sheet from the bottom on narrow ones. */
function Sheet(p: { title: string; onClose: () => void; children: preact.ComponentChildren }) {
  return (
    <div class="scrim sheet-scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <section class="dialog tall" role="dialog" aria-modal="true" aria-label={p.title} onClick={(e) => e.stopPropagation()}>
        <h2>{p.title}</h2>
        {p.children}
      </section>
    </div>
  )
}

const Search = (p: { value: string; onInput: (v: string) => void }) => (
  <label class="dsearch">
    <input aria-label="ค้นหางาน" placeholder="ค้นหางาน" autofocus value={p.value} onInput={(e) => p.onInput(e.currentTarget.value)} />
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

/** Any open task with a date can be the countdown, today's or months away. */
export function CountdownDialog(p: { choices: Pick[]; current: string | null; onPick: (title: string | null) => void; onClose: () => void }) {
  const [text, setText] = useState('')
  const shown = p.choices.filter((c) => !text.trim() || c.title.toLowerCase().includes(text.trim().toLowerCase()))
  return (
    <Sheet title="นับถอยหลังถึงงานไหน" onClose={p.onClose}>
      <Search value={text} onInput={setText} />
      <div class="dlist">
        {shown.length === 0 && <p class="muted pad">ไม่พบงานที่มีวันครบกำหนด</p>}
        {shown.map((c) => (
          <button key={c.key} class="drow" onClick={() => { p.onPick(c.title); p.onClose() }}>
            <span class={`dtitle${c.title === p.current ? ' on' : ''}`}>{c.title}</span>
            <span class="muted small">{c.sub}</span>
          </button>
        ))}
      </div>
      <div class="dialog-actions">
        {p.current && <button class="ghost danger" onClick={() => { p.onPick(null); p.onClose() }}>เลิกนับ</button>}
        <button class="ghost" onClick={p.onClose}>ปิด</button>
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

/** Tonight's bedtime, when it is not the usual one; the usual one is in the profile note. */
export function BedtimeDialog(p: { time: string; onSave: (time: string) => void; onClose: () => void }) {
  const [time, setTime] = useState(p.time)
  return (
    <Sheet title="เวลานอนคืนนี้" onClose={p.onClose}>
      <label class="field">
        <span class="muted small">นอนกี่โมง</span>
        <input type="time" aria-label="เวลานอน" value={time} onInput={(e) => setTime(e.currentTarget.value)} />
      </label>
      <p class="muted small">ใช้แค่คืนนี้ พรุ่งนี้กลับเป็นเวลาปกติ</p>
      <div class="dialog-actions">
        <button class="ghost" onClick={p.onClose}>ยกเลิก</button>
        <button class="primary" disabled={!time} onClick={() => { p.onSave(time); p.onClose() }}>บันทึก</button>
      </div>
    </Sheet>
  )
}
