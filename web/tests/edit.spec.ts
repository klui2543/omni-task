import { expect, test, type Page } from '@playwright/test'

import { at, open } from './helpers'

// A task with a description and three subtasks, and a task that waits on another.
const NOTE = [
  '- [ ] เขียน paper 📅 2026-10-10',
  '    - อ่านงานเก่าก่อน',
  '    - [ ] บทนำ',
  '    - [ ] วิธีการ',
  '    - [ ] ผลลัพธ์',
  '- [ ] ส่งรายงาน 🆔 rp 📅 2026-10-09',
  '- [ ] นัดอาจารย์ ⛔ rp',
  '',
].join('\n')

async function start(page: Page, title: string) {
  const ctx = await open(page, { now: at(9, 20), note: NOTE })
  await page.evaluate(() => { location.hash = '/tasks' })
  await page.getByRole('button', { name: new RegExp('^' + title) }).first().click()
  const panel = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  await expect(panel.getByRole('heading', { name: title })).toBeVisible()
  return { ...ctx, panel }
}

/** Picks a PNG of [w] x [h], drawn by the browser, in the panel's image input. */
async function pickPng(page: Page, w: number, h: number) {
  await page.getByLabel('แนบรูป').evaluate(async (input: HTMLInputElement, [w, h]) => {
    const c = document.createElement('canvas')
    c.width = w
    c.height = h
    const g = c.getContext('2d')!
    g.fillStyle = '#3d4f6e'
    g.fillRect(0, 0, w, h)
    const blob = await new Promise<Blob>((r) => c.toBlob((b) => r(b!), 'image/png'))
    const dt = new DataTransfer()
    dt.items.add(new File([blob], 'photo.png', { type: 'image/png' }))
    input.files = dt.files
    input.dispatchEvent(new Event('change', { bubbles: true }))
  }, [w, h])
}

test('a note of the vault is linked under a task, opens in Obsidian and comes off again', async ({ page }) => {
  const { drive, file, panel } = await start(page, 'เขียน paper')
  drive.note('Projects/ประชุมทีม.md', '# ประชุมทีม\n')
  drive.note('Projects/อื่นๆ.md', '# อื่นๆ\n')
  await panel.getByRole('button', { name: /^โน้ตที่เกี่ยวข้อง/ }).click()
  await panel.getByRole('button', { name: '+ ลิงก์โน้ต' }).click()
  await panel.getByRole('searchbox', { name: 'ค้นหาโน้ต' }).fill('ประชุม')
  await expect(panel.locator('.note-result')).toHaveCount(1)
  await panel.locator('.note-result', { hasText: 'ประชุมทีม' }).click()
  await expect.poll(() => file.text).toContain('    - อ่านงานเก่าก่อน\n    - [[ประชุมทีม]]\n    - [ ] บทนำ')
  const link = panel.getByRole('link', { name: 'ประชุมทีม' })
  await expect(link).toHaveAttribute('href', 'obsidian://open?vault=ObsidianVault&file=' + encodeURIComponent('ประชุมทีม'))
  await panel.getByRole('button', { name: 'เอาลิงก์ ประชุมทีม ออก' }).click()
  await expect.poll(() => file.text).not.toContain('[[ประชุมทีม]]')
})

test('an image is attached as WebP in Attachments, shown, and deleted with its file', async ({ page }) => {
  const { drive, file, panel } = await start(page, 'ส่งรายงาน')
  await panel.getByRole('button', { name: /^รูปแนบ/ }).click()
  await pickPng(page, 40, 30)
  const name = 'Omni-2026-10-08-092000.webp'
  await expect.poll(() => file.text).toContain(`- [ ] ส่งรายงาน 🆔 rp 📅 2026-10-09\n    - ![[${name}]]`)
  const saved = drive.nodes.find((n) => n.name === name)!
  expect(saved.mime).toBe('image/webp')
  expect(String.fromCharCode(...saved.bytes!.subarray(8, 12))).toBe('WEBP')
  expect(drive.nodes.find((n) => n.id === saved.parent)!.name).toBe('Attachments')
  await expect(page.getByRole('alert')).toContainText(`แนบรูปแล้ว (${name}`)

  const thumb = panel.getByRole('button', { name: `ดูรูป ${name}` })
  await expect(thumb.locator('img')).toHaveJSProperty('naturalWidth', 40)
  await thumb.click()
  const viewer = page.getByRole('dialog', { name: `รูป ${name}` })
  await expect(viewer.locator('img')).toHaveJSProperty('naturalHeight', 30)
  await viewer.getByRole('button', { name: 'ลบรูป' }).click()
  await viewer.getByRole('button', { name: 'ลบ', exact: true }).click()
  await expect.poll(() => file.text).not.toContain('![[')
  await expect.poll(() => drive.nodes.some((n) => n.name === name)).toBe(false)
})

