import { expect, test, type Page } from '@playwright/test'

import { Profile, allowFolders } from './assistantHelpers'
import { goTo, open } from './helpers'

// The stuck log, as Android's: a dated line under a long-open task says why it is still open. Today is 8 October 2026.
const NOTE = [
  '- [ ] ทำเว็บร้าน ➕ 2026-08-01 📅 2026-10-05',
  '    - ใช้ธีมเดิม',
  '    - ค้าง 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ',
  '    - [x] ได้รูปสินค้า ✅ 2026-09-20',
  '    - [ ] จัดหน้า',
  '- [ ] เขียนบทความ ➕ 2026-09-01',
  '- [ ] จองตั๋ว ➕ 2026-10-06 📅 2026-10-08',
  '',
].join('\n')

const openTask = (page: Page, title: string) => page.locator('.pbody', { hasText: title }).click()

test('Focus: a late task has a bar a week and when it last moved, and either writes why under it', async ({ page }) => {
  const { file } = await open(page, { note: NOTE })
  // The subtask finished 18 days ago is the last move; the note is grey, the subtask a tall bar.
  await expect(page.getByRole('button', { name: 'ติดอะไร: ทำเว็บร้าน' })).toHaveText('เลย 3 วัน, ขยับล่าสุด 18 วันก่อน, งานย่อย 1/2')
  const bars = page.getByRole('button', { name: 'ความเป็นไปของ ทำเว็บร้าน' })
  await expect(bars.locator('i')).toHaveCount(8)
  await expect(bars.locator('i.note')).toHaveCount(1)
  await expect(bars.locator('i.done')).toHaveCount(1)
  // A task due today and only two days old has neither.
  await expect(page.getByRole('button', { name: 'ความเป็นไปของ จองตั๋ว' })).toHaveCount(0)

  await bars.click()
  const dialog = page.getByRole('dialog', { name: 'ทำเว็บร้าน' })
  const path = dialog.getByRole('list', { name: 'ความเป็นไปของงาน' })
  await expect(path.getByRole('listitem')).toHaveText([/1 ส\.ค\.\s*เริ่มงาน/, /2 ก\.ย\.\s*รอคนอื่น: รอรูปสินค้าจากพี่เอ/, /20 ก\.ย\.\s*งานย่อยเสร็จ: ได้รูปสินค้า/, /วันนี้/])
  await expect(dialog.getByRole('button', { name: 'บันทึก', exact: true })).toBeDisabled()
  // Words first, then a pick: the pick goes in front of them.
  await dialog.getByLabel('ตอนนี้ติดอะไร').fill('งานด่วนแทรก')
  await dialog.getByRole('button', { name: 'ไม่มีเวลา' }).click()
  await expect(dialog.getByLabel('ตอนนี้ติดอะไร')).toHaveValue('ไม่มีเวลา: งานด่วนแทรก')
  await expect(dialog.getByRole('button', { name: 'ไม่มีเวลา' })).toHaveAttribute('aria-pressed', 'true')
  await dialog.getByRole('button', { name: 'บันทึก', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  // Under the task's own lines, before its subtasks.
  await expect.poll(() => file.text).toContain(
    '    - ค้าง 2026-09-02 รอคนอื่น: รอรูปสินค้าจากพี่เอ\n    - ค้าง 2026-10-08 ไม่มีเวลา: งานด่วนแทรก\n    - [x] ได้รูปสินค้า',
  )
  await expect(page.getByRole('button', { name: 'ติดอะไร: ทำเว็บร้าน' })).toContainText('ขยับล่าสุดวันนี้')
})

test('edit panel: how a late task is going up front, the log of every task, and a fold for the others', async ({ page }) => {
  const { file } = await open(page, { note: NOTE })
  await openTask(page, 'ทำเว็บร้าน')
  const panel = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  const block = panel.getByRole('region', { name: 'ความเป็นไป' })
  await expect(block).toContainText('งานย่อย 1/2')
  await expect(block.getByRole('listitem')).toHaveCount(4)
  // The note line stays out of the description.
  await expect(panel.locator('.desc-text')).toHaveText('ใช้ธีมเดิม')
  await expect(panel.getByRole('button', { name: 'ทำไมยังค้าง' })).toHaveCount(0)

  await block.getByRole('button', { name: '+ จด' }).click()
  const dialog = page.getByRole('dialog', { name: 'ทำเว็บร้าน' })
  await dialog.getByRole('button', { name: 'เป็นงานยาวอยู่แล้ว' }).click()
  await dialog.getByRole('button', { name: 'บันทึก', exact: true }).click()
  await expect.poll(() => file.text).toContain('    - ค้าง 2026-10-08 เป็นงานยาวอยู่แล้ว\n')
  await expect(block.getByRole('listitem')).toHaveCount(5)

  // Every task with notes, each as its path; a tap opens the task.
  await block.getByRole('button', { name: 'ทุกงาน' }).click()
  const log = page.getByRole('dialog', { name: 'บันทึกงานค้าง' })
  await expect(log.getByRole('button', { name: /ทำเว็บร้าน/ })).toContainText('เปิดมา 68 วัน')
  await expect(log.getByRole('button', { name: /ทำเว็บร้าน/ })).toContainText('เป็นงานยาวอยู่แล้ว')
  await expect(log.getByText('เริ่มงาน')).toHaveCount(0)
  await log.getByRole('button', { name: 'ปิด', exact: true }).click()

  // A young task due today keeps it folded at the bottom.
  await panel.getByRole('button', { name: 'ปิด', exact: true }).click()
  await openTask(page, 'จองตั๋ว')
  await expect(panel.getByRole('region', { name: 'ความเป็นไป' })).toHaveCount(0)
  const fold = panel.getByRole('button', { name: /ทำไมยังค้าง/ })
  await expect(fold).toContainText('ยังไม่มีบันทึก')
  await fold.click()
  await panel.getByRole('button', { name: '+ จดว่าติดอะไร' }).click()
  const own = page.getByRole('dialog', { name: 'จองตั๋ว' })
  await own.getByLabel('ตอนนี้ติดอะไร').fill('รอวันลาอนุมัติ')
  await own.getByRole('button', { name: 'บันทึก', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] จองตั๋ว ➕ 2026-10-06 📅 2026-10-08\n    - ค้าง 2026-10-08 รอวันลาอนุมัติ')
  // Explained now, it moves up front.
  await expect(panel.getByRole('region', { name: 'ความเป็นไป' })).toContainText('รอวันลาอนุมัติ')
})

test('the review deck asks what holds a task up before each choice, and opens the log', async ({ page }) => {
  const { file } = await open(page, { note: NOTE })
  await page.getByRole('button', { name: /งานไม่มีเดดไลน์ถึงรอบทบทวน/ }).click()
  const dialog = page.getByRole('dialog', { name: 'ทบทวนงาน (เหลือ 1)' })
  await expect(dialog.getByRole('button', { name: 'เขียนบทความ' })).toBeVisible()
  await expect(dialog.getByText('ติดอะไรอยู่ (ไม่ใส่ก็ได้)')).toBeVisible()

  await dialog.getByRole('button', { name: 'บันทึกงานค้าง' }).click()
  const log = page.getByRole('dialog', { name: 'บันทึกงานค้าง' })
  await expect(log.getByRole('button', { name: /ทำเว็บร้าน/ })).toBeVisible()
  await log.getByRole('button', { name: 'ปิด', exact: true }).click()

  await dialog.getByRole('button', { name: 'ไม่รู้จะเริ่มยังไง' }).click()
  await dialog.getByRole('button', { name: 'เก็บไว้แบบเดิม' }).click()
  await expect.poll(() => file.text).toContain('- [ ] เขียนบทความ ➕ 2026-09-01\n    - ค้าง 2026-10-08 ไม่รู้จะเริ่มยังไง')
  await expect(page.getByRole('dialog', { name: 'ทบทวนครบแล้ว' })).toBeVisible()
})

test('a choice in the review without a reason writes nothing under the task', async ({ page }) => {
  const { file } = await open(page, { note: NOTE })
  await page.getByRole('button', { name: /งานไม่มีเดดไลน์ถึงรอบทบทวน/ }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'ทำเสาร์นี้' }).click()
  await expect.poll(() => file.text).toContain('- [ ] เขียนบทความ ➕ 2026-09-01 ⏳ 2026-10-10')
  expect(file.text).not.toContain('ค้าง 2026-10-08')
})

test('the weekly review mentions unexplained long-open tasks and asks about each', async ({ page }) => {
  const r = await open(page, { note: NOTE, profile: Profile })
  await allowFolders(page, r.drive)
  await goTo(page, 'ผู้ช่วย')
  await page.getByRole('button', { name: /^ทบทวนสัปดาห์/ }).click()
  // Both have sat 21 days or more since they were made or last explained.
  await page.getByRole('button', { name: /ค้างนาน 2 งาน แตะเพื่อจดว่าติดอะไร/ }).click()

  const deck = page.getByRole('dialog', { name: 'ติดอะไรอยู่ (เหลือ 2)' })
  await expect(deck.getByRole('button', { name: 'เขียนบทความ' })).toBeVisible()
  await expect(deck.getByText('เปิดมา 37 วัน')).toBeVisible()
  await deck.getByRole('button', { name: 'งานใหญ่เกิน' }).click()
  await deck.getByLabel('ตอนนี้ติดอะไร').fill('งานใหญ่เกิน: ต้องแบ่งเป็นตอน')
  await deck.getByRole('button', { name: 'บันทึก', exact: true }).click()
  await expect.poll(() => r.file.text).toContain('- [ ] เขียนบทความ ➕ 2026-09-01\n    - ค้าง 2026-10-08 งานใหญ่เกิน: ต้องแบ่งเป็นตอน')

  const next = page.getByRole('dialog', { name: 'ติดอะไรอยู่ (เหลือ 1)' })
  await expect(next.getByRole('button', { name: 'ทำเว็บร้าน' })).toBeVisible()
  await expect(next.getByLabel('ตอนนี้ติดอะไร')).toHaveValue('')
  await next.getByRole('button', { name: 'ข้าม' }).click()
  const done = page.getByRole('dialog', { name: 'จดครบแล้ว' })
  await expect(done.getByText('จดไป 1 งาน ดูย้อนหลังได้ที่บันทึกงานค้าง')).toBeVisible()
  await done.getByRole('button', { name: 'ดูบันทึก' }).click()
  await expect(page.getByRole('dialog', { name: 'บันทึกงานค้าง' }).getByRole('button', { name: /เขียนบทความ/ })).toContainText('ต้องแบ่งเป็นตอน')
})
