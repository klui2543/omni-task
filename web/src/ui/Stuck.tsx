import { useState } from 'preact/hooks'
import { stuckReasons, today } from '../core'
import type { StuckEvent, StuckTask, StuckView, StuckWeek } from '../types'
import { Sheet } from './FocusDialogs'
import './stuck.css'

/*
 * The stuck log drawn as a path, as Android's StuckUi: one line down the left, a dot for each point on it. Colours
 * come from the theme, so the Graphite look keeps them quiet: the line and notes in grey, finished subtasks and
 * today in the accent.
 */

const shortDate = (iso: string) => new Date(iso + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })
const dayText = (iso: string) => (iso === today() ? 'วันนี้' : shortDate(iso))

/**
 * The task's path, oldest first. With [now], the path ends at today and [now] (the box to write in, or the add
 * button) sits on that last point.
 */
export function StuckPath(p: { events: StuckEvent[]; now?: preact.ComponentChildren }) {
  return (
    <ol class="stuck-path" aria-label="ความเป็นไปของงาน">
      {p.events.map((e, i) => (
        <li key={i} class={`stuck-point ${e.step.toLowerCase()}`}>
          <span class="stuck-day">{dayText(e.day)}</span>
          <span class="stuck-text">{e.text}</span>
        </li>
      ))}
      {p.now !== undefined && (
        <li class="stuck-point now">
          <span class="stuck-day">วันนี้</span>
          {p.now}
        </li>
      )}
    </ol>
  )
}

/** The box to write the reason in. */
export function StuckField(p: { text: string; onChange: (text: string) => void }) {
  return (
    <textarea class="stuck-field" rows={1} aria-label="ตอนนี้ติดอะไร" placeholder="ตอนนี้ติดอะไร" value={p.text}
      onInput={(e) => p.onChange(e.currentTarget.value)} />
  )
}

/**
 * Quick picks that start the reason. Picking one puts it at the front of the text (in place of another pick), so
 * "รอคนอื่น: รอรูปจากพี่เอ" stays one line and the words already typed are kept.
 */
export function StuckChips(p: { text: string; onChange: (text: string) => void }) {
  const picked = stuckReasons.find((r) => p.text === r || p.text.startsWith(r + ':'))
  const pick = (label: string) => {
    const rest = picked ? p.text.slice(picked.length).replace(/^:/, '').trim() : p.text.trim()
    p.onChange(label === picked ? rest : rest ? `${label}: ${rest}` : label)
  }
  return (
    <div class="chips stuck-chips" role="group" aria-label="เหตุผลที่ใช้บ่อย">
      {stuckReasons.map((r) => (
        <button key={r} type="button" class={`chip${r === picked ? ' on' : ''}`} aria-pressed={r === picked} onClick={() => pick(r)}>{r}</button>
      ))}
    </div>
  )
}

/** A bar a week, oldest first: low and grey when nothing happened, a grey note, a tall accent bar for a finished subtask. */
export function WeekBars(p: { weeks: StuckWeek[] }) {
  return (
    <span class="week-bars" aria-hidden="true">
      {p.weeks.map((w, i) => <i key={i} class={w.toLowerCase()} />)}
    </span>
  )
}

/** The task's path and a new note at its end: from a row on Focus or the edit panel. One pick and Save is enough. */
export function StuckDialog(p: { task: StuckTask; onSave: (text: string) => void; onClose: () => void }) {
  const [text, setText] = useState('')
  return (
    <Sheet title={p.task.title} onClose={p.onClose}>
      <StuckPath events={p.task.events} now={<StuckField text={text} onChange={setText} />} />
      <StuckChips text={text} onChange={setText} />
      <div class="dialog-actions">
        <button class="ghost" onClick={p.onClose}>ยกเลิก</button>
        <button class="primary" disabled={!text.trim()} onClick={() => { p.onSave(text); p.onClose() }}>บันทึก</button>
      </div>
    </Sheet>
  )
}

