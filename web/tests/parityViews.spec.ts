import { expect, type Page, test } from '@playwright/test'

import { NOTE, at, open } from './helpers'

// The views and the Projects page brought level with Android: search in the views, a project's calendar, the
// browser's back inside Projects, the calendar's "try again", done rows, lane taps, mouse swipes and kept state.
// Today is Thursday 2026-10-08, 09:20 (see helpers.ts).

const MORE = '- [x] จองห้องประชุม ✅ 2026-10-08 📅 2026-10-08\n- [ ] ร่างบทความ #peddose 🛫 2026-10-06 📅 2026-10-09\n'

async function views(page: Page, opts: Parameters<typeof open>[1] = {}, mode?: string) {
  if (mode) await page.addInitScript((m) => localStorage.setItem('omni.viewMode', m), mode)
  const opened = await open(page, { note: NOTE + MORE, ...opts })
  await page.evaluate(() => { location.hash = '/views' })
  await expect(page.getByRole('heading', { name: 'งาน', level: 1 })).toBeVisible()
  return opened
}

// Two projects, each with a dated subtask, a done task with all its details, and a branch for the mind map.
const PROJECTS = [
  '- [ ] ส่งรายงานความก้าวหน้า #peddose ⏫ 📅 2026-10-07',
  '    - [ ] ตรวจตัวเลข ⏳ 2026-10-09',
  '- [ ] ทำหน้าเว็บ #peddose/แอป',
  '- [ ] ทบทวนเคสก่อนราวด์ #Siriraj',
  '    - [ ] เตรียมสไลด์เคส 📅 2026-10-09',
  '- [x] ส่งเวร #Siriraj #remind-at-due ⏰ 08:00 ⏫ 🔁 every week 📅 2026-10-06 ✅ 2026-10-06',
  '    - ส่งที่ห้องพยาบาลชั้นสาม',
  '',
].join('\n')

async function projects(page: Page, opts: Parameters<typeof open>[1] = {}) {
  const ctx = await open(page, { now: at(9, 20), note: PROJECTS, ...opts })
  await page.evaluate(() => { location.hash = '/projects' })
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()
  return ctx
}

const layoutTab = (page: Page, name: string) => page.getByRole('tablist', { name: 'มุมมอง' }).getByRole('tab', { name, exact: true })
const tab = (page: Page, name: string) => page.getByRole('tab', { name, exact: true })
const edit = (page: Page) => page.getByRole('dialog', { name: 'แก้ไขงาน' })
const dayCard = (page: Page) => page.getByRole('region', { name: 'งานของวันที่เลือก' })
const search = (page: Page) => page.getByRole('textbox', { name: 'ค้นหาชื่องานในมุมมอง' })
const hash = (page: Page) => page.evaluate(() => decodeURIComponent(location.hash))

test('the search box narrows Kanban, Matrix, Gantt and the calendar by title, and stays while the views change', async ({ page }) => {
  await views(page, {}, 'kanban')
  await search(page).fill('รายงาน')
  const todo = page.getByRole('region', { name: 'ยังไม่เริ่ม', exact: true })
  await expect(todo.locator('.kcard')).toHaveCount(1)
  await expect(todo.locator('.kopen')).toHaveText('ส่งรายงาน')

  await layoutTab(page, 'Matrix').click()
  await expect(page.getByText('ส่งรายงาน', { exact: true })).toBeVisible()
  await expect(page.getByText('ทบทวนเคส', { exact: true })).toHaveCount(0)
  await expect(search(page)).toHaveValue('รายงาน')

  await layoutTab(page, 'Timeline').click()
  await expect(page.getByRole('button', { name: /ส่งรายงาน/ }).first()).toBeVisible()
  await expect(page.getByText('ร่างบทความ')).toHaveCount(0)

  await layoutTab(page, 'ปฏิทิน').click()
  await page.getByRole('button', { name: 'พุธ 7 ตุลาคม' }).click()
  await expect(dayCard(page).getByText('ส่งรายงาน')).toBeVisible()
  await page.getByRole('button', { name: 'พฤหัสบดี 8 ตุลาคม' }).click()
  await expect(dayCard(page).getByText('ทบทวนเคส')).toHaveCount(0)

  // Cleared, everything is back.
  await page.getByRole('button', { name: 'ล้างคำค้น' }).click()
  await expect(dayCard(page).getByText('ทบทวนเคส')).toBeVisible()
})

