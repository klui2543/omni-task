// The choices the web shares with Android, through Android's own settings file in the vault (`omni-settings.json` in
// the Omni folder): the projects page (order, stars, branch states, task order, "do in order"), the task kinds, what
// counts as urgent, and the archive choices. Android writes every setting it has with a `savedAt` time and reads
// the file back when it is newer than its last sync; the web does the same with the keys it understands and leaves
// every other key as Android wrote it. Everything else the web remembers stays on this device.
import { legacyOmniDirPath, omniDirPath } from './core'
import { Drive, DriveError, type Versioned } from './drive'

/** One setting as Android writes it: its kind of value, and the value. */
interface Value {
  string?: string
  set?: string[]
  int?: number
  long?: number
  float?: number
  bool?: boolean
}

interface SettingsFile {
  version?: number
  savedAt?: number
  settings?: Record<string, Value>
}

const NAME = 'omni-settings.json'
const SYNCED_KEY = 'omni.settingsSyncedAt'

const get = (key: string): string | null => {
  try { return localStorage.getItem(key) } catch { return null }
}
const put = (key: string, value: string) => {
  try { localStorage.setItem(key, value) } catch { /* just not kept */ }
}
const json = <T>(key: string, fallback: T): T => {
  try { return { ...fallback, ...JSON.parse(get(key) ?? '{}') } } catch { return fallback }
}

const URGENT = ['TWO_DAYS', 'THREE_DAYS', 'THIS_WEEK']
const sorted = (xs: string[]) => [...xs].sort()

interface Projects { order: string[]; starred: string[]; branches: string[]; taskOrder: Record<string, string[]>; strict: string[] }
const NO_PROJECTS: Projects = { order: [], starred: [], branches: [], taskOrder: {}, strict: [] }

/** This device's choices, as Android keys and values. */
export function localSettings(): Record<string, Value> {
  const p = json<Projects>('omni.projects', NO_PROJECTS)
  const k = json<{ custom: string[]; hidden: string[] }>('omni.kinds', { custom: [], hidden: [] })
  const out: Record<string, Value> = {
    projectOrder: { string: p.order.join('\n') },
    starredProjects: { set: sorted(p.starred) },
    projectTaskOrder: { set: sorted(Object.entries(p.taskOrder).map(([name, keys]) => name + '\t' + keys.join('\u001F'))) },
    strictProjects: { set: sorted(p.strict) },
    branchStates: { set: sorted(p.branches) },
    customKinds: { set: sorted(k.custom) },
    hiddenKinds: { set: sorted(k.hidden) },
  }
  const urgent = get('omni.urgent')
  if (urgent && URGENT.includes(urgent)) out.urgentRule = { string: urgent }
  const days = get('omni.archiveDays')
  if (days !== null && Number.isInteger(Number(days))) out['archive.days'] = { int: Number(days) }
  const ask = get('omni.askOnDone')
  if (ask !== null) out['archive.askOnDone'] = { bool: ask !== 'no' }
  return out
}

/** Takes Android's values for the keys the web understands into this device's choices. */
export function applySettings(s: Record<string, Value>) {
  const p = json<Projects>('omni.projects', NO_PROJECTS)
  const k = json<{ custom: string[]; hidden: string[] }>('omni.kinds', { custom: [], hidden: [] })
  const set = (key: string) => s[key]?.set
  if (s.projectOrder?.string !== undefined) p.order = s.projectOrder.string.split('\n').filter((x) => x)
  if (set('starredProjects')) p.starred = set('starredProjects')!
  if (set('projectTaskOrder')) {
    p.taskOrder = Object.fromEntries(set('projectTaskOrder')!.map((e) => {
      const at = e.indexOf('\t')
      return at < 0 ? [e, []] : [e.slice(0, at), e.slice(at + 1).split('\u001F').filter((x) => x)]
    }))
  }
  if (set('strictProjects')) p.strict = set('strictProjects')!
  if (set('branchStates')) p.branches = set('branchStates')!
  if (set('customKinds')) k.custom = set('customKinds')!
  if (set('hiddenKinds')) k.hidden = set('hiddenKinds')!
  put('omni.projects', JSON.stringify(p))
  put('omni.kinds', JSON.stringify(k))
  const urgent = s.urgentRule?.string
  if (urgent && URGENT.includes(urgent)) put('omni.urgent', urgent)
  const days = s['archive.days']?.int
  if (typeof days === 'number' && days !== Number(get('omni.archiveDays') ?? NaN)) {
    put('omni.archiveDays', String(days))
    // A new choice sweeps again at the next load, as on Android.
    try { localStorage.removeItem('omni.archiveSweptOn') } catch { /* nothing kept */ }
  }
  const ask = s['archive.askOnDone']?.bool
  if (typeof ask === 'boolean') put('omni.askOnDone', ask ? 'yes' : 'no')
}

