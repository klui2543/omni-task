import type { AgendaOut, Proposal, Ranked } from '../../assistantCore'
import type { Chat } from '../../assistantState'
import { evLink, evTag } from '../views/dates'

/** "จ. 12 ต.ค." */
export const dayShort = (iso: string) =>
  new Date(iso + 'T00:00').toLocaleDateString('th-TH', { weekday: 'short', day: 'numeric', month: 'short' })
const dayOnly = (iso: string) => new Date(iso + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })

/** "12 ต.ค. ถึง 18 ต.ค." */
export const rangeTitle = (from: string, to: string) => (from === to ? dayOnly(from) : `${dayOnly(from)} ถึง ${dayOnly(to)}`)

/** A length of time in words, as the planner says it: 45 นาที, 1 ชั่วโมง 30 นาที. */
export const duration = (m: number) =>
  m < 60 ? `${m} นาที` : m % 60 === 0 ? `${m / 60} ชั่วโมง` : `${Math.floor(m / 60)} ชั่วโมง ${m % 60} นาที`

export const Bubble = (p: { text: string }) => <div class="as-bubble">{p.text}</div>

/** "About how long?" with ready answers, including "don't know" (the assistant then estimates from the kind of work). */
export function DurationCard(p: { item: Extract<Chat, { t: 'duration' }>; onAnswer: (minutes: number) => void }) {
  const answered = p.item.answered
  return (
    <section class="as-card">
      <span>งานนี้น่าจะใช้เวลาประมาณเท่าไร</span>
      <div class="chips">
        {[15, 30, 45, 60, 90, 120, 180].map((m) => (
          <button key={m} class={`chip${answered === m ? ' on' : ''}`} disabled={answered !== undefined} onClick={() => p.onAnswer(m)}>{duration(m)}</button>
        ))}
        <button class={`chip${answered === -1 ? ' on' : ''}`} disabled={answered !== undefined} onClick={() => p.onAnswer(-1)}>ไม่รู้</button>
      </div>
      {answered === -1 && <span class="muted small">ใช้ค่าประมาณจากประเภทงานแทน</span>}
    </section>
  )
}

/** "Also add to Google Calendar", as on Android's plan card; turning it on the first time explains and asks Google (see the Assistant page). */
export function CalendarSwitch(p: { on: boolean; onChange: (on: boolean) => void }) {
  return (
    <label class="as-switch">
      <span class="grow">ลง Google Calendar ด้วย</span>
      <input type="checkbox" role="switch" class="switch" aria-label="ลง Google Calendar ด้วย" checked={p.on} onChange={(e) => p.onChange(e.currentTarget.checked)} />
    </label>
  )
}

/** Three suggested times with their reasons, then the ways to put the task in the note. */
export function SlotsCard(p: {
  item: Extract<Chat, { t: 'slots' }>
  busy: boolean
  onPick: (slot: number) => void
  onMore: () => void
  onTitle: (title: string) => void
  onConfirm: () => void
  onCustom: (allDay: boolean) => void
  onClaude: () => void
  onCalendar: (on: boolean) => void
}) {
  const { plan, page, picked, title, done } = p.item
  const first = page * 3
  const shown = plan.slots.slice(first, first + 3)
  return (
    <section class="as-card">
      <span class="as-intro">{plan.intro}</span>
      {done === undefined && (
        <label class="as-title">
          <span class="muted small">ชื่องาน</span>
          <input aria-label="ชื่องาน" value={title} onInput={(e) => p.onTitle(e.currentTarget.value)} />
        </label>
      )}
      <div class="as-slots" role="radiogroup" aria-label="ช่วงเวลาที่แนะนำ">
        {shown.map((s, i) => {
          const index = first + i
          const on = picked === index
          return (
            <button key={index} class={`as-slot${on ? ' on' : ''}`} role="radio" aria-checked={on} onClick={() => p.onPick(index)}>
              <span class="as-slot-when"><span class="muted small">{s.dayLabel}</span><span class="as-slot-time">{s.start}</span></span>
              <span class="as-slot-body">
                <span class="as-slot-title"><span class="medium">{s.title}</span>{index === 0 && <span class="pill lime">แนะนำ</span>}</span>
                <span class="muted small">{s.why}</span>
              </span>
            </button>
          )
        })}
      </div>
      {shown.length > 0 && (
        <div class="as-actions">
          {plan.slots.length > 3 && <button class="ghost" onClick={p.onMore}>ดูช่วงอื่น</button>}
          <button class="primary" disabled={p.busy || (done === undefined && !title.trim())} onClick={() => done === undefined && p.onConfirm()}
            style={done !== undefined ? { background: 'var(--lime)' } : undefined}>{done ?? 'สร้างงานในโน้ต'}</button>
        </div>
      )}
      {done === undefined && (
        <>
          <CalendarSwitch on={!!p.item.toCalendar} onChange={p.onCalendar} />
          <div class="as-more">
            <span class="muted small">{shown.length === 0 ? 'ลงตารางแบบไหนดี' : 'ไม่ใช่ช่วงไหนเลย'}</span>
            <button class="chip" disabled={!title.trim() || p.busy} onClick={() => p.onCustom(true)}>ลงทั้งวัน</button>
            <button class="chip" disabled={!title.trim() || p.busy} onClick={() => p.onCustom(false)}>ตั้งเวลาเอง</button>
            <button class="chip" onClick={p.onClaude}>ถาม Claude</button>
          </div>
        </>
      )}
    </section>
  )
}

