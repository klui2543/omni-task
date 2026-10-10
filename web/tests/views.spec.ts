import { expect, type Page, test } from '@playwright/test'

import { NOTE, at, open } from './helpers'

// Today is Thursday 2026-10-08, 09:20 (see helpers.ts), so this week runs Monday the 5th to Sunday the 11th.
const MORE = '- [x] จองห้องประชุม ✅ 2026-10-08 📅 2026-10-08\n- [ ] ร่างบทความ #peddose 🛫 2026-10-06 📅 2026-10-09\n'

async function views(page: Page, opts: Parameters<typeof open>[1] = {}, mode?: string) {
  if (mode) await page.addInitScript((m) => localStorage.setItem('omni.viewMode', m), mode)
  const opened = await open(page, { note: NOTE + MORE, ...opts })
  await page.evaluate(() => { location.hash = '/views' })
  await expect(page.getByRole('heading', { name: 'มุมมอง', level: 1 })).toBeVisible()
  return opened
}
const tab = (page: Page, name: string) => page.getByRole('tab', { name, exact: true })
const card = (page: Page, title: string) => page.locator('.kcard', { hasText: title })
const column = (page: Page, name: string) => page.getByRole('region', { name, exact: true })
const edit = (page: Page) => page.getByRole('dialog', { name: 'แก้ไขงาน' })
const lineOf = (text: string | undefined, title: string) => text!.split('\n').find((l) => l.includes(title))
const events = () => [
  { title: 'ประชุมทีม', begin: at(10), end: at(11) },
  { title: 'คลินิกนอกเวลา', begin: at(17), end: at(20) },
  { title: 'งานสัมมนา', begin: at(0, 0, 9), end: at(0, 0, 10), allDay: true },
]

