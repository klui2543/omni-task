import { expect, test, type Page } from '@playwright/test'

import { FakeDrive } from './fakeGoogle'
import { at, open } from './helpers'

// Two projects (peddose with branches and a chain of waits), a task in the Bucket list by tag, and two list notes.
const NOTE = [
  '- [ ] ส่งรายงานความก้าวหน้า #peddose 🆔 aa ⏫ ➕ 2026-10-01 📅 2026-10-07',
  '- [ ] เตรียมสไลด์ประชุมทีม #peddose 🆔 bb ⛔ aa 📅 2026-10-09',
  '- [ ] เขียน draft บทนำ #peddose 🆔 cc ⛔ bb',
  '- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ',
  '- [ ] ลองหน้าเว็บ #peddose/แอป/เว็บ',
  '- [ ] ทำหน้าคำนวณ #peddose/แอป/มือถือ',
  '- [x] สรุป requirement #peddose/research ✅ 2026-10-03',
  '- [ ] ทบทวนเคสก่อนราวด์ #Siriraj 📅 2026-10-08',
  '- [x] ส่งเวร #Siriraj ✅ 2026-10-06',
  '- [ ] จองตั๋วไปเชียงใหม่ #บ้าน #bucketlist #เที่ยว',
  '- [ ] ซื้อนม',
  '',
].join('\n')

const BUCKET =
  '---\nomni-list: true\nicon: 🏔️\ncategories: เที่ยว, ประสบการณ์, เรียนรู้\ntag: bucketlist\n---\n# Bucket list\n\n' +
  '- [ ] ไปดูแสงเหนือ #bucketlist #เที่ยว\n- [ ] วิ่งฮาล์ฟมาราธอน #bucketlist #ประสบการณ์\n- [x] ปีนภูกระดึง #bucketlist #เที่ยว ✅ 2026-10-01\n'
const WATCH = '---\nomni-list: true\nicon: 🎬\ncategories: หนัง, ซีรีส์\ntag: watchlist\n---\n# Watch list\n\n- [ ] Shogun #watchlist #ซีรีส์\n'

async function start(page: Page, opts: { lists?: boolean; note?: string } = {}) {
  const ctx = await open(page, { now: at(9, 20), note: opts.note ?? NOTE })
  const bucket = opts.lists === false ? null : ctx.drive.omniNote('Bucket list.md', BUCKET)
  const watch = opts.lists === false ? null : ctx.drive.omniNote('Watch list.md', WATCH)
  await page.evaluate(() => { location.hash = '/projects' })
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()
  return { ...ctx, bucket, watch }
}

const writes = (drive: FakeDrive) => drive.requests.filter((r) => r.startsWith('PATCH') || r.startsWith('POST')).length
const stored = (page: Page) => page.evaluate(() => JSON.parse(localStorage.getItem('omni.projects') ?? '{}'))

test('shows each project with its progress, next task and what is late or blocked, and the lists under them', async ({ page }) => {
  await start(page)
  const peddose = page.locator('.pj-card', { hasText: 'peddose' })
  await expect(peddose).toContainText('เสร็จ 1 จาก 7 งาน')
  await expect(peddose.getByRole('img', { name: 'เสร็จ 14%' })).toBeVisible()
  await expect(peddose).toContainText('ถัดไป: ส่งรายงานความก้าวหน้า')
  await expect(peddose).toContainText('เลยกำหนด 1')
  await expect(peddose).toContainText('ติดรองานอื่น 2')
  await expect(page.locator('.pj-card', { hasText: 'Siriraj' })).toContainText('เสร็จ 1 จาก 2 งาน')
  // The list's tag and its categories never become projects.
  await expect(page.locator('.pj-card', { hasText: 'bucketlist' })).toHaveCount(0)
  await expect(page.locator('.pj-card', { hasText: 'เที่ยว' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /Bucket list/ })).toContainText('ทำแล้ว 1 จาก 4')
  await expect(page.getByRole('button', { name: /Watch list/ })).toContainText('ทำแล้ว 0 จาก 1')
  await expect(page.getByRole('button', { name: '+ สร้างรายการใหม่' })).toBeVisible()
})

