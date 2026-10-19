import { expect, test, type Page } from '@playwright/test'

import { NOTE as FOCUS_NOTE, at, open } from './helpers'

// The edit panel, the task list, quick add and the app's frame, matched with the Android app.
// Today is Thursday 2026-10-08, 09:20 (see helpers.ts).

const NOTE = [
  '- [ ] รดน้ำต้นไม้ 🔁 every day 📅 2026-10-08',
  '- [ ] เขียน paper 📅 2026-10-10',
  '    - [x] บทนำ ✅ 2026-10-07',
  '    - [ ] วิธีการ',
  '    - [ ] ผลลัพธ์ ⛔ rp',
  '- [ ] ส่งรายงาน 🆔 rp 📅 2026-10-09',
  '- [ ] นัดอาจารย์ ⛔ rp',
  '- [ ] ตอบอีเมลทุน #รอ/พี่เอ ➕ 2026-09-26',
  '- [ ] ซื้อของ #บ้าน',
  '- [ ] ประชุมใหญ่ [[วาระ]]',
  '    - [[ประชุมทีม]]',
  '    - [[ไม่มีจริง]]',
  '',
].join('\n')

const panelOf = (page: Page) => page.getByRole('dialog', { name: 'แก้ไขงาน' })
const lineOf = (text: string | undefined, title: string) => text!.split('\n').find((l) => l.includes(title))

async function tasks(page: Page, opts: Parameters<typeof open>[1] = {}) {
  const ctx = await open(page, { note: NOTE, ...opts })
  await page.evaluate(() => { location.hash = '/tasks' })
  await expect(page.getByRole('heading', { name: 'งาน', level: 1 })).toBeVisible()
  return ctx
}

async function edit(page: Page, title: string) {
  await page.locator('.trow', { hasText: title }).first().locator('.trow-body').click()
  const panel = panelOf(page)
  await expect(panel.getByRole('heading', { name: new RegExp('^' + title) })).toBeVisible()
  return panel
}

test('"done" in the status row goes through the tick: a repeating task moves on and says to when', async ({ page }) => {
  const { file } = await tasks(page)
  const panel = await edit(page, 'รดน้ำต้นไม้')
  await panel.getByRole('radio', { name: 'เสร็จ' }).click()
  await expect.poll(() => file.text).toContain('- [ ] รดน้ำต้นไม้ 🔁 every day 📅 2026-10-09')
  expect(file.text).not.toContain('- [x] รดน้ำต้นไม้')
  await expect(page.getByRole('alert').filter({ hasText: /^เสร็จแล้ว รอบถัดไป 9 / })).toBeVisible()
})

test('"done" in the status row asks about open subtasks, then offers the archive with the days it waits', async ({ page }) => {
  const { file } = await tasks(page)
  const panel = await edit(page, 'เขียน paper')
  await panel.getByRole('radio', { name: 'เสร็จ' }).click()
  const ask = page.getByRole('dialog', { name: 'งานย่อยยังค้าง' })
  await ask.getByRole('button', { name: 'ติ๊กงานย่อยด้วย' }).click()
  await expect.poll(() => file.text).toContain('- [x] เขียน paper 📅 2026-10-10 ✅ 2026-10-08\n    - [x] บทนำ ✅ 2026-10-07\n    - [x] วิธีการ ✅ 2026-10-08')
  const done = page.getByRole('status').filter({ hasText: 'เสร็จแล้ว: เขียน paper' })
  await expect(done).toContainText('ถ้าไม่เลือก จะย้ายเข้าคลังเองหลัง 7 วัน')
})

test('with the archive days off, the done prompt asks as before', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('omni.archiveDays', '0'))
  await tasks(page)
  await page.getByRole('checkbox', { name: 'ติ๊กเสร็จ นัดอาจารย์' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'เสร็จแล้ว: นัดอาจารย์' })).toContainText('เก็บเข้าคลัง หรือลบออกจากโน้ตเลยไหม')
})

test('a blocked task says on whom it waits, its title greyed', async ({ page }) => {
  await tasks(page)
  const row = page.locator('.trow', { hasText: 'นัดอาจารย์' })
  await expect(row.locator('.pill.amber')).toHaveText('รอ ส่งรายงาน')
  await expect(row.locator('.ttitle')).toHaveClass(/blocked/)
  await expect(page.locator('.trow', { hasText: 'ส่งรายงาน' }).first().locator('.pill.amber')).toHaveCount(0)
})

