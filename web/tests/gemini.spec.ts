import { expect, test, type Page } from '@playwright/test'

import { Profile, allowFolders } from './assistantHelpers'
import { goTo, open } from './helpers'

const GEMINI = 'https://generativelanguage.googleapis.com/**'

/** A key kept on this browser before the app loads, as Settings > Gemini saves it. */
const withKey = (page: Page, chat = true) =>
  page.addInitScript((c) => localStorage.setItem('omni.gemini', JSON.stringify({ key: 'test-key', model: '', chat: c })), chat)

/** The number Gemini's question gives the task titled [title]. */
const numberOf = (question: string, title: string) => Number(question.split('\n').find((l) => /^\d+\. /.test(l) && l.includes(`[ ] ${title}`))!.split('.')[0])

const ask = async (page: Page, text: string) => {
  await page.getByLabel('ข้อความถึงผู้ช่วย').fill(text)
  await page.getByRole('button', { name: 'ส่ง', exact: true }).click()
}

test('the key is kept on this browser only, never in the vault settings file', async ({ page }) => {
  const { drive } = await open(page, { profile: Profile })
  await allowFolders(page, drive)
  await goTo(page, 'ตั้งค่า')
  const card = page.getByRole('region', { name: 'Gemini' })
  await card.getByLabel('API key ของ Gemini').fill('AIza-secret')
  await card.getByRole('button', { name: 'บันทึก' }).click()
  await expect(card.getByRole('button', { name: 'ลบ key' })).toBeVisible()
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('omni.gemini') ?? '{}').key)).toBe('AIza-secret')
  // A shared setting changed now still leaves the key out of the file Android reads.
  await page.getByRole('radiogroup', { name: 'ย้ายเข้าคลังอัตโนมัติหลัง' }).getByRole('radio', { name: '3 วัน' }).click()
  await expect.poll(() => drive.nodes.find((n) => n.name === 'omni-settings.json')?.text ?? '').toContain('archive')
  for (const n of drive.nodes) expect(n.text ?? '').not.toContain('AIza-secret')
})

test('Gemini answers with the tasks numbered, and only the picked changes are saved', async ({ page }) => {
  await withKey(page)
  const { drive, file } = await open(page, { profile: Profile })
  await allowFolders(page, drive)
  let sent: { key?: string; body?: any } = {}
  await page.route(GEMINI, async (route) => {
    const req = route.request()
    sent = { key: req.headers()['x-goog-api-key'], body: req.postDataJSON() }
    const question: string = sent.body.contents.at(-1).parts[0].text
    const answer = {
      reply: 'เสนอเลื่อนส่งรายงาน — ปิดงานโทรหาแม่ และเพิ่มนัดหมอ',
      changes: [
        { op: 'due', task: numberOf(question, 'ส่งรายงาน'), date: '2026-10-12', why: 'เลยกำหนดแล้ว' },
        { op: 'remind', task: numberOf(question, 'ส่งรายงาน'), time: '9:00', why: 'ทำตอนเช้า' },
        { op: 'done', task: numberOf(question, 'โทรหาแม่'), why: 'โทรแล้ว' },
        { op: 'add', task: 0, title: 'นัดหมอฟัน', date: '2026-10-20', why: 'งานใหม่' },
      ],
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ candidates: [{ content: { role: 'model', parts: [{ text: JSON.stringify(answer) }] } }] }) })
  })
  await goTo(page, 'ผู้ช่วย')
  await expect(page.getByRole('button', { name: 'Gemini', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await ask(page, 'งานไหนเลื่อนได้บ้าง')

  const card = page.getByRole('region', { name: 'คำตอบจาก Gemini' })
  // The owner's rule holds for Gemini's words too: no em dash.
  await expect(card.getByText('เสนอเลื่อนส่งรายงาน, ปิดงานโทรหาแม่ และเพิ่มนัดหมอ')).toBeVisible()
  expect(sent.key).toBe('test-key')
  expect(sent.body.contents.at(-1).parts[0].text).toContain('[งานที่แก้ได้ อ้างด้วยเลข]')
  expect(sent.body.generationConfig.responseMimeType).toBe('application/json')
  await expect(card.getByRole('checkbox')).toHaveCount(4)
  await expect(card.getByText('ครบกำหนด จ. 12 ต.ค.')).toBeVisible()
  await expect(card.getByText('เตือน 09:00')).toBeVisible()

  // Leave "done" out, save the rest.
  await card.getByRole('checkbox', { name: 'เลือก โทรหาแม่' }).uncheck()
  await card.getByRole('button', { name: 'บันทึกที่เลือก 3 รายการ' }).click()
  await expect(card.getByRole('button', { name: 'บันทึกแล้ว' })).toBeVisible()
  await expect.poll(() => file.text).toContain('- [ ] ส่งรายงาน #remind-at-due ⏰ 09:00 ⏫ ➕ 2026-10-01 📅 2026-10-12')
  expect(file.text).toContain('- [ ] นัดหมอฟัน ➕ 2026-10-08 📅 2026-10-20')
  expect(file.text).toContain('- [ ] โทรหาแม่ #remind-at-due ⏰ 19:30 ➕ 2026-10-01 📅 2026-10-08')
})

test('a follow-up carries the earlier answer, and an error says what to do', async ({ page }) => {
  await withKey(page)
  const { drive } = await open(page, { profile: Profile })
  await allowFolders(page, drive)
  const bodies: any[] = []
  await page.route(GEMINI, async (route) => {
    bodies.push(route.request().postDataJSON())
    if (bodies.length === 1) {
      return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ candidates: [{ content: { parts: [{ text: '{"reply":"วันนี้ทำทบทวนเคสก่อน","changes":[]}' }] } }] }) })
    }
    return route.fulfill({ status: 429, contentType: 'application/json', body: JSON.stringify({ error: { code: 429, message: 'Quota exceeded', status: 'RESOURCE_EXHAUSTED' } }) })
  })
  await goTo(page, 'ผู้ช่วย')
  await ask(page, 'วันนี้ทำอะไรก่อน')
  await expect(page.getByText('วันนี้ทำทบทวนเคสก่อน')).toBeVisible()
  await ask(page, 'แล้วงานถัดไปล่ะ')
  await expect(page.getByText('ใช้โควตาฟรีของ Gemini เต็มแล้ว รอสักครู่แล้วลองใหม่')).toBeVisible()
  expect(bodies[1].contents.map((c: any) => c.role)).toEqual(['user', 'model', 'user'])
  expect(bodies[1].contents[1].parts[0].text).toBe('วันนี้ทำทบทวนเคสก่อน')
})

test('switched to the rules, typed questions stay off the network', async ({ page }) => {
  await withKey(page, false)
  const { drive } = await open(page, { profile: Profile })
  await allowFolders(page, drive)
  let called = false
  await page.route(GEMINI, (route) => { called = true; return route.abort() })
  await goTo(page, 'ผู้ช่วย')
  await expect(page.getByRole('button', { name: 'กฎ', exact: true })).toBeVisible()
  await ask(page, 'จัดลำดับงานทั้งหมดให้หน่อย')
  await expect(page.getByText('ลำดับที่ควรทำ', { exact: false })).toBeVisible()
  expect(called).toBe(false)
})
