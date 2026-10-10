import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import * as A from '../assistantCore'
import { declined, doneLog, useChat, type Chat } from '../assistantState'
import { Auth, CALENDAR_SCOPE } from '../auth'
import { CalendarError, localIso } from '../calendar'
import { today } from '../core'
import { useFocusLocal } from '../focusState'
import type { PageProps } from '../Home'
import { EditPanel } from '../ui/EditPanel'
import { CustomTimeDialog, RangeDialog } from '../ui/assistant/AssistantDialogs'
import { AgendaCard, Bubble, DurationCard, RangeCard, RankedCard, rangeTitle, ReviewCard, SlotsCard, dayShort } from '../ui/assistant/Cards'
import { AskCard, Interview } from '../ui/assistant/Interview'

const CALENDAR_OFF =
  'เปิด Google Calendar API ในโปรเจกต์ Google Cloud ของแอปนี้ก่อน (APIs & Services > Library > Google Calendar API > Enable) แล้วกดโหลดใหม่'
const CALENDAR_DENIED = 'Google ยังไม่อนุญาตให้อ่านปฏิทิน กดอนุญาตอีกครั้ง'

const EXAMPLES = ['อยากไปวิ่งสัปดาห์นี้ ควรไปตอนไหนดี', 'อยากเขียน proposal 2 ชม. ควรทำตอนไหน', 'ต้องไปธนาคาร พรุ่งนี้ตอนไหนดี']
const CLAUDE_QUESTION = 'ช่วยจัดลำดับงานวันนี้ให้หน่อย ตามเวลาว่างในปฏิทิน'

const iso = (d: Date) => d.toLocaleDateString('en-CA')
const addDays = (d: Date, n: number) => { const x = new Date(d); x.setDate(x.getDate() + n); return x }
/** The Monday after today and the Sunday after that. */
const nextWeek = (): [string, string] => {
  const d = new Date(); d.setHours(0, 0, 0, 0)
  const mon = addDays(d, ((8 - d.getDay()) % 7) || 7)
  return [iso(mon), iso(addDays(mon, 6))]
}
const nextMonth = (): [string, string] => {
  const d = new Date(); d.setHours(0, 0, 0, 0)
  return [iso(new Date(d.getFullYear(), d.getMonth() + 1, 1)), iso(new Date(d.getFullYear(), d.getMonth() + 2, 0))]
}
const thisMonth = (): [string, string] => {
  const d = new Date(); d.setHours(0, 0, 0, 0)
  return [iso(d), iso(new Date(d.getFullYear(), d.getMonth() + 1, 0))]
}

type Dialog = { kind: 'custom'; index: number; allDay: boolean } | { kind: 'range' } | null

