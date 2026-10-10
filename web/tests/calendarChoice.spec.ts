import { expect, test } from '@playwright/test'
import { at, open } from './helpers'

// The owner picks which Google calendars show; the choice stays on this device and a new calendar shows by default.

const at13 = (h: number) => new Date(2026, 9, 8, h)

async function start(page: import('@playwright/test').Page) {
  const ctx = await open(page, {
    now: at(9, 20), granted: true,
    events: [{ title: 'ประชุมทีม', begin: at13(13), end: at13(14) }],
  })
  ctx.drive.otherCalendars = [
    { id: 'work@x', name: 'งาน', events: [{ title: 'สัมมนาที่ทำงาน', begin: at13(15), end: at13(16) }] },
    { id: 'hidden@x', name: 'ซ่อนอยู่ใน Google', selected: false, events: [{ title: 'ไม่ควรเห็น', begin: at13(17), end: at13(18) }] },
  ]
  return ctx
}

test('Settings lists the calendars Google shows, and a switched-off one leaves Focus', async ({ page }) => {
  await start(page)
  await page.evaluate(() => { location.hash = '/settings' })
  const card = page.getByRole('region', { name: 'ปฏิทินที่แสดง' })
  await expect(card.getByRole('switch', { name: 'ปฏิทินของฉัน' })).toBeChecked()
  await expect(card.getByRole('switch', { name: 'งาน' })).toBeChecked()
  // A calendar Google itself hides is not offered.
  await expect(card.getByRole('switch', { name: 'ซ่อนอยู่ใน Google' })).toHaveCount(0)

  await page.evaluate(() => { location.hash = '/focus' })
  await expect(page.getByText('สัมมนาที่ทำงาน')).toBeVisible()
  await expect(page.getByText('ประชุมทีม')).toBeVisible()
  await expect(page.getByText('ไม่ควรเห็น')).toHaveCount(0)

  await page.evaluate(() => { location.hash = '/settings' })
  await card.getByRole('switch', { name: 'งาน' }).uncheck()
  await page.evaluate(() => { location.hash = '/focus' })
  await expect(page.getByText('ประชุมทีม')).toBeVisible()
  await expect(page.getByText('สัมมนาที่ทำงาน')).toHaveCount(0)
})

test('the choice is kept on this device after a reload, and switching back shows the calendar again', async ({ page }) => {
  await start(page)
  await page.evaluate(() => { location.hash = '/settings' })
  const card = page.getByRole('region', { name: 'ปฏิทินที่แสดง' })
  await card.getByRole('switch', { name: 'ปฏิทินของฉัน' }).uncheck()
  expect(await page.evaluate(() => localStorage.getItem('omni.calendars.hidden'))).toBe('["primary"]')
  await page.reload()
  await expect(card.getByRole('switch', { name: 'ปฏิทินของฉัน' })).not.toBeChecked()
  await page.evaluate(() => { location.hash = '/focus' })
  await expect(page.getByText('สัมมนาที่ทำงาน')).toBeVisible()
  await expect(page.getByText('ประชุมทีม')).toHaveCount(0)

  await page.evaluate(() => { location.hash = '/settings' })
  await card.getByRole('switch', { name: 'ปฏิทินของฉัน' }).check()
  expect(await page.evaluate(() => localStorage.getItem('omni.calendars.hidden'))).toBeNull()
})

test('without the calendar allowed there is no picker', async ({ page }) => {
  await open(page, { now: at(9, 20) })
  await page.evaluate(() => { location.hash = '/settings' })
  await expect(page.getByRole('region', { name: 'ปฏิทินที่แสดง' })).toHaveCount(0)
})