test('a project\'s calendar shows only the dated subtasks of its own tasks, and its views have the search too', async ({ page }) => {
  await projects(page)
  await page.locator('.pj-card', { hasText: 'peddose' }).click()
  await page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' }).getByRole('tab', { name: 'ปฏิทิน' }).click()
  await tab(page, 'เดือน').click()
  await page.getByRole('button', { name: 'ศุกร์ 9 ตุลาคม' }).click()
  await expect(dayCard(page).getByText('ตรวจตัวเลข')).toBeVisible()
  // Siriraj's subtask on the same day stays out.
  await expect(dayCard(page).getByText('เตรียมสไลด์เคส')).toHaveCount(0)

  await search(page).fill('ไม่มีงานนี้')
  await expect(dayCard(page).getByText('ตรวจตัวเลข')).toHaveCount(0)
})

test('inside Projects the address follows each screen, so the browser\'s back steps out as Android\'s back does', async ({ page }) => {
  await projects(page)
  await page.locator('.pj-card', { hasText: 'peddose' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'peddose' })).toBeVisible()
  expect(await hash(page)).toBe('#/projects/project/peddose')

  await page.getByRole('button', { name: /Mind map ของโปรเจกต์/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: '#peddose' })).toBeVisible()
  expect(await hash(page)).toBe('#/projects/project/peddose/map')
  await page.goBack()
  await expect(page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' })).toBeVisible()

  await page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' }).getByRole('tab', { name: 'Kanban' }).click()
  await tab(page, 'Matrix').click()
  expect(await hash(page)).toBe('#/projects/project/peddose/matrix')
  // Switching views is one step: back goes to the overview, not to Kanban.
  await page.goBack()
  await expect(page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' })).toBeVisible()
  await page.goBack()
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()
  expect(await hash(page)).toBe('#/projects')

  // A project opened from Ygdrasil goes back to the tree.
  await page.getByRole('button', { name: /Project Ygdrasil/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Project Ygdrasil' })).toBeVisible()
  await page.locator('.pj-node', { hasText: '#peddose' }).click()
  await page.getByRole('region', { name: 'กิ่งที่เลือก' }).getByRole('button', { name: 'เปิด Mind map' }).click()
  await expect(page.getByRole('heading', { level: 1, name: '#peddose' })).toBeVisible()
  await page.goBack()
  await expect(page.getByRole('heading', { level: 1, name: 'Project Ygdrasil' })).toBeVisible()
  await page.goBack()
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()
})

test('a screen inside Projects can be linked to, and "‹ กลับ" still steps out of it', async ({ page }) => {
  await projects(page)
  await page.evaluate(() => { location.hash = '/projects/project/Siriraj/gantt' })
  await expect(page.getByRole('heading', { level: 1, name: 'Siriraj' })).toBeVisible()
  await expect(tab(page, 'Timeline')).toHaveAttribute('aria-selected', 'true')
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await expect(page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' })).toBeVisible()
  expect(await hash(page)).toBe('#/projects/project/Siriraj')
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()

  // A reload keeps the screen.
  await page.locator('.pj-card', { hasText: 'peddose' }).click()
  await page.reload()
  await expect(page.getByRole('heading', { level: 1, name: 'peddose' })).toBeVisible()
  // The on-screen back after a step made here is the browser's own back.
  await page.getByRole('button', { name: /Mind map ของโปรเจกต์/ }).click()
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await expect(page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' })).toBeVisible()
  await page.goForward()
  await expect(page.getByRole('heading', { level: 1, name: '#peddose' })).toBeVisible()
})

test('a list note opened from Projects is a step the browser\'s back leaves', async ({ page }) => {
  const ctx = await open(page, { now: at(9, 20), note: PROJECTS })
  ctx.drive.omniNote('Watch list.md', '---\nomni-list: true\nicon: 🎬\ncategories: หนัง\ntag: watchlist\n---\n# Watch list\n\n- [ ] Shogun #watchlist #หนัง\n')
  await page.evaluate(() => { location.hash = '/projects' })
  await page.getByRole('button', { name: /Watch list/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Watch list' })).toBeVisible()
  expect(await hash(page)).toContain('#/projects/list/')
  await page.goBack()
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()
})

test('done work in a project\'s overview is a full task row: priority, details, dates, reminder, repeat and tags', async ({ page }) => {
  await projects(page)
  await page.locator('.pj-card', { hasText: 'Siriraj' }).click()
  const row = page.locator('.pj-section', { hasText: 'เสร็จแล้ว' }).locator('.trow.done')
  await expect(row).toHaveCount(1)
  await expect(row.locator('.ttitle')).toHaveText('ส่งเวร')
  await expect(row.locator('.tpreview')).toHaveText('ส่งที่ห้องพยาบาลชั้นสาม')
  await expect(row.locator('.pill')).toContainText(['ครบ 6 ต.ค.', '08:00', 'Siriraj'])
  await expect(row.locator('.pill', { hasText: /สัปดาห์|week/ })).toHaveCount(1)
  await row.getByRole('button', { name: /ส่งเวร/ }).click()
  await expect(edit(page).getByRole('heading', { name: 'ส่งเวร' })).toBeVisible()
})

test('Calendar: when it cannot be read or no calendar is found, "ลองอีกครั้ง" reads it again', async ({ page }) => {
  const { drive } = await views(page, { granted: true, calendarOff: true, events: [{ title: 'ประชุมทีม', begin: at(10), end: at(11) }] }, 'calendar')
  const alert = page.getByRole('alert').filter({ hasText: 'Google Calendar API' })
  await expect(alert).toBeVisible()
  drive.calendarOff = false
  await alert.getByRole('button', { name: 'ลองอีกครั้ง' }).click()
  await expect(dayCard(page).getByText('ประชุมทีม')).toBeVisible()
  await expect(alert).toHaveCount(0)

  // Every calendar switched off here: nothing to read, said with the same way out.
  await page.evaluate(() => localStorage.setItem('omni.calendars.hidden', JSON.stringify(['primary'])))
  await page.getByRole('button', { name: 'เดือนถัดไป' }).click()
  const none = page.getByRole('alert').filter({ hasText: 'ปิดทุกปฏิทินไว้' })
  await expect(none).toBeVisible()
  await page.evaluate(() => localStorage.removeItem('omni.calendars.hidden'))
  await none.getByRole('button', { name: 'ลองอีกครั้ง' }).click()
  await expect(none).toHaveCount(0)
})

test('Timeline: a tap anywhere on a lane picks its task, or opens the next event of an event lane', async ({ page }) => {
  await views(page, { granted: true, events: [{ title: 'ประชุมทีม', begin: at(10), end: at(11), link: 'https://www.google.com/calendar/event?eid=x1' }] }, 'gantt')
  await page.evaluate(() => {
    const w = window as unknown as { opened: string[] }
    w.opened = []
    window.open = (url?: string | URL) => { w.opened.push(String(url)); return null }
  })
  // The range starts yesterday (7 Oct); the first day of each lane is empty for these.
  const eventLane = page.locator('.glane', { hasText: 'ประชุมทีม' })
  await eventLane.click({ position: { x: 3, y: 23 } })
  expect(await page.evaluate(() => (window as unknown as { opened: string[] }).opened)).toEqual(['https://www.google.com/calendar/event?eid=x1'])
  // The bar itself opens it once, not twice.
  await eventLane.locator('.gbar').click()
  expect(await page.evaluate(() => (window as unknown as { opened: string[] }).opened.length)).toBe(2)

  await page.locator('.glane', { hasText: 'เขียน paper' }).click({ position: { x: 3, y: 23 } })
  await page.getByRole('region', { name: /งานที่เลือก เขียน paper/ }).getByRole('button', { name: 'เปิดงาน' }).click()
  await expect(edit(page).getByRole('heading', { name: 'เขียน paper' })).toBeVisible()
})

/** Drags with the mouse across [box], from right to left or left to right. */
async function mouseSwipe(page: Page, selector: string, leftward: boolean) {
  const box = (await page.locator(selector).boundingBox())!
  const y = box.y + Math.min(box.height / 2, 60)
  const [a, b] = leftward ? [box.x + box.width * 0.7, box.x + box.width * 0.3] : [box.x + box.width * 0.3, box.x + box.width * 0.7]
  await page.mouse.move(a, y)
  await page.mouse.down()
  for (let i = 1; i <= 8; i++) await page.mouse.move(a + ((b - a) * i) / 8, y)
  await page.mouse.up()
}

test('the Gantt and the timelines page with a mouse drag too, and the drag opens nothing', async ({ page }) => {
  await views(page, {}, 'gantt')
  // + walks 14, 10, 7 days.
  await page.getByRole('button', { name: 'ขยาย ดูรายวัน' }).click()
  await page.getByRole('button', { name: 'ขยาย ดูรายวัน' }).click()
  await expect(page.locator('.grange')).toHaveText('7 ถึง 13 ต.ค.')
  await mouseSwipe(page, '.gcard', true)
  await expect(page.locator('.grange')).toHaveText('14 ถึง 20 ต.ค.')
  await mouseSwipe(page, '.gcard', false)
  await expect(page.locator('.grange')).toHaveText('7 ถึง 13 ต.ค.')
  await expect(edit(page)).toHaveCount(0)

  await layoutTab(page, 'ปฏิทิน').click()
  await tab(page, '7 วัน').click()
  await expect(page.locator('.grange')).toHaveText('5 ถึง 11 ต.ค.')
  await mouseSwipe(page, '.tcard', true)
  await expect(page.locator('.grange')).toHaveText('12 ถึง 18 ต.ค.')
  await expect(edit(page)).toHaveCount(0)
})

test('the month and its chosen day, the timeline\'s day and the Gantt\'s range stay when the view is left and opened again', async ({ page }) => {
  await views(page, {}, 'calendar')
  await tab(page, 'เดือน').click()
  await page.getByRole('button', { name: 'เดือนถัดไป' }).click()
  await page.getByRole('button', { name: 'ศุกร์ 13 พฤศจิกายน' }).click()
  await layoutTab(page, 'Kanban').click()
  await layoutTab(page, 'ปฏิทิน').click()
  await expect(page.getByText('พฤศจิกายน 2026')).toBeVisible()
  await expect(dayCard(page).getByRole('heading', { name: 'ศุกร์ 13 พฤศจิกายน' })).toBeVisible()

  await tab(page, '3 วัน').click()
  await page.getByRole('button', { name: 'ถัดไป 3 วัน' }).click()
  await expect(page.locator('.grange')).toHaveText('11 ถึง 13 ต.ค.')
  await layoutTab(page, 'รายการ').click()
  await layoutTab(page, 'ปฏิทิน').click()
  await expect(page.locator('.grange')).toHaveText('11 ถึง 13 ต.ค.')

  await layoutTab(page, 'Timeline').click()
  await page.getByRole('button', { name: 'ขยาย ดูรายวัน' }).click()
  await page.getByRole('button', { name: 'ขยาย ดูรายวัน' }).click()
  await page.getByRole('button', { name: 'ถัดไป 7 วัน' }).click()
  await layoutTab(page, 'Matrix').click()
  await layoutTab(page, 'Timeline').click()
  await expect(page.locator('.grange')).toHaveText('14 ถึง 20 ต.ค.')
})

test('the mind map keeps its chosen branch when it is left and opened again', async ({ page }) => {
  await projects(page)
  await page.locator('.pj-card', { hasText: 'peddose' }).click()
  await page.getByRole('button', { name: /Mind map ของโปรเจกต์/ }).click()
  await page.locator('.pj-node', { hasText: 'แอป' }).click()
  await expect(page.getByRole('region', { name: 'กิ่งที่เลือก' })).toContainText('#peddose/แอป')
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await page.getByRole('button', { name: /Mind map ของโปรเจกต์/ }).click()
  await expect(page.locator('.pj-node', { hasText: 'แอป' })).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByRole('region', { name: 'กิ่งที่เลือก' })).toContainText('#peddose/แอป')
})
