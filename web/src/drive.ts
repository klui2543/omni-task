// The little of Google Drive's REST API the app needs: find folders and files by name, read and replace text.

const API = 'https://www.googleapis.com/drive/v3'
const UPLOAD = 'https://www.googleapis.com/upload/drive/v3'
export const FOLDER = 'application/vnd.google-apps.folder'

export class AuthExpired extends Error {}
export class DriveError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

export interface DriveFile {
  id: string
  name: string
  mimeType?: string
  parents?: string[]
  version?: string
  modifiedTime?: string
}

/**
 * Whether two file names are the same to a person. Sync apps and systems store an emoji like 📁 in different ways
 * (with or without an invisible "show as emoji" mark, composed or not), so those differences are ignored.
 */
export const sameName = (a: string, b: string) => {
  const norm = (s: string) => s.normalize('NFC').replace(/[\uFE0E\uFE0F\u200B-\u200D]/g, '').trim()
  return norm(a) === norm(b)
}

const quote = (s: string) => `'${s.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`

export class Drive {
  /** [token] returns a valid access token, or throws [AuthExpired] when the owner must sign in again. */
  constructor(private token: () => string) {}

  private async call(url: string, init: RequestInit = {}): Promise<Response> {
    const res = await fetch(url, { ...init, headers: { ...init.headers, Authorization: `Bearer ${this.token()}` } })
    if (res.status === 401) throw new AuthExpired()
    if (!res.ok) throw new DriveError(res.status, `Drive ${res.status}: ${(await res.text()).slice(0, 200)}`)
    return res
  }

  /** A GET of any Google API with the owner's token (used for Calendar), answered as JSON. */
  async json(url: string): Promise<any> {
    return (await this.call(url)).json()
  }

  /** A POST of a JSON body to any Google API with the owner's token (used for Calendar), answered as JSON. */
  async postJson(url: string, body: unknown): Promise<any> {
    return (await this.call(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })).json()
  }

  /** Every match, following Drive's pages (a folder like Attachments can hold hundreds of files). */
  private async list(q: string, fields = 'files(id,name,mimeType,parents)'): Promise<DriveFile[]> {
    const out: DriveFile[] = []
    let pageToken = ''
    do {
      const params = new URLSearchParams({ q, fields: `nextPageToken,${fields}`, pageSize: '1000', supportsAllDrives: 'true', includeItemsFromAllDrives: 'true' })
      if (pageToken) params.set('pageToken', pageToken)
      const page = await (await this.call(`${API}/files?${params}`)).json()
      out.push(...(page.files ?? []))
      pageToken = page.nextPageToken ?? ''
    } while (pageToken)
    return out
  }

  /** Files with this exact name anywhere in the owner's Drive. */
  findFiles(name: string): Promise<DriveFile[]> {
    return this.list(`name = ${quote(name)} and trashed = false`)
  }

  /** The folders directly inside [parentId] ('root' is My Drive), by name. */
  async folders(parentId: string): Promise<DriveFile[]> {
    const found = await this.list(`${quote(parentId)} in parents and mimeType = '${FOLDER}' and trashed = false`)
    return found.sort((a, b) => a.name.localeCompare(b.name, 'th'))
  }

  /** Folders with this name anywhere in the owner's Drive. */
  findFolders(name: string): Promise<DriveFile[]> {
    return this.list(`name = ${quote(name)} and mimeType = '${FOLDER}' and trashed = false`)
  }

  /** A folder's own entry, for showing where it sits. */
  async get(id: string, fields = 'id,name,parents,version,modifiedTime'): Promise<DriveFile> {
    return (await this.call(`${API}/files/${id}?fields=${fields}&supportsAllDrives=true`)).json()
  }

  /**
   * The item called [name] directly inside [parentId]. Names are compared loosely (see [sameName]) on the folder's
   * listing rather than with Drive's exact name search, which misses a name whose emoji was written differently.
   */
  async child(parentId: string, name: string): Promise<DriveFile | null> {
    const found = await this.list(`${quote(parentId)} in parents and trashed = false`)
    return found.find((f) => sameName(f.name, name)) ?? null
  }

  /** Follows a path of folders and a file name down from [rootId]; null when any step is missing. */
  async resolve(rootId: string, path: string): Promise<DriveFile | null> {
    let current: DriveFile | null = { id: rootId, name: '' }
    for (const part of path.split('/')) {
      current = await this.child(current.id, part)
      if (!current) return null
    }
    return current
  }

  async readText(id: string): Promise<{ text: string; version: string }> {
    const version = (await this.get(id, 'version')).version ?? ''
    const text = await (await this.call(`${API}/files/${id}?alt=media&supportsAllDrives=true`)).text()
    return { text, version }
  }

  async version(id: string): Promise<string> {
    return (await this.get(id, 'version')).version ?? ''
  }

  /** Everything directly inside [parentId], with names, for spotting a sync app's conflict copies. */
  children(parentId: string): Promise<DriveFile[]> {
    return this.list(`${quote(parentId)} in parents and trashed = false`)
  }

  /** A new text file in [parentId]; returns its id. */
  async createText(parentId: string, name: string, text: string): Promise<string> {
    const boundary = 'omni' + Math.random().toString(36).slice(2)
    const body =
      `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n${JSON.stringify({ name, parents: [parentId], mimeType: 'text/markdown' })}\r\n` +
      `--${boundary}\r\nContent-Type: text/markdown; charset=UTF-8\r\n\r\n${text}\r\n--${boundary}--`
    const res = await this.call(`${UPLOAD}/files?uploadType=multipart&supportsAllDrives=true&fields=id`, {
      method: 'POST',
      headers: { 'Content-Type': `multipart/related; boundary=${boundary}` },
      body,
    })
    return (await res.json()).id
  }

  /** Moves a file to Drive's trash, where it can still be restored. */
  async trash(id: string): Promise<void> {
    await this.call(`${API}/files/${id}?supportsAllDrives=true`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ trashed: true }),
    })
  }

  async writeText(id: string, text: string): Promise<void> {
    await this.call(`${UPLOAD}/files/${id}?uploadType=media&supportsAllDrives=true`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'text/markdown; charset=UTF-8' },
      body: text,
    })
  }

  /** A new folder in [parentId]; returns its id. */
  async createFolder(parentId: string, name: string): Promise<string> {
    const res = await this.call(`${API}/files?supportsAllDrives=true&fields=id`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, parents: [parentId], mimeType: FOLDER }),
    })
    return (await res.json()).id
  }
}
