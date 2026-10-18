// The Omni Task tab outside Obsidian, for looking at it in a browser (scripts/preview.mjs): the same shadow root,
// frame and scoped styles as omniView.ts, over a vault in memory with example notes.
import { host, memoryAddress, type GoogleLink, type GoogleState } from '../../web/src/host'
import { memoryVault } from '../test/memoryVault'

const iso = (days: number) => {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d.toLocaleDateString('en-CA')
}

const NOTE = [
  '# Omni note',
  `- [ ] ส่งรายงานประจำเดือน #งาน ⏫ 📅 ${iso(-1)}`,
  `- [ ] #remind-at-due ⏰ 12:30 โทรนัดหมอฟัน #ส่วนตัว 📅 ${iso(0)}`,
  `- [ ] ซื้อของเข้าบ้าน #บ้าน 📅 ${iso(0)}`,
  `- [x] อ่านบทที่ 4 #เรียน 📅 ${iso(0)} ✅ ${iso(0)}`,
  `- [ ] จองตั๋วเครื่องบินไปเชียงใหม่ #เดินทาง 📅 ${iso(1)}`,
  `- [ ] ทำสไลด์ประชุมทีม #peddose 📅 ${iso(2)}`,
  '- [ ] ทบทวนเคส #Siriraj',
  '',
].join('\n')

const params = new URLSearchParams(location.search)
const dark = params.has('dark')
if (dark) document.body.classList.add('theme-dark')

const mem = memoryVault({
  '📁 Folder/หลังบ้าน/Omni/Omni note.md': NOTE,
  'Daily/2026-10-18.md': '- [ ] เขียนบันทึกประจำวัน\n',
})

// The notes as the app left them, for a test to read.
;(window as unknown as { omniVault: Map<string, string> }).omniVault = mem.texts

const el = document.querySelector('.view-content') as HTMLElement
const shadow = el.appendChild(document.createElement('div')).attachShadow({ mode: 'open' })
;(shadow.host as HTMLElement).style.height = '100%'
const frame = document.createElement('div')
frame.className = 'omni-frame'
const root = document.createElement('div')
root.className = 'omni-root'
frame.append(root)
shadow.append(frame)
/** Google Calendar as it would stand (?google=nosecret|off|waiting|on|revoked), with example events once linked. */
function stubGoogle(start: GoogleState): GoogleLink {
  let state = start
  const listeners = new Set<() => void>()
  const set = (s: GoogleState) => { state = s; listeners.forEach((fn) => fn()) }
  const at = (h: number, m = 0) => { const d = new Date(); d.setHours(h, m, 0, 0); return d.toISOString() }
  return {
    state: () => state,
    email: () => (state === 'on' ? 'klui@gmail.com' : null),
    error: () => null,
    setSecret: async () => set('off'),
    connect: () => set('waiting'),
    cancel: () => set('off'),
    disconnect: async () => set('off'),
    listen: (fn) => { listeners.add(fn); return () => { listeners.delete(fn) } },
    async call(url) {
      if (url.includes('calendarList')) return { items: [{ id: 'klui@gmail.com', summary: 'Klui', primary: true }, { id: 'shift', summary: 'เวร' }] }
      if (url.includes('/events')) return { items: url.includes('shift') ? [{ id: 's1', summary: 'เวรเช้า OPD', start: { dateTime: at(8) }, end: { dateTime: at(16) } }] : [{ id: 'e1', summary: 'ประชุมทีม', start: { dateTime: at(13, 30) }, end: { dateTime: at(14, 30) } }] }
      return {}
    },
  }
}
const googleState = params.get('google') as GoogleState | null

host.embed({
  root, frame, scope: shadow, address: memoryAddress(params.get('page') ?? '#/focus'), vaultName: 'Klui Vault', dark: () => dark,
  ...(googleState ? { google: stubGoogle(googleState) } : {}),
})
import('../../web/src/embed').then(({ mountOmni }) => mountOmni({ scope: shadow, frame, root, fs: mem.fs, rootId: 'obsidian:preview' }))