/** Every task with notes, each as its own path, the most recently explained first; tapping a task opens it. */
export function StuckLogDialog(p: { view: StuckView; onOpen: (key: string) => void; onClose: () => void }) {
  return (
    <Sheet title="บันทึกงานค้าง" onClose={p.onClose}>
      {p.view.log.length === 0 ? (
        <p class="muted">ยังไม่มีบันทึก จดได้จากหน้างาน หรือตอนทบทวน</p>
      ) : (
        <div class="stuck-log">
          {p.view.log.map((t) => (
            <button key={t.key} class="stuck-log-task" onClick={() => p.onOpen(t.key)}>
              <span class="stuck-log-head">
                <span class={`grow ellipsis${t.open ? '' : ' closed'}`}>{t.title}</span>
                {t.age != null && <span class="muted small">เปิดมา {t.age} วัน</span>}
              </span>
              <StuckPath events={t.events} />
            </button>
          ))}
        </div>
      )}
      <div class="dialog-actions"><button class="ghost" onClick={p.onClose}>ปิด</button></div>
    </Sheet>
  )
}

/**
 * What the weekly review's line opens: each task open a long while with no word on why, one at a time. Writing the
 * reason or skipping moves on; the review deck's choices (park, drop...) stay where they are.
 */
export function StuckDeck(p: {
  view: StuckView
  info: (key: string) => StuckTask | null
  busy: boolean
  onSave: (key: string, text: string) => void
  onOpen: (key: string) => void
  onClose: () => void
}) {
  // Notes reach the list on the next read, so the ones handled here are passed over until then.
  const [passed, setPassed] = useState<string[]>([])
  const [written, setWritten] = useState(0)
  const [text, setText] = useState('')
  const [showLog, setShowLog] = useState(false)
  const queue = p.view.toAsk.filter((t) => !passed.includes(t.key))
  const t = queue[0]
  const info = t ? p.info(t.key) : null
  const next = () => { setPassed([...passed, t!.key]); setText('') }
  if (showLog) return <StuckLogDialog view={p.view} onOpen={(key) => { setShowLog(false); p.onClose(); p.onOpen(key) }} onClose={() => setShowLog(false)} />
  return (
    <Sheet title={t ? `ติดอะไรอยู่ (เหลือ ${queue.length})` : 'จดครบแล้ว'} onClose={p.onClose}>
      {!t ? (
        <p class="muted">{written > 0 ? `จดไป ${written} งาน ดูย้อนหลังได้ที่บันทึกงานค้าง` : 'ไม่มีงานค้างนานที่ยังไม่ได้จด'}</p>
      ) : (
        <>
          <div class="review-task">
            <button class="review-title" onClick={() => { p.onClose(); p.onOpen(t.key) }}>{t.title}</button>
            {t.age != null && <span class="muted small">เปิดมา {t.age} วัน</span>}
          </div>
          <StuckPath events={(info?.events ?? []).slice(-4)} now={<StuckField text={text} onChange={setText} />} />
          <StuckChips text={text} onChange={setText} />
        </>
      )}
      <div class="dialog-actions">
        <button class="ghost" onClick={() => setShowLog(true)}>ดูบันทึก</button>
        <span class="grow" />
        {t ? (
          <>
            <button class="ghost" onClick={next}>ข้าม</button>
            <button class="primary" disabled={!text.trim() || p.busy} onClick={() => { p.onSave(t.key, text); setWritten(written + 1); next() }}>บันทึก</button>
          </>
        ) : <button class="ghost" onClick={p.onClose}>ปิด</button>}
      </div>
    </Sheet>
  )
}

/**
 * In the edit panel, right under the description for a task that is late, old or already explained: subtasks done,
 * and the path from the start to today with the button to add a note on today's point.
 */
export function StuckBlock(p: { task: StuckTask; onAdd: () => void; onLog: () => void }) {
  const t = p.task
  return (
    <section class="stuck-block" aria-label="ความเป็นไป">
      <div class="stuck-block-head">
        <span class="muted small grow">ความเป็นไป</span>
        {t.total > 0 && <span class="muted small">งานย่อย {t.done}/{t.total}</span>}
      </div>
      {t.total > 0 && <span class="bar-track stuck-bar"><span class="bar-fill" style={{ width: `${(t.done / t.total) * 100}%` }} /></span>}
      <StuckPath events={t.events} now={
        <span class="stuck-now-row">
          <button class="link" onClick={p.onAdd}>+ จด</button>
          <span class="grow" />
          <button class="link quiet small" onClick={p.onLog}>ทุกงาน</button>
        </span>
      } />
    </section>
  )
}
