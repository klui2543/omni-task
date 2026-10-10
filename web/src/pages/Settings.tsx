import { useEffect, useState } from 'preact/hooks'
import { profileApply, profileOf } from '../assistantCore'
import { Auth, CALENDAR_SCOPE } from '../auth'
import { askOnDone, type PageProps } from '../Home'
import { URGENT_RULES, urgentRule } from '../settings'
import { ARCHIVE_DAYS, archiveDays, sweepAndSay } from '../settingsDevice'
import { FONTS, PALETTES, SCALES, THEMES, appearance, setAppearance } from '../settingsAppearance'

/** A row of choices that works like a radio group, in Android's segmented look. */
function Segmented<T extends string | number>(p: { label: string; options: [T, string][]; value: T; disabled?: T[]; onPick: (v: T) => void }) {
  return (
    <div class={`segmented n${p.options.length}`} role="radiogroup" aria-label={p.label}>
      {p.options.map(([v, text]) => (
        <button key={String(v)} role="radio" aria-checked={p.value === v} disabled={p.disabled?.includes(v)} lang={v === 'en' ? 'en' : undefined} onClick={() => p.onPick(v)}>{text}</button>
      ))}
    </div>
  )
}

/**
 * What Android's Settings and Notifications screens hold that makes sense on the web. The choices here stay on this
 * device (Android syncs its own through omni-settings.json in the Omni folder, which the web does not read yet); the sleep times are
 * written to the profile note in the vault, which the assistant and Focus read.
 */
