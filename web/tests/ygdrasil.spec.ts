import { expect, test, type Page } from '@playwright/test'

import { at, open } from './helpers'

// Two projects tied across by a wait (Siriraj waits for peddose), branches in peddose, and a task with no project.
const NOTE = [
  '- [ ] ส่งรายงานความก้าวหน้า #peddose 🆔 aa ⏫ 📅 2026-10-07',
  '- [ ] เตรียมสไลด์ประชุมทีม #peddose ⛔ aa 📅 2026-10-09',
  '- [ ] ทำหน้าเว็บ #peddose/แอป/เว็บ',
  '- [ ] ทำหน้าคำนวณ #peddose/แอป/มือถือ',
  '- [x] สรุป requirement #peddose/research ✅ 2026-10-03',
  '- [ ] ทบทวนเคสก่อนราวด์ #Siriraj ⛔ aa 📅 2026-10-10',
  '- [ ] ซื้อนม',
  '',
].join('\n')

async function start(page: Page) {
  const ctx = await open(page, { now: at(9, 20), note: NOTE })
  await page.evaluate(() => { location.hash = '/projects' })
  await expect(page.getByRole('heading', { level: 1, name: 'โปรเจกต์/ลิสต์' })).toBeVisible()
  await page.getByRole('button', { name: /Project Ygdrasil/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Project Ygdrasil' })).toBeVisible()
  return ctx
}

const limb = (page: Page, name: string) => page.locator('.pj-node', { hasText: name })
const task = (page: Page, title: string) => page.locator('.pj-mtask', { hasText: title })

test('every project grows from one trunk, with waits tied across projects', async ({ page }) => {
  await start(page)
  await expect(page.locator('.pj-node.yg-trunk')).toContainText('5')
  for (const n of ['#peddose', '#Siriraj', 'แอป', 'เว็บ', 'มือถือ', 'research']) await expect(limb(page, n).first()).toBeVisible()
  await expect(task(page, 'ทบทวนเคสก่อนราวด์')).toBeVisible()
  // Open tasks only, so the done one is not on the tree, and a task with no project has no world.
  await expect(task(page, 'สรุป requirement')).toHaveCount(0)
  await expect(task(page, 'ซื้อนม')).toHaveCount(0)
  await expect(page.locator('.yg-link.across')).toHaveCount(1)
  await expect(page.locator('.yg-link:not(.across)')).toHaveCount(1)
  await expect(page.getByRole('heading', { level: 1, name: 'Project Ygdrasil' }).locator('..')).toContainText('2 โปรเจกต์, 5 งาน, งานที่รอกัน 2 เส้น')
})

test('filters narrow the tree and stay for the next visit', async ({ page }) => {
  await start(page)
  await page.getByRole('button', { name: 'ตัวกรอง' }).click()
  const filters = page.getByRole('region', { name: 'ตัวกรอง' })
  await filters.getByRole('button', { name: 'เลยกำหนด' }).click()
  await expect(task(page, 'ส่งรายงานความก้าวหน้า')).toBeVisible()
  await expect(task(page, 'ทำหน้าเว็บ')).toHaveCount(0)
  await expect(limb(page, '#Siriraj')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'ตัวกรอง (1)' })).toBeVisible()

  await filters.getByRole('button', { name: 'ทุกวัน' }).click()
  await filters.getByRole('button', { name: 'ติดรองานอื่น' }).click()
  await expect(task(page, 'เตรียมสไลด์ประชุมทีม')).toBeVisible()
  await expect(task(page, 'ทบทวนเคสก่อนราวด์')).toBeVisible()
  await expect(task(page, 'ส่งรายงานความก้าวหน้า')).toHaveCount(0)

  await filters.getByRole('searchbox').fill('ราวด์')
  await expect(task(page, 'ทบทวนเคสก่อนราวด์')).toBeVisible()
  await expect(task(page, 'เตรียมสไลด์ประชุมทีม')).toHaveCount(0)

  // Back and in again: the filter is still there.
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await page.getByRole('button', { name: /Project Ygdrasil/ }).click()
  await expect(page.getByRole('button', { name: 'ตัวกรอง (2)' })).toBeVisible()
  await expect(task(page, 'เตรียมสไลด์ประชุมทีม')).toHaveCount(0)
  await page.getByRole('button', { name: 'ตัวกรอง (2)' }).click()
  await page.getByRole('button', { name: 'ล้างตัวกรอง' }).click()
  await expect(task(page, 'ทำหน้าเว็บ')).toBeVisible()
})