test('changing the kind marks the task reviewed, as on Android', async ({ page }) => {
  const { file } = await tasks(page)
  const panel = await edit(page, 'ซื้อของ')
  await panel.getByRole('button', { name: /^ประเภท/ }).click()
  await panel.getByRole('group', { name: 'ประเภทงาน' }).getByRole('button', { name: 'ลงทุนอนาคต' }).click()
  await expect.poll(() => lineOf(file.text, 'ซื้อของ')).toContain('#อนาคต')
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('omni.focus') ?? '{}').reviewed?.['ซื้อของ'])).toBe('2026-10-08')
})

test('kinds are managed in a dialog over the edit panel', async ({ page }) => {
  const { file } = await tasks(page)
  const panel = await edit(page, 'ซื้อของ')
  await panel.getByRole('button', { name: /^ประเภท/ }).click()
  await panel.getByRole('button', { name: 'จัดการประเภท' }).click()
  const manager = page.getByRole('dialog', { name: 'ประเภทงาน' })
  await manager.getByLabel('อีโมจิของประเภท').fill('🏠')
  await manager.getByLabel('ชื่อประเภทใหม่').fill('งานบ้าน')
  await expect(manager.getByText('จะติดแท็ก #งานบ้าน')).toBeVisible()
  await manager.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect(manager.getByText('🏠 งานบ้าน')).toBeVisible()
  await manager.getByRole('button', { name: 'ซ่อน พักไว้ก่อน' }).click()
  await manager.getByRole('button', { name: 'เสร็จ', exact: true }).click()
  await expect(manager).toHaveCount(0)

  const kinds = panel.getByRole('group', { name: 'ประเภทงาน' })
  await expect(kinds.getByRole('button', { name: 'พักไว้ก่อน' })).toHaveCount(0)
  await kinds.getByRole('button', { name: '🏠 งานบ้าน' }).click()
  await expect.poll(() => lineOf(file.text, 'ซื้อของ')).toContain('#งานบ้าน')
  const kept = await page.evaluate(() => JSON.parse(localStorage.getItem('omni.kinds') ?? '{}'))
  expect(kept.custom).toEqual(['งานบ้าน\t🏠\tงานบ้าน'])
  expect(kept.hidden).toEqual(['SOMEDAY'])

  await panel.getByRole('button', { name: 'จัดการประเภท' }).click()
  await manager.getByRole('button', { name: 'ลบ 🏠 งานบ้าน' }).click()
  await manager.getByRole('button', { name: 'แสดง พักไว้ก่อน' }).click()
  await manager.getByRole('button', { name: 'เสร็จ', exact: true }).click()
  await expect(kinds.getByRole('button', { name: 'พักไว้ก่อน' })).toBeVisible()
})

test('subtasks are numbered, the next one marked, done ones green and a locked one waits', async ({ page }) => {
  await tasks(page)
  const panel = await edit(page, 'เขียน paper')
  const rows = panel.locator('.sub-row')
  await expect(rows).toHaveCount(3)
  await expect(rows.locator('.pj-n')).toHaveText(['1', '2', '3'])
  await expect(rows.nth(0).locator('.pj-n')).toHaveClass(/done/)
  await expect(rows.nth(1).locator('.pj-n')).toHaveClass(/next/)
  await expect(rows.nth(2).locator('.ttitle')).toHaveClass(/blocked/)
  await expect(rows.nth(2)).toContainText('รอ ส่งรายงาน')
})

test('the Tag field searches every tag in the vault, other notes too', async ({ page }) => {
  const { file } = await tasks(page, { before: (drive) => drive.note('Projects/นิยาย.md', '- [ ] วางโครงเรื่อง #นิยาย\n') })
  await expect(page.getByText('วางโครงเรื่อง')).toBeVisible()
  const panel = await edit(page, 'ซื้อของ')
  await panel.getByRole('button', { name: /^Tag/ }).click()
  await panel.getByLabel('Tag ใหม่').fill('#นิ')
  const found = panel.getByRole('group', { name: 'Tag ในโน้ต' })
  await expect(found.getByRole('button')).toHaveText(['#นิยาย'])
  await panel.getByLabel('Tag ใหม่').fill('ไม่เคยมี')
  await expect(panel.getByText('ยังไม่มี Tag นี้ในโน้ต กดเพิ่มเพื่อใช้เป็น Tag ใหม่')).toBeVisible()
  await panel.getByLabel('Tag ใหม่').fill('นิ')
  await found.getByRole('button', { name: '#นิยาย' }).click()
  await expect.poll(() => lineOf(file.text, 'ซื้อของ')).toBe('- [ ] ซื้อของ #บ้าน #นิยาย')
})

