import { expect, test, type Page } from '@playwright/test'

import { allowFolders } from './assistantHelpers'
import { NOTE, open } from './helpers'

const go = (page: Page, hash: string) => page.evaluate((h) => { location.hash = h }, hash)

async function openSettings(page: Page, opts: Parameters<typeof open>[1] = {}) {
  const r = await open(page, opts)
  await allowFolders(page, r.drive)
  await go(page, '/settings')
  await expect(page.getByRole('heading', { name: 'ตั้งค่า', level: 1 })).toBeVisible()
  return { ...r, card: page.getByRole('region', { name: 'ประเภทงาน' }) }
}

async function addKind(page: Page, name: string, emoji = '') {
  const card = page.getByRole('region', { name: 'ประเภทงาน' })
  if (emoji) await card.getByLabel('อีโมจิของประเภท').fill(emoji)
  await card.getByLabel('ชื่อประเภทใหม่').fill(name)
  await card.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
}

const NOTE_READ = NOTE + '- [ ] Atomic Habits #อ่าน ➕ 2026-10-01\n'

test('settings: a kind of your own is added with its tag, kept after a reload, and removed', async ({ page }) => {
  const { card } = await openSettings(page, { note: NOTE_READ })
  await expect(card.getByText('ยังไม่มี')).toBeVisible()
  await card.getByLabel('ชื่อประเภทใหม่').fill('งาน บ้าน')
  await expect(card.getByText('จะติดแท็ก #งานบ้าน')).toBeVisible()
  await addKind(page, 'อ่าน', '📖')
  await expect(card.getByText('📖 อ่าน')).toBeVisible()
  await expect(card.getByText('#อ่าน', { exact: true })).toBeVisible()

  // The same name (or a built-in kind's tag) is refused, and nothing is added twice.
  await addKind(page, 'อ่าน')
  await expect(card.getByRole('alert')).toHaveText('มีประเภทนี้อยู่แล้ว')
  await card.getByLabel('ชื่อประเภทใหม่').fill('รอ')
  await card.getByRole('button', { name: 'เพิ่ม', exact: true }).click()
  await expect(card.getByRole('alert')).toHaveText('มีประเภทนี้อยู่แล้ว')
  await expect(card.getByText('📖 อ่าน')).toHaveCount(1)

  // Kept on this device in Android's form.
  const saved = await page.evaluate(() => JSON.parse(localStorage.getItem('omni.kinds')!))
  expect(saved).toEqual({ custom: ['อ่าน\t📖\tอ่าน'], hidden: [] })
  await page.reload()
  await go(page, '/settings')
  await expect(card.getByText('📖 อ่าน')).toBeVisible()

  await card.getByRole('button', { name: 'ลบ 📖 อ่าน' }).click()
  await expect(card.getByText('ยังไม่มี')).toBeVisible()
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('omni.kinds')!).custom)).toEqual([])
})

test('settings: hiding a built-in kind takes its card off Focus and out of the edit panel', async ({ page }) => {
  const { card } = await openSettings(page)
  await expect(card.getByText('ประเภทที่มากับแอป')).toBeVisible()
  for (const label of ['มีคนรอ', 'ลงทุนอนาคต', 'พักไว้ก่อน']) await expect(card.getByText(label, { exact: true })).toBeVisible()
  await card.getByRole('button', { name: 'ซ่อน มีคนรอ' }).click()
  await expect(card.getByRole('button', { name: 'แสดง มีคนรอ' })).toBeVisible()

  await go(page, '/focus')
  await expect(page.getByText('ลงทุนอนาคต', { exact: true })).toBeVisible()
  await expect(page.getByText('คนรออยู่', { exact: true })).toHaveCount(0)
  await expect(page.getByText('พี่เอ รอ 12 วัน')).toHaveCount(0)

  // The picker leaves it out too, for a task that is not waiting; the task that is waiting still shows its own kind.
  await go(page, '/tasks')
  await page.getByRole('button', { name: /^เขียน paper/ }).click()
  const panel = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  await panel.getByRole('button', { name: /^ประเภท/ }).click()
  const group = panel.getByRole('group', { name: 'ประเภทงาน' })
  await expect(group.getByRole('button', { name: 'ลงทุนอนาคต' })).toBeVisible()
  await expect(group.getByRole('button', { name: 'พักไว้ก่อน' })).toBeVisible()
  await expect(group.getByRole('button', { name: 'มีคนรอ' })).toHaveCount(0)
  await panel.getByRole('button', { name: 'ปิด', exact: true }).click()
  await page.getByRole('button', { name: /^ตอบอีเมลทุน/ }).click()
  await expect(panel.getByRole('heading', { name: 'ตอบอีเมลทุน' })).toBeVisible()
  await panel.getByRole('button', { name: /^ประเภท/ }).click()
  await expect(panel.getByRole('group', { name: 'ประเภทงาน' }).getByRole('button', { name: 'มีคนรอ' })).toHaveAttribute('aria-pressed', 'true')

  // Hidden choices survive a reload, and showing the kind brings the card back.
  await page.reload()
  await expect(page.getByText('คนรออยู่', { exact: true })).toHaveCount(0)
  await go(page, '/settings')
  await card.getByRole('button', { name: 'แสดง มีคนรอ' }).click()
  await go(page, '/focus')
  await expect(page.getByText('คนรออยู่', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('omni.kinds')!).hidden)).toEqual([])
})

test('settings: hiding both Focus cards removes the row', async ({ page }) => {
  const { card } = await openSettings(page)
  await card.getByRole('button', { name: 'ซ่อน มีคนรอ' }).click()
  await card.getByRole('button', { name: 'ซ่อน ลงทุนอนาคต' }).click()
  await go(page, '/focus')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('สวัสดี')
  await expect(page.getByText('คนรออยู่', { exact: true })).toHaveCount(0)
  await expect(page.getByText('ลงทุนอนาคต', { exact: true })).toHaveCount(0)
})

