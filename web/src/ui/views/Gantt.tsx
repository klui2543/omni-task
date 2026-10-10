import { useState } from 'preact/hooks'
import { useCalendarFeed } from '../../calendarFeed'
import type { Task } from '../../types'
import { CalendarConnect } from './CalendarConnect'
import type { ViewCtx } from './ctx'
import {
  type Ev, WEEKDAYS, addDays, daysBetween, evBeginDay, evClock, evLastDay, rangeText, weekdayIndex,
} from './dates'
import { useStored, useSwipe, useWidth } from './hooks'

const ZOOMS = ['7', '14', '30'] as const
const ZOOM_LABEL: Record<(typeof ZOOMS)[number], string> = { '7': '7 วัน', '14': '14 วัน', '30': '30 วัน' }
/** Bars narrower than this carry their title beside them instead of inside. */
const INSIDE_MIN = 90
const GAP = 6

const TINT: Record<Task['priority'], string> = {
  HIGHEST: 'var(--red)', HIGH: 'var(--amber)', MEDIUM: 'var(--blue)', NONE: 'var(--v-grey)', LOW: 'var(--v-grey)', LOWEST: 'var(--v-grey)',
}

interface Bar {
  from: number
  to: number
  sub: string
  dim?: boolean
  onClick?: () => void
}
interface Placed {
  bar: Bar
  x: number
  w: number
}
interface Beside {
  at: Placed
  x: number
  w: number
  alignEnd: boolean
  covers: boolean
}

/**
 * Where a title goes when its bars are too short for it: right after the first bar with room after it, else the
 * widest free stretch (left of the bar when it sits at the end of the range), else over the bars that crowd the row.
 */
function besideOf(placed: Placed[], rowW: number): Beside {
  const after = placed.map((p, i): Beside => {
    const x = p.x + p.w + GAP
    const end = i < placed.length - 1 ? placed[i + 1].x - GAP : rowW
    return { at: p, x, w: end - x, alignEnd: false, covers: false }
  })
  const roomy = after.find((b) => b.w >= INSIDE_MIN)
  if (roomy) return roomy
  const head = placed[0]
  const widest = [...after, { at: head, x: 0, w: head.x - GAP, alignEnd: true, covers: false }].reduce((a, b) => (b.w > a.w ? b : a))
  if (widest.w >= INSIDE_MIN / 2) return widest
  const x = head.x + head.w + GAP
  return { at: head, x, w: rowW - x, alignEnd: false, covers: true }
}

type Look = { tint: string; kind: 'task' | 'late' | 'done' | 'event' }

function Lane(p: { title: string; bars: Bar[]; look: Look; dayW: number; rowW: number }) {
  const inset = p.dayW < 20 ? 1 : 2
  const placed: Placed[] = [...p.bars].sort((a, b) => a.from - b.from).map((bar) => ({
    bar, x: p.dayW * bar.from + inset, w: Math.max(p.dayW * (bar.to - bar.from + 1) - inset * 2, 2),
  }))
  const beside = placed.length === 0 || placed.some((b) => b.w >= INSIDE_MIN) ? null : besideOf(placed, p.rowW)
  const text = (sub: string, end = false) => (
    <span class={`gtext${end ? ' end' : ''}`}>
      <span class="gtitle">{p.title}</span>
      <span class="gsub">{sub}</span>
    </span>
  )
  return (
    <div class="glane">
      {placed.map(({ bar, x, w }, i) => {
        const inside = w >= INSIDE_MIN
        const body = inside ? text(bar.sub) : null
        const style = { left: `${x}px`, width: `${w}px`, '--tint': p.look.tint }
        const cls = `gbar ${p.look.kind}${bar.dim ? ' dim' : ''}`
        // A bar too short for its title is named by the title beside it (or, when there is none, by a label).
        const name = inside ? {} : beside ? { 'aria-hidden': true, tabIndex: -1 } : { 'aria-label': `${p.title} ${bar.sub}` }
        return bar.onClick
          ? <button key={i} class={cls} style={style} onClick={bar.onClick} {...name}>{body}</button>
          : <div key={i} class={cls} style={style} {...name}>{body}</div>
      })}
      {beside && (
        <button
          class={`gbeside ${p.look.kind}${beside.covers ? ' covers' : ''}`}
          style={{ left: `${beside.x}px`, width: `${Math.max(beside.w, 0)}px`, justifyContent: beside.alignEnd ? 'flex-end' : 'flex-start' }}
          onClick={beside.at.bar.onClick}
          disabled={!beside.at.bar.onClick}
        >
          {text(beside.at.bar.sub, beside.alignEnd)}
        </button>
      )}
    </div>
  )
}

