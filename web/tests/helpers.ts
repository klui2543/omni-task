import { expect, type Page } from '@playwright/test'
import { FakeDrive } from './fakeGoogle'

// Every clock here is the device's own, so the results do not depend on the time zone the tests run in.
export const at = (h: number, m = 0, day = 8) => new Date(2026, 9, day, h, m)

export const NOTE = [
  '- [ ] ส่งรายงาน ⏫ ➕ 2026-10-01 📅 2026-10-07',
  '- [ ] ทบทวนเคส 🔺 ➕ 2026-10-01 📅 2026-10-08',
  '- [/] สไลด์ประชุม #remind-at-due ⏰ 10:00 ➕ 2026-10-01 📅 2026-10-08',
  '    - [x] โครง ✅ 2026-10-07',
  '    - [ ] ซ้อม',
  '- [ ] โทรหาแม่ #remind-at-due ⏰ 19:30 ➕ 2026-10-01 📅 2026-10-08',
  '- [ ] ตอบอีเมลทุน #รอ/พี่เอ ➕ 2026-09-26',
  '- [ ] อ่าน Deep Work #อนาคต ➕ 2026-09-17',
  '- [ ] ทำเว็บคำนวณยา ➕ 2026-08-01',
  '- [ ] เขียน paper 📅 2026-10-10',
  '',
].join('\n')

/** Signs in and picks the vault; the app opens on Focus. [granted] has the calendar already allowed. */
export async function open(page: Page, opts: { now?: Date; note?: string; profile?: string; events?: FakeDrive['events']; granted?: boolean; writeGranted?: boolean; layout?: FakeDrive['layout'] } = {}) {
  const drive = new FakeDrive()
  if (opts.layout) drive.layout = opts.layout
  const { root, file } = drive.vault(opts.note ?? NOTE)
  if (opts.profile) drive.add('โปรไฟล์.md', drive.omniFolder(root).id, opts.profile)
  drive.events = opts.events ?? []
  if (opts.granted) {
    drive.calendarGranted = true
    await page.addInitScript(() => localStorage.setItem('omni.scopes', 'https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/calendar.readonly'))
  }
  if (opts.writeGranted) {
    drive.calendarGranted = true
    drive.calendarWriteGranted = true
    await page.addInitScript(() => localStorage.setItem('omni.scopes', 'https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/calendar.events'))
  }
  await page.clock.setFixedTime(opts.now ?? at(9, 20))
  await drive.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await page.getByRole('button', { name: /ObsidianVault/ }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  return { drive, file }
}

