import { expect, test, type Page } from '@playwright/test'

import { Profile, allowFolders, profileText } from './assistantHelpers'
import { at, open } from './helpers'

/** Opens the app on the Assistant page. */
async function openAssistant(page: Page, opts: Parameters<typeof open>[1] = {}) {
  const r = await open(page, opts)
  await allowFolders(page, r.drive)
  await page.getByRole('navigation', { name: 'เมนูหลัก' }).getByRole('button', { name: 'ผู้ช่วย', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
  return r
}

const ask = async (page: Page, text: string) => {
  await page.getByLabel('ข้อความถึงผู้ช่วย').fill(text)
  await page.getByRole('button', { name: 'ส่ง', exact: true }).click()
}

test('the first visit is the interview, saved to the profile note in a new Omni folder', async ({ page }) => {
  const { drive } = await openAssistant(page)
  await expect(page.getByText('ขอรู้จักกันก่อน')).toBeVisible()
  await expect(page.getByText('คำตอบจะเก็บใน หลังบ้าน/Omni/โปรไฟล์.md')).toBeVisible()
  expect(profileText(drive)).toBeNull()

  await page.getByRole('radio', { name: '05:30', exact: true }).click()
  await page.getByRole('radio', { name: '22:00', exact: true }).click()
  await page.getByRole('radio', { name: '13:00 ถึง 16:00' }).click()
  // A time typed under "other" counts as the answer; a description is kept word for word.
  await page.getByRole('radiogroup', { name: 'ชอบออกกำลังกายตอนไหน' }).getByRole('radio', { name: 'อื่นๆ' }).click()
  await page.getByLabel('ชอบออกกำลังกายตอนไหน (อื่นๆ)').fill('5.45')
  await page.getByRole('radiogroup', { name: 'เข้านอนกี่โมง' }).getByRole('radio', { name: 'อื่นๆ' }).click()
  await page.getByLabel('เข้านอนกี่โมง (อื่นๆ)').fill('แล้วแต่เวร')
  await page.getByRole('button', { name: 'บันทึกลงโปรไฟล์' }).click()

  await expect(page.getByText('ขอรู้จักกันก่อน')).toHaveCount(0)
  await expect.poll(() => profileText(drive)).not.toBeNull()
  const text = profileText(drive)!
  expect(text).toContain('- อัปเดต: 2026-10-08')
  expect(text).toContain('- ตื่น: 05:30')
  expect(text).toContain('- นอน: 22:00')
  expect(text).toContain('- ช่วงสมองดี: 13:00-16:00')
  expect(text).toContain('- ออกกำลังกาย: 05:45')
  expect(text).toContain('- นอน: แล้วแต่เวร')
  await expect(page.getByText('อ่านจาก โปรไฟล์.md')).toBeVisible()
  // The interview does not come back.
  await page.reload()
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
  await expect(page.getByText('ถามได้ว่าอยากทำอะไร')).toBeVisible()
  await expect(page.getByText('ขอรู้จักกันก่อน')).toHaveCount(0)
})

test('a request without a length asks how long, then offers three times with reasons and writes the task', async ({ page }) => {
  const { file } = await openAssistant(page, { profile: Profile })
  await page.getByRole('button', { name: '"อยากไปวิ่งสัปดาห์นี้ ควรไปตอนไหนดี"' }).click()
  await expect(page.getByText('งานนี้น่าจะใช้เวลาประมาณเท่าไร')).toBeVisible()
  await page.getByRole('button', { name: '45 นาที' }).click()

  const slots = page.getByRole('radiogroup', { name: 'ช่วงเวลาที่แนะนำ' }).getByRole('radio')
  await expect(slots).toHaveCount(3)
  await expect(slots.first()).toContainText('แนะนำ')
  await expect(slots.first()).toContainText('ตรงกับเวลาออกกำลังกายที่คุณตั้งไว้ (17:30)')
  await expect(slots.first()).toContainText('17:30')
  await expect(page.getByLabel('ชื่องาน')).toHaveValue('ไปวิ่ง')

  await page.getByLabel('ชื่องาน').fill('วิ่งสวนสาธารณะ')
  const label = (await slots.nth(1).locator('.as-slot-when .muted').textContent())!
  const dayNo = Number(/\d+/.exec(label)![0])
  await slots.nth(1).click()
  await page.getByRole('button', { name: 'สร้างงานในโน้ต' }).click()
  await expect(page.getByRole('button', { name: /เพิ่มงานแล้ว/ })).toBeVisible()
  // The chosen row is the second one: its day is the one written.
  await expect.poll(() => file.text).toContain(`- [ ] วิ่งสวนสาธารณะ #remind-at-scheduled 🎯 17:30 ➕ 2026-10-08 ⏳ 2026-10-${String(dayNo).padStart(2, '0')}\n`)
})

test('a request that says how long goes straight to the times, and the time avoids a shift', async ({ page }) => {
  // A shift on Friday 9 October, all day: the best time for deep work is not that day.
  await openAssistant(page, {
    profile: Profile, granted: true,
    events: [{ title: 'เวร OPD', begin: at(8, 0, 9), end: at(16, 0, 9) }],
  })
  await expect(page.getByText('อ่านจาก โปรไฟล์.md, งาน 9 รายการ และ Google Calendar')).toBeVisible()
  await ask(page, 'อยากเขียน proposal 2 ชม. ควรทำตอนไหน')
  await expect(page.getByText('ใช้เวลาราว 2 ชั่วโมง')).toBeVisible()
  await expect(page.getByLabel('ชื่องาน')).toHaveValue('เขียน proposal 2 ชม.')
  const first = page.getByRole('radiogroup', { name: 'ช่วงเวลาที่แนะนำ' }).getByRole('radio').first()
  await expect(first).toContainText('อยู่ในช่วงสมองดีของคุณ 08:00 ถึง 11:00')
  await expect(first).not.toContainText('วันนั้นมีเวร')
})

test('"none of these" puts the task on a day all day, or at a time picked by hand', async ({ page }) => {
  const { file } = await openAssistant(page, { profile: Profile })
  await ask(page, 'ต้องไปธนาคาร 30 นาที')
  await page.getByRole('button', { name: 'ลงทั้งวัน' }).click()
  const dialog = page.getByRole('dialog', { name: 'ลงทั้งวัน' })
  await dialog.getByLabel('วัน').fill('2026-10-14')
  await dialog.getByRole('button', { name: 'ลงทั้งวัน' }).click()
  await expect.poll(() => file.text).toContain('- [ ] ไปธนาคาร 30 นาที ➕ 2026-10-08 ⏳ 2026-10-14\n')

  await ask(page, 'ต้องซื้อของ 30 นาที')
  await page.getByRole('button', { name: 'ตั้งเวลาเอง' }).click()
  const hand = page.getByRole('dialog', { name: 'ตั้งเวลาเอง' })
  await hand.getByLabel('วัน').fill('2026-10-15')
  await hand.getByLabel('เวลา').fill('16:30')
  await hand.getByRole('button', { name: 'สร้างงาน' }).click()
  await expect.poll(() => file.text).toContain('- [ ] ซื้อของ 30 นาที #remind-at-scheduled 🎯 16:30 ➕ 2026-10-08 ⏳ 2026-10-15\n')
})

test('plan next week: undated work is placed in free time, and "ลงแผน" sets its day and reminder time', async ({ page }) => {
  const { file } = await openAssistant(page, { profile: Profile })
  await page.getByRole('button', { name: /^วางแผนสัปดาห์หน้า/ }).click()
  await expect(page.getByText('แผน 12 ต.ค. ถึง 18 ต.ค.')).toBeVisible()
  const plus = page.getByRole('button', { name: /^ลงแผน / })
  const n = await plus.count()
  expect(n).toBeGreaterThanOrEqual(3)
  // One at a time...
  await page.getByRole('button', { name: 'ลงแผน ทำเว็บคำนวณยา' }).click()
  await expect.poll(() => file.text).toMatch(/- \[ \] ทำเว็บคำนวณยา #remind-at-scheduled 🎯 \d\d:\d\d ➕ 2026-08-01 ⏳ 2026-10-1[2-8]/)
  await expect(page.getByRole('img', { name: 'ลงแผนแล้ว' })).toHaveCount(1)
  // ...then all that are left.
  await page.getByRole('button', { name: /^ลงแผนทั้งหมด \d+ งาน$/ }).click()
  await expect(page.getByRole('button', { name: 'ลงแผนครบแล้ว' })).toBeVisible()
  expect(file.text!.match(/⏳ 2026-10-1[2-8]/g)!.length).toBe(n)
  await expect(page.getByText('เว็บยังไม่ได้ลง Google Calendar ให้')).toBeVisible()
})

test('what is on next week shows events and tasks by day', async ({ page }) => {
  await openAssistant(page, {
    profile: Profile, granted: true,
    events: [{ title: 'ประชุมทีม', begin: at(10, 0, 12), end: at(11, 0, 12) }, { title: 'วันหยุดยาว', begin: at(0, 0, 13), end: at(0, 0, 14), allDay: true }],
  })
  await page.getByRole('button', { name: /^สัปดาห์หน้ามีอะไร/ }).click()
  const card = page.locator('.as-card', { hasText: '12 ต.ค. ถึง 18 ต.ค.' })
  await expect(card).toContainText('นัด 2 รายการ')
  await expect(card).toContainText('10:00 ประชุมทีม')
  await expect(card).toContainText('ทั้งวัน วันหยุดยาว')
})

test('ranking, today and the weekly review', async ({ page }) => {
  await openAssistant(page, { profile: Profile })
  await page.getByRole('button', { name: /^จัดลำดับวันนี้/ }).click()
  await expect(page.getByText('เรียงจากต้องทำก่อน ไปคนที่รอ แล้วค่อยงานเพื่ออนาคต')).toBeVisible()
  await expect(page.getByText('เลยกำหนดแล้ว')).toBeVisible()
  // Once the chat has begun the shortcuts are a row under it.
  const chips = page.getByRole('toolbar', { name: 'ทางลัด' })
  await chips.getByRole('button', { name: 'จัดลำดับทั้งหมด' }).click()
  await expect(page.getByText('ลำดับที่ควรทำ เรียงจากเลยกำหนด ใกล้ครบ คนรอ แล้วค่อยความสำคัญ')).toBeVisible()
  await chips.getByRole('button', { name: 'ทบทวนสัปดาห์' }).click()
  await expect(page.getByText('สัปดาห์นี้เสร็จ 1 งาน')).toBeVisible()
  await expect(page.getByText('งานที่มีคนรอ: เสร็จ 0 ค้าง 1')).toBeVisible()
})

test('the conversation stays when leaving the page, and "เริ่มใหม่" clears it', async ({ page }) => {
  await openAssistant(page, { profile: Profile })
  await ask(page, 'อยากอ่านหนังสือ 1 ชม.')
  await expect(page.getByText('ใช้เวลาราว 1 ชั่วโมง')).toBeVisible()
  const nav = page.getByRole('navigation', { name: 'เมนูหลัก' })
  await nav.getByRole('button', { name: 'งาน', exact: true }).click()
  await nav.getByRole('button', { name: 'ผู้ช่วย', exact: true }).click()
  await expect(page.getByText('ใช้เวลาราว 1 ชั่วโมง')).toBeVisible()
  await page.getByRole('button', { name: 'เริ่มใหม่' }).click()
  await expect(page.getByText('ใช้เวลาราว 1 ชั่วโมง')).toHaveCount(0)
  await expect(page.getByText('ถามได้ว่าอยากทำอะไร')).toBeVisible()
})

test('ask Claude opens claude.ai in a new tab with the day as text', async ({ page, context }) => {
  await context.route('https://claude.ai/**', (route) => route.fulfill({ contentType: 'text/html', body: '<title>claude</title>' }))
  await openAssistant(page, { profile: Profile, granted: true, events: [{ title: 'ประชุมทีม', begin: at(14, 0), end: at(15, 0) }] })
  const [popup] = await Promise.all([context.waitForEvent('page'), page.getByRole('button', { name: /^ถาม Claude/ }).click()])
  const url = new URL(popup.url())
  expect(url.origin + url.pathname).toBe('https://claude.ai/new')
  const q = url.searchParams.get('q')!
  expect(q.startsWith('ช่วยจัดลำดับงานวันนี้ให้หน่อย ตามเวลาว่างในปฏิทิน\n\nวันนี้ 2026-10-08\n[โปรไฟล์]\n- อัปเดต: 2026-10-01\n- ตื่น: 06:30')).toBe(true)
  expect(q).toContain('- นัด: ประชุมทีม 14:00 ถึง 15:00')
  expect(q).toContain('[งานที่ยังไม่เสร็จทั้งหมด]\n- [ ] ส่งรายงาน')
})

test('without the calendar the page offers to connect it and says what it reads', async ({ page }) => {
  await openAssistant(page, { profile: Profile })
  await expect(page.getByText('อ่านจาก โปรไฟล์.md และ งาน 9 รายการ')).toBeVisible()
  await expect(page.getByText('เชื่อมต่อ Google Calendar')).toBeVisible()
  await expect(page.getByRole('button', { name: 'อนุญาต' })).toBeVisible()
})

test('a pattern the assistant noticed is asked about first, and only a yes is remembered', async ({ page }) => {
  const done = (day: number) => `2026-10-0${day}T14:10:00|DEEP`
  await page.addInitScript((log) => localStorage.setItem('omni.doneLog', JSON.stringify(log)), [1, 2, 3, 4, 5, 6].map(done))
  const { drive } = await openAssistant(page, { profile: Profile })
  await expect(page.getByText('จำไว้ไหม?')).toBeVisible()
  await expect(page.getByText('ดูเหมือนคุณปิดงานที่ใช้สมองได้บ่อยช่วง 12:00 ถึง 15:00 (6 จาก 6 งาน)')).toBeVisible()
  await expect(page.getByText('สังเกต 4 สัปดาห์')).toBeVisible()
  expect(profileText(drive)).toBe(Profile)

  await page.getByRole('button', { name: 'ใช่ จำไว้' }).click()
  await expect(page.getByText('จำไว้ไหม?')).toHaveCount(0)
  await expect.poll(() => profileText(drive)).toContain('- ช่วงสมองดี: 12:00-15:00')
  expect(profileText(drive)).toContain('## จำไว้\n- ช่วงสมองดีคือ 12:00 ถึง 15:00')
  expect(profileText(drive)).toContain('- อัปเดต: 2026-10-08')
})

test('saying no to a question is remembered on this device', async ({ page }) => {
  await page.addInitScript(() => { if (!localStorage.getItem('omni.doneLog')) localStorage.setItem('omni.doneLog', JSON.stringify([1, 2, 3, 4, 5, 6].map((d) => `2026-10-0${d}T14:10:00|DEEP`))) })
  const { drive } = await openAssistant(page, { profile: Profile })
  await page.getByRole('button', { name: 'ไม่ใช่' }).click()
  await expect(page.getByText('จำไว้ไหม?')).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
  await expect(page.getByText('ถามได้ว่าอยากทำอะไร')).toBeVisible()
  await expect(page.getByText('จำไว้ไหม?')).toHaveCount(0)
  expect(profileText(drive)).toBe(Profile)
})

test('ticking a task logs when it was done, for the next pattern', async ({ page }) => {
  await open(page, { profile: Profile })
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ทบทวนเคส/ }).click()
  await expect(page.getByRole('img', { name: 'เสร็จแล้ว 1 จาก 4' })).toBeVisible()
  const log = await page.evaluate(() => JSON.parse(localStorage.getItem('omni.doneLog') ?? '[]'))
  expect(log).toHaveLength(1)
  expect(log[0]).toMatch(/^2026-10-08T09:20:\d\d\|(DEEP|GENERAL|QUICK|MOVE|ERRAND)$/)
})

test('a profile older than a month is asked about again', async ({ page }) => {
  const { drive } = await openAssistant(page, { profile: Profile.replace('2026-10-01', '2026-08-01') })
  await expect(page.getByText('ชีวิตช่วงนี้ยังเหมือนเดิมไหม?')).toBeVisible()
  await expect(page.getByText('ไม่ได้ทบทวนมา 30 วัน')).toBeVisible()
  await expect(page.getByText('ตื่น 06:30 นอน 22:30 สมองดี 08:00 ถึง 11:00 ออกกำลังกาย 17:30')).toBeVisible()
  await page.getByRole('button', { name: 'ยังเหมือนเดิม' }).click()
  await expect.poll(() => profileText(drive)).toContain('- อัปเดต: 2026-10-08')
  await expect(page.getByText('ชีวิตช่วงนี้ยังเหมือนเดิมไหม?')).toHaveCount(0)
})

test('"ปรับ" opens the interview with the current answers, and cancel leaves the note alone', async ({ page }) => {
  const stale = Profile.replace('2026-10-01', '2026-08-01').replace('- ตื่น: 06:30', '- ตื่น: 07:00')
  const { drive } = await openAssistant(page, { profile: stale })
  await page.getByRole('button', { name: 'ปรับ', exact: true }).click()
  await expect(page.getByText('ปรับโปรไฟล์')).toBeVisible()
  await expect(page.getByRole('radio', { name: '07:00', exact: true }).first()).toHaveAttribute('aria-checked', 'true')
  await page.getByRole('button', { name: 'ยกเลิก' }).click()
  await expect(page.getByText('ปรับโปรไฟล์')).toHaveCount(0)
  expect(profileText(drive)).toBe(stale)
})