test('a world folds, takes a new task and opens its mind map', async ({ page }) => {
  const { file } = await start(page)
  const panel = page.getByRole('region', { name: 'กิ่งที่เลือก' })
  await limb(page, '#peddose').click()
  await expect(panel).toContainText('#peddose')
  await panel.getByRole('button', { name: 'พับ', exact: true }).click()
  await expect(task(page, 'ทำหน้าเว็บ')).toHaveCount(0)
  await expect(limb(page, '#peddose')).toContainText('+4')
  // The wait across now ends on the folded world.
  await expect(page.locator('.yg-link.across')).toHaveCount(1)
  await panel.getByRole('button', { name: 'กาง', exact: true }).click()
  await expect(task(page, 'ทำหน้าเว็บ')).toBeVisible()

  await limb(page, 'มือถือ').click()
  await panel.getByRole('button', { name: '+ งาน' }).click()
  await page.getByRole('dialog').getByRole('textbox').fill('ทดสอบบนมือถือ')
  await page.getByRole('dialog').getByRole('button', { name: 'ตกลง' }).click()
  await expect.poll(() => file.text).toContain('ทดสอบบนมือถือ #peddose/แอป/มือถือ')
  await expect(task(page, 'ทดสอบบนมือถือ')).toBeVisible()

  // Ticking a task's ring on the tree writes it.
  await page.getByRole('checkbox', { name: 'ติ๊กเสร็จ ทำหน้าเว็บ' }).click()
  await expect.poll(() => file.text).toContain('- [x] ทำหน้าเว็บ #peddose/แอป/เว็บ ✅ 2026-10-08')

  await limb(page, '#Siriraj').click()
  await panel.getByRole('button', { name: 'เปิด Mind map' }).click()
  await expect(page.getByRole('heading', { level: 1, name: '#Siriraj' })).toBeVisible()
})

test('the tree zooms with its buttons and fits again', async ({ page }) => {
  await start(page)
  const layer = page.getByTestId('yg-layer')
  const scale = async () => (await layer.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).a))
  const first = await scale()
  await page.getByRole('button', { name: 'ซูมเข้า' }).click()
  expect(await scale()).toBeGreaterThan(first)
  await page.getByRole('button', { name: 'พอดีจอ' }).click()
  expect(await scale()).toBeLessThanOrEqual(first + 0.01)
})

test('on a phone the tree opens readable from the trunk, and "fit" shows all of it', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await start(page)
  const layer = page.getByTestId('yg-layer')
  const scale = async () => (await layer.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).a))
  const map = (await page.locator('.pj-map').boundingBox())!
  expect(await scale()).toBeGreaterThanOrEqual(0.6)
  const trunk = (await page.locator('.pj-node.yg-trunk').boundingBox())!
  expect(trunk.x).toBeGreaterThanOrEqual(map.x)
  expect(trunk.y).toBeGreaterThanOrEqual(map.y)
  expect(trunk.y + trunk.height).toBeLessThanOrEqual(map.y + map.height)
  // The map takes the whole screen: the bottom bar and the round assistant button step aside, so nothing covers the panel.
  await expect(page.getByRole('button', { name: 'เปิดผู้ช่วย' })).toHaveCount(0)
  await expect(page.getByRole('navigation', { name: 'เมนูหลัก' })).toHaveCount(0)
  await page.getByRole('button', { name: 'พอดีจอ' }).click()
  expect(await scale()).toBeLessThan(0.6)
})

