import { expect, test } from '@playwright/test'
import { at, open } from './helpers'

// A sign-in lasts 24 hours from the owner's last sign-in. Quiet renewals of the one-hour token in between do not
// extend it; after the 24 hours the sign-in page asks again and says why.

const HOUR = 3_600_000

test('within 24 hours of the last sign-in a run-out token renews quietly', async ({ page }) => {
  const { drive } = await open(page, { now: at(9, 20) })
  await page.evaluate((h) => {
    const t = JSON.parse(localStorage.getItem('omni.token')!)
    localStorage.setItem('omni.token', JSON.stringify({ ...t, expiresAt: Date.now() - 1 }))
    localStorage.setItem('omni.loginAt', String(Date.now() - 23 * h))
  }, HOUR)
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  expect(drive.signIns).toEqual(['select_account', 'none'])
  // The quiet renewal is not a new sign-in: the 24 hours still count from the owner's own.
  // (The page's clock is fixed by the test, so the age is worked out there.)
  const age = await page.evaluate(() => Date.now() - Number(localStorage.getItem('omni.loginAt')))
  expect(age).toBeGreaterThan(22 * HOUR)
})

test('after 24 hours the sign-in page asks again, and says why', async ({ page }) => {
  const { drive } = await open(page, { now: at(9, 20) })
  await page.evaluate((h) => {
    const t = JSON.parse(localStorage.getItem('omni.token')!)
    localStorage.setItem('omni.token', JSON.stringify({ ...t, expiresAt: Date.now() - 1 }))
    localStorage.setItem('omni.loginAt', String(Date.now() - 25 * h))
  }, HOUR)
  await page.reload()
  await expect(page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' })).toBeVisible()
  await expect(page.getByRole('status')).toContainText('ครบ 24 ชั่วโมง')
  expect(drive.signIns).toEqual(['select_account'])
  expect(await page.evaluate(() => localStorage.getItem('omni.token'))).toBeNull()

  // Signing in again starts a new 24 hours and the note goes away.
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  expect(await page.evaluate(() => localStorage.getItem('omni.sessionEnded'))).toBeNull()
  const age = await page.evaluate(() => Date.now() - Number(localStorage.getItem('omni.loginAt')))
  expect(age).toBeLessThan(HOUR)
})

test('a sign-in from before the limit existed counts from the first visit after it', async ({ page }) => {
  await open(page, { now: at(9, 20) })
  await page.evaluate(() => localStorage.removeItem('omni.loginAt'))
  await page.reload()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  expect(await page.evaluate(() => localStorage.getItem('omni.loginAt'))).not.toBeNull()
})
