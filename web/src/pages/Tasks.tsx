import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { quickPreview, today } from '../core'
import { pendingQuestion } from '../assistantState'
import type { PageProps } from '../Home'
import { BUCKETS, DEFAULT_QUERY, GROUPS, KINDS, ListQuery, PRIORITIES, SORTS, STATUSES, filterCount, labelOf, storedQuery } from '../query'
import type { Group, ListNote, QuickList, QuickPick, Task } from '../types'
import { EditPanel } from '../ui/EditPanel'
import { FilterPanel, SortDialog } from '../ui/ListTools'
import { LayoutBar, useLayout, type Layout } from '../ui/LayoutBar'
import { ViewsPage } from './Views'
import { PageHead } from '../ui/PageHead'
import { TaskRow } from '../ui/TaskRow'
import { useStored } from '../ui/views/hooks'

const DRAFT_KEY = 'omni.draft'
const FOLD_KEY = 'omni.folded'
const read = (store: () => Storage, k: string) => {
  try {
    return store().getItem(k) ?? ''
  } catch {
    return ''
  }
}
const write = (store: () => Storage, k: string, v: string) => {
  try {
    if (v) store().setItem(k, v)
    else store().removeItem(k)
  } catch {
    /* just not kept */
  }
}

const TONE: Record<Group['tone'], string> = { ALERT: 'alert', ACCENT: 'accent', PLAIN: '', MUTED: 'muted' }

/** The Tasks tab: the list, or one of the views over the same filters (Kanban, Matrix, Gantt, calendar) behind one switch. */
export function TasksPage(p: PageProps) {
  const [layout, setLayout] = useLayout()
  return layout === 'list' ? <TaskList {...p} layout={layout} onLayout={setLayout} /> : <ViewsPage {...p} layout={layout} onLayout={setLayout} />
}

