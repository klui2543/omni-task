import { expect, test, type Page } from '@playwright/test'
import { FakeDrive } from './fakeGoogle'
import { at } from './helpers'

// Like Android, the web reads every note of the vault, not only Omni note.md: the tasks of all notes are shown
// together and each edit is written to the note the task is in. New tasks still go to Omni note.md.

const MAIN = '- [ ] งานในโน้ตหลัก 📅 2026-10-08\n'
const BOOK = '# เขียนหนังสือ\n\n- [ ] เขียนบทที่ 1 #นิยาย 📅 2026-10-08\n- [ ] หาสำนักพิมพ์ #นิยาย\n'

/** A vault with the task note and whatever [extra] adds, signed in and opened on the task list. */
async function start(page: Page, extra: (d: FakeDrive) => void = () => {}, main = MAIN) {
  const drive = new FakeDrive()
  const { file } = drive.vault(main)
  extra(drive)
  await page.clock.setFixedTime(at(9, 20))
  await drive.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await page.getByRole('button', { name: /ObsidianVault/ }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  await page.evaluate(() => { location.hash = '/tasks' })
  return { drive, file }
}

/** Reads the vault again from the Focus page's menu, then goes back to the task list. */
async function reload(page: Page) {
  await page.evaluate(() => { location.hash = '/focus' })
  await page.getByRole('button', { name: 'เมนู' }).click()
  await page.getByRole('menuitem', { name: 'โหลดใหม่' }).click()
  await page.evaluate(() => { location.hash = '/tasks' })
}

const writesTo = (drive: FakeDrive) => drive.requests.filter((r) => r.startsWith('PATCH')).map((r) => /\/files\/([^?]+)/.exec(r)![1])

test('tasks from every note of the vault are shown together', async ({ page }) => {
  let book = null as ReturnType<FakeDrive['note']> | null
  const { drive } = await start(page, (d) => { book = d.note('Projects/Book.md', BOOK) })
  await expect(page.getByText('งานในโน้ตหลัก')).toBeVisible()
  await expect(page.getByText('เขียนบทที่ 1')).toBeVisible()
  await expect(page.getByText('หาสำนักพิมพ์')).toBeVisible()

  // The edit panel says which note the task is in.
  await page.getByRole('button', { name: /^เขียนบทที่ 1/ }).click()
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' })).toContainText('Book.md บรรทัด 3')
  await page.getByRole('button', { name: 'ปิด', exact: true }).click()

  // Focus and Projects see them too.
  await page.evaluate(() => { location.hash = '/focus' })
  await expect(page.getByText('เขียนบทที่ 1')).toBeVisible()
  await page.evaluate(() => { location.hash = '/projects' })
  await expect(page.locator('.pj-card', { hasText: 'นิยาย' })).toContainText('เสร็จ 0 จาก 2 งาน')
  expect(writesTo(drive)).toEqual([])
  expect(book!.version).toBe(1)
})

test('ticking or editing a task of another note writes only that note; a new task still goes to Omni note.md', async ({ page }) => {
  let book = null as ReturnType<FakeDrive['note']> | null
  const { drive, file } = await start(page, (d) => { book = d.note('Projects/Book.md', BOOK) })
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ เขียนบทที่ 1/ }).click()
  await expect.poll(() => book!.text).toContain('- [x] เขียนบทที่ 1 #นิยาย 📅 2026-10-08 ✅ 2026-10-08')
  expect(file.text).toBe(MAIN)
  expect(writesTo(drive)).toEqual([book!.id])

  await page.getByRole('button', { name: /^หาสำนักพิมพ์/ }).click()
  const panel = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  await panel.getByRole('button', { name: /^ความสำคัญ/ }).click()
  await panel.getByRole('button', { name: 'สูงสุด', exact: true }).click()
  await expect.poll(() => book!.text).toContain('- [ ] หาสำนักพิมพ์ #นิยาย 🔺')
  expect(file.text).toBe(MAIN)
  await page.getByRole('button', { name: 'ปิด', exact: true }).click()

  await page.getByRole('button', { name: /เพิ่มงาน/ }).click()
  await page.getByLabel('งานใหม่').fill('จองห้องประชุม')
  await page.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] จองห้องประชุม')
  expect(book!.text).not.toContain('จองห้องประชุม')
})

