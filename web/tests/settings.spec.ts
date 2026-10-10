import { expect, test, type Page } from '@playwright/test'

import { Profile, allowFolders, profileText } from './assistantHelpers'
import { NOTE, open } from './helpers'

async function openSettings(page: Page, opts: Parameters<typeof open>[1] = {}) {
  const r = await open(page, opts)
  await allowFolders(page, r.drive)
  await page.evaluate(() => { location.hash = '/settings' })
  await expect(page.getByRole('heading', { name: 'ตั้งค่า', level: 1 })).toBeVisible()
  return r
}

test('the usual sleep and wake times go into the profile note and nothing else in it changes', async ({ page }) => {
  const edited = Profile.replace('- ช่วงสมองดี: 08:00-11:00', '- ช่วงสมองดี: 10:00-13:00') + '- ชอบเดินเช้า\n'
  const { drive } = await openSettings(page, { profile: edited })
  await expect(page.getByLabel('เวลานอนประจำ')).toHaveValue('22:30')
  await expect(page.getByLabel('เวลาตื่นประจำ')).toHaveValue('06:30')
  await page.getByLabel('เวลานอนประจำ').fill('23:15')
  await expect.poll(() => profileText(drive)).toContain('- นอน: 23:15')
  await page.getByLabel('เวลาตื่นประจำ').fill('05:45')
  await expect.poll(() => profileText(drive)).toContain('- ตื่น: 05:45')
  const text = profileText(drive)!
  expect(text).toContain('- ช่วงสมองดี: 10:00-13:00')
  expect(text).toContain('- อัปเดต: 2026-10-08')
  // What was written in Obsidian under "จำไว้" is still there.
  expect(text).toContain('- ชอบเดินเช้า')
  await page.reload()
  await expect(page.getByLabel('เวลานอนประจำ')).toHaveValue('23:15')
})

test('with no profile note the first time written creates it', async ({ page }) => {
  const { drive } = await openSettings(page)
  expect(profileText(drive)).toBeNull()
  await page.getByLabel('เวลานอนประจำ').fill('22:00')
  await expect.poll(() => profileText(drive)).toContain('- นอน: 22:00')
  expect(profileText(drive)).toContain('- ตื่น: 06:30')
})

test('archive days: a new choice moves old finished tasks to the archive note', async ({ page }) => {
  const note = NOTE + '- [x] เก่ามาก ✅ 2026-09-01\n- [x] เพิ่งเสร็จ ✅ 2026-10-07\n- [x] งานโปรเจกต์ #เว็บ ✅ 2026-09-01\n'
  await page.addInitScript(() => { if (!localStorage.getItem('omni.archiveDays')) localStorage.setItem('omni.archiveDays', '0') })
  const { drive, file } = await openSettings(page, { note })
  await expect(page.getByRole('radio', { name: 'ไม่ย้าย' })).toHaveAttribute('aria-checked', 'true')
  expect(file.text).toContain('เก่ามาก')

  await page.getByRole('radio', { name: '7 วัน' }).click()
  await expect.poll(() => file.text).not.toContain('เก่ามาก')
  const archive = drive.nodes.find((n) => n.name === 'Omni note Archive.md')!
  expect(archive.text).toBe('# Omni note Archive\n\n## 2026-10\n\n- [x] เก่ามาก ✅ 2026-09-01\n')
  // A recent one and project work stay where they are.
  expect(file.text).toContain('- [x] เพิ่งเสร็จ ✅ 2026-10-07')
  expect(file.text).toContain('- [x] งานโปรเจกต์ #เว็บ ✅ 2026-09-01')
  await page.reload()
  await expect(page.getByRole('radio', { name: '7 วัน' })).toHaveAttribute('aria-checked', 'true')
})

test('the sweep runs once a day when the app opens', async ({ page }) => {
  const note = NOTE + '- [x] เก่ามาก ✅ 2026-09-01\n'
  const { file } = await open(page, { note })
  await expect.poll(() => file.text).not.toContain('เก่ามาก')
})

