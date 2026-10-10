import { expect, test, type Page } from '@playwright/test'
import { FakeDrive, TASKS } from './fakeGoogle'

const TODAY = new Date('2026-10-08T10:00:00+07:00')

async function setup(page: Page, text = TASKS) {
  const drive = new FakeDrive()
  const { file } = drive.vault(text)
  await page.clock.setFixedTime(TODAY)
  await drive.install(page)
  return { drive, file }
}

async function signInAndPick(page: Page) {
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await page.getByRole('button', { name: /ObsidianVault/ }).click()
  // The app opens on Focus, as Android does; most of these tests are about the task list.
  await page.evaluate(() => { location.hash = '/tasks' })
}

test('signs in, finds the vault by its TaskForge note and shows tasks by when they are due', async ({ page }) => {
  await setup(page)
  await signInAndPick(page)

  await expect(page.getByRole('heading', { name: /เลยกำหนด/ })).toBeVisible()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  await expect(page.getByRole('heading', { name: /วันนี้/ })).toBeVisible()
  await expect(page.getByText('โทรหาแม่')).toBeVisible()
  await expect(page.getByText('09:30', { exact: true })).toBeVisible()
  await expect(page.getByText('ทุกวัน', { exact: true })).toBeVisible()
  await expect(page.locator('.pill.teal', { hasText: 'งาน' })).toBeVisible()
  await expect(page.getByRole('heading', { name: /ไม่มีวันที่/ })).toBeVisible()
  await expect(page.getByText('เสร็จแล้วเมื่อวาน')).toHaveCount(0)
})

test('ticking a task changes only its line in the note', async ({ page }) => {
  const { file } = await setup(page)
  await signInAndPick(page)

  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
  await expect(page.getByText('ส่งรายงาน')).toHaveCount(0)
  expect(file.text).toBe(TASKS.replace('📅 2026-10-07', '📅 2026-10-07 ✅ 2026-10-08').replace('- [ ] ส่งรายงาน', '- [x] ส่งรายงาน'))
})

test('a repeating task moves on instead of being ticked', async ({ page }) => {
  const { file } = await setup(page)
  await signInAndPick(page)

  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ซื้อนม/ }).click()
  await expect.poll(() => file.text).toContain('- [ ] ซื้อนม 🔁 every day ➕ 2026-10-01 📅 2026-10-09')
  await expect(page.getByText('ซื้อนม')).toBeVisible()
})

test('quick add writes a task line in TaskForge order', async ({ page }) => {
  const { file } = await setup(page)
  await signInAndPick(page)

  await page.getByRole('button', { name: /เพิ่มงาน/ }).click()
  await page.getByLabel('งานใหม่').fill('จองตั๋ว พรุ่งนี้ 9:00 #เดินทาง')
  await page.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect(page.getByText('จองตั๋ว')).toBeVisible()
  expect(file.text).toContain('- [ ] จองตั๋ว #เดินทาง #remind-at-due ⏰ 09:00 ➕ 2026-10-08 📅 2026-10-09')
  expect(file.text!.startsWith(TASKS.trimEnd())).toBe(true)
})

test('a change made elsewhere meanwhile is kept, not overwritten', async ({ page }) => {
  const { drive, file } = await setup(page)
  await signInAndPick(page)
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()

  // While the app reads the note to tick a task, Obsidian adds a line.
  drive.afterNextRead = (n) => {
    n.text += '- [ ] เพิ่มจาก Obsidian\n'
    n.version++
  }
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
  await expect(page.getByText('เพิ่มจาก Obsidian')).toBeVisible()
  expect(file.text).toContain('- [x] ส่งรายงาน')
  expect(file.text).toContain('- [ ] เพิ่มจาก Obsidian')
})

test('says so when the line was changed elsewhere', async ({ page }) => {
  const { file } = await setup(page)
  await signInAndPick(page)
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()

  file.text = file.text!.replace('ส่งรายงาน', 'ส่งรายงานฉบับแก้')
  file.version++
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
  await expect(page.getByRole('alert')).toContainText('ถูกแก้จากที่อื่น')
  await expect(page.getByText('ส่งรายงานฉบับแก้')).toBeVisible()
  expect(file.text).toContain('- [ ] ส่งรายงานฉบับแก้')
})