/** A numbered list of tasks with why each one sits where it does. */
export function RankedCard(p: { intro: string; items: Ranked[]; hot: number; onOpen: (key: string) => void }) {
  return (
    <section class="as-card tight">
      <span class="as-intro pad-h">{p.intro}</span>
      {p.items.length === 0 && <span class="muted pad-h">ไม่มีงานค้าง</span>}
      {p.items.map((r, i) => (
        <button key={r.key} class="as-rank" onClick={() => p.onOpen(r.key)}>
          <span class="as-rank-n">{i + 1}</span>
          <span class="as-rank-body">
            <span>{r.title}</span>
            {r.reason && <span class={`small ${i < p.hot ? 'amber-text' : 'muted'}`}>{r.reason}</span>}
          </span>
        </button>
      ))}
    </section>
  )
}

export function ReviewCard(p: { title: string; lines: string[] }) {
  return (
    <section class="as-card">
      <span class="medium">{p.title}</span>
      {p.lines.map((l) => <span key={l} class="muted">{l}</span>)}
    </section>
  )
}

/** What is coming in a range, day by day, from the note and the calendar. */
export function AgendaCard(p: { title: string; summary: string; data: AgendaOut; today: string; onOpen: (key: string) => void }) {
  return (
    <section class="as-card tight">
      <span class="medium pad-h">{p.title}</span>
      <span class="muted small pad-h">{p.summary}</span>
      {p.data.days.length === 0 && <span class="muted pad-h">ว่างทั้งช่วง ไม่มีงานหรือนัด</span>}
      {p.data.days.map((d) => (
        <div key={d.day} class="as-day">
          <span class={`as-day-label${d.day === p.today ? ' today' : ''}`}>{d.day === p.today ? 'วันนี้' : dayShort(d.day)}</span>
          <div class="as-day-items">
            {d.events.map((e, i) => {
              const Ev = evTag(e.link, 'span')
              return <Ev key={i} class="teal-text" {...evLink(e.link)}>{e.allDay ? 'ทั้งวัน ' : `${e.time} `}{e.title}</Ev>
            })}
            {d.tasks.map((t) => (
              <button key={t.key} class="link-plain as-day-task" onClick={() => p.onOpen(t.key)}>{t.due ? 'ครบ: ' : 'นัดทำ: '}{t.title}</button>
            ))}
          </div>
        </div>
      ))}
    </section>
  )
}

/** Undated work placed into free time; each row can be planned on its own or all together. */
export function RangeCard(p: {
  title: string
  proposals: Proposal[]
  accepted: number[]
  busy: boolean
  toCalendar: boolean
  onCalendar: (on: boolean) => void
  onAccept: (which: number[]) => void
  onOpen: (key: string) => void
}) {
  const left = p.proposals.map((_, i) => i).filter((i) => !p.accepted.includes(i))
  return (
    <section class="as-card">
      <span class="medium">แผน {p.title}</span>
      <span class="muted small">
        {p.proposals.length === 0 ? 'ไม่มีงานที่ยังไม่มีวัน หรือช่วงนี้ไม่มีเวลาว่างพอ' : 'จัดงานที่ยังไม่มีวันลงช่องว่าง เรียงจากเร่งสุด ไม่ชนนัดและเวร'}
      </span>
      {p.proposals.map((r, i) => {
        const done = p.accepted.includes(i)
        return (
          <div key={r.key} class="as-prop">
            <span class="as-prop-when"><span class="muted small">{dayShort(r.day)}</span><span class="medium">{r.start}</span></span>
            <button class="as-prop-body link-plain" onClick={() => p.onOpen(r.key)}>
              <span>{r.title}</span>
              <span class="muted small">{duration(r.minutes)}</span>
            </button>
            {done
              ? <span class="as-done" role="img" aria-label="ลงแผนแล้ว">✓</span>
              : <button class="as-plus" aria-label={`ลงแผน ${r.title}`} disabled={p.busy} onClick={() => p.onAccept([i])}>+</button>}
          </div>
        )
      })}
      {p.proposals.length > 0 && (
        <>
          <CalendarSwitch on={p.toCalendar} onChange={p.onCalendar} />
          <button class="primary" disabled={p.busy || left.length === 0} style={left.length === 0 ? { background: 'var(--lime)' } : undefined}
            onClick={() => p.onAccept(left)}>{left.length === 0 ? 'ลงแผนครบแล้ว' : `ลงแผนทั้งหมด ${left.length} งาน`}</button>
          <span class="muted small">ลงแผน = ตั้งวันนัดทำและเวลาเตือนให้งานนั้นในโน้ต</span>
        </>
      )}
    </section>
  )
}
