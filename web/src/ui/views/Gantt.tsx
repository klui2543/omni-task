import { useState } from 'preact/hooks'
import { useCalendarFeed } from '../../calendarFeed'
import type { Task } from '../../types'
import { CalendarConnect } from './CalendarConnect'
import type { ViewCtx } from './ctx'
import {
  type Ev, WEEKDAYS, addDays, daysBetween, evBeginDay, evClock, evLastDay, rangeText, weekdayIndex,
} from './dates'
import { useSwipe, useWidth } from './hooks'

/** The fewest and most days across; a pinch, Ctrl with the wheel, or the − and + buttons move between them. */
const MIN_DAYS = 3
const MAX_DAYS = 90
const SPAN_KEY = 'omni.timelineSpan'
/** Whose work shows: everyone's, mine (all but Claude's), Claude's; or only work that has sat too long. */
type Who = 'all' | 'me' | 'claude' | 'stuck'
const CLAUDE_TAG = 'claudedo'
const ME_TAG = 'kluido'
/** Bars narrower than this carry their title beside them instead of inside. */
const INSIDE_MIN = 90
const GAP = 6

const isClaude = (t: Task) => t.tags.some((g) => g.toLowerCase() === CLAUDE_TAG)
/** Whose turn colours the bar: mine in teal, Claude's in the accent, the rest grey. */
const tintOf = (t: Task) => (isClaude(t) ? 'var(--accent-text)' : t.tags.some((g) => g.toLowerCase() === ME_TAG) ? 'var(--teal)' : 'var(--v-grey)')
const clampSpan = (n: number) => Math.min(MAX_DAYS, Math.max(MIN_DAYS, Math.round(n)))
/** The steps the − and + buttons walk; a pinch goes anywhere between them. */
const STEPS = [3, 5, 7, 10, 14, 21, 30, 45, 60, 90]
const wider = (n: number) => STEPS.find((s) => s > n) ?? MAX_DAYS
const narrower = (n: number) => [...STEPS].reverse().find((s) => s < n) ?? MIN_DAYS