test('Kanban: a column per status, cards by due date, done work folded away', async ({ page }) => {
  await views(page)
  await expect(page.getByRole('tab', { name: 'Kanban' })).toHaveAttribute('aria-selected', 'true')
  const todo = column(page, 'ยังไม่เริ่ม')
  await expect(todo.locator('.kcard')).toHaveCount(8)
  // Soonest first: the late report, then today's, with the ones without a date last.
  await expect(todo.locator('.kopen').first()).toHaveText('ส่งรายงาน')
  await expect(todo.locator('.kopen').last()).toHaveText('อ่าน Deep Work')
  await expect(card(page, 'ส่งรายงาน').getByText('เลย 7 ต.ค.')).toBeVisible()
  await expect(card(page, 'โทรหาแม่').getByText('19:30')).toBeVisible()
  const doing = column(page, 'กำลังทำ')
  await expect(doing.locator('.kcard')).toHaveCount(1)
  await expect(card(page, 'สไลด์ประชุม').getByText('1/2')).toBeVisible()
  // With "hide done" on, Done is a thin folded strip that still shows its count.
  await expect(column(page, 'เสร็จ')).toHaveCount(0)
  await expect(page.getByRole('button', { name: /เสร็จ \(พับอยู่/ })).toContainText('1')
})

test('Kanban: tapping the folded Done strip shows last week\'s done work and turns "hide done" off for good', async ({ page }) => {
  await views(page)
  await page.getByRole('button', { name: /เสร็จ \(พับอยู่/ }).click()
  await expect(column(page, 'เสร็จ').locator('.kcard')).toHaveCount(1)
  await expect(card(page, 'จองห้องประชุม').getByText('เสร็จ 8 ต.ค.')).toBeVisible()
  await page.reload()
  await expect(column(page, 'เสร็จ')).toBeVisible()
})

test('Kanban: the arrows move a card to the next status and write the note', async ({ page }) => {
  const { file } = await views(page)
  await card(page, 'ส่งรายงาน').getByRole('button', { name: 'ย้ายไป กำลังทำ' }).click()
  await expect.poll(() => lineOf(file.text, 'ส่งรายงาน')).toMatch(/^- \[\/\] ส่งรายงาน/)
  await expect(column(page, 'กำลังทำ').locator('.kcard')).toHaveCount(2)
  // Moving on to Done is a tick, so it stamps the done date like ticking anywhere else.
  await card(page, 'ส่งรายงาน').getByRole('button', { name: 'ย้ายไป เสร็จ' }).click()
  await expect.poll(() => lineOf(file.text, 'ส่งรายงาน')).toContain('✅ 2026-10-08')
  expect(lineOf(file.text, 'ส่งรายงาน')).toMatch(/^- \[x\]/)
})

test('Kanban: dragging a card to another column changes its status', async ({ page }) => {
  const { file } = await views(page)
  await card(page, 'ทบทวนเคส').dragTo(column(page, 'กำลังทำ'))
  await expect.poll(() => lineOf(file.text, 'ทบทวนเคส')).toMatch(/^- \[\/\] ทบทวนเคส/)
  await expect(column(page, 'กำลังทำ').locator('.kcard', { hasText: 'ทบทวนเคส' })).toBeVisible()
})

test('Kanban: a finger drags after a hold, and a quick swipe on a card leaves the page to scroll', async ({ page, browserName }, info) => {
  test.skip(info.project.name !== 'ipad' || browserName !== 'chromium', 'a touch screen')
  const { file } = await views(page)
  const cdp = await page.context().newCDPSession(page)
  const centre = async (loc: ReturnType<typeof card>) => {
    const b = (await loc.boundingBox())!
    return { x: b.x + b.width / 2, y: b.y + b.height / 2 }
  }
  const touch = (type: 'touchStart' | 'touchMove' | 'touchEnd', x: number, y: number) =>
    cdp.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y }] })

  // A quick swipe starts no drag.
  const from = await centre(card(page, 'ทบทวนเคส'))
  const target = await centre(column(page, 'กำลังทำ'))
  await touch('touchStart', from.x, from.y)
  await touch('touchMove', from.x + 40, from.y + 60)
  await touch('touchMove', target.x, target.y)
  await touch('touchEnd', 0, 0)
  await expect(page.locator('.vghost')).toHaveCount(0)
  expect(lineOf(file.text, 'ทบทวนเคส')).toMatch(/^- \[ \]/)

  // Held for half a second, the card follows the finger and drops where it is lifted. The swipe above may have
  // scrolled the page, so the card and the column are measured again.
  await card(page, 'ทบทวนเคส').scrollIntoViewIfNeeded()
  const from2 = await centre(card(page, 'ทบทวนเคส'))
  const target2 = await centre(column(page, 'กำลังทำ'))
  await touch('touchStart', from2.x, from2.y)
  await page.waitForTimeout(600)
  await touch('touchMove', from2.x + 20, from2.y + 20)
  await touch('touchMove', target2.x, target2.y)
  await expect(page.locator('.vghost')).toBeVisible()
  await expect(column(page, 'กำลังทำ')).toHaveClass(/over/)
  await touch('touchEnd', 0, 0)
  await expect.poll(() => lineOf(file.text, 'ทบทวนเคส')).toMatch(/^- \[\/\] ทบทวนเคส/)
})