test('starring and arranging stay on this device and write nothing to the vault', async ({ page }) => {
  const { drive } = await start(page)
  const before = writes(drive)
  const names = () => page.locator('.pj-name').allTextContents()
  expect(await names()).toEqual(['peddose', 'Siriraj', 'บ้าน'])

  await page.getByRole('button', { name: 'ติดดาว บ้าน' }).click()
  expect(await names()).toEqual(['บ้าน', 'peddose', 'Siriraj'])
  await expect(page.getByRole('button', { name: 'เอาดาวออก บ้าน' })).toHaveAttribute('aria-pressed', 'true')

  // Arrange: the handle moves a project with the arrow keys (or by dragging).
  await page.getByRole('button', { name: 'จัดลำดับ' }).click()
  await expect(page.getByRole('button', { name: 'เสร็จ', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '+ สร้างรายการใหม่' })).toHaveCount(0)
  await page.getByRole('button', { name: 'ลากเพื่อย้าย Siriraj' }).focus()
  await page.keyboard.press('ArrowUp')
  await page.getByRole('button', { name: 'เสร็จ', exact: true }).click()
  expect(await names()).toEqual(['บ้าน', 'Siriraj', 'peddose'])

  const local = await stored(page)
  expect(local.starred).toEqual(['บ้าน'])
  expect(local.order.slice(0, 2)).toEqual(['บ้าน', 'Siriraj'])
  expect(writes(drive)).toBe(before)
  expect(drive.nodes.some((n) => n.name.includes('omni-settings'))).toBe(false)

  await page.reload()
  await expect(page.locator('.pj-name').first()).toHaveText('บ้าน')
})

test('arranging also works by dragging a handle', async ({ page }) => {
  await start(page)
  await page.getByRole('button', { name: 'จัดลำดับ' }).click()
  const handle = page.getByRole('button', { name: 'ลากเพื่อย้าย peddose' })
  const box = (await handle.boundingBox())!
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  for (let i = 1; i <= 12; i++) await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2 + i * 14)
  await page.mouse.up()
  expect(await page.locator('.pj-name').allTextContents()).not.toEqual(['peddose', 'Siriraj', 'บ้าน'])
  expect((await page.locator('.pj-name').allTextContents())[0]).not.toBe('peddose')
})

test('the overview orders open work, says why a task waits and ticking writes the note', async ({ page }) => {
  const { file } = await start(page)
  await page.getByRole('button', { name: /peddose/ }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: 'peddose' })).toBeVisible()
  await expect(page.locator('.pj-stat', { hasText: 'เสร็จแล้ว' })).toContainText('14%')
  await expect(page.locator('.pj-stat', { hasText: 'เลยกำหนด' })).toContainText('1')
  await expect(page.locator('.pj-stat', { hasText: 'ติดรองานอื่น' })).toContainText('2')
  await expect(page.getByRole('button', { name: /Mind map ของโปรเจกต์/ })).toContainText('กำลังทำ 4')

  const rows = page.locator('.pj-order:not(.done)')
  await expect(rows).toHaveCount(6)
  await expect(rows.first()).toContainText('ส่งรายงานความก้าวหน้า')
  await expect(rows.first()).toContainText('ทำถัดไป, 7 ต.ค.')
  await expect(rows.nth(1)).toContainText('รอ ส่งรายงานความก้าวหน้า')
  await expect(rows.nth(1)).toContainText('ล็อก')
  await expect(page.locator('.pj-section', { hasText: 'เสร็จแล้ว' })).toContainText('สรุป requirement')
  await expect(page.locator('.pj-section', { hasText: 'เสร็จแล้ว' })).toContainText('3 ต.ค.')

  // Ticking the first task unlocks the one waiting for it.
  await page.getByRole('checkbox', { name: 'ติ๊กเสร็จ ส่งรายงานความก้าวหน้า' }).click()
  await expect.poll(() => file.text).toContain('- [x] ส่งรายงานความก้าวหน้า #peddose 🆔 aa ⏫ ➕ 2026-10-01 📅 2026-10-07 ✅ 2026-10-08')
  await expect(page.locator('.pj-order:not(.done)').first()).toContainText('เตรียมสไลด์ประชุมทีม')
  await expect(page.locator('.pj-order:not(.done)').first()).not.toContainText('ล็อก')
})

