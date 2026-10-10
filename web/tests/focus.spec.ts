import { expect, test } from '@playwright/test'

import { NOTE, at, open } from './helpers'

test('opens on Focus with the day, who is waiting and the future pick', async ({ page }) => {
  await open(page)
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('สวัสดีตอนเช้า')
  await expect(page.getByText('พฤหัสบดี 8 ตุลาคม')).toBeVisible()
  // 1 overdue, 4 due today (one done: none), so 0 of 4 done and the late one first.
  await expect(page.getByRole('img', { name: 'เสร็จแล้ว 0 จาก 4' })).toBeVisible()
  await expect(page.locator('.plabel', { hasText: 'ค้างอยู่' })).toBeVisible()
  await expect(page.getByText('เลย 1 วัน')).toBeVisible()
  await expect(page.getByText('ครบวันนี้, กำลังทำ, งานย่อย 1/2')).toBeVisible()
  await expect(page.getByText('พี่เอ รอ 12 วัน')).toBeVisible()
  await expect(page.getByText('ค้าง 21 วัน')).toBeVisible()
  await expect(page.getByText('มีคนรอมา 12 วันแล้ว ยกเป็นสำคัญ?')).toBeVisible()
  await expect(page.getByText('2 งานไม่มีเดดไลน์ถึงรอบทบทวน')).toBeVisible()
  // The red line sits before the first timed task still to come.
  await expect(page.locator('.nowline')).toContainText('09:20')
})

test('ticking from Focus writes the note and moves the ring', async ({ page }) => {
  const { file } = await open(page)
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ทบทวนเคส/ }).click()
  await expect(page.getByRole('img', { name: 'เสร็จแล้ว 1 จาก 4' })).toBeVisible()
  expect(file.text).toContain('- [x] ทบทวนเคส 🔺 ➕ 2026-10-01 📅 2026-10-08 ✅ 2026-10-08')
})

test('accepting a suggestion changes the task', async ({ page }) => {
  const { file } = await open(page)
  await page.getByRole('button', { name: 'ตกลง' }).click()
  await expect.poll(() => file.text).toContain('#รอ/พี่เอ ⏫ ➕ 2026-09-26')
  await expect(page.getByText('มีคนรอมา 12 วันแล้ว ยกเป็นสำคัญ?')).toHaveCount(0)
})

test('a suggestion waved away stays away after a reload', async ({ page }) => {
  const { file } = await open(page)
  await page.getByRole('button', { name: 'ไม่เอา' }).click()
  await expect(page.getByText('มีคนรอมา 12 วันแล้ว ยกเป็นสำคัญ?')).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  await expect(page.getByText('มีคนรอมา 12 วันแล้ว ยกเป็นสำคัญ?')).toHaveCount(0)
  expect(file.text).not.toContain('#รอ/พี่เอ ⏫')
})

test('future work: skip for today, change how many a day, and pick which tasks count', async ({ page }) => {
  const { file } = await open(page)
  await page.getByRole('button', { name: 'ข้ามวันนี้' }).click()
  await expect(page.getByText('ค้าง 21 วัน')).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('เลือกงานที่สำคัญต่ออนาคต')).toBeVisible()

  // On the narrow layout the bottom bar floats over the page's foot, so bring the stepper to the middle first.
  const more = page.getByRole('button', { name: 'เพิ่ม', exact: true })
  await more.evaluate((el) => el.scrollIntoView({ block: 'center' }))
  await more.click()
  await expect(page.getByText('2', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'เลือกงาน' }).click()
  const dialog = page.getByRole('dialog', { name: 'เลือกงานลงทุนอนาคต' })
  await dialog.getByRole('checkbox', { name: /ทำเว็บคำนวณยา/ }).click()
  await expect.poll(() => file.text).toContain('- [ ] ทำเว็บคำนวณยา #อนาคต ➕ 2026-08-01')
  await expect(dialog.getByText('เลือกไว้แล้ว 2 งาน (แตะเพื่อเอาออก)')).toBeVisible()
  await dialog.getByRole('checkbox', { name: /ทำเว็บคำนวณยา/ }).click()
  await expect.poll(() => file.text).toContain('- [ ] ทำเว็บคำนวณยา ➕ 2026-08-01')
  await dialog.getByRole('button', { name: 'เสร็จ' }).click()
  await expect(dialog).toHaveCount(0)
})

test('the review goes one task at a time: park, drop, keep', async ({ page }) => {
  const { file } = await open(page)
  await page.getByRole('button', { name: /งานไม่มีเดดไลน์ถึงรอบทบทวน/ }).click()
  const dialog = page.getByRole('dialog')
  // The longest-ignored comes first.
  await expect(dialog.getByRole('button', { name: 'ทำเว็บคำนวณยา' })).toBeVisible()
  await dialog.getByRole('button', { name: 'พักไว้ก่อน' }).click()
  await expect.poll(() => file.text).toContain('ทำเว็บคำนวณยา ➕ 2026-08-01 #สักวัน'.replace(' ➕ 2026-08-01 #สักวัน', ' #สักวัน ➕ 2026-08-01'))

  await expect(dialog.getByRole('button', { name: 'อ่าน Deep Work' })).toBeVisible()
  await dialog.getByRole('button', { name: 'เก็บไว้แบบเดิม' }).click()
  await expect(dialog.getByText('ทบทวนครบแล้ว')).toBeVisible()
  await expect(dialog.getByText('จัดการไป 2 งาน')).toBeVisible()
  await dialog.getByRole('button', { name: 'ปิด' }).click()

  // Kept tasks stay out of the queue after a reload.
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  await expect(page.getByText('งานไม่มีเดดไลน์ถึงรอบทบทวน')).toHaveCount(0)
})

