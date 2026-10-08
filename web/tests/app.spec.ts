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
}

test('signs in, finds the vault by its TaskForge note and shows tasks by when they are due', async ({ page }) => {
  await setup(page)
  await signInAndPick(page)

  await expect(page.getByRole('heading', { name: /เลยกำหนด/ })).toBeVisible()
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  await expect(page.getByRole('heading', { name: /วันนี้/ })).toBeVisible()
  await expect(page.getByText('โทรหาแม่')).toBeVisible()
  await expect(page.getByText('⏰ 09:30')).toBeVisible()
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

  await page.getByLabel('เพิ่มงาน').fill('จองตั๋ว พรุ่งนี้ 9:00 #เดินทาง')
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