test('the order of tasks is the owner own, kept on this device, and tapping a task opens the edit pane', async ({ page }) => {
  const { drive } = await start(page)
  await page.getByRole('button', { name: /peddose/ }).first().click()
  const before = writes(drive)
  const titles = () => page.locator('.pj-order:not(.done) .ttitle').allTextContents()
  expect((await titles())[0]).toBe('ส่งรายงานความก้าวหน้า')
  await page.getByRole('button', { name: 'ลากเพื่อเรียง ทำหน้าเว็บ' }).focus()
  for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowUp')
  expect((await titles())[0]).toBe('ทำหน้าเว็บ')
  expect((await stored(page)).taskOrder.peddose[0]).toBe('ทำหน้าเว็บ')
  // Not strict: nothing is written to the vault for the order.
  expect(writes(drive)).toBe(before)

  await page.locator('.pj-order-body', { hasText: 'ทำหน้าเว็บ' }).click()
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' })).toContainText('ทำหน้าเว็บ')
})

test('"do in order" writes ids and waits into the note, and turning it off takes the waits back', async ({ page }) => {
  const { file } = await start(page)
  await page.getByRole('button', { name: /peddose/ }).first().click()
  await page.getByRole('switch', { name: 'ต้องทำตามลำดับ' }).click()
  await expect(page.getByRole('switch', { name: 'ต้องทำตามลำดับ' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByText('งานถัดไปรองานก่อนหน้า')).toBeVisible()
  // Each task waits for the one before it; the second one now waits for the first one's id (aa).
  await expect.poll(() => file.text).toMatch(/- \[ \] ทำหน้าเว็บ #peddose\/แอป\/เว็บ.*🆔 \S+ ⛔ \S+/)
  expect(file.text).toContain('- [ ] เตรียมสไลด์ประชุมทีม #peddose 📅 2026-10-09 🆔 bb ⛔ aa')
  const rows = page.locator('.pj-order:not(.done)')
  await expect(rows.nth(3)).toContainText('รองานข้อ 3 ก่อน')

  await page.getByRole('switch', { name: 'ต้องทำตามลำดับ' }).click()
  await expect.poll(() => file.text!.includes('⛔')).toBe(false)
  await expect(page.getByText('ปิดอยู่: ลำดับเป็นแค่คำแนะนำ')).toBeVisible()
})

test('renaming a project shows what changes, then rewrites every line and keeps the device choices', async ({ page }) => {
  const { file } = await start(page)
  await page.getByRole('button', { name: 'ติดดาว peddose' }).click()
  await page.getByRole('button', { name: /peddose/ }).first().click()
  await page.getByRole('button', { name: 'เปลี่ยนชื่อโปรเจกต์' }).click()
  const dialog = page.getByRole('dialog', { name: 'เปลี่ยนชื่อโปรเจกต์' })
  await expect(dialog.getByRole('button', { name: 'เปลี่ยนชื่อ' })).toBeDisabled()
  await dialog.getByLabel('ชื่อใหม่').fill('peddose app')
  await expect(dialog).toContainText('#peddose-app/…')
  await expect(dialog).toContainText('แก้ 7 บรรทัดงานใน 1 ไฟล์')
  await dialog.getByLabel('ชื่อใหม่').fill('a/b')
  await expect(dialog).toContainText('ชื่อโปรเจกต์มี / ไม่ได้')
  await expect(dialog.getByRole('button', { name: 'เปลี่ยนชื่อ' })).toBeDisabled()
  await dialog.getByLabel('ชื่อใหม่').fill('peddose app')
  await dialog.getByRole('button', { name: 'เปลี่ยนชื่อ' }).click()

  await expect(page.getByRole('heading', { level: 1, name: 'peddose-app' })).toBeVisible()
  await expect(page.getByText('เปลี่ยนชื่อเป็น #peddose-app แล้ว (7 บรรทัด)')).toBeVisible()
  expect(file.text).toContain('- [ ] ทำหน้าเว็บ #peddose-app/แอป/เว็บ')
  expect(file.text).toContain('- [x] สรุป requirement #peddose-app/research ✅ 2026-10-03')
  expect(file.text).not.toContain('#peddose ')
  expect(file.text).toContain('#Siriraj')
  expect((await stored(page)).starred).toEqual(['peddose-app'])
})

test('the mind map shows branches and tasks, sets states, adds, renames and deletes branches', async ({ page }) => {
  const { file } = await start(page)
  await page.getByRole('button', { name: /peddose/ }).first().click()
  await page.getByRole('button', { name: /Mind map ของโปรเจกต์/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: '#peddose' })).toBeVisible()
  const node = (name: string) => page.locator('.pj-node', { hasText: name })
  for (const n of ['research', 'แอป', 'เว็บ', 'มือถือ']) await expect(node(n).first()).toBeVisible()
  await expect(page.locator('.pj-mtask', { hasText: 'ลองหน้าเว็บ' })).toBeVisible()
  await expect(page.locator('.pj-edges path').first()).toBeVisible()

  // A branch: pick it, then its panel offers the states.
  await node('เว็บ').click()
  const panel = page.getByRole('region', { name: 'กิ่งที่เลือก' })
  await expect(panel).toContainText('#peddose/แอป/เว็บ')
  await panel.getByRole('button', { name: 'กำลังลอง' }).click()
  await expect(panel.getByRole('button', { name: 'กำลังลอง' })).toHaveAttribute('aria-pressed', 'true')
  await expect(panel).toContainText('เลือกแล้ว ทางที่กำลังลองข้างกันจะพับเก็บ')
  await node('มือถือ').click()
  await panel.getByRole('button', { name: 'กำลังลอง' }).click()
  // Choosing one parks the sibling still being tried.
  await node('เว็บ').click()
  await panel.getByRole('button', { name: 'เลือกแล้ว' }).click()
  await node('มือถือ').click()
  await expect(panel.getByRole('button', { name: 'พับเก็บ' })).toHaveAttribute('aria-pressed', 'true')
  await expect(panel).toContainText('พับเก็บ: งานซ่อนจากโฟกัส')
  const local = await stored(page)
  expect(local.branches).toContain('peddose\tแอป/เว็บ\tCHOSEN')
  expect(local.branches).toContain('peddose\tแอป/มือถือ\tPARKED')

  // A new empty branch under the project, then a task in it.
  await node('#peddose').click()
  await panel.getByRole('button', { name: '+ กิ่ง' }).click()
  await page.getByRole('dialog', { name: 'กิ่งใหม่ใต้ peddose' }).getByLabel('พิมพ์ชื่อ').fill('ทุน วิจัย')
  await page.getByRole('dialog').getByRole('button', { name: 'ตกลง' }).click()
  await expect(node('ทุน-วิจัย')).toBeVisible()
  await node('ทุน-วิจัย').click()
  await panel.getByRole('button', { name: '+ งาน' }).click()
  await page.getByRole('dialog', { name: 'งานใหม่ใน ทุน-วิจัย' }).getByLabel('พิมพ์ชื่อ').fill('ขอทุน พรุ่งนี้')
  await page.getByRole('dialog').getByRole('button', { name: 'ตกลง' }).click()
  await expect.poll(() => file.text).toMatch(/- \[ \] ขอทุน .*#peddose\/ทุน-วิจัย.*2026-10-09/)
  await expect(page.locator('.pj-mtask', { hasText: 'ขอทุน' })).toBeVisible()

  // Tapping a task ticks it; its branch counts it.
  await page.locator('.pj-mtask', { hasText: 'ขอทุน' }).click()
  await expect.poll(() => file.text).toMatch(/- \[x\] ขอทุน .*✅ 2026-10-08/)

  // A right click (a long press on a touch screen) opens the task instead.
  await page.locator('.pj-mtask', { hasText: 'ลองหน้าเว็บ' }).click({ button: 'right' })
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' })).toContainText('ลองหน้าเว็บ')
  expect(file.text).toContain('- [ ] ลองหน้าเว็บ')
  await page.getByRole('dialog', { name: 'แก้ไขงาน' }).getByRole('button', { name: 'ปิด' }).click()

  // Renaming a branch rewrites the tags of every line under it and moves its state along.
  await node('เว็บ').click()
  await panel.getByRole('button', { name: 'เปลี่ยนชื่อ' }).click()
  await page.getByRole('dialog', { name: 'ชื่อใหม่ของ เว็บ' }).getByLabel('พิมพ์ชื่อ').fill('ไซต์')
  await page.getByRole('dialog').getByRole('button', { name: 'ตกลง' }).click()
  await expect(node('ไซต์')).toBeVisible()
  await expect.poll(() => file.text).toContain('- [ ] ทำหน้าเว็บ #peddose/แอป/ไซต์')
  expect(file.text).toContain('- [ ] ลองหน้าเว็บ #peddose/แอป/ไซต์')
  await expect.poll(async () => (await stored(page)).branches).toContain('peddose\tแอป/ไซต์\tCHOSEN')

  // A branch with tasks cannot be deleted; an empty one can.
  await expect(panel.getByRole('button', { name: 'ลบกิ่ง' })).toHaveCount(0)
  await node('#peddose').click()
  await panel.getByRole('button', { name: '+ กิ่ง' }).click()
  await page.getByRole('dialog').getByLabel('พิมพ์ชื่อ').fill('ว่าง')
  await page.getByRole('dialog').getByRole('button', { name: 'ตกลง' }).click()
  await node('ว่าง').click()
  await panel.getByRole('button', { name: 'ลบกิ่ง' }).click()
  await expect(node('ว่าง')).toHaveCount(0)
})

test('the mind map zooms with its buttons and fits the whole map again', async ({ page }) => {
  await start(page)
  await page.getByRole('button', { name: /peddose/ }).first().click()
  await page.getByRole('button', { name: /Mind map ของโปรเจกต์/ }).click()
  const layer = page.getByTestId('map-layer')
  const scale = async () => (await layer.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).a))
  await expect(layer).toBeVisible()
  const fitted = await scale()
  await page.getByRole('button', { name: 'ซูมเข้า' }).click()
  expect(await scale()).toBeGreaterThan(fitted)
  await page.getByRole('button', { name: 'ซูมออก' }).click()
  await page.getByRole('button', { name: 'ซูมออก' }).click()
  expect(await scale()).toBeLessThan(fitted)
  await page.getByRole('button', { name: 'พอดีจอ' }).click()
  expect(Math.abs((await scale()) - fitted)).toBeLessThan(0.01)

  // Dragging the map moves it.
  const map = page.locator('.pj-map')
  const box = (await map.boundingBox())!
  const x0 = (await layer.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).e))
  await page.mouse.move(box.x + 40, box.y + 40)
  await page.mouse.down()
  await page.mouse.move(box.x + 120, box.y + 70, { steps: 5 })
  await page.mouse.up()
  expect((await layer.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).e)) - x0).toBeGreaterThan(40)
  // Back goes to the overview, then to the projects.
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'peddose' })).toBeVisible()
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()
})

