import { expect, test, type Page } from '@playwright/test'
import type { FakeDrive } from './fakeGoogle'
import { at, goTo, open } from './helpers'

// The web brought up to the Android app: tonight's sleep all day on Focus, the settings file carrying the Focus
// choices, filters, folds, the look and task reminders, the assistant's morning summary, and reminders while the
// page is open (banner, system notification, badge), each put off or done as from Android's notification.

const settingsFile = (savedAt: number, settings: Record<string, unknown>) => JSON.stringify({ version: 1, savedAt, settings }, null, 2)
const kept = (page: Page, key: string) => page.evaluate((k) => JSON.parse(localStorage.getItem(k) ?? 'null'), key)

/** The behavior log so far: what waits on the device and what reached the file. */
const logged = async (page: Page, drive: FakeDrive) =>
  (await page.evaluate(() => (JSON.parse(localStorage.getItem('omni.behavior.waiting') ?? '[]') as string[]).join('\n'))) + (drive.textOf('omni-behavior.json') ?? '')

/** Stands in for the browser's notifications and the app badge, so a test can see what the page asked for. */
async function fakeSystem(page: Page, permission: 'granted' | 'default' = 'granted') {
  await page.addInitScript((perm) => {
    const w = window as any
    w.__notes = []
    w.__badge = null
    class FakeNotification {
      static permission = perm
      static async requestPermission() { FakeNotification.permission = 'granted'; return 'granted' }
      onclick: (() => void) | null = null
      constructor(title: string, options: { body?: string; tag?: string }) { w.__notes.push({ title, body: options?.body }) }
      close() {}
    }
    w.Notification = FakeNotification
    ;(navigator as any).setAppBadge = async (n: number) => { w.__badge = n }
    ;(navigator as any).clearAppBadge = async () => { w.__badge = 0 }
  }, permission)
}

/** The page has read the note (a reminder is looked for from the first read on). */
const loaded = (page: Page) => expect(page.getByText('ส่งรายงาน').first()).toBeVisible()

/** Moves the clock and lets the page look again, as the 30 second timer does. */
async function moveTo(page: Page, d: Date) {
  await page.clock.setFixedTime(d)
  await page.clock.runFor(31_000)
}

test.describe('Focus: tonight\'s sleep', () => {
  test('in the daytime tonight is planned, with last night\'s sleep until noon and the countdown under it', async ({ page }) => {
    await open(page)
    const sleep = page.getByLabel('การนอนคืนนี้')
    await expect(sleep.getByRole('button', { name: 'นอน 22:30' })).toBeVisible()
    await expect(sleep.getByRole('button', { name: 'ตื่น 06:30 พรุ่งนี้' })).toBeVisible()
    await expect(sleep).toContainText('ได้นอน 8:00 ชม.')
    await expect(sleep).toContainText('เมื่อคืนได้นอน 8:00 ชม.')
    // The daytime summary still counts the free time, and the countdown is always there.
    await expect(page.getByText('ว่างเหลือ')).toBeVisible()
    await expect(page.getByText('ก่อนนอน')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'ตั้งนับถอยหลังถึงงานสำคัญ' })).toBeVisible()

    // After noon last night is no longer told.
    await page.clock.setFixedTime(at(13))
    await page.reload()
    await expect(page.getByLabel('การนอนคืนนี้')).toBeVisible()
    await expect(page.getByText(/เมื่อคืนได้นอน/)).toHaveCount(0)
  })

  test('a wake time picked for tonight says so, and goes back to the usual one', async ({ page }) => {
    await open(page)
    await page.getByRole('button', { name: 'ตื่น 06:30 พรุ่งนี้' }).click()
    await page.getByLabel('เวลาตื่น', { exact: true }).fill('05:45')
    await page.getByRole('button', { name: 'บันทึก' }).click()
    await expect(page.getByRole('button', { name: 'ตื่น 05:45 พรุ่งนี้' })).toBeVisible()
    await expect(page.getByText('ตั้งเองสำหรับคืนนี้')).toBeVisible()
    expect((await kept(page, 'omni.focus')).tonightWake).toEqual({ evening: '2026-10-08', time: '05:45' })

    await page.getByRole('button', { name: 'ใช้เวลาปกติ' }).click()
    await expect(page.getByRole('button', { name: 'ตื่น 06:30 พรุ่งนี้' })).toBeVisible()
    await expect(page.getByText('ตั้งเองสำหรับคืนนี้')).toHaveCount(0)
  })

  test('at night it counts down to bed, and before the wake time it is still last night', async ({ page }) => {
    await open(page, { now: at(5, 0) })
    await expect(page.getByText('ก่อนนอน')).toBeVisible()
    await expect(page.getByRole('button', { name: 'ตื่น 06:30' })).toBeVisible()
    await expect(page.getByText(/เมื่อคืนได้นอน/)).toHaveCount(0)
  })

  test('a wake time set on the watch arrives through the settings file and says where it came from', async ({ page }) => {
    await open(page, {
      before: (d) => d.omniNote('omni-settings.json', settingsFile(Date.now() + 1000, {
        'sleep.tonight.wake': { string: '2026-10-08 05:15' },
        'sleep.wake.watch': { bool: true },
      })),
    })
    await expect(page.getByRole('button', { name: 'ตื่น 05:15 พรุ่งนี้' })).toBeVisible()
    await expect(page.getByText('ตั้งจากนาฬิกา')).toBeVisible()
  })
})

