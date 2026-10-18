import { describe, expect, it, vi } from 'vitest'
import { Google, SCOPES, type GoogleStore, type HttpRequest } from '../src/google'
import { VaultDrive } from '../../web/src/vaultDrive'
import { createEvent, listCalendars, readEvents } from '../../web/src/calendar'
import { memoryVault } from './memoryVault'

const REDIRECT = 'https://klui2543.github.io/omni-task/obsidian.html'
const CAL = 'https://www.googleapis.com/calendar/v3'

/** Google's servers as far as the link uses them, with a clock the test moves. */
function fakeGoogle() {
  const calls: HttpRequest[] = []
  const opened: string[] = []
  let store: GoogleStore = {}
  let clock = 1_000_000
  let tokens = 0
  const live = new Set<string>()
  const grants = { refresh: 'refresh-1', revoked: false, secret: 'GOCSPX-right' }
  const json = (status: number, body: unknown) => ({ status, text: JSON.stringify(body) })
  const google = new Google({
    clientId: 'client.apps.googleusercontent.com',
    redirectUri: REDIRECT,
    vaultName: 'Klui Vault',
    load: () => store,
    save: async (s) => { store = s },
    now: () => clock,
    open: (url) => { opened.push(url) },
    async http(req) {
      calls.push(req)
      if (req.url === 'https://oauth2.googleapis.com/token') {
        const f = new URLSearchParams(req.body)
        if (f.get('client_secret') !== grants.secret) return json(401, { error: 'invalid_client' })
        if (f.get('grant_type') === 'authorization_code') {
          if (f.get('code') !== 'the-code' || !f.get('code_verifier')) return json(400, { error: 'invalid_grant' })
          const t = `access-${++tokens}`
          live.add(t)
          return json(200, { access_token: t, expires_in: 3600, refresh_token: grants.refresh })
        }
        if (grants.revoked || f.get('refresh_token') !== grants.refresh) return json(400, { error: 'invalid_grant' })
        const t = `access-${++tokens}`
        live.add(t)
        return json(200, { access_token: t, expires_in: 3600 })
      }
      if (req.url.startsWith('https://oauth2.googleapis.com/revoke')) return json(200, {})
      const token = req.headers?.Authorization?.replace('Bearer ', '') ?? ''
      if (!live.has(token)) return json(401, { error: 'unauthorized' })
      if (req.url === `${CAL}/calendars/primary`) return json(200, { id: 'klui@gmail.com' })
      if (req.url.startsWith(`${CAL}/users/me/calendarList`)) return json(200, { items: [{ id: 'klui@gmail.com', summary: 'Klui', primary: true }, { id: 'shift', summary: 'เวร' }] })
      if (req.url.startsWith(`${CAL}/calendars/`) && req.method === 'GET') {
        return json(200, { items: [{ id: 'e1', summary: 'เวรเช้า', start: { dateTime: '2026-10-19T08:00:00+07:00' }, end: { dateTime: '2026-10-19T16:00:00+07:00' } }] })
      }
      if (req.url === `${CAL}/calendars/primary/events` && req.method === 'POST') return json(200, { htmlLink: 'https://calendar.google.com/e1' })
      return json(404, {})
    },
  })
  return {
    google, calls, opened, grants, live,
    store: () => store,
    tick: (ms: number) => { clock += ms },
    /** What the browser hands back after the sign-in, with the state the link sent out. */
    answer: (extra: Record<string, string> = { code: 'the-code' }) => ({ state: new URL(opened[opened.length - 1]).searchParams.get('state')!, ...extra }),
  }
}


/** Waits for the browser to be opened at Google (the proof is made first). */
const opened = (g: ReturnType<typeof fakeGoogle>, count = 1) => vi.waitFor(() => { if (g.opened.length < count) throw new Error('not yet') })

async function linked() {
  const g = fakeGoogle()
  await g.google.setSecret('GOCSPX-right')
  g.google.connect()
  await opened(g)
  expect(await g.google.finish(g.answer())).toBe(true)
  return g
}