test('a list shows its items with categories, ticks into its own note and adds new ones', async ({ page }) => {
  const { bucket } = await start(page)
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Bucket list' })).toBeVisible()
  const items = page.locator('.pj-item')
  // Open first, done after; the task tagged in TaskForge says where it lives.
  await expect(items).toHaveCount(4)
  await expect(items.first()).toContainText('จองตั๋วไปเชียงใหม่')
  await expect(items.first()).toContainText('เที่ยว, TaskForge')
  await expect(items.last()).toContainText('ปีนภูกระดึง')
  await expect(page.getByText('เก็บใน Omni/Bucket list.md และงานที่ติด #bucketlist')).toBeVisible()

  await page.getByRole('button', { name: 'เรียนรู้' }).click()
  await expect(items).toHaveCount(0)
  await expect(page.getByText('ยังว่างอยู่ เพิ่มสิ่งแรกได้เลย')).toBeVisible()
  await page.getByRole('button', { name: 'เที่ยว', exact: true }).click()
  await expect(items).toHaveCount(3)
  await expect(page.getByLabel('เพิ่มรายการ')).toHaveAttribute('placeholder', 'เพิ่มใน เที่ยว')

  // A new item goes to the end of the list note with the list's tag, the category and today's date.
  await page.getByLabel('เพิ่มรายการ').fill('ไปเชียงราย')
  await page.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => bucket!.text).toContain('- [ ] ไปเชียงราย #bucketlist #เที่ยว ➕ 2026-10-08')
  await expect(items.filter({ hasText: 'ไปเชียงราย' })).toBeVisible()
  expect(bucket!.text).toContain('# Bucket list')

  // Tapping an item ticks it, in the note it lives in.
  await page.getByRole('checkbox', { name: 'ติ๊ก ไปดูแสงเหนือ' }).click()
  await expect.poll(() => bucket!.text).toContain('- [x] ไปดูแสงเหนือ #bucketlist #เที่ยว ✅ 2026-10-08')
  await expect(page.getByRole('checkbox', { name: 'ยกเลิกการติ๊ก ไปดูแสงเหนือ' })).toBeVisible()
})