test('the edit panel picks a kind and writes only that tag', async ({ page }) => {
  const { file } = await openSettings(page, { note: NOTE_READ })
  await addKind(page, 'อ่าน', '📖')
  await go(page, '/tasks')
  await page.getByRole('button', { name: /^Atomic Habits/ }).click()
  const panel = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  // The kind is its own field: shown there with its emoji, and not as a plain tag.
  await expect(panel.getByRole('button', { name: /^ประเภท/ })).toContainText('📖 อ่าน')
  await expect(panel.getByRole('button', { name: /^Tag/ })).not.toContainText('#อ่าน')
  await panel.getByRole('button', { name: /^ประเภท/ }).click()
  const group = panel.getByRole('group', { name: 'ประเภทงาน' })
  await expect(group.getByRole('button', { name: '📖 อ่าน' })).toHaveAttribute('aria-pressed', 'true')

  // Another kind takes the place of this one.
  await group.getByRole('button', { name: 'ลงทุนอนาคต' }).click()
  await expect.poll(() => file.text).toContain('- [ ] Atomic Habits #อนาคต ➕ 2026-10-01')
  expect(file.text).not.toContain('#อ่าน')
  await expect(panel.getByText('หมุนเวียนขึ้นหน้าโฟกัสวันละงาน ทบทวนทุก 14 วัน')).toBeVisible()

  await group.getByRole('button', { name: '📖 อ่าน' }).click()
  await expect.poll(() => file.text).toContain('- [ ] Atomic Habits #อ่าน ➕ 2026-10-01')
  expect(file.text).not.toContain('#อนาคต ➕ 2026-10-01\n- [ ] Atomic')

  // Back to normal clears it.
  await group.getByRole('button', { name: 'ปกติ' }).click()
  await expect.poll(() => file.text).toContain('- [ ] Atomic Habits ➕ 2026-10-01')
})

test('waiting asks who, and changing away from it clears the name too', async ({ page }) => {
  const { file } = await open(page)
  await go(page, '/tasks')
  await page.getByRole('button', { name: /^เขียน paper/ }).click()
  const panel = page.getByRole('dialog', { name: 'แก้ไขงาน' })
  await panel.getByRole('button', { name: /^ประเภท/ }).click()
  await panel.getByRole('group', { name: 'ประเภทงาน' }).getByRole('button', { name: 'มีคนรอ' }).click()
  await panel.getByLabel('ใครรองานนี้').fill(' สมชาย ')
  await panel.getByRole('button', { name: 'ตั้งเป็นมีคนรอ' }).click()
  await expect.poll(() => file.text).toContain('- [ ] เขียน paper #รอ/สมชาย 📅 2026-10-10')
  await expect(panel.getByText('สมชาย รออยู่ ขึ้นในการ์ด "คนรออยู่" ตามที่รอนานสุด')).toBeVisible()

  await panel.getByRole('group', { name: 'ประเภทงาน' }).getByRole('button', { name: 'พักไว้ก่อน' }).click()
  await expect.poll(() => file.text).toContain('- [ ] เขียน paper #สักวัน 📅 2026-10-10')
  expect(file.text).not.toContain('#รอ/สมชาย')

  // Waiting with no name is just #รอ.
  await panel.getByRole('group', { name: 'ประเภทงาน' }).getByRole('button', { name: 'มีคนรอ' }).click()
  await panel.getByRole('button', { name: 'ตั้งเป็นมีคนรอ' }).click()
  await expect.poll(() => file.text).toContain('- [ ] เขียน paper #รอ 📅 2026-10-10')
})

test('the Tasks filter has a chip for each kind of your own, and it filters by that tag', async ({ page }) => {
  await openSettings(page, { note: NOTE_READ })
  await addKind(page, 'อ่าน', '📖')
  await go(page, '/tasks')
  await expect(page.getByRole('button', { name: /^Atomic Habits/ })).toBeVisible()
  await page.getByRole('button', { name: 'กรอง', exact: true }).click()
  const filter = page.getByRole('dialog', { name: 'กรอง' })
  // It is with the kinds, and not twice (the Tag list leaves it out).
  await expect(filter.getByRole('group', { name: 'ประเภทงาน' }).getByRole('button', { name: '📖 อ่าน' })).toBeVisible()
  await expect(filter.getByRole('button', { name: '#อ่าน' })).toHaveCount(0)
  await filter.getByRole('button', { name: '📖 อ่าน' }).click()
  await expect(filter.getByRole('button', { name: 'แสดง 1 งาน' })).toBeVisible()
  await filter.getByRole('button', { name: 'แสดง 1 งาน' }).click()
  await expect(page.getByRole('button', { name: /^Atomic Habits/ })).toBeVisible()
  await expect(page.getByRole('button', { name: /^เขียน paper/ })).toHaveCount(0)
})

test('the tag of a kind of your own does not make a project', async ({ page }) => {
  await openSettings(page, { note: NOTE_READ })
  await go(page, '/projects')
  await expect(page.getByRole('heading', { name: 'โปรเจกต์/ลิสต์', level: 1 })).toBeVisible()
  await expect(page.getByText('อ่าน', { exact: true }).first()).toBeVisible()

  await go(page, '/settings')
  await addKind(page, 'อ่าน', '📖')
  await go(page, '/projects')
  await expect(page.getByRole('heading', { name: 'โปรเจกต์/ลิสต์', level: 1 })).toBeVisible()
  await expect(page.getByText('อ่าน', { exact: true })).toHaveCount(0)
})
