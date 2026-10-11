import { expect, test, type Page } from '@playwright/test'
import { FakeDrive, TASKS } from './fakeGoogle'

// How the web stays in step with Android and Obsidian on the same notes: a write that lands in the same moment as
// ours, edits made without a network, and the settings shared through Android's omni-settings.json.

const TODAY = new Date('2026-10-08T10:00:00+07:00')

async function setup(page: Page, opts: { settings?: object } = {}) {
  const drive = new FakeDrive()
  const { root, file } = drive.vault(TASKS)
  const settings = opts.settings ? drive.add('omni-settings.json', drive.omniFolder(root).id, JSON.stringify(opts.settings, null, 2)) : null
  await page.clock.setFixedTime(TODAY)
  await drive.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await page.getByRole('button', { name: /ObsidianVault/ }).click()
  await page.evaluate(() => { location.hash = '/tasks' })
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  return { drive, file, settings }
}

const syncBadge = (page: Page) => page.getByRole('button', { name: 'โหลดใหม่' }).first()

test('a write that lands between the version check and ours is joined back, not replaced', async ({ page }) => {
  const { drive, file } = await setup(page)
  // Android ticks another task right after the web checked the version: Drive takes both writes, the web's last.
  drive.beforeNextWrite = (n) => drive.edit(n, n.text!.replace('- [ ] อ่านหนังสือ', '- [x] อ่านหนังสือ ✅ 2026-10-08'))
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
  await expect.poll(() => file.text).toContain('- [x] อ่านหนังสือ ✅ 2026-10-08')
  expect(file.text).toContain('- [x] ส่งรายงาน')
  await expect(page.getByLabel('การแก้ที่ชนกัน')).toHaveCount(0)
})

test('a clash in that moment keeps the other side and offers this device\'s line back', async ({ page }) => {
  const { drive, file } = await setup(page)
  drive.beforeNextWrite = (n) => drive.edit(n, n.text!.replace('- [ ] ส่งรายงาน', '- [ ] ส่งรายงานฉบับแก้'))
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
  const banner = page.getByLabel('การแก้ที่ชนกัน')
  await expect(banner).toContainText('- [x] ส่งรายงาน')
  expect(file.text).toContain('- [ ] ส่งรายงานฉบับแก้')
  expect(file.text).not.toContain('- [x] ส่งรายงาน')

  await banner.getByRole('button', { name: 'ใส่ท้ายโน้ต' }).click()
  await expect(banner).toHaveCount(0)
  await expect.poll(() => file.text!.trimEnd().split('\n').at(-1)).toContain('- [x] ส่งรายงาน')
})

test('without a network, ticks wait on the device and are sent, joined with what changed, when it comes back', async ({ page }) => {
  const { drive, file } = await setup(page)
  drive.offline = true
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
  await expect(page.getByText('ส่งรายงาน')).toHaveCount(0)
  await expect(syncBadge(page)).toContainText('ออฟไลน์ รอส่ง 1 โน้ต')
  expect(file.text).toContain('- [ ] ส่งรายงาน')

  // Meanwhile Android adds a task to the same note.
  drive.edit(file, file.text + '- [ ] งานจาก Android\n')
  drive.offline = false
  await syncBadge(page).click()
  await expect(page.getByText('งานจาก Android')).toBeVisible()
  expect(file.text).toContain('- [x] ส่งรายงาน')
  expect(file.text).toContain('- [ ] งานจาก Android')
  await expect(page.getByText(/ออฟไลน์/)).toHaveCount(0)
})

test('opened again without a network, the page shows the note as last read', async ({ page }) => {
  const { drive } = await setup(page)
  drive.offline = true
  await page.reload()
  await page.evaluate(() => { location.hash = '/tasks' })
  await expect(page.getByText('ส่งรายงาน')).toBeVisible()
  await expect(syncBadge(page)).toContainText('ออฟไลน์')
})

test('choices from Android\'s settings file arrive, and a change here goes back with the other keys kept', async ({ page }) => {
  const { settings } = await setup(page, {
    settings: {
      version: 1,
      savedAt: 1_700_000_000_000,
      settings: {
        urgentRule: { string: 'THREE_DAYS' },
        'archive.askOnDone': { bool: false },
        appearance: { string: 'android-only' },
      },
    },
  })
  await page.evaluate(() => { location.hash = '/settings' })
  await expect(page.getByRole('radio', { name: 'ภายใน 3 วัน' })).toHaveAttribute('aria-checked', 'true')
  await expect(page.getByRole('switch', { name: /ถามเมื่อติ๊กเสร็จ/ })).not.toBeChecked()

  await page.getByRole('radio', { name: 'ภายใน 2 วัน' }).click()
  await expect.poll(() => JSON.parse(settings!.text!).settings.urgentRule.string).toBe('TWO_DAYS')
  const saved = JSON.parse(settings!.text!)
  expect(saved.settings.appearance).toEqual({ string: 'android-only' })
  expect(saved.settings['archive.askOnDone']).toEqual({ bool: false })
  expect(saved.savedAt).toBeGreaterThan(1_700_000_000_000)
})

test.describe('with the site kept on the device', () => {
  test.use({ serviceWorkers: 'allow' })

  test('opens with no network at all after the sign-in ran out, on the note as last read', async ({ page, context }) => {
    await setup(page)
    // The service worker has kept the site once it controls the page.
    await page.evaluate(async () => { await navigator.serviceWorker.ready })
    await page.reload()
    await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null)).toBe(true)
    await page.evaluate(() => { location.hash = '/tasks' })
    await expect(page.getByText('ส่งรายงาน')).toBeVisible()

    // The hour of the token passes with the iPad offline.
    await page.evaluate(() => localStorage.setItem('omni.token', JSON.stringify({ token: 'fake-token', expiresAt: 1 })))
    await context.setOffline(true)
    await page.reload()
    await page.evaluate(() => { location.hash = '/tasks' })
    await expect(page.getByText('ส่งรายงาน')).toBeVisible()
    await expect(syncBadge(page)).toContainText('ออฟไลน์')
    await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ส่งรายงาน/ }).click()
    await expect(syncBadge(page)).toContainText('ออฟไลน์ รอส่ง 1 โน้ต')
  })
})