test('an item of a list opens the edit pane, and edits and deletes go to the list note', async ({ page }) => {
  const { bucket } = await start(page)
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await page.getByRole('button', { name: 'แก้ วิ่งฮาล์ฟมาราธอน' }).click()
  const pane = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  await expect(pane).toContainText('วิ่งฮาล์ฟมาราธอน')
  await pane.getByRole('button', { name: /ความสำคัญ/ }).click()
  await pane.getByRole('button', { name: 'สูง', exact: true }).click()
  await expect.poll(() => bucket!.text).toContain('- [ ] วิ่งฮาล์ฟมาราธอน #bucketlist #ประสบการณ์ ⏫')
  await pane.getByRole('button', { name: 'ลบงาน' }).click()
  await pane.getByRole('button', { name: 'ลบ', exact: true }).click()
  await expect.poll(() => bucket!.text).not.toContain('วิ่งฮาล์ฟมาราธอน')
  // The bar offers to put it back, into the same note.
  await page.getByRole('button', { name: 'เลิกทำ' }).click()
  await expect.poll(() => bucket!.text).toContain('วิ่งฮาล์ฟมาราธอน')
})

test('pulling existing tasks into a list tags them where they are', async ({ page }) => {
  const { file } = await start(page)
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await page.getByRole('button', { name: 'ดึงงานที่มีอยู่แล้วเข้ามา' }).click()
  const dialog = page.getByRole('dialog', { name: 'ดึงงานเข้า Bucket list' })
  await expect(dialog).toContainText('งานยังอยู่ที่เดิม แค่ติด #bucketlist เพิ่ม')
  await expect(dialog.getByRole('button', { name: 'เลือกงานที่จะเพิ่ม' })).toBeDisabled()
  // Already in the list: not offered.
  await expect(dialog.getByText('จองตั๋วไปเชียงใหม่')).toHaveCount(0)
  await dialog.getByLabel('ค้นชื่องานหรือ #แท็ก').fill('#siriraj')
  await expect(dialog.getByRole('checkbox')).toHaveCount(1)
  await dialog.getByLabel('ค้นชื่องานหรือ #แท็ก').fill('')
  await dialog.getByRole('button', { name: 'เที่ยว', exact: true }).click()
  await dialog.getByRole('checkbox', { name: /ซื้อนม/ }).click()
  await dialog.getByRole('checkbox', { name: /ทบทวนเคสก่อนราวด์/ }).click()
  await dialog.getByRole('button', { name: 'เพิ่ม 2 งาน' }).click()
  await expect.poll(() => file.text).toContain('- [ ] ซื้อนม #bucketlist #เที่ยว')
  expect(file.text).toContain('- [ ] ทบทวนเคสก่อนราวด์ #Siriraj #bucketlist #เที่ยว 📅 2026-10-08')
  await expect(page.getByText('เพิ่ม 2 งานเข้า Bucket list แล้ว')).toBeVisible()
  await expect(page.locator('.pj-item', { hasText: 'ซื้อนม' })).toContainText('เที่ยว, TaskForge')
})