test('"who is waiting" offers the names already used on waiting tasks', async ({ page }) => {
  const { file } = await tasks(page)
  const panel = await edit(page, 'ซื้อของ')
  await panel.getByRole('button', { name: /^ประเภท/ }).click()
  await panel.getByRole('group', { name: 'ประเภทงาน' }).getByRole('button', { name: 'มีคนรอ' }).click()
  await panel.getByRole('group', { name: 'ชื่อที่เคยใช้' }).getByRole('button', { name: 'พี่เอ' }).click()
  await expect.poll(() => lineOf(file.text, 'ซื้อของ')).toContain('#รอ/พี่เอ')
})

test('the archive is offered only for a finished top-level task', async ({ page }) => {
  await tasks(page)
  let panel = await edit(page, 'ส่งรายงาน')
  await expect(panel.getByRole('button', { name: 'เก็บเข้าคลัง' })).toHaveCount(0)
  await panel.getByRole('button', { name: 'ปิด', exact: true }).click()
  // A subtask, even a finished one, stays with its task.
  panel = await edit(page, 'เขียน paper')
  await panel.locator('.sub-row', { hasText: 'บทนำ' }).locator('.sub-body').click()
  await expect(panel.getByRole('heading', { name: 'บทนำ' })).toBeVisible()
  await expect(panel.getByRole('button', { name: 'เก็บเข้าคลัง' })).toHaveCount(0)
})

test('linked notes say their folder, or that the note is not there yet; only a link on its own line comes off', async ({ page }) => {
  const { file } = await tasks(page, { before: (drive) => drive.note('Projects/ประชุมทีม.md', '# ประชุมทีม\n') })
  const panel = await edit(page, 'ประชุมใหญ่')
  await panel.getByRole('button', { name: /^โน้ตที่เกี่ยวข้อง/ }).click()
  const row = (name: string) => panel.locator('.link-row', { has: page.getByRole('link', { name, exact: true }) })
  await expect(row('ประชุมทีม')).toContainText('Projects')
  await expect(row('ไม่มีจริง').locator('.amber-text')).toHaveText('ยังไม่มีโน้ตนี้')
  await expect(row('วาระ').locator('.amber-text')).toHaveText('ยังไม่มีโน้ตนี้')
  await expect(panel.getByRole('button', { name: 'เอาลิงก์ วาระ ออก' })).toHaveCount(0)
  await panel.getByRole('button', { name: 'เอาลิงก์ ไม่มีจริง ออก' }).click()
  await expect.poll(() => file.text).not.toContain('[[ไม่มีจริง]]')
  expect(file.text).toContain('- [ ] ประชุมใหญ่ [[วาระ]]\n    - [[ประชุมทีม]]\n')
})