test.describe('settings shared with Android', () => {
  test('the Focus choices, filters, folds, the look and reminders come from the file and go back in Android\'s form', async ({ page }) => {
    const { drive } = await open(page, {
      before: (d) => d.omniNote('omni-settings.json', settingsFile(1_900_000_000_000, {
        futureCount: { int: 2 },
        'focus.countdown': { string: 'เขียน paper' },
        dismissedSuggestions: { set: ['RAISE_PRIORITY:ตอบอีเมลทุน'] },
        reviewedTasks: { set: ['ทำเว็บคำนวณยา|2026-10-07'] },
        'sleep.tonight': { string: '2026-10-08 23:15' },
        savedFilters: { set: ['ด่วน\tTODO\u001FIN_PROGRESS\u001EHIGHEST\u001E\u001E\u001E\u001E\u001E'] },
        foldedGroups: { set: ['DATE:วันนี้', 'PRIORITY:สูง'] },
        foldedColumns: { set: ['DONE'] },
        'appearance.font': { string: 'PLEX_LOOPED' },
        'appearance.scale': { float: 1.149999976158142 },
        'appearance.theme': { string: 'DARK' },
        'appearance.palette': { string: 'LINEAR' },
        'notify.task': { bool: false },
        recurHistory: { set: ['รดน้ำต้นไม้|2026-10-01'] },
        vault: { string: 'content://android-only' },
      })),
    })
    // Focus follows the file at once.
    await expect(page.getByRole('button', { name: /นับถอยหลัง เขียน paper/ })).toBeVisible()
    await expect(page.getByText('มีคนรอมา 12 วันแล้ว ยกเป็นสำคัญ?')).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'นอน 23:15' })).toBeVisible()
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
    expect(await kept(page, 'omni.appearance')).toEqual({ theme: 'dark', palette: 'linear', font: 'plex', scale: 1.15 })
    expect(await kept(page, 'omni.filters')).toEqual([
      { name: 'ด่วน', query: expect.objectContaining({ statuses: ['TODO', 'IN_PROGRESS'], priorities: ['HIGHEST'], tags: [], groupBy: 'DATE' }) },
    ])
    expect(await page.evaluate(() => localStorage.getItem('omni.foldedGroups'))).toBe('DATE:วันนี้\nPRIORITY:สูง')
    expect(await page.evaluate(() => localStorage.getItem('omni.foldedColumns'))).toBe('DONE')
    expect((await kept(page, 'omni.notify')).task).toBe(false)

    // A change here goes back with every key in Android's form, the phone's own keys left alone.
    await goTo(page, 'ตั้งค่า')
    await expect(page.getByRole('switch', { name: 'เตือนตามเวลาในงาน' })).not.toBeChecked()
    await page.getByRole('radio', { name: /Noto Sans Thai Looped/ }).click()
    const file = () => JSON.parse(drive.textOf('omni-settings.json')!).settings
    await expect.poll(() => file()['appearance.font']).toEqual({ string: 'NOTO_LOOPED' })
    const s = file()
    expect(s['appearance.scale']).toEqual({ float: 1.15 })
    expect(s['appearance.theme']).toEqual({ string: 'DARK' })
    expect(s.futureCount).toEqual({ int: 2 })
    expect(s['focus.countdown']).toEqual({ string: 'เขียน paper' })
    expect(s['sleep.tonight']).toEqual({ string: '2026-10-08 23:15' })
    expect(s.reviewedTasks).toEqual({ set: ['ทำเว็บคำนวณยา|2026-10-07'] })
    expect(s.savedFilters).toEqual({ set: ['ด่วน\tTODO\u001FIN_PROGRESS\u001EHIGHEST\u001E\u001E\u001E\u001E\u001E'] })
    expect(s.foldedGroups).toEqual({ set: ['DATE:วันนี้', 'PRIORITY:สูง'] })
    expect(s.foldedColumns).toEqual({ set: ['DONE'] })
    expect(s['notify.task']).toEqual({ bool: false })
    expect(s.recurHistory).toEqual({ set: ['รดน้ำต้นไม้|2026-10-01'] })
    expect(s.vault).toEqual({ string: 'content://android-only' })
  })

  test('a group folded on the Tasks page and a countdown let go here reach the file', async ({ page }) => {
    const { drive } = await open(page, {
      before: (d) => d.omniNote('omni-settings.json', settingsFile(1_900_000_000_000, {
        'focus.countdown': { string: 'เขียน paper' },
        foldedGroups: { set: ['PRIORITY:สูง'] },
      })),
    })
    const file = () => JSON.parse(drive.textOf('omni-settings.json')!).settings
    await page.getByRole('button', { name: /นับถอยหลัง เขียน paper/ }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'เลิกนับ' }).click()
    // Android reads an empty countdown as none, so letting it go here lets it go on the phone too.
    await expect.poll(file).toMatchObject({ 'focus.countdown': { string: '' } })

    await page.evaluate(() => { location.hash = '/tasks' })
    await page.locator('.group-head').filter({ has: page.locator('.group-label', { hasText: /^วันนี้$/ }) }).click()
    await expect.poll(() => file().foldedGroups).toEqual({ set: ['DATE:วันนี้', 'PRIORITY:สูง'] })
  })

  test('the first time the web knows a key, the phone\'s value is taken even from an older file', async ({ page }) => {
    await page.addInitScript(() => { if (!localStorage.getItem('omni.settingsSyncedAt')) localStorage.setItem('omni.settingsSyncedAt', String(2_000_000_000_000)) })
    await open(page, {
      before: (d) => d.omniNote('omni-settings.json', settingsFile(1_900_000_000_000, { 'focus.countdown': { string: 'เขียน paper' }, urgentRule: { string: 'TWO_DAYS' } })),
    })
    await expect(page.getByRole('button', { name: /นับถอยหลัง เขียน paper/ })).toBeVisible()
    // A key the web already shared is not taken from an older file.
    expect(await page.evaluate(() => localStorage.getItem('omni.urgent'))).toBeNull()
  })

  test('ticking a repeating task notes the day in the history Android keeps', async ({ page }) => {
    const { drive } = await open(page, {
      note: '- [ ] รดน้ำต้นไม้ 🔁 every day ➕ 2026-10-01 📅 2026-10-08\n',
      before: (d) => d.omniNote('omni-settings.json', settingsFile(1_900_000_000_000, { recurHistory: { set: ['รดน้ำต้นไม้|2026-10-07'] } })),
    })
    await page.getByRole('checkbox', { name: /ติ๊กเสร็จ รดน้ำต้นไม้/ }).click()
    await expect.poll(() => JSON.parse(drive.textOf('omni-settings.json')!).settings.recurHistory)
      .toEqual({ set: ['รดน้ำต้นไม้|2026-10-07', 'รดน้ำต้นไม้|2026-10-08'] })
  })
})

