import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { Auth, CALENDAR_SCOPE } from '../auth'
import { CalendarError, localIso } from '../calendar'
import { today } from '../core'
import { customKindTags, kindsStore } from '../kinds'
import { skippedToday, useFocusLocal } from '../focusState'
import type { PageProps } from '../Home'
import type { EditOp, FocusIn, FocusOut, PlanItem, Task } from '../types'
import { EditPanel } from '../ui/EditPanel'
import { CountdownDialog, FutureDialog, ReviewAction, ReviewDialog, SleepTimeDialog } from '../ui/FocusDialogs'
import { URGENT_RULES, urgentRule } from '../settings'
import { PagedCard } from '../ui/PagedCard'
import { Check, PriorityDot } from '../ui/TaskRow'
import { evLink, evTag } from '../ui/views/dates'
import '../ui/focusSleep.css'

const hm = (m: number) => `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')} ชม.`
const hours = (m: number) => (m >= 60 ? `${Math.floor(m / 60)} ชม.` : `${m} นาที`)

const longDate = (d: Date) =>
  `${d.toLocaleDateString('th-TH', { weekday: 'long' }).replace(/^วัน/, '')} ${d.getDate()} ${d.toLocaleDateString('th-TH', { month: 'long' })}`
const greeting = (d: Date) => (d.getHours() < 12 ? 'สวัสดีตอนเช้า' : d.getHours() < 17 ? 'สวัสดีตอนบ่าย' : 'สวัสดีตอนเย็น')

const CALENDAR_OFF =
  'เปิด Google Calendar API ในโปรเจกต์ Google Cloud ของแอปนี้ก่อน (APIs & Services > Library > Google Calendar API > Enable) แล้วกดโหลดใหม่'

/** Yesterday to the end of tomorrow. */
function focusRange() {
  const from = new Date(); from.setHours(0, 0, 0, 0); from.setDate(from.getDate() - 1)
  const to = new Date(from); to.setDate(to.getDate() + 3)
  return { from, to }
}