test('a list gets a new icon, and a new list is made with its categories', async ({ page }) => {
  const { drive, bucket } = await start(page)
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await page.getByRole('button', { name: 'เปลี่ยนไอคอน' }).click()
  const picker = page.getByRole('dialog', { name: 'เลือกไอคอน' })
  await picker.getByRole('button', { name: '✈️' }).click()
  await expect.poll(() => bucket!.text).toContain('icon: ✈️\ncategories: เที่ยว, ประสบการณ์, เรียนรู้\ntag: bucketlist')
  expect(bucket!.text).toContain('- [ ] ไปดูแสงเหนือ #bucketlist #เที่ยว')
  await expect(page.getByRole('button', { name: 'เปลี่ยนไอคอน' })).toHaveText('✈️')

  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await page.getByRole('button', { name: '+ สร้างรายการใหม่' }).click()
  const dialog = page.getByRole('dialog', { name: 'สร้างรายการใหม่' })
  await expect(dialog.getByRole('button', { name: 'สร้าง', exact: true })).toBeDisabled()
  await dialog.getByLabel('ชื่อรายการ').fill('หนังสือที่อยากอ่าน')
  await dialog.getByRole('button', { name: '📚' }).click()
  await dialog.getByLabel('หมวดย่อย').fill('นิยาย, ธุรกิจ, ')
  await dialog.getByRole('button', { name: 'สร้าง', exact: true }).click()
  await expect(page.getByRole('button', { name: /หนังสือที่อยากอ่าน/ })).toContainText('ทำแล้ว 0 จาก 0')
  expect(drive.textOf('หนังสือที่อยากอ่าน.md')).toBe('---\nomni-list: true\nicon: 📚\ncategories: นิยาย, ธุรกิจ\ntag: หนังสือที่อยากอ่าน\n---\n# หนังสือที่อยากอ่าน\n\n')

  // The same name again is refused, and the first note is left alone.
  await page.getByRole('button', { name: '+ สร้างรายการใหม่' }).click()
  await page.getByLabel('ชื่อรายการ').fill('หนังสือที่อยากอ่าน')
  await page.getByRole('dialog').getByRole('button', { name: 'สร้าง', exact: true }).click()
  await expect(page.getByText('มีรายการชื่อนี้แล้ว')).toBeVisible()
})

