import type { Page } from '@playwright/test'
import type { FakeDrive } from './fakeGoogle'

/** A profile note as the assistant writes it, for tests that start with the interview already answered. */
export const Profile = [
  '# โปรไฟล์',
  '',
  'ผู้ช่วยใน Omni Task อ่านไฟล์นี้ แก้ได้ตามสบาย',
  '',
  '- อัปเดต: 2026-10-01',
  '- ตื่น: 06:30',
  '- นอน: 22:30',
  '- ช่วงสมองดี: 08:00-11:00',
  '- ออกกำลังกาย: 17:30',
  '- คำที่หมายถึงเวร: เวร',
  '- คำที่หมายถึงเวรดึก: ดึก',
  '- วันที่ทำงานได้ดี: ',
  '',
  '## จำไว้',
  '',
].join('\n')

/**
 * Lets the fake Drive make folders (the real one does, with a JSON body), which the profile note needs the first
 * time. Routes added later win over the fake's own, so this one answers first and passes everything else on.
 */
export async function allowFolders(page: Page, drive: FakeDrive) {
  await page.route('https://www.googleapis.com/drive/v3/files**', async (route) => {
    const req = route.request()
    if (req.method() !== 'POST' || !(req.headers()['content-type'] ?? '').includes('application/json')) return route.fallback()
    const meta = JSON.parse(req.postData() ?? '{}')
    const node = drive.add(meta.name, meta.parents[0])
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ id: node.id }) })
  })
}

/** The text of the profile note in the fake Drive, or null when there is none. */
export const profileText = (drive: FakeDrive): string | null => {
  const omni = drive.nodes.find((n) => n.name === 'Omni' && n.folder)
  return drive.nodes.find((n) => n.name === 'โปรไฟล์.md' && n.parent === omni?.id)?.text ?? null
}