export function SettingsPage(p: PageProps) {
  const [ask, setAsk] = useState(askOnDone.get)
  const [days, setDays] = useState(archiveDays.get)
  const [look, setLook] = useState(appearance)
  const [urgent, setUrgent] = useState(urgentRule.get)
  const [profile, setProfile] = useState<string | null | undefined>(undefined)
  const [note, setNote] = useState('')
  const connected = Auth.granted(CALENDAR_SCOPE)

  useEffect(() => {
    p.vault.profile().then(setProfile, () => { setProfile(null); setNote('อ่านโปรไฟล์ไม่ได้ ลองโหลดใหม่') })
  }, [p.vault])
  const sleep = profileOf(profile ?? null)

  /** The usual bedtime or wake time goes into the profile note, changing only that line of it. */
  const saveTimes = async (change: { sleep?: string; wake?: string }) => {
    if (!/^\d\d:\d\d$/.test(change.sleep ?? change.wake ?? '')) return
    try {
      setProfile(await p.vault.changeProfile((current) => profileApply(current, change)))
      setNote('')
    } catch {
      setNote('บันทึกโปรไฟล์ไม่ได้ ลองอีกครั้ง')
    }
  }
  const change = (c: Parameters<typeof setAppearance>[0]) => { setAppearance(c); setLook(appearance()) }
  const back = () => (history.length > 1 ? history.back() : p.onNavigate('focus'))
  const k = look.scale

  return (
    <main class="page settings">
      <header class="head">
        <div class="row-gap">
          <button class="ghost" onClick={back}>‹ กลับ</button>
          <h1>ตั้งค่า</h1>
        </div>
      </header>
      {note && <section class="notice" role="alert"><span class="grow">{note}</span></section>}

      <div class="set-cols">
        <div class="set-col">
          <section class="set-card">
            <h2>ภาษา</h2>
            <Segmented label="ภาษา" options={[['th', 'ไทย'], ['en', 'English']]} value="th" disabled={['en']} onPick={() => {}} />
            <span class="set-note">เว็บยังมีภาษาไทยอย่างเดียว ส่วน English ใช้ได้ในแอป Android</span>
          </section>

          <section class="set-card">
            <h2>การนอน</h2>
            <label class="set-row">
              <span class="grow">เวลานอนประจำ</span>
              <input class="set-time" type="time" aria-label="เวลานอนประจำ" value={sleep.sleep} disabled={profile === undefined}
                onChange={(e) => saveTimes({ sleep: e.currentTarget.value })} />
            </label>
            <label class="set-row">
              <span class="grow">เวลาตื่นประจำ</span>
              <input class="set-time" type="time" aria-label="เวลาตื่นประจำ" value={sleep.wake} disabled={profile === undefined}
                onChange={(e) => saveTimes({ wake: e.currentTarget.value })} />
            </label>
            <span class="set-note">เก็บในโปรไฟล์ หลังบ้าน/Omni/โปรไฟล์.md ผู้ช่วยกับหน้าโฟกัสอ่านจากที่เดียวกัน หลัง 6 โมงเย็น หน้าโฟกัสบอกเวลาก่อนนอนและชั่วโมงที่ได้นอน แตะที่บรรทัดนั้นเพื่อเปลี่ยนเฉพาะคืนนี้</span>
          </section>

          <section class="set-card">
            <h2>งานที่เสร็จแล้ว</h2>
            <span class="set-row"><span>ย้ายเข้าคลังอัตโนมัติหลัง</span></span>
            <div class="set-chips" role="radiogroup" aria-label="ย้ายเข้าคลังอัตโนมัติหลัง">
              {ARCHIVE_DAYS.map((d) => (
                <button key={d} class={`chip${days === d ? ' on' : ''}`} role="radio" aria-checked={days === d}
                  onClick={() => { archiveDays.set(d); setDays(d); p.run(() => sweepAndSay(p.vault)) }}>{d === 0 ? 'ไม่ย้าย' : `${d} วัน`}</button>
              ))}
            </div>
            <span class="set-note">ย้ายวันละครั้งไปที่ Omni note Archive.md ข้างไฟล์ Omni note งานโปรเจกต์ไม่ถูกย้าย ยังติ๊กเสร็จอยู่ที่เดิม</span>
            <label class="set-row">
              <span class="grow">
                <span>ถามเมื่อติ๊กเสร็จ</span>
                <span class="set-note">เก็บเข้าคลัง ลบ หรือไว้ก่อน</span>
              </span>
              <input type="checkbox" role="switch" class="switch" checked={ask} onChange={(e) => { askOnDone.set(e.currentTarget.checked); setAsk(e.currentTarget.checked) }} />
            </label>
          </section>

          <section class="set-card">
            <h2>ธีม</h2>
            <Segmented label="ธีม" options={THEMES} value={look.theme} disabled={look.palette === 'midnight' ? ['light'] : []} onPick={(theme) => change({ theme })} />
            <Segmented label="ชุดสี" options={PALETTES} value={look.palette} onPick={(palette) => change({ palette })} />
          </section>

          <section class="set-card">
            <h2>ขนาดตัวอักษร</h2>
            <Segmented label="ขนาดตัวอักษร" options={SCALES.map((s) => [s, `${Math.round(s * 100)}%`] as [number, string])} value={look.scale} onPick={(scale) => change({ scale })} />
            <div class="set-preview" aria-label="ตัวอย่างขนาดตัวอักษร">
              <span class="set-note" style={{ fontSize: `${12.5 * k}px` }}>ตัวอย่าง</span>
              <span style={{ fontSize: `${16 * k}px`, fontWeight: 500 }}>ส่งรายงานเวร</span>
              <span style={{ fontSize: `${14 * k}px` }}>แนบตารางเวรเดือนหน้า แล้วส่งให้หัวหน้าตึกก่อนเที่ยง</span>
              <span class="set-note" style={{ fontSize: `${12.5 * k}px` }}>พรุ่งนี้ 09:00</span>
            </div>
          </section>
        </div>

        <div class="set-col">
          <section class="set-card">
            <h2>ฟอนต์</h2>
            <div class="set-fonts" role="radiogroup" aria-label="ฟอนต์">
              {FONTS.map((f) => (
                <button key={f.id} class="set-font" role="radio" aria-checked={look.font === f.id} onClick={() => change({ font: f.id })}>
                  <span class="grow">
                    <span class="set-note">{f.label}</span>
                    <span class="sample" style={{ fontFamily: `${f.family}, Sarabun, system-ui, sans-serif` }}>ส่งรายงานเวร พรุ่งนี้ 09:00</span>
                  </span>
                  <span class="mark" aria-hidden="true">{look.font === f.id ? '✓' : ''}</span>
                </button>
              ))}
            </div>
          </section>

          <section class="set-card">
            <h2>บัญชีและข้อมูล</h2>
            <div class="set-row">
              <span class="grow"><span>Vault ใน Google Drive</span><span class="set-note">{p.snapshot?.path ?? ''}</span></span>
              <button class="pillbtn" disabled={p.busy} onClick={p.onReload}>โหลดใหม่</button>
            </div>
            <div class="set-row">
              <span class="grow"><span>เปลี่ยน vault</span></span>
              <button class="pillbtn" onClick={p.onChangeVault}>เลือกใหม่</button>
            </div>
            <div class="set-row">
              <span class="grow">
                <span>Google Calendar</span>
                <span class="set-note">{connected ? 'อ่านนัดและเวรได้แล้ว (เว็บลงนัดให้ไม่ได้ อ่านได้อย่างเดียว)' : 'ยังไม่ได้เชื่อม ให้เว็บอ่านนัดและเวรเพื่อจัดเวลาว่าง'}</span>
              </span>
              {!connected && <button class="pillbtn" onClick={p.onConnectCalendar}>อนุญาต</button>}
            </div>
            <div class="set-row">
              <span class="grow"><span>บัญชี Google</span><span class="set-note">เข้าสู่ระบบอยู่ จะต่ออายุเองทุกชั่วโมง</span></span>
              <button class="pillbtn danger" onClick={p.onSignOut}>ออกจากระบบ</button>
            </div>
            <div class="set-row">
              <span class="grow"><span>ด่วน</span><span class="set-note">ใช้ใน Matrix และหน้าโฟกัส</span></span>
            </div>
            <div class="set-chips" role="radiogroup" aria-label="ด่วน">
              {URGENT_RULES.map(([rule, label]) => (
                <button key={rule} class={`chip${urgent === rule ? ' on' : ''}`} role="radio" aria-checked={urgent === rule}
                  onClick={() => { urgentRule.set(rule); setUrgent(rule) }}>{label}</button>
              ))}
            </div>
            <div class="set-row">
              <span class="grow">
                <span>ประเภทงาน</span>
                <span class="set-note">ปกติ, มีคนรอ, ลงทุนอนาคต, พักไว้ก่อน ส่วนประเภทที่ตั้งเองและการซ่อนประเภท ตั้งได้ในแอป Android</span>
              </span>
              <span class="set-android">ใช้ได้ในแอป Android</span>
            </div>
          </section>

          <section class="set-card" aria-label="การแจ้งเตือน">
            <h2>การแจ้งเตือน</h2>
            <div class="set-row">
              <span class="grow">
                <span>ตั้งในแอป Android</span>
                <span class="set-note">เว็บตั้งปลุกหรือส่งการแจ้งเตือนตอนปิดหน้าเว็บไม่ได้ จึงไม่มีสวิตช์ให้ตั้งที่นี่ เตือนตามเวลาในงาน สรุปงานตามเวลาที่เลือก ทบทวนสัปดาห์ และเตือนนัดใน Google Calendar ตั้งในแอป Android</span>
              </span>
              <span class="set-android">ใช้ได้ในแอป Android</span>
            </div>
          </section>
        </div>
      </div>
    </main>
  )
}
