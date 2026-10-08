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

  private async list(q: string, fields = 'files(id,name,mimeType,parents)'): Promise<DriveFile[]> {
    const params = new URLSearchParams({ q, fields, pageSize: '100', supportsAllDrives: 'true', includeItemsFromAllDrives: 'true' })
    return (await (await this.call(`${API}/files?${params}`)).json()).files ?? []
  }

  /** Folders with this name anywhere in the owner's Drive. */
  findFolders(name: string): Promise<DriveFile[]> {
    return this.list(`name = ${quote(name)} and mimeType = '${FOLDER}' and trashed = false`)
  }

  /** A folder's own entry, for showing where it sits. */
  async get(id: string, fields = 'id,name,parents,version,modifiedTime'): Promise<DriveFile> {
    return (await this.call(`${API}/files/${id}?fields=${fields}&supportsAllDrives=true`)).json()
  }

  async child(parentId: string, name: string): Promise<DriveFile | null> {
    const found = await this.list(`${quote(parentId)} in parents and name = ${quote(name)} and trashed = false`)
    return found[0] ?? null
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

  async writeText(id: string, text: string): Promise<void> {
    await this.call(`${UPLOAD}/files/${id}?uploadType=media&supportsAllDrives=true`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'text/markdown; charset=UTF-8' },
      body: text,
    })
  }
}
