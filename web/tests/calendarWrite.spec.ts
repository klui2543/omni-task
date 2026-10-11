import { expect, test, type Page } from '@playwright/test'

import { Profile, allowFolders } from './assistantHelpers'
import { open } from './helpers'

// The assistant can put what it plans on Google Calendar, as on Android. The web asks for the write permission
// (calendar.events) only the first time the owner turns that on, and says why first.

async function openAssistant(page: Page, opts: Parameters<typeof open>[1] = {}) {
  const r = await open(page, { profile: Profile, ...opts })
  await allowFolders(page, r.drive)
  await page.getByRole('navigation', { name: 'เมนูหลัก' }).getByRole('button', { name: 'ผู้ช่วย', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
  return r
}
const ask = async (page: Page, text: string) => {
  await page.getByLabel('ข้อความถึงผู้ช่วย').fill(text)
  await page.getByRole('button', { name: 'ส่ง', exact: true }).click()
}
const planNextWeek = async (page: Page) => {
  await page.getByRole('button', { name: /^วางแผนสัปดาห์หน้า/ }).click()
  await expect(page.getByText('แผน 12 ต.ค. ถึง 18 ต.ค.')).toBeVisible()
}
const calendarSwitch = (page: Page) => page.getByRole('switch', { name: 'ลง Google Calendar ด้วย' })

test('turning the calendar switch on the first time explains, and "not now" leaves it off without going to Google', async ({ page }) => {
  const { drive } = await openAssistant(page, { granted: true })
  await planNextWeek(page)
  await expect(calendarSwitch(page)).not.toBeChecked()
  await calendarSwitch(page).check()
  const dialog = page.getByRole('dialog', { name: 'ให้ผู้ช่วยลงนัดใน Google Calendar' })
  await expect(dialog).toContainText('เพิ่มและแก้นัดใน Google Calendar')
  await expect(dialog).toContainText('แผนที่เลือกไว้จะยังอยู่')
  await dialog.getByRole('button', { name: 'ยกเลิก' }).click()
  await expect(dialog).toHaveCount(0)
  await expect(calendarSwitch(page)).not.toBeChecked()
  expect(drive.scopesAsked).toEqual(['https://www.googleapis.com/auth/drive'])
})

test('allowing it goes to Google for calendar.events only, then comes back to the same plan and adds the events', async ({ page }) => {
  const { drive, file } = await openAssistant(page, { granted: true })
  await planNextWeek(page)
  await calendarSwitch(page).check()
  await page.getByRole('dialog').getByRole('button', { name: 'ไปที่ Google เพื่ออนุญาต' }).click()

  // Back from Google: still on the assistant, the plan is there, and the switch is on.
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
  await expect(page.getByText('แผน 12 ต.ค. ถึง 18 ต.ค.')).toBeVisible()
  await expect(calendarSwitch(page)).toBeChecked()
  expect(drive.scopesAsked.at(-1)).toContain('https://www.googleapis.com/auth/calendar.events')
  expect(drive.calendarWriteGranted).toBe(true)
  expect(drive.inserted).toHaveLength(0)

  await page.getByRole('button', { name: 'ลงแผน ทำเว็บคำนวณยา' }).click()
  await expect.poll(() => drive.inserted.length).toBe(1)
  const sent = drive.inserted[0]
  expect(sent.summary).toBe('ทำเว็บคำนวณยา')
  expect(sent.start.dateTime).toMatch(/^2026-10-1[2-8]T\d\d:\d\d:00$/)
  expect(sent.start.timeZone).toBeTruthy()
  expect(sent.end.dateTime! > sent.start.dateTime!).toBe(true)
  expect(file.text).toMatch(/ทำเว็บคำนวณยา #remind-at-scheduled 🎯 \d\d:\d\d ➕ 2026-08-01 ⏳ 2026-10-1[2-8]/)

  // All the rest at once: one event for each, and no alert.
  const n = await page.getByRole('button', { name: /^ลงแผน /, }).count()
  await page.getByRole('button', { name: /^ลงแผนทั้งหมด \d+ งาน$/ }).click()
  await expect(page.getByRole('button', { name: 'ลงแผนครบแล้ว' })).toBeVisible()
  await expect.poll(() => drive.inserted.length).toBe(1 + n)
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('a plan with the switch off writes the note and nothing to the calendar', async ({ page }) => {
  const { drive } = await openAssistant(page, { writeGranted: true })
  await planNextWeek(page)
  await expect(calendarSwitch(page)).not.toBeChecked()
  await page.getByRole('button', { name: 'ลงแผน ทำเว็บคำนวณยา' }).click()
  await expect(page.getByRole('img', { name: 'ลงแผนแล้ว' })).toHaveCount(1)
  expect(drive.inserted).toHaveLength(0)
})

test('with the permission given, a chosen time is added to the note and to the calendar', async ({ page }) => {
  const { drive, file } = await openAssistant(page, { writeGranted: true })
  await ask(page, 'ต้องไปธนาคาร 30 นาที')
  await expect(calendarSwitch(page)).toBeChecked()
  await page.getByLabel('ชื่องาน').fill('ไปธนาคาร')
  await page.getByRole('button', { name: 'สร้างงานในโน้ต' }).click()
  await expect(page.getByRole('button', { name: 'เพิ่มงาน + ลงปฏิทินแล้ว' })).toBeVisible()
  expect(drive.inserted).toHaveLength(1)
  expect(drive.inserted[0].summary).toBe('ไปธนาคาร')
  expect(drive.inserted[0].start.dateTime).toMatch(/^2026-10-\d\dT\d\d:\d\d:00$/)
  expect(file.text).toContain('- [ ] ไปธนาคาร ')
})

test('with the switch turned off on a chosen time, only the note is written', async ({ page }) => {
  const { drive } = await openAssistant(page, { writeGranted: true })
  await ask(page, 'ต้องไปธนาคาร 30 นาที')
  await calendarSwitch(page).uncheck()
  await page.getByRole('button', { name: 'สร้างงานในโน้ต' }).click()
  await expect(page.getByRole('button', { name: 'เพิ่มงานแล้ว (วันและเวลาอยู่ในโน้ต)' })).toBeVisible()
  expect(drive.inserted).toHaveLength(0)
})

test('"none of these": an all-day item is an all-day event, a timed one lasts the planned minutes', async ({ page }) => {
  const { drive } = await openAssistant(page, { writeGranted: true })
  await ask(page, 'ต้องไปธนาคาร 30 นาที')
  await page.getByRole('button', { name: 'ลงทั้งวัน' }).click()
  const dialog = page.getByRole('dialog', { name: 'ลงทั้งวัน' })
  await dialog.getByLabel('วัน').fill('2026-10-14')
  await dialog.getByRole('button', { name: 'ลงทั้งวัน' }).click()
  await expect.poll(() => drive.inserted.length).toBe(1)
  expect(drive.inserted[0].start).toEqual({ date: '2026-10-14' })
  expect(drive.inserted[0].end).toEqual({ date: '2026-10-15' })

  await ask(page, 'ต้องซื้อของ 30 นาที')
  await page.getByRole('button', { name: 'ตั้งเวลาเอง' }).click()
  const hand = page.getByRole('dialog', { name: 'ตั้งเวลาเอง' })
  await hand.getByLabel('วัน').fill('2026-10-15')
  await hand.getByLabel('เวลา').fill('16:30')
  await hand.getByRole('button', { name: 'สร้างงาน' }).click()
  await expect.poll(() => drive.inserted.length).toBe(2)
  expect(drive.inserted[1].start.dateTime).toBe('2026-10-15T16:30:00')
  expect(drive.inserted[1].end.dateTime).toBe('2026-10-15T17:00:00')
})

test('when Google refuses the event the task is still added and the page says so', async ({ page }) => {
  const { drive, file } = await openAssistant(page, { writeGranted: true })
  drive.insertFails = true
  await ask(page, 'ต้องไปธนาคาร 30 นาที')
  await page.getByLabel('ชื่องาน').fill('ไปธนาคาร')
  await page.getByRole('button', { name: 'สร้างงานในโน้ต' }).click()
  await expect(page.getByRole('button', { name: 'เพิ่มงานแล้ว (ยังลงปฏิทินไม่ได้)' })).toBeVisible()
  await expect(page.getByRole('alert')).toContainText('Google Calendar API')
  expect(file.text).toContain('- [ ] ไปธนาคาร ')
})

test('an event added shows up in the calendar views', async ({ page }) => {
  await openAssistant(page, { writeGranted: true })
  await ask(page, 'ต้องไปธนาคาร 30 นาที')
  await page.getByLabel('ชื่องาน').fill('ไปธนาคาร')
  await page.getByRole('button', { name: 'สร้างงานในโน้ต' }).click()
  await expect(page.getByRole('button', { name: 'เพิ่มงาน + ลงปฏิทินแล้ว' })).toBeVisible()
  await page.evaluate(() => { localStorage.setItem('omni.viewMode', 'gantt'); location.hash = '/views' })
  await expect(page.getByRole('heading', { name: 'มุมมอง', level: 1 })).toBeVisible()
  await expect(page.getByRole('button', { name: /ไปธนาคาร/ }).first()).toBeVisible()
})
