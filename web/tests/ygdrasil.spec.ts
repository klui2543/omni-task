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

  // Ticking a task on the tree writes it.
  await task(page, 'ทำหน้าเว็บ').click()
  await expect.poll(() => file.text).toContain('- [x] ทำหน้าเว็บ #peddose/แอป/เว็บ ✅ 2026-10-08')

  await limb(page, '#Siriraj').click()
  await panel.getByRole('button', { name: 'เปิด Mind map' }).click()
  await expect(page.getByRole('heading', { level: 1, name: '#Siriraj' })).toBeVisible()
})

test('the tree zooms with its buttons and fits again', async ({ page }) => {
  await start(page)
  const layer = page.getByTestId('yg-layer')
  const scale = async () => (await layer.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).a))
  const fitted = await scale()
  await page.getByRole('button', { name: 'ซูมเข้า' }).click()
  expect(await scale()).toBeGreaterThan(fitted)
  await page.getByRole('button', { name: 'พอดีจอ' }).click()
  expect(Math.abs((await scale()) - fitted)).toBeLessThan(0.01)
})
