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
  await page.getByLabel('Client ID').fill('test.apps.googleusercontent.com')
  await page.getByRole('button', { name: 'บันทึก' }).click()
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await page.getByLabel('ชื่อโฟลเดอร์ vault').fill('ObsidianVault')
  await page.getByRole('button', { name: 'ค้นหา' }).click()
  await page.getByRole('button', { name: /ObsidianVault/ }).click()
}

test('signs in, finds the vault and shows tasks by when they are due', async ({ page }) => {
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
  await signInAndPick(page)
  await expect(page.getByRole('alert')).toContainText('ไม่พบ')
})