test('ask-on-done and the urgent rule are kept on this device', async ({ page }) => {
  await openSettings(page)
  const ask = page.getByRole('switch', { name: /ถามเมื่อติ๊กเสร็จ/ })
  await expect(ask).toBeChecked()
  await ask.uncheck()
  await page.getByRole('radio', { name: 'ภายใน 2 วัน' }).click()
  await page.reload()
  await expect(page.getByRole('switch', { name: /ถามเมื่อติ๊กเสร็จ/ })).not.toBeChecked()
  await expect(page.getByRole('radio', { name: 'ภายใน 2 วัน' })).toHaveAttribute('aria-checked', 'true')
  expect(await page.evaluate(() => localStorage.getItem('omni.urgent'))).toBe('TWO_DAYS')
})

test('theme, colours, font and text size apply at once and stay after a reload', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' })
  await openSettings(page)
  const root = page.locator('html')
  await expect(root).toHaveAttribute('data-theme', 'light')

  await page.getByRole('radio', { name: 'มืด', exact: true }).click()
  await expect(root).toHaveAttribute('data-theme', 'dark')
  expect(await page.evaluate(() => getComputedStyle(document.body).backgroundColor)).toBe('rgb(14, 14, 16)')

  await page.getByRole('radio', { name: 'สีเดิม (มืดเท่านั้น)' }).click()
  await expect(root).toHaveAttribute('data-palette', 'midnight')
  expect(await page.evaluate(() => getComputedStyle(document.body).backgroundColor)).toBe('rgb(13, 15, 20)')
  await page.getByRole('radio', { name: 'Linear' }).click()

  await page.getByRole('radio', { name: 'Prompt (ไม่มีหัว)' }).click()
  expect(await page.evaluate(() => document.documentElement.style.getPropertyValue('--app-font'))).toContain('Prompt')
  await page.getByRole('radio', { name: '130%' }).click()
  expect(await page.evaluate(() => document.documentElement.style.getPropertyValue('--text-scale'))).toBe('1.3')

  await page.reload()
  await expect(root).toHaveAttribute('data-theme', 'dark')
  await expect(page.getByRole('radio', { name: '130%' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('radio', { name: 'Prompt (ไม่มีหัว)' })).toHaveAttribute('aria-checked', 'true')
})

test('"follow the system" follows the system', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' })
  await open(page)
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.emulateMedia({ colorScheme: 'light' })
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light')
})

test('what only Android can do is said so, with no switches that do nothing', async ({ page }) => {
  await openSettings(page)
  const notify = page.getByRole('region', { name: 'การแจ้งเตือน' })
  await expect(notify).toContainText('ใช้ได้ในแอป Android')
  await expect(notify).toContainText('เว็บตั้งปลุกหรือส่งการแจ้งเตือนตอนปิดหน้าเว็บไม่ได้')
  await expect(notify.getByRole('switch')).toHaveCount(0)
  // English is shown but cannot be picked: the web is Thai only.
  await expect(page.getByRole('radio', { name: 'English' })).toBeDisabled()
  await expect(page.getByRole('radio', { name: 'ไทย' })).toHaveAttribute('aria-checked', 'true')
  // Task kinds are managed here on the web (see kinds.spec.ts), so they are not on the Android-only list.
  await expect(page.getByRole('region', { name: 'ประเภทงาน' })).toBeVisible()
  await expect(page.getByText('ประเภทที่ตั้งเองและการซ่อนประเภท')).toHaveCount(0)
})

test('Google Calendar is read only, and says so', async ({ page }) => {
  await openSettings(page, { granted: true })
  await expect(page.getByText('อ่านนัดและเวรได้แล้ว (เว็บลงนัดให้ไม่ได้ อ่านได้อย่างเดียว)')).toBeVisible()
  await page.getByRole('button', { name: 'ออกจากระบบ' }).waitFor()
})