const same = (a: Value | undefined, b: Value | undefined) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null)

/** The settings file of one vault on Drive: read when the page loads, written when a shared choice changes here. */
export class SettingsSync {
  private fileId: string | null = null
  /** Whether the file was read once in this visit: no write goes out before, so this device never overwrites Android's choices with its defaults. */
  private pulled = false
  private wantPush = false

  constructor(private drive: Drive, private rootId: string, private ensureFolders: (path: string) => Promise<string>) {}

  private async file(): Promise<string | null> {
    if (this.fileId) return this.fileId
    const found = (await this.drive.resolve(this.rootId, `${omniDirPath}/${NAME}`)) ?? (await this.drive.resolve(this.rootId, `${legacyOmniDirPath}/${NAME}`))
    return (this.fileId = found?.id ?? null)
  }

  private async read(): Promise<(Versioned & { id: string; data: SettingsFile }) | null> {
    const id = await this.file()
    if (!id) return null
    let read: Versioned
    try {
      read = await this.drive.readText(id)
    } catch (e) {
      if (e instanceof DriveError && e.status === 404) {
        this.fileId = null
        return null
      }
      throw e
    }
    let data: SettingsFile = {}
    try { data = JSON.parse(read.text) } catch { /* a broken file is written anew */ }
    return { ...read, id, data }
  }

  /** Takes the file in when it was saved after this device last synced. True when a choice changed here. */
  async pull(): Promise<boolean> {
    const file = await this.read()
    this.pulled = true
    let changed = false
    const savedAt = file?.data.savedAt ?? 0
    if (file && savedAt > Number(get(SYNCED_KEY) ?? 0) && file.data.settings) {
      const before = JSON.stringify(localSettings())
      applySettings(file.data.settings)
      put(SYNCED_KEY, String(savedAt))
      changed = JSON.stringify(localSettings()) !== before
    }
    if (this.wantPush) await this.push()
    return changed
  }

  /** Writes this device's choices into the file, leaving Android's other keys alone; nothing is written when they match. */
  async push(): Promise<void> {
    if (!this.pulled) {
      this.wantPush = true
      return
    }
    this.wantPush = false
    const mine = localSettings()
    for (let attempt = 0; attempt < 3; attempt++) {
      const file = await this.read()
      const settings = { ...(file?.data.settings ?? {}) }
      const differs = Object.entries(mine).some(([k, v]) => !same(settings[k], v))
      if (!differs) {
        if (file?.data.savedAt) put(SYNCED_KEY, String(file.data.savedAt))
        return
      }
      Object.assign(settings, mine)
      // Later than the file's own time, so a phone whose clock runs ahead still takes it as newer.
      const savedAt = Math.max(Date.now(), (file?.data.savedAt ?? 0) + 1)
      const text = JSON.stringify({ version: file?.data.version ?? 1, savedAt, settings }, null, 2)
      if (!file) {
        const folder = await this.ensureFolders(omniDirPath)
        this.fileId = await this.drive.createText(folder, NAME, text, 'application/json')
      } else {
        if ((await this.drive.version(file.id)) !== file.version) continue
        await this.drive.writeText(file.id, text, 'application/json')
      }
      put(SYNCED_KEY, String(savedAt))
      return
    }
  }
}