test('says so when the vault has no TaskForge note', async ({ page }) => {
  const drive = new FakeDrive()
  drive.add('ObsidianVault', 'root')
  await page.clock.setFixedTime(TODAY)
  await drive.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await expect(page.getByText('ไม่พบ TaskForge.md ใน Drive')).toBeVisible()
  await page.getByRole('button', { name: 'เลือกโฟลเดอร์ vault เอง' }).click()
  await page.getByRole('button', { name: '📁 ObsidianVault' }).click()
  await page.getByRole('button', { name: 'ใช้โฟลเดอร์ "ObsidianVault" เป็น vault' }).click()
  await expect(page.getByRole('alert')).toContainText('ไม่พบ')
})

test('opening the app again stays signed in, and a run-out token renews quietly', async ({ page }) => {
  const { drive } = await setup(page)
  await signInAndPick(page)
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()

  await page.reload()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  expect(drive.signIns).toEqual(['select_account'])

  // An hour later the token has run out: one quiet trip to Google, no sign-in screen.
  await page.evaluate(() => {
    const t = JSON.parse(localStorage.getItem('omni.token')!)
    localStorage.setItem('omni.token', JSON.stringify({ ...t, expiresAt: Date.now() - 1 }))
  })
  await page.reload()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  expect(drive.signIns).toEqual(['select_account', 'none'])
})

test('when Google refuses a quiet renewal the sign-in button shows, without a loop', async ({ page }) => {
  const { drive } = await setup(page)
  await signInAndPick(page)
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()

  drive.refuseQuiet = true
  await page.evaluate(() => {
    const t = JSON.parse(localStorage.getItem('omni.token')!)
    localStorage.setItem('omni.token', JSON.stringify({ ...t, expiresAt: Date.now() - 1 }))
  })
  await page.reload()
  await expect(page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  expect(drive.signIns).toEqual(['select_account', 'none'])
})

test('a parent with open subtasks asks before ticking', async ({ page }) => {
  const text = '- [ ] เตรียมสไลด์ 📅 2026-10-08\n    - [ ] ทำโครง\n    - [x] หาข้อมูล ✅ 2026-10-07\n'
  const { file } = await setup(page, text)
  await signInAndPick(page)

  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ เตรียมสไลด์/ }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: 'ติ๊กงานย่อยด้วย' }).click()
  await expect.poll(() => file.text).toBe(
    '- [x] เตรียมสไลด์ 📅 2026-10-08 ✅ 2026-10-08\n    - [x] ทำโครง ✅ 2026-10-08\n    - [x] หาข้อมูล ✅ 2026-10-07\n',
  )
})

test('finds the note when Drive stores the folder emoji another way', async ({ page }) => {
  const drive = new FakeDrive()
  // The same 📁 with the invisible "show as emoji" mark some sync apps add.
  drive.vault(TASKS, 'ObsidianVault', '📁\uFE0F Folder')
  await page.clock.setFixedTime(TODAY)
  await drive.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await expect(page.getByText('ObsidianVault / 📁\uFE0F Folder / หลังบ้าน / TaskForge')).toBeVisible()
  await page.getByRole('button', { name: /ObsidianVault/ }).click()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()

  // Picked by browsing instead of the search, the path is still followed.
  await page.evaluate(() => localStorage.removeItem('omni.taskFileId'))
  await page.reload()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
})

test('the vault folder can be picked by opening folders', async ({ page }) => {
  const drive = new FakeDrive()
  const { root } = drive.vault(TASKS)
  const elsewhere = drive.add('งานอื่น', 'root')
  drive.add('Vault2', elsewhere.id)
  await page.clock.setFixedTime(TODAY)
  await drive.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await page.getByRole('button', { name: 'เลือกโฟลเดอร์ vault เอง' }).click()
  await page.getByRole('button', { name: '📁 ObsidianVault' }).click()
  await expect(page.getByRole('button', { name: 'My Drive' })).toBeVisible()
  await page.getByRole('button', { name: 'ใช้โฟลเดอร์ "ObsidianVault" เป็น vault' }).click()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  expect(root.name).toBe('ObsidianVault')
})

