import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { useCalendarFeed } from '../../calendarFeed'
import type { Task } from '../../types'
import { TaskRow } from '../TaskRow'
import { CalendarConnect } from './CalendarConnect'
import type { ViewCtx } from './ctx'
import {
  type Ev, WEEKDAYS, addDays, addMonths, evCovers, evInStrip, evTimes, firstOfMonth, longDay, mediumDay, mondayOf, monthTitle, rangeText, weekdayIndex,
} from './dates'
import { useStored, useSwipe } from './hooks'

const SPANS = ['month', '7', '3', '1'] as const
const SPAN_LABEL: Record<(typeof SPANS)[number], string> = { month: 'เดือน', '7': '7 วัน', '3': '3 วัน', '1': 'วัน' }

interface Dated {
  t: Task
  /** When the reminder fires (yyyy-MM-ddTHH:mm). */
  at?: string
}

/** The calendar's tasks, by the days they fall on: due or scheduled, never cancelled ones. */
function useDated(p: ViewCtx): Dated[] {
  return useMemo(
    () => p.out.calendar.flatMap((c) => {
      const t = p.task(c.key)
      return t && t.status !== 'CANCELLED' ? [{ t, at: c.at }] : []
    }),
    [p.out, p.task],
  )
}
const tasksOn = (all: Dated[], d: string) => all.filter(({ t }) => t.due === d || t.scheduled === d)

/** The calendar: a month grid, or a timeline of 7, 3 or 1 days, each with Google Calendar events beside the tasks. */
export function CalendarViews(p: ViewCtx & { onConnect: () => void }) {
  const [span, setSpan] = useStored('omni.calSpan', 'month', SPANS)
  return (
    <div class="cal">
      <div class="seg" role="tablist" aria-label="ช่วงเวลา">
        {SPANS.map((s) => <button key={s} role="tab" aria-selected={s === span} onClick={() => setSpan(s)}>{SPAN_LABEL[s]}</button>)}
      </div>
      {span === 'month' ? <Month {...p} /> : <Timeline {...p} days={+span} />}
    </div>
  )
}

function Month(p: ViewCtx & { onConnect: () => void }) {
  const [shown, setShown] = useState(firstOfMonth(p.today))
  const [sel, setSel] = useState(p.today)
  const firstCell = mondayOf(shown)
  const feed = useCalendarFeed(p.p, firstCell, addDays(firstCell, 42))
  const all = useDated(p)
  const eventsOn = (d: string) => feed.events.filter((e) => e.begin.slice(0, 10) <= d && (e.end.slice(0, 10) > d || e.begin.slice(0, 10) === d))
  const go = (n: number) => {
    const next = addMonths(shown, n)
    setShown(next)
    setSel(next.slice(0, 7) === p.today.slice(0, 7) ? p.today : next)
  }
  const weeks = [0, 1, 2, 3, 4, 5].filter((w) => w < 5 || addDays(firstCell, w * 7).slice(0, 7) === shown.slice(0, 7))
  const dayTasks = tasksOn(all, sel)
  const dayEvents = eventsOn(sel)

  return (
    <>
      <CalendarConnect connected={feed.connected} note={feed.note} onConnect={p.onConnect} />
      <div class="mwrap">
        <section class="mcard" aria-label="ปฏิทินเดือน">
          <div class="mtop">
            <span class="mtitle2">{monthTitle(shown)}</span>
            <button class="chip tool" onClick={() => { setShown(firstOfMonth(p.today)); setSel(p.today) }}>วันนี้</button>
            <button class="sq" aria-label="เดือนก่อน" onClick={() => go(-1)}>‹</button>
            <button class="sq" aria-label="เดือนถัดไป" onClick={() => go(1)}>›</button>
          </div>
          <div class="mdays" aria-hidden="true">{WEEKDAYS.map((w) => <span key={w}>{w}</span>)}</div>
          <div class="mcells">
            {weeks.flatMap((w) => Array.from({ length: 7 }, (_, i) => addDays(firstCell, w * 7 + i))).map((d) => {
              const ts = tasksOn(all, d)
              const dots = [
                ts.some(({ t }) => t.open && t.due != null && t.due < p.today) && 'var(--red)',
                ts.length > 0 && 'var(--accent)',
                eventsOn(d).length > 0 && 'var(--teal)',
              ].filter(Boolean) as string[]
              const inMonth = d.slice(0, 7) === shown.slice(0, 7)
              return (
                <button key={d} class={`mcell${d === sel ? ' sel' : ''}${inMonth ? '' : ' out'}${d === p.today ? ' today' : ''}`} aria-label={longDay(d)} aria-pressed={d === sel} onClick={() => setSel(d)}>
                  <span class="mnum">{+d.slice(8, 10)}</span>
                  <span class="mdots">{dots.map((c) => <span key={c} style={{ background: c }} />)}</span>
                </button>
              )
            })}
          </div>
        </section>

        <section class="dcard" aria-label="งานของวันที่เลือก">
          <h2 class="dhead2">{(sel === p.today ? 'วันนี้, ' : '') + longDay(sel)}</h2>
          {dayEvents.length === 0 && dayTasks.length === 0 && <p class="muted pad">ว่างทั้งวัน</p>}
          {dayEvents.map((e, i) => (
            <div key={i} class="ebox mev">
              <span class="ebar" />
              <span class="etext"><span class="etitle">{e.title}</span><span class="erange">{evTimes(e)}</span></span>
            </div>
          ))}
          <ul class="plain">
            {dayTasks.map(({ t }) => (
              <TaskRow key={t.key} task={t} progress={p.out.progress[t.key]} busy={p.p.busy} selected={p.selectedKey === t.key} onToggle={() => p.p.tick(t)} onOpen={() => p.open(t)} />
            ))}
          </ul>
        </section>
      </div>
    </>
  )
}