test('the description is tapped to write, and Cancel leaves it as it was', async ({ page }) => {
  const { file } = await tasks(page)
  const panel = await edit(page, 'ซื้อของ')
  await panel.getByRole('button', { name: 'แตะเพื่อเขียนรายละเอียด' }).click()
  await panel.getByRole('textbox', { name: 'รายละเอียด' }).fill('นม ไข่')
  await panel.getByRole('button', { name: 'ยกเลิก', exact: true }).click()
  await expect(panel.getByRole('textbox', { name: 'รายละเอียด' })).toHaveCount(0)
  await expect(panel.getByRole('button', { name: 'แตะเพื่อเขียนรายละเอียด' })).toBeVisible()
  await panel.getByRole('button', { name: 'แตะเพื่อเขียนรายละเอียด' }).click()
  await panel.getByRole('textbox', { name: 'รายละเอียด' }).fill('นม ไข่')
  await panel.getByRole('button', { name: 'บันทึก', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] ซื้อของ #บ้าน\n    - นม ไข่\n')
  await expect(panel.getByRole('button', { name: 'นม ไข่' })).toBeVisible()
})

test('a saved filter keeps the grouping and sorting in use', async ({ page }) => {
  await tasks(page)
  await page.getByRole('button', { name: 'กรอง', exact: true }).click()
  let filter = page.getByRole('dialog', { name: 'กรอง' })
  await filter.getByRole('group', { name: 'ตัวกรองด่วน' }).getByRole('button', { name: 'วันนี้' }).click()
  await filter.getByRole('button', { name: 'บันทึกตัวกรองนี้' }).click()
  await filter.getByLabel('ชื่อตัวกรอง').fill('ด่วน')
  await filter.getByRole('button', { name: 'บันทึก', exact: true }).click()
  await filter.getByRole('button', { name: 'ล้างทั้งหมด' }).click()
  await filter.getByRole('button', { name: /^แสดง/ }).click()

  await page.getByRole('button', { name: 'เรียงและจัดกลุ่ม' }).click()
  await page.getByRole('radio', { name: 'ความสำคัญ' }).click()
  await page.getByRole('button', { name: 'เสร็จ', exact: true }).click()

  await page.getByRole('button', { name: 'กรอง', exact: true }).click()
  filter = page.getByRole('dialog', { name: 'กรอง' })
  await filter.getByRole('button', { name: '★ ด่วน' }).click()
  await filter.getByRole('button', { name: /^แสดง/ }).click()
  await expect(page.getByRole('button', { name: 'เอา วันนี้ ออก' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'เรียงและจัดกลุ่ม' })).toHaveAttribute('title', /กลุ่ม: ความสำคัญ/)
})

test('the search text counts as a filter, and "clear all" clears it', async ({ page }) => {
  await tasks(page)
  const search = page.getByRole('textbox', { name: 'ค้นหาชื่องาน' })
  await search.fill('ส่ง')
  await page.getByRole('button', { name: 'กรอง 1', exact: true }).click()
  await page.getByRole('dialog', { name: 'กรอง' }).getByRole('button', { name: 'ล้างทั้งหมด' }).click()
  await expect(search).toHaveValue('')
  await expect(page.getByRole('button', { name: 'กรอง', exact: true })).toBeVisible()
})

test('folded groups are remembered for each grouping; folds kept under the old key still count', async ({ page }) => {
  await page.addInitScript(() => { if (!localStorage.getItem('omni.foldedGroups')) localStorage.setItem('omni.folded', 'ไม่มีวันที่') })
  await tasks(page)
  const head = (label: string) => page.locator('.group-head', { has: page.locator('.group-label', { hasText: new RegExp(`^${label}$`) }) })
  await expect(head('ไม่มีวันที่')).toHaveAttribute('aria-expanded', 'false')
  await head('วันนี้').click()
  await expect(head('วันนี้')).toHaveAttribute('aria-expanded', 'false')

  await page.getByRole('button', { name: 'เรียงและจัดกลุ่ม' }).click()
  await page.getByRole('radio', { name: 'ไม่จัดกลุ่ม' }).click()
  await page.getByRole('button', { name: 'เสร็จ', exact: true }).click()
  await expect(page.locator('.group-head')).toHaveAttribute('aria-expanded', 'true')

  await page.reload()
  await page.getByRole('button', { name: 'เรียงและจัดกลุ่ม' }).click()
  await page.getByRole('radio', { name: 'วันที่' }).click()
  await page.getByRole('button', { name: 'เสร็จ', exact: true }).click()
  await expect(head('วันนี้')).toHaveAttribute('aria-expanded', 'false')
  await expect(head('ไม่มีวันที่')).toHaveAttribute('aria-expanded', 'false')
  await head('ไม่มีวันที่').click()
  await page.reload()
  await expect(head('ไม่มีวันที่')).toHaveAttribute('aria-expanded', 'true')
})

test('quick add says what it added', async ({ page }) => {
  const { file } = await tasks(page)
  await page.getByRole('button', { name: /เพิ่มงาน/ }).first().click()
  await page.getByRole('dialog', { name: 'เพิ่มงาน' }).getByLabel('งานใหม่').fill('จองตั๋ว พรุ่งนี้')
  await page.getByRole('dialog', { name: 'เพิ่มงาน' }).getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] จองตั๋ว ➕ 2026-10-08 📅 2026-10-09')
  await expect(page.getByRole('alert').filter({ hasText: 'เพิ่มงานแล้ว: จองตั๋ว' })).toBeVisible()
})

test('a thin bar runs along the top while the vault is read or written', async ({ page }) => {
  const { drive } = await tasks(page)
  await expect(page.getByRole('progressbar', { name: 'กำลังซิงก์' })).toHaveCount(0)
  drive.latency = 600
  await page.getByRole('checkbox', { name: 'ติ๊กเสร็จ ซื้อของ' }).click()
  const bar = page.getByRole('progressbar', { name: 'กำลังซิงก์' })
  await expect(bar).toBeVisible()
  const box = (await bar.boundingBox())!
  expect(box.y).toBe(0)
  expect(box.height).toBeLessThanOrEqual(3)
  await expect(bar).toHaveCount(0, { timeout: 15_000 })
})