test('search narrows the list, and a folded group stays folded', async ({ page }) => {
  await setup(page)
  await signInAndPick(page)
  await expect(page.getByText('โทรหาแม่')).toBeVisible()

  await page.getByLabel('ค้นหาชื่องาน').fill('นม')
  await expect(page.getByText('ซื้อนม')).toBeVisible()
  await expect(page.getByText('โทรหาแม่')).toHaveCount(0)
  await page.getByLabel('ค้นหาชื่องาน').fill('')

  await page.getByRole('heading', { name: /วันนี้/ }).getByRole('button').click()
  await expect(page.getByText('โทรหาแม่')).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  await expect(page.getByText('โทรหาแม่')).toHaveCount(0)
})

test('a parent shows how many of its subtasks are done', async ({ page }) => {
  await setup(page, '- [ ] เตรียมสไลด์ 📅 2026-10-08\n    - [ ] ทำโครง\n    - [x] หาข้อมูล ✅ 2026-10-07\n')
  await signInAndPick(page)
  await expect(page.getByText('1/2', { exact: true })).toBeVisible()
  await expect(page.getByText('ทำโครง')).toHaveCount(0)
})

test('the menu moves between pages and the page stays after a reload', async ({ page }) => {
  await setup(page)
  await signInAndPick(page)
  const menu = page.getByRole('navigation', { name: 'เมนูหลัก' })
  await expect(menu).toHaveCount(1)
  await expect(menu.getByRole('button', { name: 'งาน', exact: true })).toHaveAttribute('aria-current', 'page')

  await menu.getByRole('button', { name: 'มุมมอง' }).click()
  await expect(page.getByRole('heading', { name: 'มุมมอง', level: 1 })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'มุมมอง', level: 1 })).toBeVisible()

  await page.getByRole('button', { name: 'ตั้งค่า' }).first().click()
  await expect(page.getByRole('heading', { name: 'ตั้งค่า', level: 1 })).toBeVisible()
})

test('a sync conflict copy beside the note is pointed out', async ({ page }) => {
  const { drive, file } = await setup(page)
  drive.add('TaskForge (conflict 2026-10-09-05-55-31).md', file.parent, TASKS)
  await signInAndPick(page)
  await expect(page.getByRole('alert')).toContainText('TaskForge (conflict 2026-10-09-05-55-31).md')
  await expect(page.getByText('ส่งรายงาน')).toHaveCount(1)
})

test('the edit panel changes dates, priority, repeat, tags and the description', async ({ page }) => {
  const { file } = await setup(page)
  await signInAndPick(page)

  await page.getByRole('button', { name: /^ส่งรายงาน/ }).click()
  const panel = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  await expect(panel.getByRole('heading', { name: 'ส่งรายงาน' })).toBeVisible()

  await panel.getByRole('button', { name: /^นัดทำ/ }).click()
  await panel.getByRole('button', { name: 'พรุ่งนี้', exact: true }).click()
  await expect.poll(() => file.text).toContain('⏳ 2026-10-09 📅 2026-10-07')

  await panel.getByRole('button', { name: /^ความสำคัญ/ }).click()
  await panel.getByRole('button', { name: 'สูงสุด', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] ส่งรายงาน #งาน 🔺 ➕')

  await panel.getByRole('button', { name: /^วนซ้ำ/ }).click()
  await panel.getByRole('button', { name: 'แบบอื่น' }).click()
  await panel.getByLabel('กฎวนซ้ำ').fill('every week on Monday')
  await expect(panel.getByText('ทุกจันทร์')).toBeVisible()
  await panel.getByRole('button', { name: 'บันทึก' }).click()
  await expect.poll(() => file.text).toContain('🔺 🔁 every week on Monday ➕')

  await panel.getByRole('button', { name: /^Tag/ }).click()
  await panel.getByLabel('Tag ใหม่').fill('ด่วน')
  await panel.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] ส่งรายงาน #งาน #ด่วน 🔺')

  await panel.getByLabel('รายละเอียด').fill('ส่งอาจารย์ก่อนเที่ยง')
  await panel.getByRole('radio', { name: 'กำลังทำ' }).click()
  await expect.poll(() => file.text).toContain('- [/] ส่งรายงาน')
  expect(file.text).toContain('📅 2026-10-07\n    - ส่งอาจารย์ก่อนเที่ยง\n')

  await panel.getByLabel('เพิ่มงานย่อย').fill('เขียนบทสรุป')
  await panel.getByLabel('เพิ่มงานย่อย').press('Enter')
  await expect(panel.getByText('เขียนบทสรุป')).toBeVisible()
  expect(file.text).toContain('    - ส่งอาจารย์ก่อนเที่ยง\n    - [ ] เขียนบทสรุป ➕ 2026-10-08\n')
})