const HOUR_H = 48
const DAY_MIN = 24 * 60
const MIN_BLOCK = 20
const STRIP_FOLD = 2

interface Block {
  top: number
  bottom: number
  col: number
  cols: number
  ev?: Ev
  task?: Task
}
const block = (from: number, to: number, extra: Pick<Block, 'ev' | 'task'>): Block => {
  const top = Math.min(Math.max(from, 0), DAY_MIN - MIN_BLOCK)
  return { top, bottom: Math.min(Math.max(to, top + MIN_BLOCK), DAY_MIN), col: 0, cols: 1, ...extra }
}

/**
 * Lays overlapping blocks side by side. Blocks that overlap, directly or through a chain, form a group;
 * each block takes the first column free at its start, and the whole group shares one column count.
 */
function packColumns(blocks: Block[]): Block[] {
  const sorted = [...blocks].sort((a, b) => a.top - b.top || b.bottom - a.bottom)
  let group: Block[] = []
  let ends: number[] = []
  let groupEnd = 0
  const close = () => {
    group.forEach((b) => { b.cols = ends.length })
    group = []
    ends = []
  }
  for (const b of sorted) {
    if (b.top >= groupEnd) close()
    const free = ends.findIndex((end) => end <= b.top)
    if (free >= 0) {
      b.col = free
      ends[free] = b.bottom
    } else {
      b.col = ends.length
      ends.push(b.bottom)
    }
    group.push(b)
    groupEnd = Math.max(groupEnd, b.bottom)
  }
  close()
  return sorted
}

const minutes = (hhmm: string) => +hhmm.slice(0, 2) * 60 + +hhmm.slice(3, 5)
const TINT: Record<Task['priority'], string> = {
  HIGHEST: 'var(--red)', HIGH: 'var(--amber)', MEDIUM: 'var(--blue)', NONE: 'var(--v-grey)', LOW: 'var(--v-grey)', LOWEST: 'var(--v-grey)',
}
const PRIORITY_ORDER: Task['priority'][] = ['HIGHEST', 'HIGH', 'MEDIUM', 'NONE', 'LOW', 'LOWEST']