export function FocusPage(p: PageProps) {
  const [local, setLocal] = useFocusLocal()
  // What was read when the page was last open is shown at once; the page reads again behind it.
  const [profile, setProfile] = useState<string | null>(() => p.vault.peekProfile() ?? null)
  const [events, setEvents] = useState<FocusIn['events']>(() => { const r = focusRange(); return p.peekCalendar(r.from, r.to) ?? [] })
  const [calendarNote, setCalendarNote] = useState('')
  const [minute, setMinute] = useState(() => localIso(new Date()))
  const [menu, setMenu] = useState(false)
  const [dialog, setDialog] = useState<'review' | 'countdown' | 'future' | 'bed' | 'wake' | null>(null)
  const [handled, setHandled] = useState(0)
  const [hidden, setHidden] = useState<string[]>([])
  const [selected, setSelected] = useState<{ key: string; title: string } | null>(null)
  const [urgent, setUrgent] = useState(urgentRule.get)
  const connected = Auth.granted(CALENDAR_SCOPE)

  // The red "now" line and the greeting follow the clock.
  useEffect(() => {
    const id = setInterval(() => setMinute(localIso(new Date())), 30_000)
    return () => clearInterval(id)
  }, [])

  // The profile note holds the usual wake and sleep times.
  useEffect(() => {
    p.vault.profile().then(setProfile, () => setProfile(null))
  }, [p.vault])

  // Calendar events from yesterday to the end of tomorrow (the night looks ahead to tomorrow's first event).
  // Read again with each reload of the note, but not more than once a minute.
  const lastRead = useRef(0)
  useEffect(() => {
    if (!connected || !p.snapshot || Date.now() - lastRead.current < 60_000) return
    lastRead.current = Date.now()
    const { from, to } = focusRange()
    p.readCalendar(from, to).then(
      (e) => { setEvents(e); setCalendarNote('') },
      (e) => {
        lastRead.current = 0
        setCalendarNote(e instanceof CalendarError ? (e.code === 'off' ? CALENDAR_OFF : 'Google ยังไม่อนุญาตให้อ่านปฏิทิน กดอนุญาตอีกครั้ง') : 'อ่านปฏิทินไม่ได้ ลองโหลดใหม่')
      },
    )
  }, [connected, p.snapshot])

  const f: FocusOut | null = useMemo(
    () => p.snapshot?.focus({
      now: minute, skippedToday: skippedToday(local), dismissed: local.dismissed, reviewed: local.reviewed, futureCount: local.futureCount,
      countdown: local.countdown, tonightBed: local.tonightBed, tonightWake: local.tonightWake ?? null, wakeFromWatch: !!local.wakeFromWatch, profile, events,
    }) ?? null,
    [p.snapshot, local, profile, events, minute],
  )

  const byKey = p.snapshot?.byKey
  const task = (key?: string) => (key ? byKey?.get(key) : undefined)
  const openTask = (t: Task) => setSelected({ key: t.key, title: t.title })
  const current: Task | null = selected
    ? (() => {
        const t = byKey?.get(selected.key)
        return t?.title === selected.title ? t : p.snapshot?.tasks.find((c) => c.title === selected.title) ?? null
      })()
    : null

  const hiddenKinds = kindsStore.get().hidden
  const showWaiting = !hiddenKinds.includes('WAITING')
  const showFuture = !hiddenKinds.includes('FUTURE')

  const markReviewed = (t: Task) => setLocal((s) => ({ ...s, reviewed: { ...s.reviewed, [t.title]: today() } }))
  const change = (t: Task, op: EditOp) => p.run(() => p.vault.change(p.fresh(t), op))
  const setKind = (t: Task, kind: 'NORMAL' | 'FUTURE' | 'SOMEDAY') => { markReviewed(t); change(t, { op: 'kind', value: kind, custom: customKindTags() }) }

  const accept = (s: FocusOut['suggestions'][number]) => {
    const t = task(s.key)
    if (!t) return
    if (s.kind === 'RAISE_PRIORITY') change(t, { op: 'priority', value: 'HIGH' })
    else if (s.kind === 'SOFT_DATE') change(t, { op: 'date', field: 'SCHEDULED', value: f!.softDate })
    else setKind(t, 'FUTURE')
  }
  const review = (t: Task, a: ReviewAction) => {
    setHandled((n) => n + 1)
    if (a === 'KEEP') markReviewed(t)
    else if (a === 'FUTURE') setKind(t, 'FUTURE')
    else if (a === 'SOMEDAY') setKind(t, 'SOMEDAY')
    else if (a === 'THIS_WEEK') { markReviewed(t); change(t, { op: 'date', field: 'SCHEDULED', value: f!.softDate }) }
    else { markReviewed(t); change(t, { op: 'status', value: 'CANCELLED' }) }
  }

  const now = new Date()
  // The night the sleep card plans is there all day; the summary counts down to bed only at night.
  const night = f?.third.night
  const toBed = f?.third.kind === 'night' ? night : undefined
  const sleepColor = night ? (night.sleep < 300 ? 'red' : night.sleep < 420 ? 'amber' : 'lime') : ''
  // Why the wake time is what it is: picked on the watch or here for tonight, or an hour before a morning event.
  const why = !night ? null
    : night.wakePicked ? (night.fromWatch ? 'ตั้งจากนาฬิกา' : 'ตั้งเองสำหรับคืนนี้')
    : night.because ? `เผื่อ 1 ชม. ก่อน ${night.becauseAt}: ${night.because}` : null
  // A time picked for tonight is for the evening the card plans; a wake time picked here is not the watch's.
  const setWake = (time: string | null) =>
    setLocal((x) => ({ ...x, tonightWake: time === null ? null : { evening: f!.evening, time }, wakeFromWatch: false }))
  const pct = f && f.ring.total > 0 ? Math.round((f.ring.done / f.ring.total) * 100) : 0
  const reviewTop = f?.review[0]
  const reviewTask = task(reviewTop?.key)

  // One line a row: what repeats on every row of today's page is not said, and a second line only when it has something to say.
  const row = (it: PlanItem, i: number) => {
    if (it.type === 'now') {
      return (
        <div key={`now${i}`} class="nowline" aria-label="เวลาตอนนี้">
          <span class="nowdot" /><span class="nowtime">{it.time}</span><span class="nowbar" />
        </div>
      )
    }
    if (it.type === 'event') {
      const Box = evTag(it.link, 'div')
      const one = it.phase !== 'active'
      return (
        <div key={`ev${i}`} class={`prow event ${it.phase ?? 'upcoming'}`}>
          <Box class={`ebox${one ? ' one' : ''}`} style={it.phase === 'active' ? { '--p': `${Math.round((it.progress ?? 0) * 100)}%` } : undefined} {...evLink(it.link)}>
            <span class="ebar" /><span class="etext"><span class="etitle">{it.title}</span><span class="erange">{it.range}</span></span>
          </Box>
        </div>
      )
    }
    const t = task(it.key)
    if (!t) return null
    const meta = !it.done && (it.time || it.lead || it.extra)
    return (
      <div key={it.key} class={`prow${it.done ? ' done' : ''}`}>
        <Check task={t} disabled={p.busy} onToggle={() => p.tick(t)} />
        <button class="pbody" onClick={() => openTask(t)}>
          <span class="tline"><span class={`ttitle${it.blocked ? ' blocked' : ''}`}>{t.title}</span><PriorityDot task={t} /></span>
          {meta && (
            <span class="plead">
              {it.time && <span class="ptm">{it.time}</span>}
              {it.time && (it.lead || it.extra) ? ', ' : ''}
              {it.lead && <span class="late">{it.lead}</span>}
              {it.lead && it.extra ? ', ' : ''}
              {it.extra}
            </span>
          )}
        </button>
      </div>
    )
  }

  // Overdue work is not part of the day's timeline: it is a card of its own below it.
  const section = (s: FocusOut['plan'][number]) => (
    <div key={s.label}>
      <div class="phead">
        <span class={`plabel${s.late ? ' late' : ''}`}>{s.label}</span>
        <span class="rule" />
        {s.tasks > 0 && <span class="muted small">{s.tasks}</span>}
      </div>
      {s.items.map(row)}
    </div>
  )

  return (
    <div class={`split${current ? ' with-pane' : ''}`}>
      <main class="page focus">
        <header class="head">
          <div class="grow">
            <div class="muted small">{longDate(now)}</div>
            <h1>{greeting(now)}</h1>
          </div>
          <div class="menu-wrap">
            <button class="square" aria-label="เมนู" aria-expanded={menu} onClick={() => setMenu(!menu)}>⋮</button>
            {menu && (
              <>
                <div class="menu-scrim" onClick={() => setMenu(false)} />
                <div class="popmenu" role="menu">
                  <button role="menuitem" onClick={() => { setMenu(false); p.onReload() }}>โหลดใหม่</button>
                  {URGENT_RULES.map(([rule, label]) => (
                    <button key={rule} role="menuitemradio" aria-checked={urgent === rule} class={urgent === rule ? 'on' : ''}
                      onClick={() => { urgentRule.set(rule); setUrgent(rule); setMenu(false) }}>ด่วน = {label}</button>
                  ))}
                  <button role="menuitem" onClick={() => { setMenu(false); p.onChangeVault() }}>เปลี่ยน vault</button>
                  <button role="menuitem" onClick={() => { setMenu(false); p.onNavigate('settings') }}>ตั้งค่า</button>
                </div>
              </>
            )}
          </div>
        </header>

        {!f ? (
          <p class="muted pad">กำลังโหลด...</p>
        ) : (
          <div class="focus-cols">
            <div class="colA">
              {!connected && (
                <section class="fcard connect">
                  <div class="grow">
                    <div class="strong">เชื่อมต่อ Google Calendar</div>
                    <div class="muted small">ให้เว็บอ่านนัดและเวรจาก Google Calendar เพื่อจัดเวลาว่าง และให้ผู้ช่วยลงนัดให้ได้</div>
                  </div>
                  <button class="primary" onClick={p.onConnectCalendar}>อนุญาต</button>
                </section>
              )}
              {calendarNote && <section class="notice" role="alert"><span class="grow">{calendarNote}</span></section>}

              <section class="fcard">
                <div class="summary">
                  <div class="donut" role="img" aria-label={`เสร็จแล้ว ${f.ring.done} จาก ${f.ring.total}`} style={{ '--pct': `${pct}%` }}>
                    <div class="donut-in"><span class="donut-num">{f.ring.done}/{f.ring.total}</span><span class="muted micro">เสร็จแล้ว</span></div>
                  </div>
                  <div class="stat"><div class={`stat-num${f.ring.overdue > 0 ? ' red' : ''}`}>{f.ring.overdue}</div><div class="muted small">เลยกำหนด</div></div>
                  <div class="stat"><div class="stat-num">{f.ring.events}</div><div class="muted small">นัดวันนี้</div></div>
                  {toBed
                    ? <div class="stat"><div class={`stat-num${toBed.toBed < 30 ? ' amber' : ''}`}>{hm(toBed.toBed)}</div><div class="muted small">ก่อนนอน</div></div>
                    : <div class="stat"><div class="stat-num lime">{hours(f.third.minutes)}</div><div class="muted small">ว่างเหลือ</div></div>}
                </div>
                {/* Tonight all day, as on Android: bed and wake to tap (the usual ones are in Settings), then the countdown. */}
                {night && (
                  <div class="sleep" aria-label="การนอนคืนนี้">
                    {f.lastNight != null && <span class="muted micro sleep-last">เมื่อคืนได้นอน {hm(f.lastNight)}</span>}
                    <div class="sleep-head">
                      <span class="grow muted small">คืนนี้</span>
                      <span class={`pill ${sleepColor}`}>ได้นอน {hm(night.sleep)}</span>
                    </div>
                    <div class="sleep-tiles">
                      <button class="sleep-tile" aria-label={`นอน ${night.bedAt}`} onClick={() => setDialog('bed')}>
                        <span class="muted micro">นอน</span>
                        <span class="sleep-time">{night.bedAt}</span>
                      </button>
                      {/* In the daytime the wake time is tomorrow's; it says so, or it reads as this morning's. */}
                      <button class="sleep-tile" aria-label={`ตื่น ${night.wakeAt}${night.tomorrow ? ' พรุ่งนี้' : ''}`} onClick={() => setDialog('wake')}>
                        <span class="muted micro">ตื่น</span>
                        <span><span class="sleep-time">{night.wakeAt}</span>{night.tomorrow && <span class="muted micro sleep-day">พรุ่งนี้</span>}</span>
                      </button>
                    </div>
                    {why && (
                      <div class="sleep-why">
                        <span class="grow faint micro ellipsis">{why}</span>
                        {night.wakePicked && <button class="link" onClick={() => setWake(null)}>ใช้เวลาปกติ</button>}
                      </div>
                    )}
                  </div>
                )}
                <button class="footrow" onClick={() => setDialog('countdown')}>
                  {f.countdown ? (
                    <>
                      <span class="muted small">นับถอยหลัง</span>
                      <span class="grow strong ellipsis">{f.countdown.title}</span>
                      <span class={f.countdown.late ? 'late' : 'lead'}>{f.countdown.text}</span>
                    </>
                  ) : <span class="grow muted">ตั้งนับถอยหลังถึงงานสำคัญ</span>}
                  <span class="chev">›</span>
                </button>
              </section>

              {f.notices.filter((n) => !hidden.includes(n)).map((n) => (
                <section key={n} class="notice">
                  <span class="grow">{n}</span>
                  <button class="link quiet" onClick={() => setHidden([...hidden, n])}>ซ่อน</button>
                </section>
              ))}

              {f.review.length > 0 && (
                <button class="fcard review-prompt" onClick={() => { setHandled(0); setDialog('review') }}>
                  <span class="accent-text big">⟳</span>
                  <span class="grow">
                    <span class="strong">{f.review.length} งานไม่มีเดดไลน์ถึงรอบทบทวน</span>
                    <span class="muted small">ทีละงาน ทำ พัก หรือทิ้ง ใช้ไม่ถึง 2 นาที</span>
                  </span>
                  <span class="chev">›</span>
                </button>
              )}

              {(f.plan.some((s) => !s.late) || !f.plan.some((s) => s.late)) && (
                <section class="fcard plan">
                  {f.plan.length === 0 && <p class="muted pad">วันนี้ยังไม่มีงานหรือนัด</p>}
                  {f.plan.filter((s) => !s.late).map(section)}
                </section>
              )}
              {f.plan.some((s) => s.late) && (
                <section class="fcard plan overdue">
                  {f.plan.filter((s) => s.late).map(section)}
                </section>
              )}
            </div>

            <div class="colB">
              {f.suggestions.length > 0 && (
                <section class="fcard suggest">
                  <span class="accent-text small medium">✦ ข้อเสนอ</span>
                  {f.suggestions.map((s) => (
                    <div key={s.id} class="sug">
                      <button class="link-plain strong" onClick={() => { const t = task(s.key); if (t) openTask(t) }}>{s.title}</button>
                      <span class="small">{s.text}</span>
                      <div class="sug-actions">
                        <button class="ghost small-btn" onClick={() => setLocal((x) => ({ ...x, dismissed: [...x.dismissed, s.id] }))}>ไม่เอา</button>
                        <button class="primary small-btn" disabled={p.busy} onClick={() => accept(s)}>ตกลง</button>
                      </div>
                    </div>
                  ))}
                </section>
              )}

              {/* Either card goes when its kind is hidden in Settings; the other then takes the row. Each shows one task at a time. */}
              {(showWaiting || showFuture) && <div class="pair">
                {showWaiting && (
                  <PagedCard
                    title="คนรออยู่"
                    items={f.waiting}
                    empty="ติด #รอ/ชื่อ ให้งานที่มีคนรอ"
                    onOpen={(w) => { const t = task(w.key); if (t) openTask(t) }}
                    render={(w, open) => {
                      const t = task(w.key)
                      if (!t) return null
                      return (
                        <>
                          <button class="pitem-title" onClick={open}>{t.title}</button>
                          <span class={`pitem-sub ${(w.age ?? 0) >= 7 ? 'red' : 'amber'}`}>
                            {(w.who ? `${w.who} ` : '') + (w.age != null ? `รอ ${w.age} วัน` : 'รออยู่')}
                          </span>
                        </>
                      )
                    }}
                  />
                )}
                {showFuture && (
                  <PagedCard
                    title="ลงทุนอนาคต"
                    items={f.future}
                    empty="เลือกงานที่สำคัญต่ออนาคต แต่ไม่มีเดดไลน์"
                    onOpen={(k) => { const t = task(k); if (t) openTask(t) }}
                    render={(k, open) => {
                      const t = task(k)
                      if (!t) return null
                      return (
                        <>
                          <button class="pitem-title" onClick={open}>{t.title}</button>
                          <span class="pitem-sub muted">
                            <span class="grow">{f.futureAge[k] != null ? `ค้าง ${f.futureAge[k]} วัน` : ''}</span>
                            <button class="link" onClick={() => setLocal((x) => ({ ...x, skipped: [...x.skipped.filter((e) => e.startsWith(today() + '|')), `${today()}|${t.title}`] }))}>ข้ามวันนี้</button>
                          </span>
                        </>
                      )
                    }}
                    more={(close) => (
                      <>
                        <button class="link" onClick={() => { close(); setDialog('future') }}>+ เลือกงาน</button>
                        <span class="grow" />
                        <span class="muted small">วันละ</span>
                        <button class="round" aria-label="ลด" onClick={() => setLocal((x) => ({ ...x, futureCount: Math.max(1, x.futureCount - 1) }))}>−</button>
                        <span class="stepn" aria-live="polite">{local.futureCount}</span>
                        <button class="round" aria-label="เพิ่ม" onClick={() => setLocal((x) => ({ ...x, futureCount: Math.min(5, x.futureCount + 1) }))}>+</button>
                      </>
                    )}
                  />
                )}
              </div>}
            </div>
          </div>
        )}

        {dialog === 'review' && f && (
          <ReviewDialog
            left={f.review.length}
            handled={handled}
            task={reviewTop && reviewTask ? { title: reviewTask.title, kind: reviewTop.kind, age: reviewTop.age, last: reviewTop.last } : null}
            onAct={(a) => reviewTask && review(reviewTask, a)}
            onOpen={() => reviewTask && openTask(reviewTask)}
            onClose={() => setDialog(null)}
          />
        )}
        {dialog === 'countdown' && f && (
          <CountdownDialog choices={f.countdownChoices} current={local.countdown} onPick={(title) => setLocal((x) => ({ ...x, countdown: title }))} onClose={() => setDialog(null)} />
        )}
        {dialog === 'future' && f && (
          <FutureDialog
            chosen={f.futureChosen}
            candidates={f.futureCandidates}
            busy={p.busy}
            onSet={(key, on) => { const t = task(key); if (t) setKind(t, on ? 'FUTURE' : 'NORMAL') }}
            onClose={() => setDialog(null)}
          />
        )}
        {dialog === 'bed' && f && (
          <SleepTimeDialog kind="bed" time={f.bedtime} onSave={(time) => setLocal((x) => ({ ...x, tonightBed: { evening: f.evening, time } }))} onClose={() => setDialog(null)} />
        )}
        {dialog === 'wake' && f && (
          <SleepTimeDialog kind="wake" time={night?.wakeAt ?? f.wake} onSave={setWake} onClose={() => setDialog(null)} />
        )}
      </main>
      {current && (
        <>
          <div class="pane-scrim" onClick={() => setSelected(null)} />
          <aside class="pane" role="dialog" aria-label="แก้ไขงาน">
            <EditPanel {...p} task={current} onOpen={openTask} onClose={() => setSelected(null)} />
          </aside>
        </>
      )}
    </div>
  )
}