function TaskList(p: PageProps & { layout: Layout; onLayout: (l: Layout) => void }) {
  const [text, setText] = useState('')
  // Folded groups are remembered on this device, as Android remembers them.
  const [folded, setFolded] = useState<string[]>(() => read(() => localStorage, FOLD_KEY).split('\n').filter(Boolean))
  const [adding, setAdding] = useState(false)
  const [hide, setHide] = useStored('omni.hideDone', 'yes', ['yes', 'no'] as const)
  const [showDone, setShowDone] = useState(false)
  const [query, setQueryState] = useState<ListQuery>(storedQuery.get)
  const setQuery = (q: ListQuery) => { setQueryState(q); storedQuery.set(q) }
  const [overlay, setOverlay] = useState<'filter' | 'sort' | null>(null)
  // The open task, by key and title: a key is its line, so the title confirms it is still the same task.
  const [selected, setSelected] = useState<{ key: string; title: string } | null>(null)
  const search = useRef<HTMLInputElement>(null)

  // "/" jumps to the search box, as on the web versions of TickTick and Linear.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement
      if (e.key === '/' && !typing && !adding && !overlay) {
        e.preventDefault()
        search.current?.focus()
      }
    }
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [adding, overlay])

  const list = useMemo(() => p.snapshot?.list({ ...query, text }), [p.snapshot, query, text])
  const shown = list ? new Set(list.groups.flatMap((g) => g.keys)).size : 0
  const doneToday = query.statuses.includes('DONE') ? [] : p.snapshot?.tasks.filter((t) => t.status === 'DONE' && t.done === today() && !t.parent) ?? []
  const tags = useMemo(() => {
    const count = new Map<string, number>()
    p.snapshot?.tasks.forEach((t) => t.tags.forEach((g) => { if (!g.startsWith('remind-at-')) count.set(g, (count.get(g) ?? 0) + 1) }))
    return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 20).map(([g]) => g)
  }, [p.snapshot])

  const all = p.snapshot?.tasks ?? []
  const current: Task | null = selected
    ? (() => {
        const byKey = p.snapshot?.find(selected.key)
        return byKey?.title === selected.title ? byKey : all.find((t) => t.title === selected.title) ?? null
      })()
    : null
  const openTask = (t: Task) => setSelected({ key: t.key, title: t.title })

  // The notes and list notes the filter can narrow to, as on Android's filter sheet.
  const filterNotes = useMemo(() => [...new Set((p.snapshot?.tasks ?? []).map((t) => (t.note ?? '').split('/').pop()!.replace(/\.md$/i, '')).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'th')), [p.snapshot])
  const filterLists = useMemo(() => [...new Set((p.snapshot?.listItems() ?? []).map((t) => t.list ?? '').filter(Boolean))].sort((a, b) => a.localeCompare(b, 'th')), [p.snapshot])
  // What the filter row shows: each active filter as a chip that takes it off.
  const active: [string, () => void][] = [
    ...query.kinds.map((k): [string, () => void] => [labelOf(KINDS, k), () => setQuery({ ...query, kinds: query.kinds.filter((x) => x !== k) })]),
    ...query.buckets.map((k): [string, () => void] => [labelOf(BUCKETS, k), () => setQuery({ ...query, buckets: query.buckets.filter((x) => x !== k) })]),
    ...query.priorities.map((k): [string, () => void] => [labelOf(PRIORITIES, k), () => setQuery({ ...query, priorities: query.priorities.filter((x) => x !== k) })]),
    ...query.tags.map((k): [string, () => void] => ['#' + k, () => setQuery({ ...query, tags: query.tags.filter((x) => x !== k) })]),
    ...query.lists.map((k): [string, () => void] => [k, () => setQuery({ ...query, lists: query.lists.filter((x) => x !== k) })]),
    ...query.notes.map((k): [string, () => void] => [k, () => setQuery({ ...query, notes: query.notes.filter((x) => x !== k) })]),
    ...(filterCount({ ...query, kinds: [], buckets: [], priorities: [], tags: [], notes: [], lists: [] })
      ? [[query.statuses.map((s) => labelOf(STATUSES, s)).join(', ') || 'ทุกสถานะ', () => setQuery({ ...query, statuses: DEFAULT_QUERY.statuses })] as [string, () => void]]
      : []),
  ]
  const sorts = query.sorts.length ? query.sorts : DEFAULT_QUERY.sorts
  const sortText = `${labelOf(SORTS, sorts[0].by)} ${sorts[0].ascending ? '↑' : '↓'}${sorts.length > 1 ? ` +${sorts.length - 1}` : ''}`
  const fold = (label: string) => {
    const next = folded.includes(label) ? folded.filter((f) => f !== label) : [...folded, label]
    setFolded(next)
    write(() => localStorage, FOLD_KEY, next.join('\n'))
  }

  return (
    <div class={`split${current ? ' with-pane' : ''}`}>
    <main class="page">
      <PageHead title="งาน">
          <label class="search">
            <span class="faint">ค้นหา</span>
            <input ref={search} aria-label="ค้นหาชื่องาน" placeholder="ค้นหาชื่องาน" value={text} onInput={(e) => setText(e.currentTarget.value)} />
            <span class="kbd wide-only">/</span>
          </label>
          <button class="primary wide-only" onClick={() => setAdding(true)}>+ เพิ่มงาน</button>
      </PageHead>

      <LayoutBar layout={p.layout} onLayout={p.onLayout} filter={{ count: filterCount(query), onOpen: () => setOverlay('filter') }}
        end={<button class="chip tool" aria-label="เรียงและจัดกลุ่ม" title={`กลุ่ม: ${labelOf(GROUPS, query.groupBy)}, เรียง: ${sortText}`} onClick={() => setOverlay('sort')}>เรียง ⇅</button>} />
      {active.length > 0 && (
        <div class="chips">
          {active.map(([label, off]) => (
            <span key={label} class="chip on saved">
              <span class="chip-main">{label}</span>
              <button class="chip-x" aria-label={`เอา ${label} ออก`} onClick={off}>×</button>
            </span>
          ))}
        </div>
      )}

      {!p.snapshot ? (
        <p class="muted pad">กำลังโหลด...</p>
      ) : (
        <div class="groups">
          {list!.groups.length === 0 && (
            <section class="group empty">{text ? 'ไม่มีงานตรงกับคำค้น' : filterCount(query) ? 'ไม่มีงานตรงกับตัวกรอง' : 'ไม่มีงานค้าง'}</section>
          )}
          {list!.groups.map((g) => {
            const open = !folded.includes(g.label)
            return (
              <section key={g.label} class="group">
                <h2>
                  <button class="group-head" aria-expanded={open} onClick={() => fold(g.label)}>
                    <span class={`caret${open ? ' open' : ''}`}>›</span>
                    <span class={`group-label ${TONE[g.tone]}`}>{g.label}</span>
                    <span class="count">{g.keys.length}</span>
                  </button>
                </h2>
                {open && (
                  <ul class="plain">
                    {g.keys.map((k) => {
                      const t = p.snapshot!.find(k)!
                      return (
                        <TaskRow key={k} task={t} progress={list!.progress[k]} busy={p.busy} selected={current?.key === k}
                          onToggle={() => p.tick(t)} onOpen={() => openTask(t)} />
                      )
                    })}
                  </ul>
                )}
              </section>
            )
          })}
          {doneToday.length > 0 && (
            <section class="group">
              <h2>
                <button class="group-head" aria-expanded={showDone} onClick={() => setShowDone(!showDone)}>
                  <span class={`caret${showDone ? ' open' : ''}`}>›</span>
                  <span class="group-label muted">เสร็จวันนี้</span>
                  <span class="count">{doneToday.length}</span>
                </button>
              </h2>
              {showDone && (
                <ul class="plain">
                  {doneToday.map((t) => (
                    <TaskRow key={t.key} task={t} busy={p.busy} selected={current?.key === t.key} onToggle={() => p.tick(t)} onOpen={() => openTask(t)} />
                  ))}
                </ul>
              )}
            </section>
          )}
        </div>
      )}

      {adding && <QuickAdd {...p} onClose={() => setAdding(false)} />}
      {overlay === 'filter' && (
        // Android's one filter sheet has the "hide done" switch here too, though only the views honour it; the choice is shared with Views.
        <FilterPanel query={query} tags={tags} notes={filterNotes} lists={filterLists} shown={shown} onChange={setQuery} onClose={() => setOverlay(null)}
          hideDone={{ on: hide === 'yes', set: (on) => setHide(on ? 'yes' : 'no') }} />
      )}
      {overlay === 'sort' && <SortDialog query={query} onChange={setQuery} onClose={() => setOverlay(null)} />}
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

/** The browser's speech recognition, where it has one (Chrome, Edge, Safari); null elsewhere. */
const Recognition = (): (new () => any) | null =>
  typeof window === 'undefined' ? null : ((window as any).SpeechRecognition ?? (window as any).webkitSpeechRecognition ?? null)

const shortDay = (iso: string) => {
  const t = today()
  const d = new Date(iso + 'T00:00')
  const tomorrow = new Date(t + 'T00:00')
  tomorrow.setDate(tomorrow.getDate() + 1)
  return iso === t ? 'วันนี้' : iso === tomorrow.toLocaleDateString('en-CA') ? 'พรุ่งนี้' : d.toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })
}

