import { useState } from 'preact/hooks'

function Sheet(p: { title: string; onClose: () => void; children: preact.ComponentChildren }) {
  return (
    <div class="scrim sheet-scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <section class="dialog" role="dialog" aria-modal="true" aria-label={p.title} onClick={(e) => e.stopPropagation()}>
        <h2>{p.title}</h2>
        {p.children}
      </section>
    </div>
  )
}

/** A task put on a day by hand: all day, or at a time the owner picks. */
export function CustomTimeDialog(p: { title: string; allDay: boolean; day: string; busy: boolean; onSet: (day: string, time: string | null) => void; onClose: () => void }) {
  const [day, setDay] = useState(p.day)
  const [time, setTime] = useState('09:00')
  return (
    <Sheet title={p.allDay ? 'ลงทั้งวัน' : 'ตั้งเวลาเอง'} onClose={p.onClose}>
      <p class="muted small">{p.title}</p>
      <label class="as-fieldcol">
        <span class="muted small">วัน</span>
        <input class="as-field" type="date" aria-label="วัน" value={day} onInput={(e) => setDay(e.currentTarget.value)} />
      </label>
      {!p.allDay && (
        <label class="as-fieldcol">
          <span class="muted small">เวลา</span>
          <input class="as-field" type="time" aria-label="เวลา" value={time} onInput={(e) => setTime(e.currentTarget.value)} />
        </label>
      )}
      <div class="dialog-actions">
        <button class="ghost" onClick={p.onClose}>ยกเลิก</button>
        <button class="primary" disabled={p.busy || !day || (!p.allDay && !time)} onClick={() => p.onSet(day, p.allDay ? null : time)}>{p.allDay ? 'ลงทั้งวัน' : 'สร้างงาน'}</button>
      </div>
    </Sheet>
  )
}

/** Pick any range, then either look at it or plan it. */
export function RangeDialog(p: { from: string; onAgenda: (from: string, to: string) => void; onPlan: (from: string, to: string) => void; onClose: () => void }) {
  const [from, setFrom] = useState(p.from)
  const [to, setTo] = useState(p.from)
  const ok = !!from && !!to && to >= from
  return (
    <Sheet title="เลือกช่วงวัน" onClose={p.onClose}>
      <label class="as-fieldcol">
        <span class="muted small">ตั้งแต่</span>
        <input class="as-field" type="date" aria-label="ตั้งแต่วันที่" value={from} onInput={(e) => { setFrom(e.currentTarget.value); if (to < e.currentTarget.value) setTo(e.currentTarget.value) }} />
      </label>
      <label class="as-fieldcol">
        <span class="muted small">ถึง</span>
        <input class="as-field" type="date" aria-label="ถึงวันที่" value={to} min={from} onInput={(e) => setTo(e.currentTarget.value)} />
      </label>
      <div class="dialog-actions">
        <button class="ghost" onClick={p.onClose}>ยกเลิก</button>
        <button class="ghost" disabled={!ok} onClick={() => p.onAgenda(from, to)}>ดูว่ามีอะไร</button>
        <button class="primary" disabled={!ok} onClick={() => p.onPlan(from, to)}>วางแผนให้</button>
      </div>
    </Sheet>
  )
}