test('the first time with no lists in the vault, the two starter lists are written', async ({ page }) => {
  const { drive } = await start(page, { lists: false })
  await expect(page.getByRole('button', { name: /Bucket list/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /Watch list/ })).toBeVisible()
  expect(drive.textOf('Bucket list.md')).toContain('omni-list: true\nicon: 🏔️\ncategories: เที่ยว, ประสบการณ์, เรียนรู้')
  expect(drive.nodes.filter((n) => n.name === 'Omni')).toHaveLength(1)
  // Deleted on purpose later, they do not come back on this device.
  const count = drive.nodes.length
  await page.reload()
  await expect(page.getByRole('button', { name: /Bucket list/ })).toBeVisible()
  expect(drive.nodes.length).toBe(count)
})

test('a change made to a list note meanwhile is kept, not overwritten', async ({ page }) => {
  const { drive, bucket } = await start(page)
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await page.getByLabel('เพิ่มรายการ').fill('ไปเชียงราย')
  // Somebody else (Obsidian) adds a line right after the app reads the note.
  drive.afterNextRead = (n) => { n.text += '- [ ] เพิ่มจาก Obsidian #bucketlist\n'; n.version++ }
  await page.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => bucket!.text).toContain('ไปเชียงราย')
  expect(bucket!.text).toContain('เพิ่มจาก Obsidian')
})

test('a project has its own Kanban, Matrix, Gantt and calendar over just its tasks', async ({ page }) => {
  await start(page)
  await page.locator('.pj-card', { hasText: 'peddose' }).click()
  const tabs = page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' })
  await expect(tabs.getByRole('tab', { name: 'ภาพรวม' })).toHaveAttribute('aria-selected', 'true')
  await tabs.getByRole('tab', { name: 'Kanban' }).click()

  // Only peddose work, branches included; the other projects' tasks stay out.
  await expect(page.getByRole('heading', { level: 1, name: 'peddose' })).toBeVisible()
  await expect(page.getByText('ส่งรายงานความก้าวหน้า').first()).toBeVisible()
  await expect(page.getByText('ทำหน้าเว็บ', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('ทบทวนเคสก่อนราวด์')).toHaveCount(0)
  await expect(page.getByText('ซื้อนม')).toHaveCount(0)

  await page.getByRole('tab', { name: 'Matrix' }).click()
  await expect(page.getByText('ทบทวนเคสก่อนราวด์')).toHaveCount(0)
  await page.getByRole('tab', { name: 'ภาพรวม' }).click()
  await expect(page.getByRole('tablist', { name: 'มุมมองของโปรเจกต์' })).toBeVisible()
})
