import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { today } from '../core'
import type { PageProps } from '../Home'
import type { Group } from '../types'
import { PageHead } from '../ui/PageHead'
import { TaskRow } from '../ui/TaskRow'

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

export function TasksPage(p: PageProps) {
  const [text, setText] = useState('')
  // Folded groups are remembered on this device, as Android remembers them.
  const [folded, setFolded] = useState<string[]>(() => read(() => localStorage, FOLD_KEY).split('\n').filter(Boolean))
  const [adding, setAdding] = useState(false)
  const [showDone, setShowDone] = useState(false)
  const search = useRef<HTMLInputElement>(null)

  // "/" jumps to the search box, as on the web versions of TickTick and Linear.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const typing = e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement
      if (e.key === '/' && !typing && !adding) {
        e.preventDefault()
        search.current?.focus()
      }
    }
    addEventListener('keydown', onKey)
    return () => removeEventListener('keydown', onKey)
  }, [adding])

  const list = useMemo(() => p.snapshot?.list({ text }), [p.snapshot, text])
  const doneToday = p.snapshot?.tasks.filter((t) => t.status === 'DONE' && t.done === today() && !t.parent) ?? []
  const fold = (label: string) => {
    const next = folded.includes(label) ? folded.filter((f) => f !== label) : [...folded, label]
    setFolded(next)
    write(() => localStorage, FOLD_KEY, next.join('\n'))
  }

  return (
    <main class="page">
      <PageHead title="งาน">
          <label class="search">
            <span class="faint">ค้นหา</span>
            <input ref={search} aria-label="ค้นหาชื่องาน" placeholder="ค้นหาชื่องาน" value={text} onInput={(e) => setText(e.currentTarget.value)} />
            <span class="kbd wide-only">/</span>
          </label>
          <button class="primary wide-only" onClick={() => setAdding(true)}>+ เพิ่มงาน</button>
      </PageHead>

      {!p.snapshot ? (
        <p class="muted pad">กำลังโหลด...</p>
      ) : (
        <div class="groups">
          {list!.groups.length === 0 && <section class="group empty">{text ? 'ไม่มีงานตรงกับคำค้น' : 'ไม่มีงานค้าง'}</section>}
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
                      const t = p.snapshot!.byKey.get(k)!
                      return <TaskRow key={k} task={t} progress={list!.progress[k]} busy={p.busy} onToggle={() => p.tick(t)} />
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
                  {doneToday.map((t) => <TaskRow key={t.key} task={t} busy={p.busy} onToggle={() => p.tick(t)} />)}
                </ul>
              )}
            </section>
          )}
        </div>
      )}

      <button class="fab narrow-only" aria-label="เพิ่มงาน" onClick={() => setAdding(true)}>+</button>
      {adding && <QuickAdd {...p} onClose={() => setAdding(false)} />}
    </main>
  )
}

/** A sentence in, a TaskForge line out: dates, times, tags and priority are read from the words, as on Android. */
function QuickAdd(p: PageProps & { onClose: () => void }) {
  // The draft survives the trip to Google when the sign-in runs out while typing.
  const [sentence, setSentenceState] = useState(() => read(() => sessionStorage, DRAFT_KEY))
  const setSentence = (v: string) => { setSentenceState(v); write(() => sessionStorage, DRAFT_KEY, v) }
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])

  // The tags already in use, most used first, to tap instead of typing.
  const tags = useMemo(() => {
    const count = new Map<string, number>()
    p.snapshot?.tasks.forEach((t) => t.tags.forEach((g) => { if (!g.startsWith('remind-at-')) count.set(g, (count.get(g) ?? 0) + 1) }))
    return [...count.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8).map(([g]) => g)
  }, [p.snapshot])
  const used = (g: string) => sentence.split(/\s+/).includes('#' + g)

  const submit = (e: Event) => {
    e.preventDefault()
    const s = sentence
    if (!s.trim()) return
    p.run(async () => {
      const res = await p.vault.add(s)
      if (res.ok) {
        setSentence('')
        p.onClose()
      }
      return res
    })
  }

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
        {tags.length > 0 && (
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
        <div class="dialog-actions">
          <button type="button" class="ghost" onClick={p.onClose}>ยกเลิก</button>
          <button class="primary" disabled={p.busy || !sentence.trim()}>เพิ่ม</button>
        </div>
      </form>
    </div>
  )
}