test('archives, conflict copies, hidden folders and Attachments are not read', async ({ page }) => {
  const ids: Record<string, string> = {}
  const { drive } = await start(page, (d) => {
    d.note('Projects/Book.md', BOOK)
    ids.archive = d.note('📁 Folder/หลังบ้าน/Omni/Omni note Archive.md', '- [ ] งานในคลัง\n').id
    ids.copy = d.note('Projects/Book (conflict 2026-10-09-05-55-31).md', '- [ ] งานในสำเนา\n').id
    ids.trash = d.note('.trash/Old.md', '- [ ] งานในถังขยะ\n').id
    ids.hidden = d.note('.obsidian/x.md', '- [ ] งานในโฟลเดอร์ซ่อน\n').id
    ids.attachment = d.note('📁 Folder/หลังบ้าน/Attachments/Clip.md', '- [ ] งานในไฟล์แนบ\n').id
  })
  await expect(page.getByText('เขียนบทที่ 1')).toBeVisible()
  for (const title of ['งานในคลัง', 'งานในสำเนา', 'งานในถังขยะ', 'งานในโฟลเดอร์ซ่อน', 'งานในไฟล์แนบ']) {
    await expect(page.getByText(title)).toHaveCount(0)
  }
  // (The archive is read only by the daily move of finished tasks, never for its tasks.)
  for (const [what, id] of Object.entries(ids)) if (what !== 'archive') expect(drive.readFiles).not.toContain(id)
  // The hidden folders and Attachments are not even listed.
  const folderOf = (name: string) => drive.nodes.find((n) => n.name === name && n.folder)!.id
  for (const name of ['.trash', '.obsidian', 'Attachments']) expect(drive.listedFolders).not.toContain(folderOf(name))
  // A conflict copy anywhere in the vault is pointed out.
  await expect(page.getByRole('alert')).toContainText('Book (conflict 2026-10-09-05-55-31).md')
})

test('a reload reads only the notes that changed, and the next visit starts from what was read', async ({ page }) => {
  let book = null as ReturnType<FakeDrive['note']> | null
  let other = null as ReturnType<FakeDrive['note']> | null
  const { drive } = await start(page, (d) => {
    book = d.note('Projects/Book.md', BOOK)
    other = d.note('Home/Chores.md', '- [ ] ล้างรถ\n')
  })
  await expect(page.getByText('ล้างรถ')).toBeVisible()
  const reads = (id: string) => drive.readFiles.filter((r) => r === id).length
  expect(reads(other!.id)).toBe(1)

  // Obsidian changes one note elsewhere.
  book!.text = book!.text!.replace('หาสำนักพิมพ์', 'หาสำนักพิมพ์ใหม่')
  book!.version++
  await reload(page)
  await expect(page.getByText('หาสำนักพิมพ์ใหม่')).toBeVisible()
  expect(reads(other!.id)).toBe(1)

  // The next visit: the notes are on the device, so they show at once and the folders are not walked again.
  const listed = drive.listedFolders.length
  await page.reload()
  await expect(page.getByText('ล้างรถ')).toBeVisible()
  expect(reads(other!.id)).toBe(1)
  expect(drive.listedFolders.length).toBe(listed)

  // A note added later is found from Drive's change log.
  drive.note('Home/Garden.md', '- [ ] รดน้ำต้นไม้\n')
  await reload(page)
  await expect(page.getByText('รดน้ำต้นไม้')).toBeVisible()
})

test('"do in order" across notes writes the ids and waits into each note', async ({ page }) => {
  let book = null as ReturnType<FakeDrive['note']> | null
  // One step of the project is in the task note, the others in the book's own note.
  const { file } = await start(page, (d) => { book = d.note('Projects/Book.md', BOOK) }, '- [ ] วางโครงเรื่อง #นิยาย\n')
  await page.evaluate(() => { location.hash = '/projects' })
  await expect(page.locator('.pj-card', { hasText: 'นิยาย' })).toContainText('เสร็จ 0 จาก 3 งาน')
  await page.getByRole('button', { name: /นิยาย/ }).first().click()
  await page.getByRole('switch', { name: 'ต้องทำตามลำดับ' }).click()
  await expect.poll(() => file.text).toMatch(/- \[ \] วางโครงเรื่อง #นิยาย 🆔 \S+ ⛔ \S+/)
  const first = /เขียนบทที่ 1 #นิยาย 📅 2026-10-08 🆔 (\S+)/.exec(book!.text!)![1]
  const second = /วางโครงเรื่อง #นิยาย 🆔 (\S+) ⛔ (\S+)/.exec(file.text!)!
  expect(second[2]).toBe(first)
  expect(book!.text).toContain(`หาสำนักพิมพ์ #นิยาย 🆔`)
  expect(book!.text).toContain(`⛔ ${second[1]}`)

  await page.getByRole('switch', { name: 'ต้องทำตามลำดับ' }).click()
  await expect.poll(() => file.text!.includes('⛔') || book!.text!.includes('⛔')).toBe(false)
})
