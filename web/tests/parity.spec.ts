import { expect, test, type Page } from '@playwright/test'
import { FakeDrive } from './fakeGoogle'
import { at, open } from './helpers'

const MAIN = '- [ ] ส่งรายงาน 📅 2026-10-08\n- [ ] ดูหนังคืนนี้ #bucketlist #เที่ยว\n'
const PAPER = '# วิจัย\n\n- [ ] เขียน paper 📅 2026-10-09\n'
const BUCKET =
  '---\nomni-list: true\nicon: 🏔️\ncategories: เที่ยว, ประสบการณ์\ntag: bucketlist\n---\n# Bucket list\n\n' +
  '- [ ] ไปดูแสงเหนือ #bucketlist #เที่ยว\n- [ ] วิ่งฮาล์ฟมาราธอน #bucketlist #ประสบการณ์\n'

/** A vault with the task note, a project note and a list note, signed in. */
async function start(page: Page) {
  const drive = new FakeDrive()
  const { file } = drive.vault(MAIN)
  drive.note('Projects/วิจัย.md', PAPER)
  const bucket = drive.omniNote('Bucket list.md', BUCKET)
  await page.clock.setFixedTime(at(9, 20))
  await drive.install(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'เข้าสู่ระบบด้วย Google' }).click()
  await page.getByRole('button', { name: /ObsidianVault/ }).click()
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  return { drive, file, bucket }
}

const row = (page: Page, title: string) => page.locator('main').getByRole('button', { name: new RegExp('^' + title) })

test('the task list narrows to one note, or to a list note\'s items', async ({ page }) => {
  await start(page)
  await page.evaluate(() => { location.hash = '/tasks' })
  await expect(row(page, 'เขียน paper')).toBeVisible()
  // A list's items stay out of the list until the list is chosen.
  await expect(row(page, 'ไปดูแสงเหนือ')).toHaveCount(0)

  await page.getByRole('button', { name: /^กรอง/ }).click()
  const panel = page.getByRole('dialog', { name: 'กรอง' })
  await panel.getByRole('button', { name: /ตัวกรองเพิ่มเติม/ }).click()
  await panel.getByRole('group', { name: 'โน้ต' }).getByRole('button', { name: 'วิจัย' }).click()
  await panel.getByRole('button', { name: /^แสดง/ }).click()
  await expect(row(page, 'เขียน paper')).toBeVisible()
  await expect(row(page, 'ส่งรายงาน')).toHaveCount(0)
  await page.getByRole('button', { name: 'เอา วิจัย ออก' }).click()
  await expect(row(page, 'ส่งรายงาน')).toBeVisible()

  await page.getByRole('button', { name: /^กรอง/ }).click()
  await panel.getByRole('button', { name: /ตัวกรองเพิ่มเติม/ }).click()
  await panel.getByRole('group', { name: 'ลิสต์' }).getByRole('button', { name: 'Bucket list' }).click()
  await panel.getByRole('button', { name: /^แสดง/ }).click()
  await expect(row(page, 'ไปดูแสงเหนือ')).toBeVisible()
  await expect(row(page, 'วิ่งฮาล์ฟมาราธอน')).toBeVisible()
  await expect(row(page, 'ส่งรายงาน')).toHaveCount(0)
  // A list item opens in the edit panel like any task.
  await row(page, 'ไปดูแสงเหนือ').click()
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' }).getByRole('heading', { name: 'ไปดูแสงเหนือ' })).toBeVisible()
})

test('a list is deleted with its items, and tasks pulled in from other notes only lose its tag', async ({ page }) => {
  const { drive, file } = await start(page)
  await page.evaluate(() => { location.hash = '/projects' })
  await page.getByRole('button', { name: /Bucket list/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Bucket list' })).toBeVisible()
  await page.getByRole('button', { name: 'ลบรายการ' }).click()
  const ask = page.getByRole('alertdialog', { name: 'ลบ Bucket list?' })
  await expect(ask).toContainText('พร้อม 2 รายการในนั้น')
  await expect(ask).toContainText('งานที่ดึงมาจากโน้ตอื่น 1 งานจะอยู่ที่เดิม แค่เอา #bucketlist ออก')
  await ask.getByRole('button', { name: 'ลบ', exact: true }).click()
  await expect.poll(() => drive.nodes.some((n) => n.name === 'Bucket list.md')).toBe(false)
  await expect.poll(() => file.text).toContain('- [ ] ดูหนังคืนนี้ #เที่ยว\n')
  await expect(page.getByRole('status').or(page.getByRole('alert')).filter({ hasText: 'ลบ Bucket list แล้ว' })).toBeVisible()
  await expect(page.getByRole('button', { name: /Bucket list/ })).toHaveCount(0)
})

test('tonight\'s wake time can be picked, and a morning event no longer pulls it earlier', async ({ page }) => {
  await open(page, { now: at(20), granted: true, events: [{ title: 'เวรเช้า', begin: at(7, 0, 9), end: at(16, 0, 9) }] })
  await expect(page.getByRole('button', { name: 'ตื่น 06:00 พรุ่งนี้' })).toBeVisible()
  await page.getByRole('button', { name: 'ตื่น 06:00 พรุ่งนี้' }).click()
  const sheet = page.getByRole('dialog', { name: 'คืนนี้' })
  await expect(sheet.getByLabel('เวลาตื่น')).toHaveValue('06:00')
  await sheet.getByLabel('เวลาตื่น').fill('06:20')
  await sheet.getByRole('button', { name: 'บันทึก' }).click()
  await expect(page.getByRole('button', { name: 'ตื่น 06:20 พรุ่งนี้' })).toBeVisible()
  await expect(page.getByText('ตั้งเองสำหรับคืนนี้')).toBeVisible()
  await expect(page.getByText('เผื่อ 1 ชม. ก่อน 07:00: เวรเช้า')).toHaveCount(0)
  // Back to the usual: the morning event sets it again.
  await page.getByRole('button', { name: 'ใช้เวลาปกติ' }).click()
  await expect(page.getByRole('button', { name: 'ตื่น 06:00 พรุ่งนี้' })).toBeVisible()
  await expect(page.getByText('เผื่อ 1 ชม. ก่อน 07:00: เวรเช้า')).toBeVisible()
})