test.describe('the assistant', () => {
  const moved = (n: number) => JSON.stringify({ id: `m${n}`, at: `2026-10-0${n}T08:00:00`, ev: 'move', src: 'android', title: 'ทบทวนเคส', field: 'DUE', from: `2026-10-0${n}`, to: `2026-10-0${n + 1}` })

  test('the morning summary is a card, with work that keeps being moved', async ({ page }) => {
    await open(page, { before: (d) => d.omniNote('omni-behavior.json', ['{"omni":"behavior","version":1}', moved(1), moved(2), moved(3)].join('\n') + '\n') })
    await goTo(page, 'ผู้ช่วย')
    const card = page.getByRole('button', { name: /สรุปเช้านี้/ })
    await expect(card).toContainText('เลยกำหนด 1, วันนี้ 3, คนรอ 1')
    await card.click()
    await expect(page.getByText('เลยกำหนด: ส่งรายงาน')).toBeVisible()
    await expect(page.getByText('รอ พี่เอ 12 วัน: ตอบอีเมลทุน')).toBeVisible()
    await expect(page.getByText('เลื่อนมาแล้ว 3 ครั้ง: ทบทวนเคส (แตกงานย่อย หรือย้ายไป #อนาคต?)')).toBeVisible()
  })

  test('plan today leaves out the future work skipped today on Focus', async ({ page }) => {
    await open(page)
    await page.getByRole('button', { name: 'ข้ามวันนี้' }).click()
    await goTo(page, 'ผู้ช่วย')
    await page.getByRole('button', { name: /จัดลำดับวันนี้/ }).first().click()
    await expect(page.locator('.as-rank').first()).toBeVisible()
    await expect(page.locator('.as-rank', { hasText: 'อ่าน Deep Work' })).toHaveCount(0)
  })
})

