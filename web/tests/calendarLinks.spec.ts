import { expect, type Page, test } from '@playwright/test'

import { Profile } from './assistantHelpers'
import { NOTE, at, open } from './helpers'

// A tap on a Google Calendar event opens its page in Google Calendar (the event's htmlLink), as on Android.

const events = () => [
  { title: 'ประชุมทีม', begin: at(10), end: at(11), link: 'https://www.google.com/calendar/event?eid=team' },
  { title: 'งานสัมมนา', begin: at(0, 0, 9), end: at(0, 0, 10), allDay: true, link: 'https://www.google.com/calendar/event?eid=seminar' },
]

async function views(page: Page, mode: string) {
  await page.addInitScript((m) => localStorage.setItem('omni.viewMode', m), mode)
  const opened = await open(page, { note: NOTE, granted: true, events: events() })
  await page.evaluate(() => { location.hash = '/views' })
  await expect(page.getByRole('heading', { name: 'มุมมอง', level: 1 })).toBeVisible()
  return opened
}

test('Focus: an event in the day plan links to its page in Google Calendar', async ({ page }) => {
  await open(page, { granted: true, events: events() })
  const link = page.getByRole('link', { name: /ประชุมทีม/ })
  await expect(link).toHaveAttribute('href', 'https://www.google.com/calendar/event?eid=team')
  await expect(link).toHaveAttribute('target', '_blank')
  await expect(link).toHaveAttribute('rel', /noopener/)
})

test('Month: the day\'s events link to Google Calendar', async ({ page }) => {
  await views(page, 'calendar')
  const day = page.getByRole('region', { name: 'งานของวันที่เลือก' })
  await expect(day.getByRole('link', { name: /ประชุมทีม/ })).toHaveAttribute('href', 'https://www.google.com/calendar/event?eid=team')
})

test('7 days: events in the strip and in the hour grid link to Google Calendar', async ({ page }) => {
  await views(page, 'calendar')
  await page.getByRole('tab', { name: '7 วัน', exact: true }).click()
  await expect(page.locator('.tstrip').getByRole('link', { name: 'งานสัมมนา' })).toHaveAttribute('href', 'https://www.google.com/calendar/event?eid=seminar')
  await expect(page.locator('.tgrid').getByRole('link', { name: 'ประชุมทีม' })).toHaveAttribute('href', 'https://www.google.com/calendar/event?eid=team')
})

test('Gantt: tapping an event bar opens its page in a new tab', async ({ page, context }) => {
  await context.route('https://www.google.com/calendar/event**', (route) => route.fulfill({ contentType: 'text/html', body: 'calendar' }))
  await views(page, 'gantt')
  const opened = context.waitForEvent('page')
  await page.getByRole('button', { name: /ประชุมทีม/ }).click()
  const tab = await opened
  await tab.waitForLoadState()
  expect(tab.url()).toBe('https://www.google.com/calendar/event?eid=team')
})

test('Assistant: an event in what is coming links to Google Calendar', async ({ page }) => {
  await open(page, { profile: Profile, granted: true, events: [{ title: 'ประชุมทีม', begin: at(10, 0, 12), end: at(11, 0, 12), link: 'https://www.google.com/calendar/event?eid=team' }] })
  await page.getByRole('navigation', { name: 'เมนูหลัก' }).getByRole('button', { name: 'ผู้ช่วย', exact: true }).click()
  await page.getByRole('button', { name: /^สัปดาห์หน้ามีอะไร/ }).click()
  await expect(page.getByRole('link', { name: /ประชุมทีม/ })).toHaveAttribute('href', 'https://www.google.com/calendar/event?eid=team')
})
