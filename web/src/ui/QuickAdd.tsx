import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { quickPreview, today } from '../core'
import { onBehavior, quickHints } from '../behavior'
import { pendingQuestion } from '../assistantState'
import type { PageProps } from '../Home'
import type { ListNote, QuickList, QuickPick, Task } from '../types'

const DRAFT_KEY = 'omni.draft'
const readDraft = () => {
  try { return sessionStorage.getItem(DRAFT_KEY) ?? '' } catch { return '' }
}
const writeDraft = (v: string) => {
  try {
    if (v) sessionStorage.setItem(DRAFT_KEY, v)
    else sessionStorage.removeItem(DRAFT_KEY)
  } catch { /* just not kept */ }
}

/** The language speech is heard in. The web is Thai only for now; this is the one place to change it. */
export const VOICE_LANG = 'th-TH'

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
 * handed to the assistant to find a time for it. From a Kanban column's "+" the new task starts in that column's
 * [status]. Every page opens the same sheet (the add button of the bottom bar or the side bar, a folded column).
 */
export function QuickAdd(p: PageProps & { onClose: () => void; status?: Task['status']; statusLabel?: string }) {
  // The draft survives the trip to Google when the sign-in runs out while typing.
  const [sentence, setSentenceState] = useState(readDraft)
  const latest = useRef(sentence)
  const setSentence = (v: string) => { latest.current = v; setSentenceState(v); writeDraft(v) }
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
  // What tasks like this one usually came with before (behavior.ts): a tap adds its words to the sentence.
  const [logRev, setLogRev] = useState(0)
  useEffect(() => onBehavior(() => setLogRev((n) => n + 1)), [])
  const hints = useMemo(() => (target || picking ? [] : quickHints(sentence, p.vault.peekProfile() ?? null)), [sentence, target, picking, logRev])

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
    r.lang = VOICE_LANG
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
    const title = read_.title.trim()
    if (!s.trim() || !title) return
    const t = target
    const note = t && lists.find((n) => n.path === t.list.path)
    if (t && !note) return
    const status = p.status && p.status !== 'TODO' ? p.status : null
    p.run(async () => {
      const res = t ? await p.vault.addListItem(note!, read_.title, t.category) : status ? await p.vault.addInStatus(s, status) : await p.vault.add(s)
      if (res.ok) {
        setSentence('')
        p.onClose()
      }
      // As Android says once a task is added (an item added to a list shows in its list instead).
      return res.ok && !t ? { ...res, notice: `เพิ่มงานแล้ว: ${title}` } : res
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
        {p.statusLabel && !target && <span class="muted small">เริ่มที่สถานะ {p.statusLabel}</span>}
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
        {hints.length > 0 && (
          <div class="chips quick-hints" role="group" aria-label="เคยใส่กับงานแบบนี้">
            <span class="muted small">เคยใส่กับงานแบบนี้</span>
            {hints.map((h) => (
              <button type="button" key={h.words} class="chip" onClick={() => { setSentence(`${sentence.trimEnd()} ${h.words} `); input.current?.focus() }}>
                + {h.label}
              </button>
            ))}
          </div>
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