/** A task wholly before or after the range: its title at that edge with an arrow, so it does not drop out of sight. */
function EdgeLane(p: { title: string; sub: string; before: boolean; look: Look; onClick: () => void }) {
  return (
    <button class={`gedge ${p.look.kind}${p.before ? '' : ' after'}`} style={{ '--tint': p.look.tint }} onClick={p.onClick}>
      {p.before && <span class="gar" role="img" aria-label="ก่อนช่วงนี้">‹</span>}
      <span class={`gtext${p.before ? '' : ' end'}`}>
        <span class="gtitle">{p.title}</span>
        <span class="gsub">{p.sub}</span>
      </span>
      {!p.before && <span class="gar" role="img" aria-label="หลังช่วงนี้">›</span>}
    </button>
  )
}

function eventSub(e: Ev, today: string) {
  const a = evBeginDay(e)
  const b = evLastDay(e)
  return a !== b ? rangeText(a, b, today) : e.allDay ? 'ทั้งวัน' : evClock(e)
}

/**
 * Tasks and Google Calendar events as bars over 7, 14 or 30 days sharing the full width. A bar carries its title;
 * one too short for it gets the title beside it. The range opens a day before today; the arrows (or a swipe) page it.
 */
export function Gantt(p: ViewCtx & { first: string; setFirst: (first: string) => void; onConnect: () => void }) {
  // Two weeks share the width of a computer or an iPad across; a narrower screen starts on one week, as in the mockups.
  const [zoom, setZoom] = useStored('omni.ganttZoom', window.innerWidth >= 1000 ? '14' : '7', ZOOMS)
  const [showEvents, setShowEvents] = useState(true)
  const [box, width] = useWidth<HTMLDivElement>()
  const days = +zoom
  const first = p.first
  const last = addDays(first, days - 1)
  const labelStep = days === 30 ? 2 : 1
  const feed = useCalendarFeed(p.p, first, addDays(last, 1))
  const shift = (n: number) => p.setFirst(addDays(first, n * days))
  const swipe = useSwipe(() => shift(1), () => shift(-1))

  const rowW = Math.max((width || 960) - 2, 1)
  const dayW = rowW / days
  const todayAt = daysBetween(first, p.today)
  const wide = (width || 960) >= 900

  // One lane per event title, so a repeating shift reads as a single row of bars (events arrive soonest first).
  const byTitle = new Map<string, Ev[]>()
  if (showEvents && feed.connected) {
    for (const e of feed.events) {
      if (evBeginDay(e) <= last && evLastDay(e) >= first) byTitle.set(e.title, [...(byTitle.get(e.title) ?? []), e])
    }
  }
  const eventRows = [...byTitle].sort((a, b) => a[1][0].begin.localeCompare(b[1][0].begin))

  const groups = p.out.gantt
  const empty = groups.length === 0 && eventRows.length === 0

  return (
    <div class="gwrap">
      {!feed.connected && <CalendarConnect connected={false} note="" onConnect={p.onConnect} />}
      {feed.note && <CalendarConnect connected note={feed.note} onConnect={p.onConnect} />}
      <div class="gtools">
        <span class="grange">{rangeText(first, last, p.today)}</span>
        <div class="seg" role="group" aria-label="จำนวนวัน">
          {ZOOMS.map((z) => <button key={z} aria-pressed={z === zoom} onClick={() => setZoom(z)}>{ZOOM_LABEL[z]}</button>)}
        </div>
        <span class="grow" />
        {feed.connected && (
          <button class={`chip tool teal${showEvents ? ' on' : ''}`} aria-pressed={showEvents} onClick={() => setShowEvents(!showEvents)}>
            {wide ? 'นัดจาก Google Calendar' : 'นัดหมาย'}
          </button>
        )}
        <button class="chip tool" onClick={() => p.setFirst(addDays(p.today, -1))}>วันนี้</button>
        <button class="sq" aria-label={`ย้อน ${days} วัน`} onClick={() => shift(-1)}>‹</button>
        <button class="sq" aria-label={`ถัดไป ${days} วัน`} onClick={() => shift(1)}>›</button>
      </div>

      <div class="gcard" ref={box} {...swipe}>
        <div class="gdays" role="row">
          {Array.from({ length: days }, (_, i) => {
            const d = addDays(first, i)
            const isToday = d === p.today
            const shown = labelStep === 1 || (((i - todayAt) % labelStep) + labelStep) % labelStep === 0
            return (
              <div key={d} class="gday" style={{ width: `${dayW}px` }}>
                {shown && (
                  <span class="gdaylabel">
                    <span class={isToday ? 'gwd today' : 'gwd'}>{WEEKDAYS[weekdayIndex(d)]}</span>
                    <span class={isToday ? 'gnum today' : 'gnum'}>{+d.slice(8, 10)}</span>
                  </span>
                )}
              </div>
            )
          })}
        </div>
        <div class="gbody">
          <div class="glanes">
            {Array.from({ length: days }, (_, i) => i).filter((i) => i > 0 && weekdayIndex(addDays(first, i)) === 0).map((i) => (
              <span key={i} class="gguide" style={{ left: `${i * dayW}px` }} />
            ))}
            {todayAt >= 0 && todayAt < days && <span class="gtoday" style={{ left: `${(todayAt + 0.5) * dayW}px` }} />}

            {eventRows.length > 0 && <div class="gcaption teal">Google Calendar</div>}
            {eventRows.map(([title, list]) => (
              <Lane
                key={title}
                title={title || 'ไม่มีชื่อ'}
                look={{ tint: 'var(--teal)', kind: 'event' }}
                dayW={dayW}
                rowW={rowW}
                bars={list.map((e) => ({
                  from: Math.max(daysBetween(first, evBeginDay(e)), 0),
                  to: Math.min(daysBetween(first, evLastDay(e)), days - 1),
                  sub: eventSub(e, p.today),
                  dim: evLastDay(e) < p.today,
                  onClick: e.link ? () => window.open(e.link, '_blank', 'noopener,noreferrer') : undefined,
                }))}
              />
            ))}

            {groups.map((g) => (
              <div key={g.project}>
                <div class="gcaption">{g.project}</div>
                {g.spans.map((s) => {
                  const t = p.task(s.key)
                  if (!t) return null
                  const look: Look = !t.open
                    ? { tint: 'var(--lime)', kind: 'done' }
                    : t.due && t.due < p.today ? { tint: 'var(--red)', kind: 'late' } : { tint: TINT[t.priority], kind: 'task' }
                  const from = daysBetween(first, s.start)
                  const to = daysBetween(first, s.end)
                  const sub = rangeText(s.start, s.end, p.today)
                  const open = () => p.open(t)
                  if (to < 0 || from >= days) return <EdgeLane key={s.key} title={t.title} sub={sub} before={to < 0} look={look} onClick={open} />
                  return (
                    <Lane key={s.key} title={t.title} look={look} dayW={dayW} rowW={rowW}
                      bars={[{ from: Math.max(from, 0), to: Math.min(to, days - 1), sub, onClick: open }]} />
                  )
                })}
              </div>
            ))}
            {empty && <p class="muted pad">ยังไม่มีงานที่มีวันที่</p>}
          </div>
        </div>
      </div>
    </div>
  )
}