test('a task held and dragged onto another project moves there, tag and all', async ({ page }) => {
  const { file } = await start(page)
  const from = (await task(page, 'ทำหน้าเว็บ').boundingBox())!
  const to = (await limb(page, '#Siriraj').boundingBox())!
  await page.mouse.move(from.x + 60, from.y + from.height / 2)
  await page.mouse.down()
  await page.waitForTimeout(700)
  await expect(page.locator('.yg-ghost')).toContainText('ทำหน้าเว็บ')
  await page.mouse.move(to.x + 40, to.y + to.height / 2, { steps: 8 })
  await expect(limb(page, '#Siriraj')).toHaveClass(/yg-drop/)
  await expect(page.locator('.yg-ghost')).toContainText('ย้ายไป #Siriraj')
  await page.mouse.up()
  await expect.poll(() => file.text).toContain('- [ ] ทำหน้าเว็บ #Siriraj\n')
  await expect(page.getByRole('status')).toContainText('ย้าย "ทำหน้าเว็บ" ไป #Siriraj แล้ว')
  // Held and let go where it is, it opens instead, and nothing is ticked.
  const box = (await task(page, 'ทำหน้าคำนวณ').boundingBox())!
  await page.mouse.move(box.x + 60, box.y + box.height / 2)
  await page.mouse.down()
  await page.waitForTimeout(700)
  await page.mouse.up()
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' })).toBeVisible()
  expect(file.text).toContain('- [ ] ทำหน้าคำนวณ #peddose/แอป/มือถือ')
})

test('an edit shows at once on a slow connection, and is written after', async ({ page }) => {
  const { drive, file } = await start(page)
  drive.latency = 1500
  const writes = () => drive.requests.filter((r) => r.startsWith('PATCH')).length
  const before = writes()
  await page.getByRole('checkbox', { name: 'ติ๊กเสร็จ ทำหน้าเว็บ' }).click()
  // Ticked on the page (so it leaves the tree of open tasks) well before Drive has answered even the first read.
  await expect(task(page, 'ทำหน้าเว็บ')).toHaveCount(0, { timeout: 1000 })
  expect(writes()).toBe(before)
  await expect.poll(() => file.text, { timeout: 20_000 }).toContain('- [x] ทำหน้าเว็บ #peddose/แอป/เว็บ ✅ 2026-10-08')
})

test('a whole project held and dropped on another becomes its branch, and the trunk takes it back out', async ({ page }) => {
  const { file } = await start(page)
  const drag = async (from: string, to: import('@playwright/test').Locator) => {
    const a = (await limb(page, from).first().boundingBox())!
    const b = (await to.boundingBox())!
    await page.mouse.move(a.x + 30, a.y + a.height / 2)
    await page.mouse.down()
    await page.waitForTimeout(400)
    await page.mouse.move(b.x + 40, b.y + b.height / 2, { steps: 8 })
    await page.mouse.up()
  }
  await drag('#Siriraj', limb(page, '#peddose'))
  await expect.poll(() => file.text).toContain('- [ ] ทบทวนเคสก่อนราวด์ #peddose/Siriraj ⛔ aa 📅 2026-10-10')
  await expect(page.getByRole('status')).toContainText('ย้าย #Siriraj ไปไว้ใต้ #peddose แล้ว')
  await expect(limb(page, '#Siriraj')).toHaveCount(0)

  await drag('Siriraj', page.locator('.pj-node.yg-trunk'))
  await expect.poll(() => file.text).toContain('- [ ] ทบทวนเคสก่อนราวด์ #Siriraj ⛔ aa 📅 2026-10-10')
  await expect(limb(page, '#Siriraj')).toBeVisible()
})

test('a project opened from the tree comes back to the tree', async ({ page }) => {
  await start(page)
  await limb(page, '#peddose').click()
  await page.getByRole('region', { name: 'กิ่งที่เลือก' }).getByRole('button', { name: 'เปิด Mind map' }).click()
  await expect(page.getByRole('heading', { level: 1, name: '#peddose' })).toBeVisible()
  await page.getByRole('button', { name: '‹ กลับ' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Project Ygdrasil' })).toBeVisible()
})

test('a tap on a task card opens it; only its ring ticks it', async ({ page }) => {
  const { file } = await start(page)
  await task(page, 'ทำหน้าคำนวณ').getByText('ทำหน้าคำนวณ').click()
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' }).getByRole('heading', { name: 'ทำหน้าคำนวณ' })).toBeVisible()
  expect(file.text).toContain('- [ ] ทำหน้าคำนวณ #peddose/แอป/มือถือ')
})
