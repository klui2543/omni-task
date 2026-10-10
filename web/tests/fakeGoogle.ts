import type { Page } from '@playwright/test'

/** A Google Drive in memory, with just the calls the app makes, plus a stand-in for Google's sign-in script. */
interface Node {
  id: string
  name: string
  folder: boolean
  parent: string | null
  text?: string
  version: number
}

export class FakeDrive {
  nodes: Node[] = []
  private next = 1
  requests: string[] = []
  /** The prompt of each trip to Google's sign-in page. */
  signIns: string[] = []
  /** Makes Google refuse quiet renewals, as when the owner has signed out of Google meanwhile. */
  refuseQuiet = false
  /** Google Calendar: the events one calendar holds, and whether the Calendar API is switched off for the project. */
  events: { title: string; begin: Date; end: Date; allDay?: boolean }[] = []
  calendarOff = false
  /** Whether the owner has agreed to read the calendar; set by the sign-in page when it is asked for. */
  calendarGranted = false
  /** Runs once, right after the app's next read of a file, to play someone else editing it. */
  afterNextRead: ((n: Node) => void) | null = null

  add(name: string, parent: string | null, text?: string): Node {
    const n: Node = { id: `id${this.next++}`, name, folder: text === undefined, parent, text, version: 1 }
    this.nodes.push(n)
    return n
  }

  /** Builds a vault folder with the TaskForge note where the app looks for it. */
  vault(taskText: string, vaultName = 'ObsidianVault', folderName = '📁 Folder') {
    const root = this.add(vaultName, 'root')
    const a = this.add(folderName, root.id)
    const b = this.add('หลังบ้าน', a.id)
    const c = this.add('TaskForge', b.id)
    const file = this.add('TaskForge.md', c.id, taskText)
    return { root, file }
  }

  /* ---------- Projects ---------- */

  /** A note in the vault's Omni folder (made when missing), e.g. a list note; returns it so a test can read its text later. */
  omniNote(name: string, text: string, vaultName = 'ObsidianVault') {
    const root = this.nodes.find((n) => n.name === vaultName && n.folder)!
    const omni = this.nodes.find((n) => n.name === 'Omni' && n.parent === root.id) ?? this.add('Omni', root.id)
    return this.add(name, omni.id, text)
  }

  /** The text of the note called [name], or null when there is none. */
  textOf(name: string) {
    return this.nodes.find((n) => n.name === name && !n.folder)?.text ?? null
  }