test('on a wide screen the side bar adds a task and asks the assistant from every page', async ({ page }, info) => {
  test.skip(info.project.name !== 'desktop', 'the side bar shows on wide screens only')
  const { file } = await open(page, { note: FOCUS_NOTE })
  const side = page.getByRole('navigation', { name: 'เมนูหลัก' })
  await side.getByRole('button', { name: '+ เพิ่มงาน' }).click()
  const add = page.getByRole('dialog', { name: 'เพิ่มงาน' })
  await add.getByLabel('งานใหม่').fill('ซื้อนม')
  await add.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] ซื้อนม ➕ 2026-10-08')

  await page.evaluate(() => { location.hash = '/settings' })
  await expect(page.getByRole('heading', { name: 'ตั้งค่า', level: 1 })).toBeVisible()
  await side.getByRole('textbox', { name: 'ถามผู้ช่วย' }).fill('ว่างตอนไหนบ้าง')
  await side.getByRole('button', { name: 'ส่งถึงผู้ช่วย' }).click()
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
  await expect(side.getByRole('textbox', { name: 'ถามผู้ช่วย' })).toHaveCount(0)
  await expect(side.getByRole('button', { name: '+ เพิ่มงาน' })).toBeVisible()
})

test('the "+" of a folded Kanban column opens the full quick add, starting in that status', async ({ page }) => {
  const { file } = await open(page, { note: FOCUS_NOTE })
  await page.evaluate(() => { location.hash = '/views' })
  await page.getByRole('button', { name: 'พับ กำลังทำ' }).click()
  await page.getByRole('button', { name: 'เพิ่มงานใน กำลังทำ' }).click()
  const add = page.getByRole('dialog', { name: 'เพิ่มงาน' })
  await expect(add.getByText('เริ่มที่สถานะ กำลังทำ')).toBeVisible()
  await add.getByLabel('งานใหม่').fill('ซื้อนม พรุ่งนี้ #อน')
  await expect(add.getByRole('group', { name: 'เลือกลิสต์หรือ Tag' }).getByRole('button', { name: '#อนาคต' })).toBeVisible()
  await add.getByLabel('งานใหม่').fill('ซื้อนม พรุ่งนี้ 9:00')
  await expect(add.getByLabel('อ่านได้ว่า')).toContainText('พรุ่งนี้')
  await expect(add.getByLabel('อ่านได้ว่า')).toContainText('เตือน 09:00')
  await expect(add.getByRole('button', { name: 'ถามผู้ช่วยว่าควรทำตอนไหน' })).toBeEnabled()
  await add.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => lineOf(file.text, 'ซื้อนม')).toMatch(/^- \[\/\] ซื้อนม .*📅 2026-10-09/)
  await expect(page.getByRole('alert').filter({ hasText: 'เพิ่มงานแล้ว: ซื้อนม' })).toBeVisible()
})

test('where a canvas cannot write WebP (Safari), the picture is encoded to WebP all the same', async ({ page }) => {
  // Safari hands back a PNG when asked for WebP.
  await page.addInitScript(() => {
    const toBlob = HTMLCanvasElement.prototype.toBlob
    HTMLCanvasElement.prototype.toBlob = function (cb: BlobCallback, type?: string, q?: number) {
      return toBlob.call(this, cb, type === 'image/webp' ? 'image/png' : type, q)
    }
  })
  const { drive, file } = await tasks(page)
  const panel = await edit(page, 'ซื้อของ')
  await panel.getByRole('button', { name: /^รูปแนบ/ }).click()
  await page.getByLabel('แนบรูป').evaluate(async (input: HTMLInputElement) => {
    const c = document.createElement('canvas')
    c.width = 40
    c.height = 30
    const g = c.getContext('2d')!
    g.fillStyle = '#3d4f6e'
    g.fillRect(0, 0, 40, 30)
    const blob = await new Promise<Blob>((r) => c.toBlob((b) => r(b!), 'image/png'))
    const dt = new DataTransfer()
    dt.items.add(new File([blob], 'photo.png', { type: 'image/png' }))
    input.files = dt.files
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })
  const name = 'Omni-2026-10-08-092000.webp'
  await expect.poll(() => file.text, { timeout: 20_000 }).toContain(`- [ ] ซื้อของ #บ้าน\n    - ![[${name}]]`)
  const saved = drive.nodes.find((n) => n.name === name)!
  expect(saved.mime).toBe('image/webp')
  expect(String.fromCharCode(...saved.bytes!.subarray(0, 4))).toBe('RIFF')
  expect(String.fromCharCode(...saved.bytes!.subarray(8, 12))).toBe('WEBP')
  await expect(panel.getByRole('button', { name: `ดูรูป ${name}` }).locator('img')).toHaveJSProperty('naturalWidth', 40)
})