interface Bar {
  from: number
  to: number
  sub: string
  dim?: boolean
  /** Late or stuck: the second line in amber. */
  warn?: boolean
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

type Look = { tint: string; kind: 'task' | 'late' | 'done' | 'event'; waiting?: boolean; stuck?: boolean; picked?: boolean; faded?: boolean }

/** One row of bars sharing a title; a tap anywhere on it opens it ([onClick]), as on Android, a bar its own. */
function Lane(p: { title: string; bars: Bar[]; look: Look; dayW: number; rowW: number; onClick?: () => void }) {
  const inset = p.dayW < 20 ? 1 : 2
  const placed: Placed[] = [...p.bars].sort((a, b) => a.from - b.from).map((bar) => ({
    bar, x: p.dayW * bar.from + inset, w: Math.max(p.dayW * (bar.to - bar.from + 1) - inset * 2, 2),
  }))
  const beside = placed.length === 0 || placed.some((b) => b.w >= INSIDE_MIN) ? null : besideOf(placed, p.rowW)
  const text = (sub: string, end = false, warn = false) => (
    <span class={`gtext${end ? ' end' : ''}`}>
      <span class="gtitle">{p.title}</span>
      <span class={`gsub${warn ? ' warn' : ''}`}>{sub}</span>
    </span>
  )
  // A bar or the title beside it opens what it stands for, without the lane opening it again.
  const own = (act?: () => void) => act && ((e: MouseEvent) => { e.stopPropagation(); act() })
  return (
    <div class={`glane${p.onClick ? ' tappable' : ''}${p.look.faded ? ' faded' : ''}`} onClick={p.onClick}>
      {placed.map(({ bar, x, w }, i) => {
        const inside = w >= INSIDE_MIN
        const body = inside ? text(bar.sub, false, bar.warn) : null
        const style = { left: `${x}px`, width: `${w}px`, '--tint': p.look.tint }
        const cls = `gbar ${p.look.kind}${bar.dim ? ' dim' : ''}${p.look.waiting ? ' waiting' : ''}${p.look.stuck ? ' stuck' : ''}${p.look.picked ? ' picked' : ''}`
        // A bar too short for its title is named by the title beside it (or, when there is none, by a label).
        const name = inside ? {} : beside ? { 'aria-hidden': true, tabIndex: -1 } : { 'aria-label': `${p.title} ${bar.sub}` }
        return bar.onClick
          ? <button key={i} class={cls} style={style} onClick={own(bar.onClick)} {...name}>{body}</button>
          : <div key={i} class={cls} style={style} {...name}>{body}</div>
      })}
      {beside && (
        <button
          class={`gbeside ${p.look.kind}${beside.covers ? ' covers' : ''}`}
          style={{ left: `${beside.x}px`, width: `${Math.max(beside.w, 0)}px`, justifyContent: beside.alignEnd ? 'flex-end' : 'flex-start' }}
          onClick={own(beside.at.bar.onClick)}
          disabled={!beside.at.bar.onClick}
        >
          {text(beside.at.bar.sub, beside.alignEnd, beside.at.bar.warn)}
        </button>
      )}
    </div>
  )
}

/** A task wholly before or after the range: its title at that edge with an arrow, so it does not drop out of sight. */
function EdgeLane(p: { title: string; sub: string; before: boolean; look: Look; onClick: () => void }) {
  return (
    <button class={`gedge ${p.look.kind}${p.before ? '' : ' after'}${p.look.faded ? ' faded' : ''}`} style={{ '--tint': p.look.tint }} onClick={p.onClick}>
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
 * The Timeline: tasks and Google Calendar events as bars across the days. A pinch on a touch screen or trackpad,
 * Ctrl with the mouse wheel, or the − and + buttons show from a few days to three months; a swipe or the arrows move
 * through time. Chips pick whose work shows, or only work that has sat too long. A tap picks a bar: what it waits
 * for and what waits for it stay bright, and a card says when, why it waits, and opens it.
 */
export function Gantt(p: ViewCtx & { first: string; setFirst: (first: string) => void; onConnect: () => void }) {
  const [span, setSpanRaw] = useState(() => {
    try { const n = Number(localStorage.getItem(SPAN_KEY)); return n ? clampSpan(n) : window.innerWidth >= 1000 ? 14 : 7 } catch { return 14 }
  })
  const setSpan = (n: number) => { const v = clampSpan(n); setSpanRaw(v); try { localStorage.setItem(SPAN_KEY, String(v)) } catch { /* just not kept */ } }
  const [showEvents, setShowEvents] = useState(true)
  const [who, setWho] = useState<Who>('all')
  const [picked, setPicked] = useState<string | null>(null)
  const [box, width] = useWidth<HTMLDivElement>()
  const days = span
  const first = p.first
  const last = addDays(first, days - 1)
  const feed = useCalendarFeed(p.p, first, addDays(last, 1))
  const shift = (n: number) => p.setFirst(addDays(first, n * days))
  const swipe = useSwipe(() => shift(1), () => shift(-1))

  const rowW = Math.max((width || 960) - 2, 1)
  const dayW = rowW / days
  const labelStep = dayW >= 22 ? 1 : dayW >= 12 ? 2 : 7
  const todayAt = daysBetween(first, p.today)
  const wide = (width || 960) >= 900

  // A pinch arrives as a wheel with Ctrl held (trackpads), as does Ctrl with a mouse wheel; the day under the pointer stays put.
  const onWheel = (e: WheelEvent) => {
    if (!e.ctrlKey) return
    e.preventDefault()
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const at = daysBetween(first, first) + (e.clientX - rect.left) / dayW
    const next = clampSpan(span * Math.exp(e.deltaY * 0.01))
    if (next === span) return
    setSpan(next)
    p.setFirst(addDays(first, Math.round(at - (at * next) / span)))
  }

  // One lane per event title, so a repeating shift reads as a single row of bars (events arrive soonest first).
  const byTitle = new Map<string, Ev[]>()
  if (showEvents && feed.connected && who !== 'stuck') {
    for (const e of feed.events) {
      if (evBeginDay(e) <= last && evLastDay(e) >= first) byTitle.set(e.title, [...(byTitle.get(e.title) ?? []), e])
    }
  }
  const eventRows = [...byTitle].sort((a, b) => a[1][0].begin.localeCompare(b[1][0].begin))

  type S = (typeof p.out.gantt)[number]['spans'][number]
  const needs = (s: S, t: Task) => t.open && (!!s.stale || (!!t.due && t.due < p.today))
  const allSpans = p.out.gantt.flatMap((g) => g.spans)
  const keep = (s: S) => {
    const t = p.task(s.key)
    if (!t) return false
    if (who === 'me') return !isClaude(t)
    if (who === 'claude') return isClaude(t)
    if (who === 'stuck') return needs(s, t)
    return true
  }
  const groups = p.out.gantt.map((g) => ({ ...g, spans: g.spans.filter(keep) })).filter((g) => g.spans.length > 0)
  const empty = groups.length === 0 && eventRows.length === 0
  const count = (w: Who) => allSpans.filter((s) => { const t = p.task(s.key); return !!t && t.open && (w === 'me' ? !isClaude(t) : w === 'claude' ? isClaude(t) : w === 'stuck' ? needs(s, t) : true) }).length

  const sel = picked ? allSpans.find((s) => s.key === picked) ?? null : null
  const selTask = sel ? p.task(sel.key) ?? null : null
  const waiters = sel ? allSpans.filter((s) => s.waitKey === sel.key) : []
  const related = new Set(sel ? [sel.key, ...(sel.waitKey ? [sel.waitKey] : []), ...waiters.map((s) => s.key)] : [])
  const chip = (w: Who, label: string, dot?: string) => (
    <button class={`chip tool${who === w ? ' on' : ''}${w === 'stuck' ? ' stuck' : ''}`} aria-pressed={who === w} onClick={() => setWho(who === w && w !== 'all' ? 'all' : w)}>
      {dot && <span class="dot-mark" style={{ background: dot }} />}{label} {w === 'all' ? '' : count(w)}
    </button>
  )

  return (
    <div class="gwrap">
      <CalendarConnect {...feed} onConnect={p.onConnect} />
      <div class="gtools">
        <span class="grange">{rangeText(first, last, p.today)}</span>
        <span class="small muted">{days >= 56 ? `เห็นประมาณ ${Math.round(days / 3) / 10} เดือน` : `เห็น ${days} วัน`}</span>
        <button class="sq faint-btn" aria-label="ย่อ ดูภาพกว้าง" disabled={days >= MAX_DAYS} onClick={() => setSpan(wider(days))}>−</button>
        <button class="sq faint-btn" aria-label="ขยาย ดูรายวัน" disabled={days <= MIN_DAYS} onClick={() => setSpan(narrower(days))}>+</button>
        <span class="grow" />
        <button class="chip tool" onClick={() => p.setFirst(addDays(p.today, -1))}>วันนี้</button>
        <button class="sq" aria-label={`ย้อน ${days} วัน`} onClick={() => shift(-1)}>‹</button>
        <button class="sq" aria-label={`ถัดไป ${days} วัน`} onClick={() => shift(1)}>›</button>
      </div>
      <div class="gtools" role="group" aria-label="งานของใคร">
        {chip('all', 'ทั้งหมด')}
        {chip('me', 'ฉัน', 'var(--teal)')}
        {chip('claude', 'Claude', 'var(--accent-text)')}
        {chip('stuck', 'ค้างนาน', 'var(--amber)')}
        {feed.connected && (
          <button class={`chip tool teal${showEvents ? ' on' : ''}`} aria-pressed={showEvents} onClick={() => setShowEvents(!showEvents)}>
            {wide ? 'นัดจาก Google Calendar' : 'นัดหมาย'}
          </button>
        )}
      </div>

      <div class="gcard" ref={box} {...swipe} onWheel={onWheel}>
        <div class="gdays" role="row">
          {Array.from({ length: days }, (_, i) => {
            const d = addDays(first, i)
            const isToday = d === p.today
            const shown = isToday || (labelStep === 7 ? weekdayIndex(d) === 0 : labelStep === 1 || (((i - todayAt) % labelStep) + labelStep) % labelStep === 0)
            return (
              <div key={d} class="gday" style={{ width: `${dayW}px` }}>
                {shown && (
                  <span class="gdaylabel">
                    <span class={isToday ? 'gwd today' : 'gwd'}>{labelStep === 7 && !isToday ? `${+d.slice(8, 10)}/${+d.slice(5, 7)}` : WEEKDAYS[weekdayIndex(d)]}</span>
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
            {eventRows.map(([title, list]) => {
              // The lane opens the next of its events (or the first, when all are past), as on Android.
              const next = list.find((e) => evLastDay(e) >= p.today) ?? list[0]
              return (
                <Lane
                  key={title}
                  title={title || 'ไม่มีชื่อ'}
                  look={{ tint: 'var(--teal)', kind: 'event', faded: !!sel }}
                  dayW={dayW}
                  rowW={rowW}
                  onClick={next.link ? () => window.open(next.link, '_blank', 'noopener,noreferrer') : undefined}
                  bars={list.map((e) => ({
                    from: Math.max(daysBetween(first, evBeginDay(e)), 0),
                    to: Math.min(daysBetween(first, evLastDay(e)), days - 1),
                    sub: eventSub(e, p.today),
                    dim: evLastDay(e) < p.today,
                    onClick: e.link ? () => window.open(e.link, '_blank', 'noopener,noreferrer') : undefined,
                  }))}
                />
              )
            })}

            {groups.map((g) => (
              <div key={g.project}>
                <div class="gcaption accent">{g.project}</div>
                {g.spans.map((s) => {
                  const t = p.task(s.key)
                  if (!t) return null
                  const late = t.open && !!t.due && t.due < p.today
                  const look: Look = {
                    tint: !t.open ? 'var(--lime)' : tintOf(t), kind: !t.open ? 'done' : 'task',
                    waiting: !!s.waitKey, stuck: !!s.stale, picked: picked === s.key, faded: !!sel && !related.has(s.key),
                  }
                  const sub = s.waitTitle ? `รอ: ${s.waitTitle}` : s.stale ? `ค้าง ${s.stale} วัน` : late ? `เลย ${daysBetween(t.due!, p.today)} วัน` : rangeText(s.start, s.end, p.today)
                  const warn = !s.waitTitle && (!!s.stale || late)
                  const from = daysBetween(first, s.start)
                  const to = daysBetween(first, s.end)
                  const pick = () => setPicked(picked === s.key ? null : s.key)
                  if (to < 0 || from >= days) return <EdgeLane key={s.key} title={t.title} sub={sub} before={to < 0} look={look} onClick={pick} />
                  return (
                    <Lane key={s.key} title={t.title} look={look} dayW={dayW} rowW={rowW} onClick={pick}
                      bars={[{ from: Math.max(from, 0), to: Math.min(to, days - 1), sub, warn, onClick: pick }]} />
                  )
                })}
              </div>
            ))}
            {empty && <p class="muted pad">{who === 'stuck' ? 'ไม่มีงานค้างนาน' : 'ยังไม่มีงานที่มีวันที่'}</p>}
          </div>
        </div>
      </div>

      {sel && selTask && (
        <section class="gpick" aria-label={`งานที่เลือก ${selTask.title}`}>
          <div class="row-gap">
            <span class="strong grow">{selTask.title}</span>
            <span class={`gwho${isClaude(selTask) ? ' claude' : ''}`}>{isClaude(selTask) ? 'Claude' : 'ฉัน'}</span>
            <button class="sq" aria-label="ปิด" onClick={() => setPicked(null)}>×</button>
          </div>
          <span class="small">{rangeText(sel.start, sel.end, p.today)}{sel.start !== sel.end ? `  (${daysBetween(sel.start, sel.end) + 1} วัน)` : ''}</span>
          {sel.stale ? <span class="small warn-text">ไม่ขยับมา {sel.stale} วัน</span>
            : selTask.open && selTask.due && selTask.due < p.today ? <span class="small warn-text">เลยกำหนด {daysBetween(selTask.due, p.today)} วัน</span> : null}
          {sel.waitTitle && <span class="small">รอ: {sel.waitTitle}</span>}
          {waiters.map((w) => <span key={w.key} class="small">งานที่รอข้อนี้: {p.task(w.key)?.title}</span>)}
          <div class="row-gap">
            <button class="primary grow" onClick={() => p.open(selTask)}>เปิดงาน</button>
          </div>
        </section>
      )}
    </div>
  )
}
