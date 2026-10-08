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
  /** Runs once, right after the app's next read of a file, to play someone else editing it. */
  afterNextRead: ((n: Node) => void) | null = null

  add(name: string, parent: string | null, text?: string): Node {
    const n: Node = { id: `id${this.next++}`, name, folder: text === undefined, parent, text, version: 1 }
    this.nodes.push(n)
    return n
  }

  /** Builds a vault folder with the TaskForge note where the app looks for it. */
  vault(taskText: string, vaultName = 'ObsidianVault') {
    const root = this.add(vaultName, 'root')
    const a = this.add('📁 Folder', root.id)
    const b = this.add('หลังบ้าน', a.id)
    const c = this.add('TaskForge', b.id)
    const file = this.add('TaskForge.md', c.id, taskText)
    return { root, file }
  }

  private find(q: string) {
    const name = /name = '((?:[^'\\]|\\.)*)'/.exec(q)?.[1]?.replace(/\\(.)/g, '$1')
    const parent = /'([^']+)' in parents/.exec(q)?.[1]
    const folderOnly = q.includes('google-apps.folder')
    return this.nodes.filter((n) => (!name || n.name === name) && (!parent || n.parent === parent) && (!folderOnly || n.folder))
  }

  async install(page: Page) {
    await page.route('https://accounts.google.com/gsi/client', (route) =>
      route.fulfill({
        contentType: 'text/javascript',
        body: `window.google = { accounts: { oauth2: {
          initTokenClient: (c) => ({ requestAccessToken: () => setTimeout(() => c.callback({ access_token: 'fake-token', expires_in: 3600 }), 10) }),
          revoke: () => {} } } }`,
      }),
    )
    await page.route('https://www.googleapis.com/**', async (route) => {
      const req = route.request()
      const url = new URL(req.url())
      this.requests.push(`${req.method()} ${url.pathname}${url.search}`)
      const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
      if (req.headers()['authorization'] !== 'Bearer fake-token') return route.fulfill({ status: 401, body: 'no' })

      const m = /\/files\/?([^/?]*)$/.exec(url.pathname)
      const id = m?.[1]
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
