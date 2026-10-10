import { expect, test } from '@playwright/test'
import { at, open } from './helpers'

// Going back to a page must not ask Drive or Google Calendar again for what the page read a moment ago: the
// profile, the list notes and the calendar events are kept for a few minutes (and a reload asks again).

const BUCKET = '---\nomni-list: true\nicon: 🏔️\ncategories: เที่ยว\ntag: bucketlist\n---\n# Bucket list\n\n- [ ] ไปดูแสงเหนือ #bucketlist #เที่ยว\n'
const PAGES = ['focus', 'projects', 'assistant', 'settings', 'tasks', 'views']

async function visit(page: import('@playwright/test').Page, name: string) {
  await page.evaluate((n) => { location.hash = '/' + n }, name)
  await page.waitForTimeout(500)
}

test('a page opened again does not read the profile, the lists or the calendar again', async ({ page }) => {
  const { drive } = await open(page, {
    now: at(9, 20), granted: true, profile: '# โปรไฟล์\n- ตื่น: 06:00\n- นอน: 23:00\n',
    events: [{ title: 'ประชุมทีม', begin: new Date(2026, 9, 8, 13), end: new Date(2026, 9, 8, 14) }],
  })
  drive.omniNote('Bucket list.md', BUCKET)

  for (const name of PAGES) await visit(page, name)
  const afterFirst = drive.requests.length

  for (const name of PAGES) await visit(page, name)
  await visit(page, 'focus')
  const again = drive.requests.slice(afterFirst)
  expect(again, again.join('\n')).toEqual([])

  // The list shows from what was kept, and a reload does ask again.
  await visit(page, 'projects')
  await expect(page.getByRole('button', { name: /Bucket list/ })).toBeVisible()
  await visit(page, 'settings')
  await page.locator('button.pillbtn', { hasText: 'โหลดใหม่' }).click()
  await expect.poll(() => drive.requests.length).toBeGreaterThan(afterFirst)
  await visit(page, 'focus')
  await expect.poll(() => drive.requests.slice(afterFirst).some((r) => r.includes('/calendar/v3/'))).toBe(true)
})

test('a list note changed by an edit here is read again', async ({ page }) => {
  const { drive } = await open(page, { now: at(9, 20) })
  const list = drive.omniNote('Bucket list.md', BUCKET)
  await visit(page, 'projects')
  await page.getByRole('button', { name: /Bucket list/ }).click()
  const item = page.locator('.pj-item', { hasText: 'ไปดูแสงเหนือ' })
  await expect(item).toBeVisible()
  await item.getByRole('checkbox').click()
  await expect.poll(() => list.text).toContain('- [x] ไปดูแสงเหนือ')
  // Back to Projects and into the list again: the tick is there (the list note was read again, not kept).
  await visit(page, 'focus')
  await visit(page, 'projects')
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await expect(page.locator('.pj-item', { hasText: 'ไปดูแสงเหนือ' }).getByRole('checkbox')).toBeChecked()
})
