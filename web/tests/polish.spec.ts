import { expect, test } from '@playwright/test'

import { NOTE, open } from './helpers'

// Small matches with Android found while comparing the pages.

test('the daily sweep says how many finished tasks it moved to the archive, as Android does', async ({ page }) => {
  const note = NOTE + '- [x] เก่ามาก ✅ 2026-09-01\n- [x] เก่ามากอีกงาน ✅ 2026-09-02\n'
  const { file } = await open(page, { note })
  await expect(page.getByRole('alert').filter({ hasText: 'ย้ายงานที่เสร็จ 2 งานเข้าคลังแล้ว' })).toBeVisible()
  expect(file.text).not.toContain('เก่ามาก')
})

test('a sweep that moves a single task says so too', async ({ page }) => {
  const { file } = await open(page, { note: NOTE + '- [x] เก่ามาก ✅ 2026-09-01\n' })
  await expect(page.getByRole('alert').filter({ hasText: 'ย้ายงานที่เสร็จ 1 งานเข้าคลังแล้ว' })).toBeVisible()
  expect(file.text).not.toContain('เก่ามาก')
})

test('with nothing old enough to move the sweep stays quiet', async ({ page }) => {
  await open(page)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('changing the archive days sweeps at once and says how many moved', async ({ page }) => {
  // Done 20 days ago: kept by the default 30 days, moved when the choice becomes 14.
  const note = NOTE + '- [x] เก่าพอควร ✅ 2026-09-20\n'
  await page.addInitScript(() => localStorage.setItem('omni.archiveDays', '30'))
  const { file } = await open(page, { note })
  expect(file.text).toContain('เก่าพอควร')
  await page.evaluate(() => { location.hash = '/settings' })
  await page.getByRole('radio', { name: '14 วัน' }).click()
  await expect(page.getByRole('alert').filter({ hasText: 'ย้ายงานที่เสร็จ 1 งานเข้าคลังแล้ว' })).toBeVisible()
  expect(file.text).not.toContain('เก่าพอควร')
})

test('the Tasks filter sheet has the "hide done" switch too, shared with Views', async ({ page }) => {
  await open(page)
  await page.evaluate(() => { location.hash = '/tasks' })
  await page.getByRole('button', { name: 'กรอง', exact: true }).click()
  const sw = page.getByRole('dialog', { name: 'กรอง' }).getByRole('switch', { name: 'ซ่อนงานที่เสร็จและยกเลิก' })
  await expect(sw).toBeChecked()
  await expect(page.getByRole('dialog', { name: 'กรอง' })).toContainText('ในมุมมอง Kanban, Matrix, Gantt และปฏิทิน')
  await sw.uncheck()
  expect(await page.evaluate(() => localStorage.getItem('omni.hideDone'))).toBe('no')

  // The Views page reads the same choice: with it off, Kanban's Done column is open.
  await page.evaluate(() => { localStorage.setItem('omni.viewMode', 'kanban'); location.hash = '/views' })
  await expect(page.getByRole('heading', { name: 'มุมมอง', level: 1 })).toBeVisible()
  await expect(page.getByRole('region', { name: 'เสร็จ', exact: true })).toBeVisible()
})

test('an item opened from a list names the list note in the edit pane', async ({ page }) => {
  const { drive } = await open(page)
  drive.omniNote('Bucket list.md', '---\nomni-list: true\nicon: 🏔️\ncategories: เที่ยว\ntag: bucketlist\n---\n# Bucket list\n\n- [ ] ไปดูแสงเหนือ #bucketlist #เที่ยว\n')
  await page.evaluate(() => { location.hash = '/projects' })
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await page.getByRole('button', { name: 'แก้ ไปดูแสงเหนือ' }).click()
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' })).toContainText('Bucket list.md บรรทัด')
})