test('a big image asks whether to keep it reduced or full', async ({ page }) => {
  const { drive, file, panel } = await start(page, 'ส่งรายงาน')
  await panel.getByRole('button', { name: /^รูปแนบ/ }).click()
  await pickPng(page, 2400, 60)
  const ask = panel.getByRole('alertdialog', { name: 'ขนาดรูป' })
  await expect(ask).toContainText('2400 x 60')
  await ask.getByRole('button', { name: /^ย่อ/ }).click()
  await expect.poll(() => file.text).toContain('![[Omni-2026-10-08-092000.webp]]')
  const saved = drive.nodes.find((n) => n.name === 'Omni-2026-10-08-092000.webp')!
  // RIFF....WEBPVP8 with the width in the header: 2048 wide after shrinking.
  const thumb = panel.getByRole('button', { name: /^ดูรูป/ })
  await expect(thumb.locator('img')).toHaveJSProperty('naturalWidth', 2048)
  expect(saved.bytes!.length).toBeGreaterThan(0)
})

test('the description moves to its own note, linked in its place', async ({ page }) => {
  const { drive, file, panel } = await start(page, 'เขียน paper')
  await expect(panel.getByRole('textbox', { name: 'รายละเอียด' })).toHaveValue('อ่านงานเก่าก่อน')
  await panel.getByRole('button', { name: 'ย้ายรายละเอียดไปเป็นโน้ต' }).click()
  await expect.poll(() => file.text).toContain('- [ ] เขียน paper 📅 2026-10-10\n    - [[เขียน paper]]\n    - [ ] บทนำ')
  expect(file.text).not.toContain('อ่านงานเก่าก่อน')
  const note = drive.nodes.find((n) => n.name === 'เขียน paper.md')!
  expect(note.text).toBe('# เขียน paper\n\nอ่านงานเก่าก่อน\n')
  const folders = (id: string | null): string[] => { const n = drive.nodes.find((x) => x.id === id); return n ? [...folders(n.parent), n.name] : [] }
  expect(folders(note.parent).join('/')).toBe('ObsidianVault/📁 Folder/หลังบ้าน/Omni/Notes')
  await expect(page.getByRole('alert')).toContainText('ย้ายรายละเอียดไปที่ 📁 Folder/หลังบ้าน/Omni/Notes/เขียน paper.md แล้ว')
  await expect(panel.getByRole('textbox', { name: 'รายละเอียด' })).toHaveValue('')
})

test('subtasks are dragged into a new order, or moved with the arrow keys', async ({ page }) => {
  const { file, panel } = await start(page, 'เขียน paper')
  const handle = (title: string) => panel.getByRole('button', { name: `ลากเพื่อเรียง ${title}` })
  const from = (await handle('ผลลัพธ์').boundingBox())!
  const to = (await handle('บทนำ').boundingBox())!
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
  await page.mouse.down()
  await page.mouse.move(to.x + to.width / 2, to.y + 4, { steps: 8 })
  await page.mouse.up()
  await expect.poll(() => file.text).toContain('    - อ่านงานเก่าก่อน\n    - [ ] ผลลัพธ์\n    - [ ] บทนำ\n    - [ ] วิธีการ\n')

  await handle('ผลลัพธ์').focus()
  await page.keyboard.press('ArrowDown')
  await expect.poll(() => file.text).toContain('    - [ ] บทนำ\n    - [ ] ผลลัพธ์\n    - [ ] วิธีการ\n')
})

test('a task goes to the archive from its panel, and undo brings it back', async ({ page }) => {
  const { drive, file, panel } = await start(page, 'นัดอาจารย์')
  await panel.getByRole('button', { name: /^รายละเอียดอื่น/ }).click()
  await expect(panel.locator('.fold-row', { hasText: 'ต้องรอ' })).toContainText('ส่งรายงาน')
  await panel.getByRole('button', { name: 'เก็บเข้าคลัง' }).click()
  await expect.poll(() => file.text).not.toContain('นัดอาจารย์')
  await expect.poll(() => drive.textOf('Omni note Archive.md')).toContain('- [ ] นัดอาจารย์ ⛔ rp')
  await page.getByRole('button', { name: 'เลิกทำ' }).click()
  await expect.poll(() => file.text).toContain('- [ ] นัดอาจารย์ ⛔ rp')
})
