import { useEffect, useRef, useState } from 'preact/hooks'
import { host, type GoogleLink, type GoogleState } from '../host'
import './googleConnect.css'

/** Draws again whenever the Google Calendar link inside Obsidian changes (nothing on the web page). */
export function useGoogleState(): GoogleState | null {
  const link = host.calendar
  const [state, setState] = useState<GoogleState | null>(() => link?.state() ?? null)
  useEffect(() => link?.listen(() => setState(link.state())), [link])
  return link ? state : null
}

/** Set when "อนุญาต" sent the owner to Settings for the client secret: the field is shown and ready to paste in. */
let askSecret = false

/** "อนุญาต" inside Obsidian: the browser opens at Google's sign-in, or Settings first when no client secret is pasted yet. */
export function connectInObsidian(link: GoogleLink) {
  if (link.state() === 'nosecret') {
    askSecret = true
    host.address.assign('#/settings')
  } else {
    link.connect()
  }
}

const OBSIDIAN_NOTE = 'ให้แท็บนี้อ่านนัดและเวร และให้ผู้ช่วยลงนัดให้ได้ กดแล้วจะเปิดเบราว์เซอร์ให้เข้าสู่ระบบ Google'

/**
 * The card that asks to link Google Calendar (Focus, the assistant, the calendar views). On the web page it goes to
 * Google and back; inside Obsidian it opens the browser, waits for the sign-in there, and says so when Google has
 * turned the link down since.
 */
export function ConnectCard(p: { note: string; onConnect: () => void }) {
  const state = useGoogleState()
  const link = host.calendar
  if (!link) {
    if (!host.google) return null
    return (
      <section class="fcard connect">
        <div class="grow">
          <div class="strong">เชื่อมต่อ Google Calendar</div>
          <div class="muted small">{p.note}</div>
        </div>
        <button class="primary" onClick={p.onConnect}>อนุญาต</button>
      </section>
    )
  }
  if (state === 'on') return null
  if (state === 'waiting') return <Waiting link={link} />
  if (state === 'revoked') {
    return (
      <section class="notice" role="note">
        <span class="grow">Google ยกเลิกสิทธิ์อ่านปฏิทินแล้ว (เช่น ถอนสิทธิ์ที่หน้าบัญชี Google)</span>
        <button class="link" onClick={() => link.connect()}>เชื่อมต่อใหม่</button>
      </section>
    )
  }
  const error = link.error()
  return (
    <section class="fcard connect">
      <div class="grow">
        <div class="strong">เชื่อมต่อ Google Calendar</div>
        <div class="muted small">{OBSIDIAN_NOTE}</div>
        {error && <div class="error small" role="alert">{error}</div>}
      </div>
      <button class="primary" onClick={() => connectInObsidian(link)}>อนุญาต</button>
    </section>
  )
}

function Waiting({ link }: { link: GoogleLink }) {
  return (
    <section class="g-wait" aria-live="polite">
      <div class="fcard g-wait-card">
        <span class="g-spin" aria-hidden="true" />
        <div class="grow">
          <div class="strong">รอเข้าสู่ระบบในเบราว์เซอร์</div>
          <div class="muted small">เข้าสู่ระบบ Google ให้เสร็จ แล้วหน้าเว็บจะพากลับมาที่ Obsidian เอง</div>
        </div>
      </div>
      <div class="g-wait-actions">
        <button class="link" onClick={() => link.cancel()}>ยกเลิก</button>
        <button class="link" onClick={() => link.connect()}>เปิดเบราว์เซอร์อีกครั้ง</button>
      </div>
    </section>
  )
}

/** The Google Calendar row of Settings inside Obsidian: the client secret once, then link, wait, linked or linked again. */
export function GoogleRow() {
  const state = useGoogleState()
  const link = host.calendar
  const [secret, setSecret] = useState('')
  const [saving, setSaving] = useState(false)
  const field = useRef<HTMLInputElement>(null)
  useEffect(() => {
    if (!askSecret || !field.current) return
    askSecret = false
    field.current.scrollIntoView({ block: 'center' })
    field.current.focus()
  }, [state])
  if (!link || !state) return null
  const error = link.error()

  if (state === 'nosecret') {
    const save = async (e: Event) => {
      e.preventDefault()
      if (!secret.trim()) return
      setSaving(true)
      try {
        await link.setSecret(secret.trim())
        setSecret('')
        link.connect()
      } finally {
        setSaving(false)
      }
    }
    return (
      <>
        <div class="set-row g-row-open">
          <span class="grow"><span>Google Calendar</span><span class="set-note">ยังไม่ได้เชื่อม</span></span>
        </div>
        <form class="g-secret" onSubmit={save}>
          <label class="set-note" for="g-secret">Client secret ของ OAuth client แบบเว็บ (Google Cloud &gt; Credentials)</label>
          <div class="g-secret-row">
            <input id="g-secret" ref={field} class="g-secret-input" type="password" autocomplete="off" spellcheck={false} value={secret}
              onInput={(e) => setSecret(e.currentTarget.value)} />
            <button class="pillbtn fill" disabled={!secret.trim() || saving}>บันทึกแล้วเชื่อม</button>
          </div>
          {error && <span class="error small" role="alert">{error}</span>}
          <span class="set-note">เก็บไว้ในเครื่องนี้ในไฟล์ตั้งค่าของปลั๊กอิน</span>
        </form>
      </>
    )
  }

  const note = state === 'on' ? `เชื่อมแล้ว: ${link.email() ?? 'บัญชี Google'} อ่านนัดและลงนัดได้`
    : state === 'waiting' ? 'รอเข้าสู่ระบบในเบราว์เซอร์'
    : state === 'revoked' ? 'Google ยกเลิกสิทธิ์แล้ว'
    : 'ยังไม่ได้เชื่อม'
  return (
    <div class="set-row">
      <span class="grow">
        <span>Google Calendar</span>
        <span class="set-note">{note}</span>
        {error && state !== 'on' && <span class="error small" role="alert">{error}</span>}
      </span>
      {state === 'on' && <button class="pillbtn danger" onClick={() => { void link.disconnect() }}>ตัดการเชื่อมต่อ</button>}
      {state === 'waiting' && <button class="pillbtn" onClick={() => link.cancel()}>ยกเลิก</button>}
      {state === 'off' && <button class="pillbtn" onClick={() => link.connect()}>อนุญาต</button>}
      {state === 'revoked' && <button class="pillbtn" onClick={() => link.connect()}>เชื่อมต่อใหม่</button>}
    </div>
  )
}