test('dropping in the review cancels the task and keeps its line', async ({ page }) => {
  const { file } = await open(page)
  await page.getByRole('button', { name: /งานไม่มีเดดไลน์ถึงรอบทบทวน/ }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'ทิ้ง', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [-] ทำเว็บคำนวณยา')
})

test('the countdown can be set to any task with a date and cleared', async ({ page }) => {
  await open(page)
  await page.getByRole('button', { name: 'ตั้งนับถอยหลังถึงงานสำคัญ' }).click()
  const dialog = page.getByRole('dialog', { name: 'นับถอยหลังถึงงานไหน' })
  await dialog.getByLabel('ค้นหางาน').fill('paper')
  await dialog.getByRole('button', { name: /เขียน paper/ }).click()
  await expect(page.getByRole('button', { name: /นับถอยหลัง เขียน paper อีก 2 วัน/ })).toBeVisible()

  await page.reload()
  await expect(page.getByRole('button', { name: /นับถอยหลัง เขียน paper อีก 2 วัน/ })).toBeVisible()
  await page.getByRole('button', { name: /นับถอยหลัง เขียน paper/ }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'เลิกนับ' }).click()
  await expect(page.getByRole('button', { name: 'ตั้งนับถอยหลังถึงงานสำคัญ' })).toBeVisible()
})

test('Google Calendar: asks first, then the events join the plan and take free time', async ({ page }) => {
  const events = [{ title: 'ประชุมทีม', begin: at(13), end: at(14, 30) }, { title: 'ประชุมพรุ่งนี้', begin: at(9, 0, 9), end: at(10, 0, 9) }]
  const { drive } = await open(page, { events })
  await expect(page.getByText('เชื่อมต่อ Google Calendar')).toBeVisible()
  await expect(page.getByText('ประชุมทีม')).toHaveCount(0)

  await page.getByRole('button', { name: 'อนุญาต' }).click()
  await expect(page.getByText('ประชุมทีม')).toBeVisible()
  await expect(page.getByText('13:00 ถึง 14:30, Google Calendar')).toBeVisible()
  await expect(page.getByText('เชื่อมต่อ Google Calendar')).toHaveCount(0)
  // Today's only: tomorrow's meeting is read (for the night) but not planned.
  await expect(page.getByText('ประชุมพรุ่งนี้')).toHaveCount(0)
  // 09:20 to the usual 22:30 bedtime is 13h10, less the 90 minute meeting.
  await expect(page.getByText('11 ชม.', { exact: true })).toBeVisible()
  expect(drive.calendarGranted).toBe(true)
})

test('Google Calendar: says what to switch on when the API is off', async ({ page }) => {
  const { drive } = await open(page, { granted: true })
  drive.calendarOff = true
  await page.getByRole('button', { name: 'เมนู' }).click()
  await page.getByRole('menuitem', { name: 'โหลดใหม่' }).click()
  await page.clock.runFor(61_000)
  await page.getByRole('button', { name: 'เมนู' }).click().catch(() => {})
  await expect(page.getByRole('alert')).toContainText('Google Calendar API')
})

test('in the evening it shows the night, and tonight\'s bedtime can move', async ({ page }) => {
  await open(page, { now: at(20), granted: true, events: [{ title: 'เวรเช้า', begin: at(7, 0, 9), end: at(16, 0, 9) }] })
  await expect(page.getByText('นอน 22:30 ตื่น 06:00')).toBeVisible()
  await expect(page.getByText('ตื่นก่อน เวรเช้า 1 ชม.')).toBeVisible()
  await page.getByRole('button', { name: /นอน 22:30/ }).click()
  await page.getByLabel('เวลานอน', { exact: true }).fill('23:30')
  await page.getByRole('button', { name: 'บันทึก' }).click()
  await expect(page.getByText('นอน 23:30 ตื่น 06:00')).toBeVisible()
})

test('the usual sleep times come from the profile note', async ({ page }) => {
  await open(page, { now: at(20), profile: '# โปรไฟล์\n- ตื่น: 06:00\n- นอน: 23:00\n' })
  await expect(page.getByText('นอน 23:00 ตื่น 06:00')).toBeVisible()
})

test('a task on Focus opens the edit pane', async ({ page }) => {
  const { file } = await open(page)
  await page.getByRole('button', { name: 'ส่งรายงาน' }).click()
  const pane = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  await expect(pane).toBeVisible()
  await expect(pane.getByText('ส่งรายงาน')).toBeVisible()
  expect(file.text).toContain('- [ ] ส่งรายงาน')
})

test('the Focus menu goes to the other pages', async ({ page }) => {
  await open(page)
  await page.getByRole('button', { name: 'เมนู' }).click()
  await page.getByRole('menuitem', { name: 'ตั้งค่า' }).click()
  await expect(page.getByRole('heading', { name: 'ตั้งค่า', level: 1 })).toBeVisible()
  await page.goBack()
  await page.getByRole('button', { name: '✦ ผู้ช่วย' }).click()
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
})