export function AssistantPage(p: PageProps) {
  const [chat, setChat] = useChat()
  const [local] = useFocusLocal()
  const [profileText, setProfileText] = useState<string | null | undefined>(undefined)
  const [events, setEvents] = useState<A.EventIn[]>([])
  const [note, setNote] = useState('')
  const [interviewing, setInterviewing] = useState(false)
  const [input, setInput] = useState('')
  const [dialog, setDialog] = useState<Dialog>(null)
  const [writing, setWriting] = useState(false)
  const [selected, setSelected] = useState<{ key: string; title: string } | null>(null)
  const [answered, setAnswered] = useState(0)
  const connected = Auth.granted(CALENDAR_SCOPE)
  const end = useRef<HTMLDivElement>(null)

  // The profile note: the answers of the interview, read again whenever the page opens.
  useEffect(() => {
    p.vault.profile().then(setProfileText, () => { setProfileText(null); setNote('อ่านโปรไฟล์ไม่ได้ ลองโหลดใหม่') })
  }, [p.vault])

  // The next five weeks of the calendar, for finding time; a plan for a farther range reads its own.
  const lastRead = useRef(0)
  useEffect(() => {
    if (!connected || !p.snapshot || Date.now() - lastRead.current < 60_000) return
    lastRead.current = Date.now()
    const from = new Date(); from.setHours(0, 0, 0, 0)
    p.readCalendar(addDays(from, -1), addDays(from, 36)).then(
      (e) => setEvents(e),
      (e) => { lastRead.current = 0; setNote(calendarNote(e)) },
    )
  }, [connected, p.snapshot])

  const profile = useMemo(() => (profileText === undefined ? null : A.profileOf(profileText)), [profileText])
  const snap = p.snapshot

  const state = (extra: Partial<A.AssistState> = {}): A.AssistState => ({
    now: localIso(new Date()), profile: profileText ?? null, events, doneLog: doneLog.get(), declined: declined.get(),
    futureCount: local.futureCount, skippedToday: [], ...extra,
  })
  const insight = useMemo(() => (snap && profile?.exists && !profile.stale ? A.insight(snap, state()) : null), [snap, profile, events, answered])

  useEffect(() => { end.current?.scrollIntoView({ block: 'end', behavior: 'smooth' }) }, [chat.length])

  const calendarNote = (e: unknown) =>
    e instanceof CalendarError ? (e.code === 'off' ? CALENDAR_OFF : CALENDAR_DENIED) : 'อ่านปฏิทินไม่ได้ ลองโหลดใหม่'

  /** Events between two days for one request; the page's own copy when the calendar cannot be read just now. */
  const eventsFor = async (from: string, to: string): Promise<A.EventIn[]> => {
    if (!connected) return []
    try {
      const got = await p.readCalendar(new Date(from + 'T00:00'), addDays(new Date(to + 'T00:00'), 1))
      setNote('')
      return got
    } catch (e) {
      setNote(calendarNote(e))
      return events
    }
  }

  const push = (...items: Chat[]) => setChat((c) => [...c, ...items])
  const patch = <T extends Chat['t']>(index: number, t: T, change: (c: Extract<Chat, { t: T }>) => Extract<Chat, { t: T }>) =>
    setChat((c) => c.map((x, i) => (i === index && x.t === t ? change(x as Extract<Chat, { t: T }>) : x)))

  // ---- The profile ----

  const saveProfile = async (change: A.ProfileChange) => {
    setWriting(true)
    try {
      setProfileText(await p.vault.changeProfile((current) => A.profileApply(current, change)))
      setInterviewing(false)
      setNote('')
    } catch {
      setNote('บันทึกโปรไฟล์ไม่ได้ ลองอีกครั้ง')
    } finally {
      setWriting(false)
    }
  }
  const answerInsight = async (yes: boolean) => {
    if (!insight || !snap) return
    if (!yes) { declined.add(insight.id); setAnswered((n) => n + 1); return }
    setWriting(true)
    try {
      const st = state({ insightId: insight.id })
      setProfileText(await p.vault.changeProfile((current) => A.insightYes(snap, st, current) ?? current ?? A.profileApply(null, {})))
      setAnswered((n) => n + 1)
    } catch {
      setNote('บันทึกโปรไฟล์ไม่ได้ ลองอีกครั้ง')
    } finally {
      setWriting(false)
    }
  }

  // ---- The chat ----

  const ask = (raw: string) => {
    const text = raw.trim()
    if (!text || !snap) return
    setInput('')
    const r = A.route(text)
    if (r.kind === 'plan') planRange([r.from, r.to], text)
    else if (r.kind === 'agenda') showAgenda([r.from, r.to], text)
    else if (r.kind === 'rank') rankAll(text)
    else if (r.kind === 'slots') push({ t: 'asked', text }, slotsItem(A.slots(snap, state(), text, 0)))
    else push({ t: 'asked', text }, { t: 'duration', request: text })
  }
  const slotsItem = (plan: A.PlanOut): Chat => ({ t: 'slots', plan, page: 0, picked: 0, title: plan.title })

  const answerDuration = (index: number, minutes: number) => {
    const item = chat[index]
    if (!snap || item?.t !== 'duration' || item.answered !== undefined) return
    const plan = A.slots(snap, state(), item.request, minutes)
    setChat((c) => [...c.map((x, i) => (i === index && x.t === 'duration' ? { ...x, answered: minutes } : x)), slotsItem(plan)])
  }

  const rankAll = (asked = 'จัดลำดับงานทั้งหมดให้หน่อย') => snap && push({ t: 'asked', text: asked }, { t: 'ranked', items: A.rank(snap, state()) })
  const planToday = () => snap && push({ t: 'asked', text: 'จัดลำดับวันนี้ให้หน่อย' }, { t: 'today', items: A.todayList(snap, state()) })
  const reviewWeek = () => {
    if (!snap) return
    const r = A.weekly(snap, state())
    push({ t: 'asked', text: 'ทบทวนสัปดาห์นี้' }, { t: 'review', title: r.title, lines: r.lines })
  }

  const showAgenda = async ([from, to]: [string, string], asked: string) => {
    if (!snap) return
    const st = state({ events: await eventsFor(from, to) })
    const data = A.agenda(snap, st, from, to)
    const summary = `นัด ${data.events} รายการ งานครบกำหนด ${data.due} งาน` + (data.busiest ? ` วันที่แน่นสุดคือ ${dayShort(data.busiest)}` : '')
    push({ t: 'asked', text: asked }, { t: 'agenda', title: rangeTitle(from, to), summary, data })
  }

  const planRange = async ([from, to]: [string, string], asked: string) => {
    if (!snap) return
    const st = state({ events: await eventsFor(from, to) })
    push({ t: 'asked', text: asked }, { t: 'range', title: rangeTitle(from, to), proposals: A.planRange(snap, st, from, to), accepted: [] })
  }

  /** Adds task lines to the note in one write; true when they were written. */
  const writeLines = async (lines: string[]): Promise<boolean> => {
    let ok = false
    await p.run(async () => {
      const r = await p.vault.editNote((text) => A.addLines(text, lines))
      ok = r.ok
      return r
    })
    return ok
  }

  const confirmSlot = async (index: number) => {
    const item = chat[index]
    if (item?.t !== 'slots') return
    const slot = item.plan.slots[item.picked]
    if (!slot || !item.title.trim()) return
    if (await writeLines([A.slotLine(item.title, slot.day, slot.start)])) {
      patch(index, 'slots', (c) => ({ ...c, done: 'เพิ่มงานแล้ว (วันและเวลาอยู่ในโน้ต)' }))
    }
  }
  const confirmCustom = async (index: number, day: string, time: string | null) => {
    const item = chat[index]
    setDialog(null)
    if (item?.t !== 'slots') return
    if (await writeLines([A.slotLine(item.title, day, time)])) {
      patch(index, 'slots', (c) => ({ ...c, done: 'เพิ่มงานแล้ว (วันและเวลาอยู่ในโน้ต)' }))
    }
  }

  /** "ลงแผน": the day and the reminder time go on each accepted task. */
  const acceptProposals = async (index: number, which: number[]) => {
    const item = chat[index]
    if (item?.t !== 'range') return
    const todo = which.filter((i) => !item.accepted.includes(i))
    if (todo.length === 0) return
    const items = todo.map((i) => ({ raw: item.proposals[i].raw, lineIndex: item.proposals[i].lineIndex, day: item.proposals[i].day, time: item.proposals[i].start }))
    let done: boolean[] = []
    await p.run(async () => {
      const r = await p.vault.editNote((text) => A.scheduleTasks(text, items))
      done = r.done ?? []
      return r
    })
    const ok = todo.filter((_, i) => done[i])
    patch(index, 'range', (c) => ({ ...c, accepted: [...c.accepted, ...ok] }))
    setNote(ok.length < todo.length ? 'บางงานบันทึกไม่ได้ ไฟล์อาจถูกแก้จากที่อื่น' : '')
  }

  /** Hands the picture of the day to Claude: a new tab on claude.ai with the same text Android sends to its app. */
  const askClaude = (question: string) => {
    if (!snap) return
    const prompt = question + '\n\n' + A.snapshotText(snap, state())
    window.open(`https://claude.ai/new?q=${encodeURIComponent(prompt)}`, '_blank', 'noopener')
  }

  const openTask = (key: string) => {
    const t = p.snapshot?.byKey.get(key)
    if (t) setSelected({ key: t.key, title: t.title })
  }
  const current = selected
    ? (() => {
        const t = p.snapshot?.byKey.get(selected.key)
        return t?.title === selected.title ? t : p.snapshot?.tasks.find((c) => c.title === selected.title) ?? null
      })()
    : null

  const openCount = snap?.tasks.filter((t) => t.open).length ?? 0
  const sources = `อ่านจาก ${[profile?.exists ? 'โปรไฟล์.md' : null, `งาน ${openCount} รายการ`, connected ? 'Google Calendar' : null].filter(Boolean).join(', ').replace(/, ([^,]*)$/, ' และ $1')}`
  const t0 = today()
  const quick = [
    { title: 'สัปดาห์หน้ามีอะไร', sub: 'งานและนัดทั้งหมด', run: () => showAgenda(nextWeek(), 'สัปดาห์หน้ามีอะไรบ้าง') },
    { title: 'วางแผนสัปดาห์หน้า', sub: 'จัดงานที่ยังไม่มีวันลงช่องว่าง', run: () => planRange(nextWeek(), 'วางแผนสัปดาห์หน้าให้หน่อย') },
    { title: 'จัดลำดับวันนี้', sub: 'ตามงานและนัดวันนี้', run: planToday },
    { title: 'ทบทวนสัปดาห์', sub: 'สรุป 7 วันที่ผ่านมา', run: reviewWeek },
  ]
  const chips: [string, () => void][] = [
    ['จัดลำดับวันนี้', planToday],
    ['จัดลำดับทั้งหมด', () => rankAll()],
    ['สัปดาห์หน้ามีอะไร', () => showAgenda(nextWeek(), 'สัปดาห์หน้ามีอะไรบ้าง')],
    ['เดือนหน้ามีอะไร', () => showAgenda(nextMonth(), 'เดือนหน้ามีอะไรบ้าง')],
    ['วางแผนสัปดาห์หน้า', () => planRange(nextWeek(), 'วางแผนสัปดาห์หน้าให้หน่อย')],
    ['วางแผนเดือนนี้', () => planRange(thisMonth(), 'วางแผนเดือนนี้ให้หน่อย')],
    ['เลือกช่วงเอง', () => setDialog({ kind: 'range' })],
    ['ทบทวนสัปดาห์', reviewWeek],
    ['ถาม Claude', () => askClaude(CLAUDE_QUESTION)],
  ]

  const first = profile && !profile.exists
  const head = first || interviewing
    ? <Interview current={profile!} first={!!first} busy={writing} onSave={saveProfile} onCancel={profile!.exists ? () => setInterviewing(false) : undefined} />
    : profile?.stale
      ? (
        <AskCard title="ชีวิตช่วงนี้ยังเหมือนเดิมไหม?" badge="ไม่ได้ทบทวนมา 30 วัน">
          <span>ตื่น {profile.wake} นอน {profile.sleep} สมองดี {profile.focusFrom} ถึง {profile.focusTo} ออกกำลังกาย {profile.exercise}</span>
          <div class="as-actions">
            <button class="ghost" onClick={() => setInterviewing(true)}>ปรับ</button>
            <button class="primary" disabled={writing} onClick={() => saveProfile({})}>ยังเหมือนเดิม</button>
          </div>
        </AskCard>
      )
      : insight
        ? (
          <AskCard title="จำไว้ไหม?" badge={insight.window}>
            <span>{insight.text}</span>
            <div class="as-actions">
              <button class="ghost" onClick={() => answerInsight(false)}>ไม่ใช่</button>
              <button class="primary" disabled={writing} onClick={() => answerInsight(true)}>ใช่ จำไว้</button>
            </div>
          </AskCard>
        )
        : null

  return (
    <div class={`split${current ? ' with-pane' : ''}`}>
      <main class="page assistant">
        <header class="head">
          <div class="grow">
            <h1>ผู้ช่วย</h1>
            <span class="muted small">{sources}</span>
          </div>
          <div class="head-tools">
            {chat.length > 0 && <button class="ghost" onClick={() => setChat(() => [])}>เริ่มใหม่</button>}
            <button class="ghost narrow-only" onClick={() => p.onNavigate('settings')}>ตั้งค่า</button>
          </div>
        </header>

        {note && <section class="notice" role="alert"><span class="grow">{note}</span></section>}

        <div class="as-scroll">
          {head}
          {chat.length === 0 && profile && (
            <>
              {!connected && (
                <section class="fcard connect">
                  <div class="grow">
                    <div class="strong">เชื่อมต่อ Google Calendar</div>
                    <div class="muted small">ให้ผู้ช่วยอ่านนัดและเวรเพื่อหาช่วงว่าง ถ้ายังไม่เชื่อม ผู้ช่วยคิดจากงานในโน้ตอย่างเดียว</div>
                  </div>
                  <button class="primary" onClick={p.onConnectCalendar}>อนุญาต</button>
                </section>
              )}
              <section class="as-card">
                <span class="as-intro">ถามได้ว่าอยากทำอะไร แล้วผู้ช่วยจะหาช่วงที่เหมาะให้ 3 ช่วง พร้อมเหตุผล</span>
                {EXAMPLES.map((q) => <button key={q} class="as-example" onClick={() => ask(q)}>"{q}"</button>)}
              </section>
              <div class="as-quick">
                {quick.map((q) => (
                  <button key={q.title} class="as-qcard" onClick={q.run}><span class="medium">{q.title}</span><span class="muted small">{q.sub}</span></button>
                ))}
                <button class="as-qcard wide" onClick={() => askClaude(CLAUDE_QUESTION)}>
                  <span class="medium">ถาม Claude</span>
                  <span class="muted small">ส่งงานและนัดวันนี้ไปที่ Claude พร้อมคำถาม (เปิดแท็บใหม่ ใช้ subscription ของคุณ)</span>
                </button>
              </div>
            </>
          )}

          {chat.map((c, i) => {
            switch (c.t) {
              case 'asked': return <Bubble key={i} text={c.text} />
              case 'duration': return <DurationCard key={i} item={c} onAnswer={(m) => answerDuration(i, m)} />
              case 'slots':
                return (
                  <SlotsCard key={i} item={c} busy={p.busy}
                    onPick={(s) => patch(i, 'slots', (x) => ({ ...x, picked: s, done: undefined }))}
                    onMore={() => patch(i, 'slots', (x) => { const pages = Math.ceil(x.plan.slots.length / 3); const page = pages === 0 ? 0 : (x.page + 1) % pages; return { ...x, page, picked: page * 3, done: undefined } })}
                    onTitle={(title) => patch(i, 'slots', (x) => ({ ...x, title }))}
                    onConfirm={() => confirmSlot(i)}
                    onCustom={(allDay) => setDialog({ kind: 'custom', index: i, allDay })}
                    onClaude={() => askClaude(c.plan.request)} />
                )
              case 'today':
                return <RankedCard key={i} hot={0} items={c.items} onOpen={openTask}
                  intro={c.items.length === 0 ? 'วันนี้ไม่มีงานเร่ง ลองหยิบงานลงทุนอนาคตสักงาน' : 'เรียงจากต้องทำก่อน ไปคนที่รอ แล้วค่อยงานเพื่ออนาคต'} />
              case 'ranked':
                return <RankedCard key={i} hot={3} items={c.items} onOpen={openTask} intro="ลำดับที่ควรทำ เรียงจากเลยกำหนด ใกล้ครบ คนรอ แล้วค่อยความสำคัญ" />
              case 'review': return <ReviewCard key={i} title={c.title} lines={c.lines} />
              case 'agenda': return <AgendaCard key={i} title={c.title} summary={c.summary} data={c.data} today={t0} onOpen={openTask} />
              case 'range':
                return <RangeCard key={i} title={c.title} proposals={c.proposals} accepted={c.accepted} busy={p.busy} onAccept={(w) => acceptProposals(i, w)} onOpen={openTask} />
            }
          })}
          <div ref={end} />
        </div>

        <div class="as-foot">
          {chat.length > 0 && (
            <div class="as-chips" role="toolbar" aria-label="ทางลัด">
              {chips.map(([label, run]) => <button key={label} class="as-chip" onClick={run}>{label}</button>)}
            </div>
          )}
          <form class="as-input" onSubmit={(e) => { e.preventDefault(); ask(input) }}>
            <input aria-label="ข้อความถึงผู้ช่วย" placeholder="อยากทำอะไร หรือถามเรื่องงาน" value={input} onInput={(e) => setInput(e.currentTarget.value)} />
            <button class="primary round-send" type="submit" disabled={!input.trim() || !snap}>ส่ง</button>
          </form>
        </div>

        {dialog?.kind === 'custom' && (() => {
          const item = chat[dialog.index]
          if (item?.t !== 'slots') return null
          return (
            <CustomTimeDialog title={item.title} allDay={dialog.allDay} day={item.plan.slots[0]?.day ?? t0} busy={p.busy}
              onSet={(day, time) => confirmCustom(dialog.index, day, time)} onClose={() => setDialog(null)} />
          )
        })()}
        {dialog?.kind === 'range' && (
          <RangeDialog from={t0} onClose={() => setDialog(null)}
            onAgenda={(from, to) => { setDialog(null); showAgenda([from, to], 'ช่วงนี้มีอะไรบ้าง') }}
            onPlan={(from, to) => { setDialog(null); planRange([from, to], 'วางแผนช่วงนี้ให้หน่อย') }} />
        )}
      </main>
      {current && (
        <>
          <div class="pane-scrim" onClick={() => setSelected(null)} />
          <aside class="pane" role="dialog" aria-label="แก้ไขงาน">
            <EditPanel {...p} task={current} onOpen={(t) => setSelected({ key: t.key, title: t.title })} onClose={() => setSelected(null)} />
          </aside>
        </>
      )}
    </div>
  )
}
