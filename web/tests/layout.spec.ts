import { expect, test } from '@playwright/test'
import { NOTE, at, open } from './helpers'

// The task note is Omni note.md in the Omni folder inside หลังบ้าน. Until the owner moves it, the old
// TaskForge.md (and the Omni folder at the vault root) are read and written where they are.

test('the new layout: Omni note.md, its archive and conflict copies sit together in the Omni folder', async ({ page }) => {
  const { drive, file } = await open(page, { now: at(9, 20) })
  expect(file.name).toBe('Omni note.md')
  await expect(page.getByText('ทบทวนเคส')).toBeVisible()
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ทบทวนเคส/ }).click()
  await expect.poll(() => file.text).toContain('- [x] ทบทวนเคส')
  await expect(page.getByText('เสร็จแล้ว: ทบทวนเคส')).toBeVisible()
  await page.getByRole('button', { name: 'เก็บเข้าคลัง' }).click()
  const archive = drive.nodes.find((n) => n.name === 'Omni note Archive.md')!
  expect(archive.parent).toBe(file.parent)
  expect(archive.text).toContain('# Omni note Archive')
  expect(drive.nodes.some((n) => n.name === 'TaskForge Archive.md')).toBe(false)

  await page.getByRole('button', { name: 'ส่งรายงาน' }).click()
  await expect(page.getByRole('dialog', { name: 'แก้ไขงาน' })).toContainText('Omni note.md บรรทัด')
})

test('before the move: TaskForge.md and the Omni folder at the vault root still work', async ({ page }) => {
  const { drive, file } = await open(page, {
    now: at(20), layout: 'legacy', profile: '# โปรไฟล์\n- ตื่น: 06:00\n- นอน: 23:00\n',
  })
  expect(file.name).toBe('TaskForge.md')
  await expect(page.getByText('ทบทวนเคส')).toBeVisible()
  // The profile at the old place is read.
  await expect(page.getByText('นอน 23:00 ตื่น 06:00')).toBeVisible()
  await page.getByRole('checkbox', { name: /ติ๊กเสร็จ ทบทวนเคส/ }).click()
  await expect.poll(() => file.text).toContain('- [x] ทบทวนเคส')
  await page.getByRole('button', { name: 'เก็บเข้าคลัง' }).click()
  // The archive keeps its old name beside the old note.
  expect(drive.nodes.find((n) => n.name === 'TaskForge Archive.md')?.parent).toBe(file.parent)
  expect(drive.nodes.some((n) => n.name === 'Omni note Archive.md')).toBe(false)
})

test('before the move: a conflict copy of TaskForge.md is still noticed', async ({ page }) => {
  const { drive, file } = await open(page, { layout: 'legacy' })
  drive.add('TaskForge (conflict 2026-10-09-05-55-31).md', file.parent, NOTE)
  await page.getByRole('button', { name: 'เมนู' }).click()
  await page.getByRole('menuitem', { name: 'โหลดใหม่' }).click()
  await expect(page.getByRole('alert')).toContainText('TaskForge (conflict 2026-10-09-05-55-31).md')
})

test('once an Omni note.md is in the new place, it is used instead of the remembered TaskForge.md', async ({ page }) => {
  const { drive } = await open(page, { layout: 'legacy' })
  await expect(page.getByText('ทบทวนเคส')).toBeVisible()
  // The owner moves the Omni folder and makes the new note.
  const back = drive.nodes.find((n) => n.name === 'หลังบ้าน')!
  const omni = drive.add('Omni', back.id)
  drive.add('Omni note.md', omni.id, '- [ ] งานในที่ใหม่ 📅 2026-10-08\n')
  await page.reload()
  await expect(page.getByText('งานในที่ใหม่')).toBeVisible()
  await expect(page.getByText('ทบทวนเคส')).toHaveCount(0)
})

test('a profile in the new Omni folder wins over the old one', async ({ page }) => {
  const { drive } = await open(page, { now: at(20), layout: 'legacy', profile: '# โปรไฟล์\n- นอน: 23:00\n' })
  const back = drive.nodes.find((n) => n.name === 'หลังบ้าน')!
  const omni = drive.add('Omni', back.id)
  drive.add('Omni note.md', omni.id, '- [ ] งานในที่ใหม่ 📅 2026-10-08\n')
  drive.add('โปรไฟล์.md', omni.id, '# โปรไฟล์\n- นอน: 22:00\n')
  await page.reload()
  await expect(page.getByText('นอน 22:00')).toBeVisible()
})