test('a finished task can go to the archive, and come back with undo', async ({ page }) => {
  const { drive, file } = await setup(page)
  await signInAndPick(page)

  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ อ่านหนังสือ/ }).click()
  await page.getByRole('button', { name: 'เก็บเข้าคลัง' }).click()
  await expect(page.getByText('ย้าย "อ่านหนังสือ" เข้าคลังแล้ว')).toBeVisible()
  expect(file.text).not.toContain('อ่านหนังสือ')
  const archive = drive.nodes.find((n) => n.name === 'TaskForge Archive.md')!
  expect(archive.parent).toBe(file.parent)
  expect(archive.text).toBe('# TaskForge Archive\n\n## 2026-10\n\n- [x] อ่านหนังสือ ✅ 2026-10-08\n')

  await page.getByRole('button', { name: 'เลิกทำ' }).click()
  await expect.poll(() => file.text).toContain('- [x] อ่านหนังสือ ✅ 2026-10-08\n- [x] เสร็จแล้วเมื่อวาน')
  expect(archive.text).not.toContain('อ่านหนังสือ')
})

test('a finished task can be deleted, and repeating or project tasks are not asked about', async ({ page }) => {
  const { file } = await setup(page)
  await signInAndPick(page)

  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
  await expect(page.getByText('เก็บเข้าคลัง หรือลบออกจากโน้ตเลยไหม')).toHaveCount(0)

  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ อ่านหนังสือ/ }).click()
  await page.getByRole('button', { name: 'ลบ', exact: true }).click()
  await expect(page.getByText('ลบ "อ่านหนังสือ" แล้ว')).toBeVisible()
  expect(file.text).not.toContain('อ่านหนังสือ')
})

test('filters, grouping and sorting change the list and are remembered', async ({ page }) => {
  await setup(page)
  await signInAndPick(page)
  await expect(page.getByText('โทรหาแม่')).toBeVisible()

  await page.getByRole('button', { name: 'กรอง', exact: true }).click()
  const filter = page.getByRole('dialog', { name: 'กรอง' })
  await filter.getByRole('group', { name: 'วันที่' }).getByRole('button', { name: 'วันนี้' }).click()
  await filter.getByRole('button', { name: 'แสดง 2 งาน' }).click()
  await expect(page.getByText('ส่งรายงาน')).toHaveCount(0)
  await expect(page.getByText('โทรหาแม่')).toBeVisible()
  await expect(page.getByRole('button', { name: 'กรอง 1' })).toBeVisible()

  await page.reload()
  await expect(page.getByText('โทรหาแม่')).toBeVisible()
  await expect(page.getByText('ส่งรายงาน')).toHaveCount(0)
  await page.getByRole('button', { name: 'เอา วันนี้ ออก' }).click()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()

  await page.getByRole('button', { name: /^กลุ่ม:/ }).click()
  await page.getByRole('radio', { name: 'ความสำคัญ' }).click()
  await page.getByRole('button', { name: 'เสร็จ', exact: true }).click()
  await expect(page.getByRole('button', { name: 'กลุ่ม: ความสำคัญ' })).toBeVisible()
  await expect(page.getByRole('heading', { name: /เลยกำหนด/ })).toHaveCount(0)
})