/**
 * A Google Calendar style timeline of 7, 3 or 1 days: all-day events and tasks without a time in a strip on
 * top, then an hour grid with timed events and tasks placed by time. Seven days start on Monday; three days and
 * one day start at the chosen day. The arrows (or a swipe) page it.
 */
function Timeline(p: ViewCtx & { onConnect: () => void; days: number }) {
  const { days } = p
  const [anchor, setAnchor] = useState(p.today)
  const [unfolded, setUnfolded] = useState(false)
  const [now, setNow] = useState(() => new Date())
  const grid = useRef<HTMLDivElement>(null)
  const start = days === 7 ? mondayOf(anchor) : anchor
  const range = Array.from({ length: days }, (_, i) => addDays(start, i))
  const end = range[days - 1]
  const feed = useCalendarFeed(p.p, start, addDays(end, 1))
  const all = useDated(p)
  const showsToday = p.today >= start && p.today <= end
  const shift = (n: number) => setAnchor(addDays(anchor, n * days))
  const swipe = useSwipe(() => shift(1), () => shift(-1))
  const hourH = days === 1 ? 56 : HOUR_H

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 30_000)
    return () => clearInterval(id)
  }, [])
  // Opens near the current hour (07:00 when today is not shown); again when the number of days changes.
  useEffect(() => {
    if (grid.current) grid.current.scrollTop = hourH * (showsToday ? Math.max(now.getHours() - 1, 0) : 7)
  }, [days])
  const toNow = () => grid.current?.scrollTo({ top: hourH * Math.max(now.getHours() - 1, 0), behavior: 'smooth' })

  // A task with a time sits in the grid on the date its reminder hangs on; on its other date it stays in the strip.
  const timedOn = (d: Dated, day: string) => d.at != null && d.at.slice(0, 10) === day
  const strip = range.map((d) => ({
    events: feed.events.filter((e) => evInStrip(e) && evCovers(e, d)),
    tasks: tasksOn(all, d).filter((x) => !timedOn(x, d))
      .sort((a, b) => Number(!a.t.open) - Number(!b.t.open) || PRIORITY_ORDER.indexOf(a.t.priority) - PRIORITY_ORDER.indexOf(b.t.priority)),
  }))
  const blocks = range.map((d) => {
    const from = d + 'T00:00'
    const to = addDays(d, 1) + 'T00:00'
    const events = feed.events.filter((e) => !evInStrip(e) && evCovers(e, d)).map((e) =>
      // Events that run past midnight are cut at the day's edges, so each day shows its own part.
      block(e.begin <= from ? 0 : minutes(e.begin.slice(11)), e.end >= to ? DAY_MIN : minutes(e.end.slice(11)), { ev: e }))
    const tasks = tasksOn(all, d).filter((x) => timedOn(x, d)).map((x) => block(minutes(x.at!.slice(11)), minutes(x.at!.slice(11)) + 30, { task: x.t }))
    return packColumns([...events, ...tasks])
  })

  const label = days === 1
    ? (start === p.today ? 'วันนี้, ' : '') + mediumDay(start) + (start.slice(0, 4) !== p.today.slice(0, 4) ? ` ${start.slice(0, 4)}` : '')
    : rangeText(start, end, p.today)
  const [prev, next] = days === 1 ? ['วันก่อน', 'วันถัดไป'] : days === 7 ? ['สัปดาห์ก่อน', 'สัปดาห์ถัดไป'] : [`ย้อน ${days} วัน`, `ถัดไป ${days} วัน`]
  const folds = strip.some((s) => s.events.length + s.tasks.length > STRIP_FOLD)

  return (
    <>
      <CalendarConnect connected={feed.connected} note={feed.note} onConnect={p.onConnect} />
      <div class="gtools">
        <span class="grange">{label}</span>
        <span class="grow" />
        <button class="chip tool" onClick={() => { setAnchor(p.today); toNow() }}>วันนี้</button>
        <button class="sq" aria-label={prev} onClick={() => shift(-1)}>‹</button>
        <button class="sq" aria-label={next} onClick={() => shift(1)}>›</button>
      </div>
      <div class={`tcard d${days}`} {...swipe}>
        <div class="theads">
          {range.map((d) => (
            <div key={d} class="thead">
              <span class={d === p.today ? 'gwd today' : 'gwd'}>{WEEKDAYS[weekdayIndex(d)]}</span>
              <span class={d === p.today ? 'gnum today' : 'gnum'}>{+d.slice(8, 10)}</span>
            </div>
          ))}
        </div>
        {strip.some((s) => s.events.length + s.tasks.length > 0) && (
          <div class="tstrip">
            <div class="tgutter">
              {folds && <button class="sq small" aria-label={unfolded ? 'ย่อ' : 'แสดงทั้งหมด'} onClick={() => setUnfolded(!unfolded)}>{unfolded ? '▴' : '▾'}</button>}
            </div>
            {strip.map((s, i) => {
              const items = [...s.events.map((e) => ({ e })), ...s.tasks.map(({ t }) => ({ t }))]
              const shown = unfolded ? items : items.slice(0, STRIP_FOLD)
              return (
                <div key={range[i]} class="tstripcol">
                  {shown.map((it, j) => 'e' in it
                    ? <span key={j} class="tpill ev">{it.e!.title}</span>
                    : <button key={j} class={`tpill${it.t!.open ? '' : ' done'}`} style={{ '--tint': TINT[it.t!.priority] }} onClick={() => p.open(it.t!)}>{it.t!.title}</button>)}
                  {items.length > shown.length && <button class="tmore" onClick={() => setUnfolded(true)}>+{items.length - shown.length}</button>}
                </div>
              )
            })}
          </div>
        )}
        <div class="tgrid" ref={grid}>
          <div class="tinner" style={{ height: `${hourH * 24 + 8}px` }}>
            {Array.from({ length: 24 }, (_, h) => (
              <span key={h} class="thour" style={{ top: `${8 + h * hourH}px` }}><span class="thl">{String(h).padStart(2, '0')}</span></span>
            ))}
            <div class="tcols">
              {range.map((d, i) => (
                <div key={d} class="tcol" style={{ height: `${hourH * 24}px` }}>
                  {blocks[i].map((b, j) => {
                    const style = {
                      top: `${(b.top / 60) * hourH}px`, height: `${((b.bottom - b.top) / 60) * hourH}px`,
                      left: `${(b.col / b.cols) * 100}%`, width: `${100 / b.cols}%`,
                    }
                    // Lines that fit the block; anything past the edge is clipped.
                    const room = Math.max(Math.floor((parseFloat(style.height) - 6) / ((days === 1 ? 14 : days === 3 ? 12.5 : 11.5) * 1.25)), 1)
                    const clamp = { WebkitLineClamp: days === 1 ? Math.max(room - 1, 1) : room }
                    if (b.ev) {
                      return (
                        <div key={j} class="tblock ev" style={style}>
                          <span class="ttl" style={clamp}>{b.ev.title}</span>
                          {days === 1 && <span class="ttm">{evTimes(b.ev)}</span>}
                        </div>
                      )
                    }
                    const t = b.task!
                    return (
                      <button key={j} class={`tblock${t.open ? '' : ' done'}`} style={{ ...style, '--tint': TINT[t.priority] }} onClick={() => p.open(t)}>
                        <span class="ttl" style={clamp}>{t.title}</span>
                        {days === 1 && t.reminder && <span class="ttm">{t.reminder}</span>}
                      </button>
                    )
                  })}
                  {d === p.today && (
                    <>
                      <span class="tnow" style={{ top: `${((now.getHours() * 60 + now.getMinutes()) / 60) * hourH - 1}px` }} aria-label="เวลาตอนนี้" />
                      <span class="tnowdot" style={{ top: `${((now.getHours() * 60 + now.getMinutes()) / 60) * hourH - 5}px` }} />
                    </>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