describe('Google Calendar in the Omni Task tab', () => {
  it('asks for the client secret first, then opens the browser at Google with both permissions and a proof', async () => {
    const g = fakeGoogle()
    expect(g.google.state()).toBe('nosecret')
    g.google.connect()
    expect(g.opened).toEqual([])
    await g.google.setSecret('  GOCSPX-right ')
    expect(g.store().secret).toBe('GOCSPX-right')
    expect(g.google.state()).toBe('off')
    g.google.connect()
    expect(g.google.state()).toBe('waiting')
    await opened(g)
    const url = new URL(g.opened[0])
    expect(url.origin + url.pathname).toBe('https://accounts.google.com/o/oauth2/v2/auth')
    expect(url.searchParams.get('redirect_uri')).toBe(REDIRECT)
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('access_type')).toBe('offline')
    expect(url.searchParams.get('scope')).toBe(SCOPES.join(' '))
    expect(url.searchParams.get('code_challenge_method')).toBe('S256')
    // The vault's name rides in the state, for the page that hands the answer back.
    expect(url.searchParams.get('state')).toMatch(/~Klui Vault$/)
    // The secret never goes to the browser.
    expect(g.opened[0]).not.toContain('GOCSPX')
  })

  it('makes the link from the answer the browser brings back, and names the account', async () => {
    const g = await linked()
    expect(g.google.state()).toBe('on')
    expect(g.google.email()).toBe('klui@gmail.com')
    expect(g.store().refreshToken).toBe('refresh-1')
    expect(g.store().pending).toBeUndefined()
    const exchange = new URLSearchParams(g.calls.find((c) => c.body?.includes('authorization_code'))!.body)
    expect(exchange.get('redirect_uri')).toBe(REDIRECT)
    expect(exchange.get('code_verifier')!.length).toBeGreaterThan(40)
  })

  it('ignores an answer it did not ask for, and one after cancelling', async () => {
    const g = fakeGoogle()
    await g.google.setSecret('GOCSPX-right')
    g.google.connect()
    await opened(g)
    expect(await g.google.finish({ code: 'the-code', state: 'someone-else' })).toBe(false)
    expect(g.google.state()).toBe('waiting')
    const answer = g.answer()
    g.google.cancel()
    expect(g.google.state()).toBe('off')
    expect(await g.google.finish(answer)).toBe(false)
    expect(g.google.state()).toBe('off')
  })

  it('says so when the owner did not allow it in Google\'s page', async () => {
    const g = fakeGoogle()
    await g.google.setSecret('GOCSPX-right')
    g.google.connect()
    await opened(g)
    expect(await g.google.finish(g.answer({ error: 'access_denied' }))).toBe(false)
    expect(g.google.state()).toBe('off')
    expect(g.google.error()).toContain('ยังไม่ได้อนุญาต')
  })

  it('asks for the secret again when Google does not take it', async () => {
    const g = fakeGoogle()
    await g.google.setSecret('GOCSPX-wrong')
    g.google.connect()
    await opened(g)
    expect(await g.google.finish(g.answer())).toBe(false)
    expect(g.google.state()).toBe('nosecret')
    expect(g.google.error()).toContain('ไม่รับรหัสลับ')
  })

  it('renews the hour-long permission by itself, and once more when Google turns one down early', async () => {
    const g = await linked()
    await g.google.call(`${CAL}/users/me/calendarList`)
    const renewals = () => g.calls.filter((c) => c.body?.includes('grant_type=refresh_token')).length
    expect(renewals()).toBe(0)
    g.tick(3600_000)
    await g.google.call(`${CAL}/users/me/calendarList`)
    expect(renewals()).toBe(1)
    // Withdrawn on Google's side before its hour was up.
    g.live.clear()
    await g.google.call(`${CAL}/users/me/calendarList`)
    expect(renewals()).toBe(2)
    expect(g.google.state()).toBe('on')
  })

  it('turns to "revoked" when the owner withdrew the permission at Google', async () => {
    const g = await linked()
    g.grants.revoked = true
    g.live.clear()
    g.tick(3600_000)
    await expect(g.google.call(`${CAL}/users/me/calendarList`)).rejects.toMatchObject({ status: 401 })
    expect(g.google.state()).toBe('revoked')
    expect(g.store().refreshToken).toBeUndefined()
  })

  it('lets go on "disconnect" and asks Google to withdraw the permission', async () => {
    const g = await linked()
    await g.google.disconnect()
    expect(g.google.state()).toBe('off')
    expect(g.google.email()).toBeNull()
    expect(g.calls.some((c) => c.url.startsWith('https://oauth2.googleapis.com/revoke') && c.body === 'token=refresh-1')).toBe(true)
  })

  it('serves the app\'s calendar calls through the vault\'s Drive: the list, the events, and a new event', async () => {
    const g = await linked()
    const drive = new VaultDrive(memoryVault({}).fs, 'obsidian:test', g.google)
    expect((await listCalendars(drive)).map((c) => c.name)).toEqual(['Klui', 'เวร'])
    const events = await readEvents(drive, new Date('2026-10-19T00:00:00+07:00'), new Date('2026-10-20T00:00:00+07:00'))
    expect(events.map((e) => e.title)).toEqual(['เวรเช้า', 'เวรเช้า'])
    expect(await createEvent(drive, { title: 'ประชุม', day: '2026-10-20', start: '13:30', minutes: 60 })).toBe('https://calendar.google.com/e1')
    const post = g.calls.find((c) => c.method === 'POST' && c.url.endsWith('/events'))!
    expect(JSON.parse(post.body!).summary).toBe('ประชุม')
  })

  it('says the calendar is not linked when there is no link', async () => {
    const drive = new VaultDrive(memoryVault({}).fs, 'obsidian:test')
    await expect(listCalendars(drive)).rejects.toMatchObject({ code: 'denied' })
  })
})
