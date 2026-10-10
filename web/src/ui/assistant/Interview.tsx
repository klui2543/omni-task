import { useState } from 'preact/hooks'
import type { ProfileChange, ProfileOut } from '../../assistantCore'

const TIME_TEXT = /(\d{1,2})[.:](\d{2})/g

const pad = (n: number) => String(n).padStart(2, '0')

/** The first time written in a free answer ("5.45", "05:45"), as HH:mm; null when there is none. */
export function timeIn(text: string): string | null {
  return timesIn(text)[0] ?? null
}

function timesIn(text: string): string[] {
  const out: string[] = []
  for (const m of text.matchAll(TIME_TEXT)) {
    const h = Number(m[1])
    const min = Number(m[2])
    if (h <= 23 && min <= 59) out.push(`${pad(h)}:${pad(min)}`)
  }
  return out
}

const FOCUS_CHOICES: [string, string][] = [['08:00', '11:00'], ['10:00', '13:00'], ['13:00', '16:00'], ['19:00', '22:00']]

/** A card in the assistant's own voice: a spark, what it is, and a small badge on the right. */
export function AskCard(p: { title: string; badge: string; children: preact.ComponentChildren }) {
  return (
    <section class="as-ask">
      <div class="as-ask-head"><span class="grow">✦ {p.title}</span><span class="as-badge">{p.badge}</span></div>
      {p.children}
    </section>
  )
}

function Question(p: {
  title: string
  options: string[]
  selected: string
  other: string | undefined
  hint: string
  onPick: (t: string) => void
  onOther: (v: string | undefined) => void
}) {
  return (
    <div class="as-q">
      <span>{p.title}</span>
      <div class="chips" role="radiogroup" aria-label={p.title}>
        {p.options.map((t) => (
          <button key={t} class={`chip${p.other === undefined && t === p.selected ? ' on' : ''}`} role="radio" aria-checked={p.other === undefined && t === p.selected}
            onClick={() => { p.onOther(undefined); p.onPick(t) }}>{t}</button>
        ))}
        <button class={`chip${p.other !== undefined ? ' on' : ''}`} role="radio" aria-checked={p.other !== undefined} onClick={() => p.onOther(p.other ?? '')}>อื่นๆ</button>
      </div>
      {p.other !== undefined && (
        <input class="as-field" aria-label={`${p.title} (อื่นๆ)`} placeholder={p.hint} autofocus value={p.other}
          onInput={(e) => { const v = e.currentTarget.value; p.onOther(v); const t = timeIn(v); if (t) p.onPick(t) }} />
      )}
    </div>
  )
}

/**
 * First-time interview (and the monthly re-ask): four questions answered with chips, saved to the profile note.
 * A free answer that is a time is used as the answer; anything else is kept word for word under its question.
 */
export function Interview(p: { current: ProfileOut; first: boolean; busy: boolean; onSave: (change: ProfileChange) => void; onCancel?: () => void }) {
  const [wake, setWake] = useState(p.current.wake)
  const [sleep, setSleep] = useState(p.current.sleep)
  const [focus, setFocus] = useState<[string, string]>([p.current.focusFrom, p.current.focusTo])
  const [exercise, setExercise] = useState(p.current.exercise)
  const [others, setOthers] = useState<Record<string, string>>({})
  const setOther = (key: string, v: string | undefined) =>
    setOthers((o) => { const n = { ...o }; if (v === undefined) delete n[key]; else n[key] = v; return n })

  const save = () => {
    const described: Record<string, string> = {}
    for (const [k, v] of Object.entries(others)) if (v.trim() && timeIn(v) === null) described[k] = v.trim()
    p.onSave({ wake, sleep, focusFrom: focus[0], focusTo: focus[1], exercise, described })
  }
  const shift = p.current.shiftWords.join('", "')
  const night = p.current.nightWords.join('", "')

  return (
    <AskCard title={p.first ? 'ขอรู้จักกันก่อน' : 'ปรับโปรไฟล์'} badge="4 ข้อ">
      <span class="as-note">คำตอบจะเก็บใน หลังบ้าน/Omni/โปรไฟล์.md แก้ใน Obsidian ได้ และผู้ช่วยจะถามใหม่ทุกเดือนเพราะชีวิตเปลี่ยนได้</span>
      <Question title="ปกติตื่นกี่โมง" options={['05:30', '06:00', '06:30', '07:00', '08:00']} selected={wake} other={others['ตื่น']}
        hint="พิมพ์เวลา เช่น 05:45 หรือเล่าเอง เช่น แล้วแต่เวร" onPick={setWake} onOther={(v) => setOther('ตื่น', v)} />
      <Question title="เข้านอนกี่โมง" options={['21:30', '22:00', '22:30', '23:00', '23:30']} selected={sleep} other={others['นอน']}
        hint="พิมพ์เวลา เช่น 05:45 หรือเล่าเอง เช่น แล้วแต่เวร" onPick={setSleep} onOther={(v) => setOther('นอน', v)} />
      <div class="as-q">
        <span>ช่วงไหนสมองดีที่สุด</span>
        <div class="chips" role="radiogroup" aria-label="ช่วงไหนสมองดีที่สุด">
          {FOCUS_CHOICES.map(([a, b]) => {
            const on = others['ช่วงสมองดี'] === undefined && focus[0] === a
            return (
              <button key={a} class={`chip${on ? ' on' : ''}`} role="radio" aria-checked={on} onClick={() => { setOther('ช่วงสมองดี', undefined); setFocus([a, b]) }}>{a} ถึง {b}</button>
            )
          })}
          <button class={`chip${others['ช่วงสมองดี'] !== undefined ? ' on' : ''}`} role="radio" aria-checked={others['ช่วงสมองดี'] !== undefined}
            onClick={() => setOther('ช่วงสมองดี', others['ช่วงสมองดี'] ?? '')}>อื่นๆ</button>
        </div>
        {others['ช่วงสมองดี'] !== undefined && (
          <input class="as-field" aria-label="ช่วงไหนสมองดีที่สุด (อื่นๆ)" placeholder="เช่น 05:00-07:00 หรือ หลังลงเวรเช้า" autofocus value={others['ช่วงสมองดี']}
            onInput={(e) => { const v = e.currentTarget.value; setOther('ช่วงสมองดี', v); const t = timesIn(v); if (t.length >= 2) setFocus([t[0], t[1]]) }} />
        )}
      </div>
      <Question title="ชอบออกกำลังกายตอนไหน" options={['06:00', '07:00', '17:30', '18:30', '20:00']} selected={exercise} other={others['ออกกำลังกาย']}
        hint="พิมพ์เวลา เช่น 05:45 หรือเล่าเอง เช่น แล้วแต่เวร" onPick={setExercise} onOther={(v) => setOther('ออกกำลังกาย', v)} />
      <span class="as-note small">เวรอ่านจากนัดใน Google Calendar ที่มีคำว่า "{shift}" และเวรดึกจากคำว่า "{night}"</span>
      <div class="as-actions">
        {p.onCancel && <button class="ghost" onClick={p.onCancel}>ยกเลิก</button>}
        <button class="primary" disabled={p.busy} onClick={save}>บันทึกลงโปรไฟล์</button>
      </div>
    </AskCard>
  )
}
