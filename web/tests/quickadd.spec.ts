import { expect, test, type Page } from '@playwright/test'

import { Profile } from './assistantHelpers'
import { at, open } from './helpers'

const BUCKET =
  '---\nomni-list: true\nicon: 🏔️\ncategories: เที่ยว, ประสบการณ์\ntag: bucketlist\n---\n# Bucket list\n\n' +
  '- [ ] ไปดูแสงเหนือ #bucketlist #เที่ยว\n'

async function sheet(page: Page) {
  await page.evaluate(() => { location.hash = '/tasks' })
  await page.getByRole('button', { name: /เพิ่มงาน/ }).first().click()
  return page.getByRole('dialog', { name: 'เพิ่มงาน' })
}

test('the sentence is shown as it is read before it is saved', async ({ page }) => {
  const { file } = await open(page, { now: at(9, 20) })
  const add = await sheet(page)
  await add.getByLabel('งานใหม่').fill('จองตั๋ว พรุ่งนี้ 9:30 #เดินทาง !!')
  const read = add.getByLabel('อ่านได้ว่า')
  for (const c of ['พรุ่งนี้', 'เตือน 09:30', '#เดินทาง', 'สูง']) await expect(read.getByText(c, { exact: true })).toBeVisible()
  await add.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] จองตั๋ว #เดินทาง #remind-at-due ⏰ 09:30 ⏫ ➕ 2026-10-08 📅 2026-10-09')
})

test('"#" picks a list and its category, and the task goes into that list note', async ({ page }) => {
  const { drive, file } = await open(page, { now: at(9, 20) })
  const bucket = drive.omniNote('Bucket list.md', BUCKET)
  const add = await sheet(page)
  await add.getByLabel('งานใหม่').fill('ไปญี่ปุ่น #')
  const picks = add.getByRole('group', { name: 'เลือกลิสต์หรือ Tag' })
  await picks.getByRole('button', { name: /Bucket list/ }).first().click()
  await expect(add.getByLabel('งานใหม่')).toHaveValue('ไปญี่ปุ่น ')
  await expect(add).toContainText('เพิ่มลงใน 🏔️ Bucket list')
  await add.getByRole('group', { name: 'หมวด' }).getByRole('button', { name: 'เที่ยว' }).click()
  await expect(add).toContainText('เพิ่มลงใน 🏔️ Bucket list / เที่ยว')
  await add.getByRole('button', { name: 'เพิ่มลงลิสต์' }).click()
  await expect.poll(() => bucket.text).toContain('- [ ] ไปญี่ปุ่น #bucketlist #เที่ยว')
  expect(file.text).not.toContain('ไปญี่ปุ่น')

  // A typed category goes straight to it; × keeps the task in the task note.
  const again = await sheet(page)
  await again.getByLabel('งานใหม่').fill('ดำน้ำ #ประสบ')
  await again.getByRole('button', { name: /Bucket list \/ ประสบการณ์/ }).click()
  await expect(again).toContainText('เพิ่มลงใน 🏔️ Bucket list / ประสบการณ์')
  await again.getByRole('button', { name: 'ไม่ใส่ลงลิสต์' }).click()
  await again.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] ดำน้ำ ➕ 2026-10-08')
})

test('a spoken sentence is added to what was typed', async ({ page }) => {
  // The browser's speech recognition, hearing one sentence.
  await page.addInitScript(() => {
    class Fake {
      lang = ''
      onresult: ((e: unknown) => void) | null = null
      onend: (() => void) | null = null
      onerror: ((e: unknown) => void) | null = null
      start() {
        (window as any).heardLang = this.lang
        setTimeout(() => { this.onresult?.({ results: [[{ transcript: 'ซื้อนม พรุ่งนี้' }]] }); this.onend?.() }, 50)
      }
      stop() {}
      abort() {}
    }
    // Chromium has the plain name as well as the prefixed one.
    ;(window as any).SpeechRecognition = Fake
    ;(window as any).webkitSpeechRecognition = Fake
  })
  const { file } = await open(page, { now: at(9, 20) })
  const add = await sheet(page)
  await add.getByLabel('งานใหม่').fill('ด่วน')
  await add.getByRole('button', { name: 'พูด' }).click()
  await expect(add.getByLabel('งานใหม่')).toHaveValue('ด่วน ซื้อนม พรุ่งนี้')
  expect(await page.evaluate(() => (window as any).heardLang)).toBe('th-TH')
  await add.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect.poll(() => file.text).toContain('- [ ] ด่วน ซื้อนม ➕ 2026-10-08 📅 2026-10-09')
})

test('without speech recognition there is no microphone button', async ({ page }) => {
  await page.addInitScript(() => { delete (window as any).webkitSpeechRecognition; delete (window as any).SpeechRecognition })
  await open(page, { now: at(9, 20) })
  const add = await sheet(page)
  await expect(add.getByRole('button', { name: 'พูด' })).toHaveCount(0)
})

test('the sentence goes to the assistant to find a time for it', async ({ page }) => {
  await open(page, { now: at(9, 20), profile: Profile })
  const add = await sheet(page)
  await add.getByLabel('งานใหม่').fill('อยากไปวิ่ง ควรไปตอนไหนดี')
  await add.getByRole('button', { name: 'ถามผู้ช่วยว่าควรทำตอนไหน' }).click()
  await expect(page.getByRole('heading', { name: 'ผู้ช่วย', level: 1 })).toBeVisible()
  await expect(page.getByText('อยากไปวิ่ง ควรไปตอนไหนดี')).toBeVisible()
  await expect(page.getByText('งานนี้น่าจะใช้เวลาประมาณเท่าไร')).toBeVisible()
})