  private find(q: string) {
    const name = /name = '((?:[^'\\]|\\.)*)'/.exec(q)?.[1]?.replace(/\\(.)/g, '$1')
    const parent = /'([^']+)' in parents/.exec(q)?.[1]
    const folderOnly = q.includes('google-apps.folder')
    return this.nodes.filter((n) => (!name || n.name === name) && (!parent || n.parent === parent) && (!folderOnly || n.folder))
  }

  async install(page: Page) {
    // Google's sign-in page: sends the browser straight back with a token (or, for a quiet try it is told to
    // refuse, with Google's error), as the real one does once the owner has agreed.
    await page.route('https://accounts.google.com/o/oauth2/v2/auth**', (route) => {
      const q = new URL(route.request().url()).searchParams
      this.signIns.push(q.get('prompt') ?? '')
      const back = new URLSearchParams({ state: q.get('state') ?? '' })
      if (q.get('scope')?.includes('calendar.readonly')) this.calendarGranted = true
      if (q.get('prompt') === 'none' && this.refuseQuiet) back.set('error', 'interaction_required')
      else {
        // The token covers what was agreed to before as well, as Google does with include_granted_scopes.
        back.set('scope', 'https://www.googleapis.com/auth/drive' + (this.calendarGranted ? ' https://www.googleapis.com/auth/calendar.readonly' : ''))
        back.set('access_token', 'fake-token')
        back.set('token_type', 'Bearer')
        back.set('expires_in', '3600')
      }
      return route.fulfill({ status: 302, headers: { location: `${q.get('redirect_uri')}#${back}` } })
    })
    await page.route('https://www.googleapis.com/**', async (route) => {
      const req = route.request()
      const url = new URL(req.url())
      this.requests.push(`${req.method()} ${url.pathname}${url.search}`)
      const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
      if (req.headers()['authorization'] !== 'Bearer fake-token') return route.fulfill({ status: 401, body: 'no' })

      if (url.pathname === '/calendar/v3/users/me/calendarList') {
        if (this.calendarOff) return route.fulfill({ status: 403, body: 'accessNotConfigured: Google Calendar API has not been used in project' })
        return json({ items: [{ id: 'primary', selected: true }] })
      }
      if (url.pathname === '/calendar/v3/calendars/primary/events') {
        const from = new Date(url.searchParams.get('timeMin')!).getTime()
        const to = new Date(url.searchParams.get('timeMax')!).getTime()
        const day = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
        const items = this.events
          .filter((e) => e.end.getTime() > from && e.begin.getTime() < to)
          .map((e, i) => ({
            id: `e${i}`, summary: e.title,
            start: e.allDay ? { date: day(e.begin) } : { dateTime: e.begin.toISOString() },
            end: e.allDay ? { date: day(e.end) } : { dateTime: e.end.toISOString() },
          }))
        return json({ items })
      }

      const m = /\/files\/?([^/?]*)$/.exec(url.pathname)
      const id = m?.[1]
      // A new folder is a plain JSON post (a new note is a multipart upload).
      if (req.method() === 'POST' && !id && (req.headers()['content-type'] ?? '').startsWith('application/json')) {
        const meta = JSON.parse(req.postData() ?? '{}')
        return json({ id: this.add(meta.name, meta.parents[0]).id })
      }
      if (req.method() === 'POST' && !id) {
        // A multipart upload: the metadata part, then the text.
        const body = req.postData() ?? ''
        const boundary = /boundary=(\S+)/.exec(req.headers()['content-type'] ?? '')![1]
        const parts = body.split(`--${boundary}`).slice(1, 3).map((p) => p.slice(p.indexOf('\r\n\r\n') + 4).replace(/\r\n$/, ''))
        const meta = JSON.parse(parts[0])
        const n = this.add(meta.name, meta.parents[0], parts[1])
        return json({ id: n.id })
      }
      if (req.method() === 'GET' && !id) {
        return json({ files: this.find(url.searchParams.get('q') ?? '').map((n) => ({ id: n.id, name: n.name, parents: n.parent ? [n.parent] : [] })) })
      }
      const node = this.nodes.find((n) => n.id === id)
      if (!node) return route.fulfill({ status: 404, body: 'not found' })
      if (req.method() === 'GET' && url.searchParams.get('alt') === 'media') {
        const text = node.text!
        const hook = this.afterNextRead
        this.afterNextRead = null
        hook?.(node)
        return route.fulfill({ contentType: 'text/plain', body: text })
      }
      if (req.method() === 'GET') {
        return json({ id: node.id, name: node.name, parents: node.parent ? [node.parent] : [], version: String(node.version) })
      }
      if (req.method() === 'PATCH' && !url.pathname.startsWith('/upload')) {
        if (JSON.parse(req.postData() ?? '{}').trashed) this.nodes = this.nodes.filter((n) => n !== node)
        return json({ id: node.id })
      }
      if (req.method() === 'PATCH') {
        node.text = req.postData() ?? ''
        node.version++
        return json({ id: node.id })
      }
      return route.fulfill({ status: 400, body: 'unsupported' })
    })
  }
}

export const TASKS = [
  '# งาน',
  '- [ ] ส่งรายงาน #งาน ⏫ ➕ 2026-10-01 📅 2026-10-07',
  '- [ ] โทรหาแม่ #remind-at-due ⏰ 09:30 📅 2026-10-08',
  '- [ ] ซื้อนม 🔁 every day ➕ 2026-10-01 📅 2026-10-08',
  '- [ ] อ่านหนังสือ',
  '- [x] เสร็จแล้วเมื่อวาน ✅ 2026-10-07',
  '',
].join('\n')