test.describe('reminders while the page is open', () => {
  test('a reminder rings at its time: a banner and the system\'s notification, put off for an hour, then done', async ({ page }) => {
    await fakeSystem(page)
    const { file, drive } = await open(page, { now: at(9, 59) })
    await loaded(page)
    // The badge counts the overdue work and what is due today.
    await expect.poll(() => page.evaluate(() => (window as any).__badge)).toBe(4)
    await expect(page.getByRole('group', { name: /^เตือน/ })).toHaveCount(0)

    await moveTo(page, at(10, 0))
    const banner = page.getByRole('group', { name: 'เตือน สไลด์ประชุม' })
    await expect(banner).toContainText('10:00')
    await expect(banner).toContainText('ครบ 2026-10-08')
    expect(await page.evaluate(() => (window as any).__notes)).toEqual([{ title: 'สไลด์ประชุม', body: 'ครบ 2026-10-08' }])

    await banner.getByRole('button', { name: 'อีก 1 ชั่วโมง' }).click()
    await expect(banner).toHaveCount(0)
    expect(await logged(page, drive)).toContain('"ev":"snooze"')
    expect(await logged(page, drive)).toContain('"min":60')

    // Each reminder shows once: a reload does not bring it back before the hour is up.
    await page.clock.setFixedTime(at(10, 30))
    await page.reload()
    await loaded(page)
    await moveTo(page, at(10, 40))
    await expect(banner).toHaveCount(0)

    // Past the hour of the sign-in: the page is opened again (which renews it), and the reminder put off rings.
    await page.clock.setFixedTime(at(11, 1))
    await page.reload()
    await loaded(page)
    await expect(banner).toBeVisible()
    await banner.getByRole('button', { name: 'เสร็จ' }).click()
    await expect(banner).toHaveCount(0)
    await expect.poll(() => file.text).toContain('- [x] สไลด์ประชุม')
    await expect(page.getByRole('img', { name: 'เสร็จแล้ว 1 จาก 4' })).toBeVisible()
  })

  test('reminders that rang while the page was away show when it is back, once each', async ({ page }) => {
    await fakeSystem(page, 'default')
    const { drive } = await open(page, { now: at(9, 0) })
    await loaded(page)
    await page.clock.setFixedTime(at(19, 45))
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')))
    await expect(page.getByRole('group', { name: 'เตือน สไลด์ประชุม' })).toBeVisible()
    await expect(page.getByRole('group', { name: 'เตือน โทรหาแม่' })).toBeVisible()
    // Not allowed: the banner only.
    expect(await page.evaluate(() => (window as any).__notes)).toEqual([])

    await page.getByRole('button', { name: 'ปิดการเตือน โทรหาแม่' }).click()
    await page.getByRole('group', { name: 'เตือน สไลด์ประชุม' }).getByRole('button', { name: 'พรุ่งนี้' }).click()
    await expect(page.getByRole('group', { name: /^เตือน/ })).toHaveCount(0)
    const log = await logged(page, drive)
    expect(log).toContain('"ev":"dismiss"')
    expect(log).toContain('"min":1440')

    await page.reload()
    await loaded(page)
    await moveTo(page, at(19, 46))
    await expect(page.getByRole('group', { name: /^เตือน/ })).toHaveCount(0)
  })

  test('Settings: reminders can be turned off (Android\'s switch), notifications allowed, and tried', async ({ page }) => {
    await fakeSystem(page, 'default')
    const { drive } = await open(page, { now: at(9, 59) })
    await loaded(page)
    await goTo(page, 'ตั้งค่า')
    const card = page.getByRole('region', { name: 'การแจ้งเตือน' })
    await expect(card).toContainText('บน iPad การแจ้งเตือนของเครื่องใช้ได้เมื่อเพิ่มแอปไว้ที่หน้าจอโฮม')
    await card.getByRole('button', { name: 'อนุญาต' }).click()
    await expect(card).toContainText('อนุญาตแล้ว')

    await card.getByRole('button', { name: 'ทดสอบ' }).click()
    await expect(page.getByRole('group', { name: 'เตือน ทดสอบการแจ้งเตือน' })).toBeVisible()
    expect(await page.evaluate(() => (window as any).__notes)).toEqual([{ title: 'ทดสอบการแจ้งเตือน', body: 'ถ้าเห็นข้อความนี้ แปลว่าการแจ้งเตือนใช้ได้แล้ว' }])
    await page.getByRole('button', { name: 'ปิดการเตือน ทดสอบการแจ้งเตือน' }).click()

    await card.getByRole('switch', { name: 'เตือนตามเวลาในงาน' }).click()
    await expect.poll(() => JSON.parse(drive.textOf('omni-settings.json') ?? '{}').settings?.['notify.task']).toEqual({ bool: false })
    await moveTo(page, at(10, 1))
    await expect(page.getByRole('group', { name: /^เตือน/ })).toHaveCount(0)
  })
})