/**
 * A sentence in, a TaskForge line out, as Android's quick-add sheet: dates, times, tags and priority are read from the
 * words and shown before saving; "#" picks a list note (and a category) or a tag; the sentence can be spoken, or
 * handed to the assistant to find a time for it.
 */
export function QuickAdd(p: PageProps & { onClose: () => void }) {
  // The draft survives the trip to Google when the sign-in runs out while typing.
  const [sentence, setSentenceState] = useState(() => read(() => sessionStorage, DRAFT_KEY))
  const latest = useRef(sentence)
  const setSentence = (v: string) => { latest.current = v; setSentenceState(v); write(() => sessionStorage, DRAFT_KEY, v) }
  const [target, setTarget] = useState<{ list: QuickList; category: string | null } | null>(null)
  const [lists, setLists] = useState<ListNote[]>(() => p.vault.peekLists() ?? [])
  const [listening, setListening] = useState(false)
  const [voiceNote, setVoiceNote] = useState('')
  const input = useRef<HTMLInputElement>(null)
  const heard = useRef<any>(null)
  useEffect(() => input.current?.focus(), [])
  useEffect(() => { p.vault.lists().then(setLists, () => undefined) }, [p.vault])
  useEffect(() => () => heard.current?.abort?.(), [])

  // The tags already in use, most used first: the first few to tap, all of them for "#".
  const allTags = useMemo(() => {
    const count = new Map<string, number>()
    p.snapshot?.tasks.forEach((t) => t.tags.forEach((g) => { if (!g.startsWith('remind-at-')) count.set(g, (count.get(g) ?? 0) + 1) }))
    return [...count.entries()].sort((a, b) => b[1] - a[1]).map(([g]) => g)
  }, [p.snapshot])
  const tags = allTags.slice(0, 8)
  const used = (g: string) => sentence.split(/\s+/).includes('#' + g)
  const read_ = useMemo(() => quickPreview(sentence, lists, allTags), [sentence, lists, allTags])
  const picking = read_.token != null && read_.picks.length > 0

  const pick = (k: QuickPick) => {
    const before = sentence.slice(0, sentence.length - (read_.token!.length + 1)).trimEnd()
    if (k.list) {
      setTarget({ list: k.list, category: k.category ?? null })
      setSentence(before ? before + ' ' : '')
    } else {
      setSentence(`${before ? before + ' ' : ''}#${k.tag} `)
    }
    input.current?.focus()
  }

  const listen = () => {
    const R = Recognition()
    if (!R) return
    if (listening) { heard.current?.stop(); return }
    const r = new R()
    heard.current = r
    r.lang = 'th-TH'
    r.interimResults = false
    r.maxAlternatives = 1
    r.onresult = (e: any) => {
      const words = e.results?.[0]?.[0]?.transcript ?? ''
      if (words) setSentence(`${latest.current.trim()} ${words}`.trim())
    }
    r.onerror = (e: any) => setVoiceNote(
      e.error === 'not-allowed' || e.error === 'service-not-allowed' ? 'เบราว์เซอร์ยังไม่อนุญาตให้ใช้ไมค์'
        : e.error === 'no-speech' ? 'ไม่ได้ยินเสียง ลองอีกครั้ง' : 'ฟังไม่ได้ ลองอีกครั้ง',
    )
    r.onend = () => { setListening(false); input.current?.focus() }
    setVoiceNote('')
    setListening(true)
    try { r.start() } catch { setListening(false) }
  }

  const askAssistant = () => {
    const text = sentence.trim()
    if (!text) return
    pendingQuestion.set(text)
    setSentence('')
    p.onClose()
    p.onNavigate('assistant')
  }

  const submit = (e: Event) => {
    e.preventDefault()
    const s = sentence
    if (!s.trim() || !read_.title.trim()) return
    const t = target
    const note = t && lists.find((n) => n.path === t.list.path)
    if (t && !note) return
    p.run(async () => {
      const res = t ? await p.vault.addListItem(note!, read_.title, t.category) : await p.vault.add(s)
      if (res.ok) {
        setSentence('')
        p.onClose()
      }
      return res
    })
  }

  // What the sentence was read as, so a wrong guess is seen before saving; a list item has no date.
  const chips = target ? [] : [
    ...(read_.due ? [shortDay(read_.due)] : []),
    ...(read_.time ? [`เตือน ${read_.time}`] : []),
    ...read_.tags.map((g) => '#' + g),
    ...(read_.priorityLabel ? [read_.priorityLabel] : []),
  ]
  const canListen = Recognition() !== null

  return (
    <div class="scrim sheet-scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <form class="dialog quick" role="dialog" aria-modal="true" aria-label="เพิ่มงาน" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>เพิ่มงาน</h2>
        <input
          ref={input}
          class="quick-input"
          aria-label="งานใหม่"
          placeholder="เช่น ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ"
          value={sentence}
          onInput={(e) => setSentence(e.currentTarget.value)}
        />
        {picking ? (
          <div class="chips quick-picks" role="group" aria-label="เลือกลิสต์หรือ Tag">
            {read_.picks.map((k) => (
              <button type="button" key={k.list ? `${k.list.path}|${k.category ?? ''}` : k.tag!} class={`chip${k.list ? ' list-pick' : ''}`} onClick={() => pick(k)}>
                {k.list ? `${k.list.emoji} ${k.list.name}${k.category ? ` / ${k.category}` : ''}` : `#${k.tag}`}
              </button>
            ))}
          </div>
        ) : !target && tags.length > 0 && (
          <div class="chips">
            {tags.map((g) => (
              <button
                type="button"
                key={g}
                class={`chip${used(g) ? ' on' : ''}`}
                onClick={() => { if (!used(g)) setSentence(`${sentence.trimEnd()} #${g} `.trimStart()); input.current?.focus() }}
              >#{g}</button>
            ))}
          </div>
        )}
        {target && (
          <>
            <span class="chip on saved quick-target">
              <span class="chip-main">เพิ่มลงใน {target.list.emoji} {target.list.name}{target.category ? ` / ${target.category}` : ''}</span>
              <button type="button" class="chip-x" aria-label="ไม่ใส่ลงลิสต์" onClick={() => setTarget(null)}>×</button>
            </span>
            {!target.category && target.list.categories.length > 0 && (
              <div class="chips" role="group" aria-label="หมวด">
                {target.list.categories.map((c) => (
                  <button type="button" key={c} class="chip" onClick={() => setTarget({ ...target, category: c })}>{c}</button>
                ))}
              </div>
            )}
          </>
        )}
        {chips.length > 0 && (
          <div class="chips quick-read" aria-label="อ่านได้ว่า">
            {chips.map((c) => <span key={c} class="pill accent">{c}</span>)}
          </div>
        )}
        <span class="faint small">วันนี้ พรุ่งนี้ วันจันทร์ 25/10 เวลา 9:00 #tag ! !! !!! สำหรับความสำคัญ และ # แล้วเลือกลิสต์</span>
        {voiceNote && <span class="error small">{voiceNote}</span>}
        <div class="dialog-actions quick-actions">
          {canListen && (
            <button type="button" class={`round-btn${listening ? ' on' : ''}`} aria-label={listening ? 'หยุดฟัง' : 'พูด'} aria-pressed={listening} onClick={listen}>
              <svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true"><path fill="currentColor" d="M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3Zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.9V21h2v-3.1A7 7 0 0 0 19 11h-2Z" /></svg>
            </button>
          )}
          <button type="button" class="round-btn accent" aria-label="ถามผู้ช่วยว่าควรทำตอนไหน" title="ถามผู้ช่วยว่าควรทำตอนไหน" disabled={!sentence.trim()} onClick={askAssistant}>
            <svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true"><path fill="currentColor" d="M12 2l1.9 5.6L19.5 9.5l-5.6 1.9L12 17l-1.9-5.6L4.5 9.5l5.6-1.9L12 2Zm6 12l.9 2.6 2.6.9-2.6.9L18 21l-.9-2.6-2.6-.9 2.6-.9L18 14Z" /></svg>
          </button>
          <span class="grow" />
          <button type="button" class="ghost" onClick={p.onClose}>ยกเลิก</button>
          <button class="primary" disabled={p.busy || !read_.title.trim()}>{target ? 'เพิ่มลงลิสต์' : 'เพิ่ม'}</button>
        </div>
      </form>
    </div>
  )
}