test('Kanban: a column folds to a strip (remembered), and its + adds a task in that status', async ({ page }) => {
  const { file } = await views(page)
  await page.getByRole('button', { name: 'พับ กำลังทำ' }).click()
  await expect(column(page, 'กำลังทำ')).toHaveCount(0)
  await page.reload()
  await expect(page.getByRole('button', { name: /กำลังทำ \(พับอยู่/ })).toBeVisible()
  await page.getByRole('button', { name: 'เพิ่มงานใน กำลังทำ' }).click()
  const dialog = page.getByRole('dialog', { name: 'เพิ่มงาน' })
  await expect(dialog.getByText('เริ่มที่สถานะ กำลังทำ')).toBeVisible()
  await dialog.getByRole('textbox', { name: 'งานใหม่' }).fill('ซื้อนม พรุ่งนี้')
  await dialog.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => lineOf(file.text, 'ซื้อนม')).toMatch(/^- \[\/\] ซื้อนม .*📅 2026-10-09/)
  await page.getByRole('button', { name: /กำลังทำ \(พับอยู่/ }).click()
  await expect(column(page, 'กำลังทำ').locator('.kcard', { hasText: 'ซื้อนม' })).toBeVisible()
})

test('Kanban: a card opens the edit panel, and a tick goes through the usual path', async ({ page }) => {
  const { file } = await views(page)
  await card(page, 'เขียน paper').getByRole('button', { name: 'เขียน paper' }).click()
  await expect(edit(page)).toBeVisible()
  await expect(edit(page).getByRole('heading', { name: 'เขียน paper' })).toBeVisible()
  await edit(page).getByRole('radio', { name: 'กำลังทำ' }).click()
  await expect.poll(() => lineOf(file.text, 'เขียน paper')).toMatch(/^- \[\/\] เขียน paper/)
})

test('Matrix: open tasks by urgency and importance, the urgent rule from the menu', async ({ page }) => {
  await views(page, {}, 'matrix')
  const quad = (name: string) => column(page, name)
  // Urgent = due by Sunday; important = high or highest priority.
  await expect(quad('ด่วนและสำคัญ').locator('.mtitle')).toHaveText(['ส่งรายงาน', 'ทบทวนเคส'])
  await expect(quad('ด่วนแต่ไม่สำคัญ').locator('.mtitle')).toHaveText(['สไลด์ประชุม', 'โทรหาแม่', 'ร่างบทความ', 'เขียน paper'])
  await expect(quad('ไม่ด่วนไม่สำคัญ')).toContainText('ทำเว็บคำนวณยา')
  await expect(quad('ไม่ด่วนแต่สำคัญ')).toContainText('0')
  await expect(quad('ด่วนและสำคัญ').getByText('7 ต.ค.')).toHaveClass(/late/)
  await expect(quad('ด่วนและสำคัญ').getByText('วันนี้')).toBeVisible()
  // Done work is not in the matrix.
  await expect(page.getByText('จองห้องประชุม')).toHaveCount(0)
  await expect(page.getByText('กดค้างแล้วลากขึ้นหรือลงเพื่อเปลี่ยนความสำคัญ')).toBeVisible()

  await page.getByRole('button', { name: /^ด่วน = / }).click()
  await page.getByRole('menuitemradio', { name: 'ด่วน = ภายใน 2 วัน' }).click()
  await expect(page.getByRole('button', { name: 'ด่วน = ภายใน 2 วัน' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('omni.urgent'))).toBe('TWO_DAYS')
})

test('Matrix: up and down change the priority, as Android does', async ({ page }) => {
  const { file } = await views(page, {}, 'matrix')
  const item = (title: string) => page.locator('.mitem', { hasText: title })
  // Up into an important quadrant: High.
  await item('สไลด์ประชุม').getByRole('button', { name: 'ย้ายไป ด่วนและสำคัญ' }).click()
  await expect.poll(() => lineOf(file.text, 'สไลด์ประชุม')).toContain('⏫')
  await expect(column(page, 'ด่วนและสำคัญ').locator('.mitem', { hasText: 'สไลด์ประชุม' })).toBeVisible()
  // Down out of it: Medium.
  await item('ส่งรายงาน').getByRole('button', { name: 'ย้ายไป ด่วนแต่ไม่สำคัญ' }).click()
  await expect.poll(() => lineOf(file.text, 'ส่งรายงาน')).toContain('🔼')
  expect(lineOf(file.text, 'ส่งรายงาน')).not.toContain('⏫')
})

test('Matrix: dragging up or down works, dragging across the urgent line is refused', async ({ page }) => {
  const { file } = await views(page, {}, 'matrix')
  const before = file.text
  await page.locator('.mitem', { hasText: 'ทบทวนเคส' }).dragTo(column(page, 'ไม่ด่วนแต่สำคัญ'))
  await expect(page.getByRole('alert')).toContainText('ลากได้แค่ขึ้นลง')
  expect(file.text).toBe(before)

  await page.locator('.mitem', { hasText: 'ทบทวนเคส' }).dragTo(column(page, 'ด่วนแต่ไม่สำคัญ'))
  await expect.poll(() => lineOf(file.text, 'ทบทวนเคส')).toContain('🔼')
  expect(lineOf(file.text, 'ทบทวนเคส')).not.toContain('🔺')
})

test('the filter is the task list\'s: priority and tags narrow every view, "hide done" is on the sheet', async ({ page }) => {
  await views(page, {}, 'matrix')
  await page.getByRole('button', { name: 'กรอง', exact: true }).click()
  const panel = page.getByRole('dialog', { name: 'กรอง' })
  await panel.getByRole('button', { name: 'สูงสุด' }).click()
  await expect(panel.getByRole('button', { name: 'แสดง 1 งาน' })).toBeVisible()
  await expect(panel.getByRole('switch', { name: 'ซ่อนงานที่เสร็จและยกเลิก' })).toBeChecked()
  await panel.getByRole('button', { name: 'แสดง 1 งาน' }).click()
  await expect(page.locator('.mitem')).toHaveCount(1)
  await expect(page.getByRole('button', { name: /กรอง 1/ })).toBeVisible()
  await page.getByRole('button', { name: 'เอา สูงสุด ออก' }).click()
  await expect(page.locator('.mitem')).toHaveCount(9)
})

test('"hide done" off brings done work into the calendar and the Kanban column', async ({ page }) => {
  await views(page, { granted: true, events: events() }, 'calendar')
  await expect(page.getByRole('region', { name: 'งานของวันที่เลือก' }).getByText('จองห้องประชุม')).toHaveCount(0)
  await page.getByRole('button', { name: 'กรอง', exact: true }).click()
  await page.getByRole('switch', { name: 'ซ่อนงานที่เสร็จและยกเลิก' }).uncheck()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('region', { name: 'งานของวันที่เลือก' }).getByText('จองห้องประชุม')).toBeVisible()
})

test('Gantt: bars by project from start to due, paging, and an edge lane for work out of range', async ({ page }) => {
  await views(page, {}, 'gantt')
  await page.getByRole('button', { name: '14 วัน', exact: true }).click()
  await expect(page.locator('.grange')).toHaveText('7 ถึง 20 ต.ค.')
  await expect(page.locator('.gcaption', { hasText: 'peddose' })).toBeVisible()
  await expect(page.locator('.gcaption', { hasText: 'ไม่มีโปรเจกต์' })).toBeVisible()
  // The article: dated 6 to 9 Oct. Bars carry the title and the range, or the title sits beside a short bar.
  await expect(page.getByRole('button', { name: /ร่างบทความ/ })).toBeVisible()
  await expect(page.getByText('6 ถึง 9 ต.ค.')).toBeVisible()
  // Overdue work is red, undated work has no bar.
  await expect(page.locator('.gbar.late').first()).toBeVisible()
  await expect(page.getByText('ทำเว็บคำนวณยา')).toHaveCount(0)
  await expect(page.getByText('จองห้องประชุม')).toHaveCount(0)

  await page.getByRole('button', { name: 'ถัดไป 14 วัน' }).click()
  await expect(page.locator('.grange')).toHaveText('21 ต.ค. ถึง 3 พ.ย.')
  await expect(page.getByRole('img', { name: 'ก่อนช่วงนี้' }).first()).toBeVisible()
  await expect(page.getByText('ยังไม่มีงานที่มีวันที่')).toHaveCount(0)
  await page.getByRole('button', { name: 'วันนี้', exact: true }).click()
  await expect(page.locator('.grange')).toHaveText('7 ถึง 20 ต.ค.')
  await page.getByRole('button', { name: '30 วัน', exact: true }).click()
  await expect(page.locator('.grange')).toHaveText('7 ต.ค. ถึง 5 พ.ย.')
})

test('Gantt: a bar opens the edit panel; events from Google Calendar can be switched off', async ({ page }) => {
  await views(page, { granted: true, events: events() }, 'gantt')
  await expect(page.locator('.gcaption', { hasText: 'Google Calendar' })).toBeVisible()
  await expect(page.getByText('ประชุมทีม')).toBeVisible()
  await expect(page.getByText('10:00 ถึง 11:00')).toBeVisible()
  await expect(page.getByText('ทั้งวัน')).toBeVisible()
  await page.getByRole('button', { name: /^นัด(หมาย|จาก Google Calendar)$/ }).click()
  await expect(page.getByText('ประชุมทีม')).toHaveCount(0)
  await page.getByRole('button', { name: /ร่างบทความ/ }).first().click()
  await expect(edit(page).getByRole('heading', { name: 'ร่างบทความ' })).toBeVisible()
})

test('Month: dots on days with work and events, the day\'s events and tasks beneath, ticking a task', async ({ page }) => {
  const { file } = await views(page, { granted: true, events: events() }, 'calendar')
  await expect(page.getByText('ตุลาคม 2026')).toBeVisible()
  const day = page.getByRole('region', { name: 'งานของวันที่เลือก' })
  await expect(day.getByRole('heading', { name: 'วันนี้, พฤหัสบดี 8 ตุลาคม' })).toBeVisible()
  await expect(day.getByText('ประชุมทีม')).toBeVisible()
  await expect(day.getByText('17:00 ถึง 20:00')).toBeVisible()
  await expect(day.getByText('ทบทวนเคส')).toBeVisible()
  await expect(day.getByText('เขียน paper')).toHaveCount(0)
  // Red for an overdue open task, accent for any task, teal for an event.
  const dots = (label: string) => page.getByRole('button', { name: label, exact: true }).locator('.mdots span')
  await expect(dots('พฤหัสบดี 8 ตุลาคม')).toHaveCount(2)
  await expect(dots('พุธ 7 ตุลาคม')).toHaveCount(2)
  await expect(dots('ศุกร์ 9 ตุลาคม')).toHaveCount(2)
  await expect(dots('อังคาร 6 ตุลาคม')).toHaveCount(0)

  await page.getByRole('button', { name: 'เสาร์ 10 ตุลาคม' }).click()
  await expect(day.getByRole('heading', { name: 'เสาร์ 10 ตุลาคม' })).toBeVisible()
  await day.getByRole('checkbox', { name: /ติ๊กเสร็จ เขียน paper/ }).click()
  await expect.poll(() => lineOf(file.text, 'เขียน paper')).toContain('✅ 2026-10-08')

  await page.getByRole('button', { name: 'พฤหัสบดี 15 ตุลาคม' }).click()
  await expect(day.getByText('ว่างทั้งวัน')).toBeVisible()
  await page.getByRole('button', { name: 'เดือนถัดไป' }).click()
  await expect(page.getByText('พฤศจิกายน 2026')).toBeVisible()
  await page.getByRole('button', { name: 'วันนี้', exact: true }).click()
  await expect(page.getByText('ตุลาคม 2026')).toBeVisible()
})

test('Month: without the calendar allowed it offers to connect, and a task still shows', async ({ page }) => {
  await views(page, {}, 'calendar')
  await expect(page.getByText('เชื่อมต่อ Google Calendar')).toBeVisible()
  await expect(page.getByRole('region', { name: 'งานของวันที่เลือก' }).getByText('ทบทวนเคส')).toBeVisible()
})

test('Calendar: a dated subtask and the Google Calendar error show as on the Focus page', async ({ page }) => {
  const note = NOTE.replace('    - [ ] ซ้อม', '    - [ ] ซ้อม ⏳ 2026-10-09')
  const { drive } = await views(page, { note, granted: true }, 'calendar')
  drive.calendarOff = true
  await page.getByRole('button', { name: 'เดือนถัดไป' }).click()
  await page.getByRole('button', { name: 'เดือนก่อน' }).click()
  await page.getByRole('button', { name: 'ศุกร์ 9 ตุลาคม' }).click()
  await expect(page.getByRole('region', { name: 'งานของวันที่เลือก' }).getByText('ซ้อม')).toBeVisible()
  await expect(page.getByRole('alert').filter({ hasText: 'Google Calendar API' })).toBeVisible()
})

test('7 days: events and timed tasks in the hour grid, the rest in the all-day strip, paging', async ({ page }) => {
  await views(page, { granted: true, events: events() }, 'calendar')
  await tab(page, '7 วัน').click()
  await expect(page.locator('.grange')).toHaveText('5 ถึง 11 ต.ค.')
  const grid = page.locator('.tgrid')
  // The task with a reminder at 10:00 sits in the grid beside the 10:00 event; a task without a time sits in the strip.
  await expect(grid.getByRole('button', { name: 'สไลด์ประชุม' })).toBeVisible()
  await expect(grid.getByText('ประชุมทีม')).toBeVisible()
  await expect(page.locator('.tstrip').getByRole('button', { name: 'ส่งรายงาน' })).toBeVisible()
  await expect(page.locator('.tstrip').getByText('งานสัมมนา')).toBeVisible()
  await expect(grid.getByRole('button', { name: 'ส่งรายงาน' })).toHaveCount(0)
  // Today gets the red line at the current time.
  await expect(page.getByLabel('เวลาตอนนี้')).toBeVisible()
  const box = await grid.getByRole('button', { name: 'สไลด์ประชุม' }).boundingBox()
  const line = await page.getByLabel('เวลาตอนนี้').boundingBox()
  expect(box!.y).toBeGreaterThan(line!.y)

  await grid.getByRole('button', { name: 'สไลด์ประชุม' }).click()
  await expect(edit(page).getByRole('heading', { name: 'สไลด์ประชุม' })).toBeVisible()
  await edit(page).getByRole('button', { name: 'ปิด' }).first().click()

  await page.getByRole('button', { name: 'สัปดาห์ถัดไป' }).click()
  await expect(page.locator('.grange')).toHaveText('12 ถึง 18 ต.ค.')
  await expect(page.getByLabel('เวลาตอนนี้')).toHaveCount(0)
  await page.getByRole('button', { name: 'วันนี้', exact: true }).click()
  await expect(page.locator('.grange')).toHaveText('5 ถึง 11 ต.ค.')
})

test('3 days and 1 day: the single day shows the times inside the blocks', async ({ page }) => {
  await views(page, { granted: true, events: events() }, 'calendar')
  await tab(page, '3 วัน').click()
  await expect(page.locator('.grange')).toHaveText('8 ถึง 10 ต.ค.')
  await page.getByRole('button', { name: 'ถัดไป 3 วัน' }).click()
  await expect(page.locator('.grange')).toHaveText('11 ถึง 13 ต.ค.')
  await tab(page, 'วัน').click()
  await page.getByRole('button', { name: 'วันนี้', exact: true }).click()
  await expect(page.locator('.grange')).toHaveText('วันนี้, พฤหัสบดี 8 ต.ค.')
  await expect(page.locator('.tgrid').getByText('10:00 ถึง 11:00')).toBeVisible()
  await expect(page.getByRole('button', { name: 'วันถัดไป' })).toBeVisible()
})

test('the chosen view is remembered on the device', async ({ page }) => {
  await views(page)
  await tab(page, 'Gantt').click()
  await page.reload()
  await expect(tab(page, 'Gantt')).toHaveAttribute('aria-selected', 'true')
})

test('every change here is a write through the usual path: a conflict is reported, not overwritten', async ({ page }) => {
  const { file } = await views(page)
  // Once the page has read the note, someone else edits the line.
  await expect(card(page, 'ส่งรายงาน')).toBeVisible()
  file.text = file.text!.replace('ส่งรายงาน', 'ส่งรายงานฉบับแก้')
  await card(page, 'ส่งรายงาน').getByRole('button', { name: 'ย้ายไป กำลังทำ' }).click()
  await expect(page.getByText('บรรทัดนี้ถูกแก้จากที่อื่นไปแล้ว')).toBeVisible()
  expect(lineOf(file.text, 'ส่งรายงานฉบับแก้')).toMatch(/^- \[ \]/)
})
