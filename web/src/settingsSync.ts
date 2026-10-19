// The choices the web shares with Android, through Android's own settings file in the vault (`omni-settings.json` in
// the Omni folder): the projects page (which tags are projects, order, stars, branch states, task order, "do in
// order"), the task kinds, what counts as urgent, the archive choices, the Focus page's choices (countdown, future
// work, suggestions waved away, reviews, tonight's sleep), saved filters, folded groups and columns, the look, task
// reminders on or off and when each repeating task was done. Android writes every setting it has with a `savedAt`
// time and reads the file back when it is newer than its last sync; the web does the same with the keys it
// understands and leaves every other key as Android wrote it. Everything else the web remembers stays on this device.
import { applyProjectTags, legacyOmniDirPath, omniDirPath } from './core'
import { Drive, DriveError, type Versioned } from './drive'
import { countdownPins, focusLocal } from './focusState'
import { DEFAULT_QUERY, type SavedFilter } from './query'
import { recurHistory } from './recurHistory'
import { appearance, appearanceFromAndroid, appearanceToAndroid, setAppearance } from './settingsAppearance'
import { settingsBus } from './settingsBus'
import { notifyChoice } from './settingsNotify'

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

interface Projects { order: string[]; starred: string[]; branches: string[]; taskOrder: Record<string, string[]>; strict: string[]; projectTags?: string[] }
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
  // Only once chosen: an empty set would turn every project into a plain tag on Android.
  if (p.projectTags) out.projectTags = { set: sorted(p.projectTags) }
  const urgent = get('omni.urgent')
  if (urgent && URGENT.includes(urgent)) out.urgentRule = { string: urgent }
  const days = get('omni.archiveDays')
  if (days !== null && Number.isInteger(Number(days))) out['archive.days'] = { int: Number(days) }
  const ask = get('omni.askOnDone')
  if (ask !== null) out['archive.askOnDone'] = { bool: ask !== 'no' }

  // The Focus page. Android removes a countdown or a wake time that is let go; the web writes it empty instead, which
  // Android reads as none, so letting go here also lets go on the phone.
  const f = focusLocal.get()
  out.futureCount = { int: f.futureCount }
  out.skippedFuture = { set: sorted(f.skipped) }
  out.dismissedSuggestions = { set: sorted(f.dismissed) }
  out.reviewedTasks = { set: sorted(Object.entries(f.reviewed).map(([title, day]) => `${title}|${day}`)) }
  // The countdowns, in Android's form (focus.countdowns); the single one of before is carried by them, so it is let go
  // as Android lets it go once it saves them.
  out['focus.countdowns'] = { set: sorted(countdownPins(f)) }
  out['focus.countdown'] = { string: '' }
  out['sleep.tonight'] = { string: nightValue(f.tonightBed) }
  out['sleep.tonight.wake'] = { string: nightValue(f.tonightWake) }
  if (typeof f.wakeFromWatch === 'boolean') out['sleep.wake.watch'] = { bool: f.wakeFromWatch }

  // Saved filters, folded groups and Kanban columns, kept by the Tasks page and the Kanban under their own keys.
  out.savedFilters = { set: sorted(filters().map((x) => `${x.name}\t${encodeFilter(x.query)}`)) }
  out.foldedGroups = { set: sorted(foldedGroups()) }
  out.foldedColumns = { set: sorted(lines('omni.foldedColumns', ',')) }

  const look = appearanceToAndroid(appearance())
  out['appearance.font'] = { string: look.font }
  out['appearance.scale'] = { float: look.scale }
  out['appearance.theme'] = { string: look.theme }
  out['appearance.palette'] = { string: look.palette }

  const task = notifyChoice.get().task
  if (typeof task === 'boolean') out['notify.task'] = { bool: task }
  out.recurHistory = { set: sorted(recurHistory.get()) }
  return out
}

/* ---------- Keys whose values the web keeps in its own form ---------- */

/** Tonight's bed or wake time as Android keeps it, "yyyy-MM-dd HH:mm"; empty for none. */
const nightValue = (n: { evening: string; time: string } | null | undefined) => (n ? `${n.evening} ${n.time}` : '')
const nightOf = (v: string): { evening: string; time: string } | null => {
  const m = /^(\d{4}-\d\d-\d\d) (\d\d?):(\d\d)/.exec(v)
  return m ? { evening: m[1], time: `${m[2].padStart(2, '0')}:${m[3]}` } : null
}

const lines = (key: string, sep: string) => (get(key) ?? '').split(sep).filter((x) => x)

const filters = (): SavedFilter[] => {
  try {
    const v = JSON.parse(get('omni.filters') ?? '[]')
    return Array.isArray(v) ? v : []
  } catch {
    return []
  }
}

// A saved filter as Android's TaskQuery.encode: its seven filters, each a list joined by \u001F, joined by \u001E.
// Grouping and sorting are not in it; a filter from Android keeps the ones the web saved under that name, if any.
const RS = '\u001E'
const US = '\u001F'
type FilterQuery = SavedFilter['query']
const FILTER_FIELDS = ['statuses', 'priorities', 'tags', 'notes', 'buckets', 'kinds', 'lists'] as const
const encodeFilter = (q: FilterQuery) => FILTER_FIELDS.map((k) => (q[k] ?? []).join(US)).join(RS)
const decodeFilter = (line: string, base: FilterQuery): FilterQuery => {
  const f = line.split(RS)
  return { ...base, ...Object.fromEntries(FILTER_FIELDS.map((k, i) => [k, (f[i] ?? '').split(US).filter((x) => x)])) }
}

// Folded groups as Android keeps them, "DATE:วันนี้", under the key the Tasks page uses; before that page remembered
// them per grouping, a fold was its label alone, taken as a fold of the grouping in use.
const groupBy = (): string => {
  try { return JSON.parse(get('omni.query') ?? '{}').groupBy ?? DEFAULT_QUERY.groupBy } catch { return DEFAULT_QUERY.groupBy }
}
const foldedGroups = (): string[] =>
  get('omni.foldedGroups') !== null ? lines('omni.foldedGroups', '\n') : lines('omni.folded', '\n').map((label) => groupBy() + ':' + label)

/** What is kept under the keys of the pages this file does not own, to notice a change made there. */
const othersPrint = () => ['omni.filters', 'omni.foldedGroups', 'omni.foldedColumns'].map((k) => get(k) ?? '').join(RS)

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
  if (set('projectTags')) p.projectTags = set('projectTags')!
  if (set('customKinds')) k.custom = set('customKinds')!
  if (set('hiddenKinds')) k.hidden = set('hiddenKinds')!
  put('omni.projects', JSON.stringify(p))
  applyProjectTags(p.projectTags)
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

  const f = focusLocal.get()
  if (typeof s.futureCount?.int === 'number') f.futureCount = s.futureCount.int
  if (set('skippedFuture')) f.skipped = set('skippedFuture')!
  if (set('dismissedSuggestions')) f.dismissed = set('dismissedSuggestions')!
  if (set('reviewedTasks')) {
    f.reviewed = Object.fromEntries(set('reviewedTasks')!.filter((e) => e.includes('|')).map((e) => [e.slice(0, e.lastIndexOf('|')), e.slice(e.lastIndexOf('|') + 1)]))
  }
  if (set('focus.countdowns')) {
    f.countdowns = set('focus.countdowns')!
    f.countdown = null
  } else if (s['focus.countdown']?.string !== undefined) {
    // A file from before there could be several: its single countdown (the ones this device keeps, if any, come first).
    f.countdown = s['focus.countdown'].string || null
  }
  if (s['sleep.tonight']?.string !== undefined) f.tonightBed = nightOf(s['sleep.tonight'].string)
  if (s['sleep.tonight.wake']?.string !== undefined) f.tonightWake = nightOf(s['sleep.tonight.wake'].string)
  if (typeof s['sleep.wake.watch']?.bool === 'boolean') f.wakeFromWatch = s['sleep.wake.watch'].bool
  focusLocal.set(f)

  if (set('savedFilters')) {
    const mine = filters()
    put('omni.filters', JSON.stringify(set('savedFilters')!.map((e): SavedFilter => {
      const at = e.indexOf('\t')
      const name = at < 0 ? e : e.slice(0, at)
      return { name, query: decodeFilter(at < 0 ? '' : e.slice(at + 1), mine.find((x) => x.name === name)?.query ?? DEFAULT_QUERY) }
    }).sort((a, b) => a.name.localeCompare(b.name))))
  }
  if (set('foldedGroups')) put('omni.foldedGroups', set('foldedGroups')!.join('\n'))
  if (set('foldedColumns')) put('omni.foldedColumns', set('foldedColumns')!.join(','))

  const look = appearanceFromAndroid({
    font: s['appearance.font']?.string, scale: s['appearance.scale']?.float, theme: s['appearance.theme']?.string, palette: s['appearance.palette']?.string,
  })
  if (Object.keys(look).length) setAppearance(look, true)

  // Task reminders travel both ways; what the morning summary holds is set on the phone and only read here.
  const n = notifyChoice.get()
  const flag = (key: string, fallback: boolean) => (typeof s[key]?.bool === 'boolean' ? s[key].bool! : fallback)
  const times = s['notify.digestTimes']?.string
  notifyChoice.set({
    task: typeof s['notify.task']?.bool === 'boolean' ? s['notify.task'].bool : n.task,
    digestTimes: times === undefined ? n.digestTimes : times.split(',').map((t) => t.trim().slice(0, 5)).filter((t) => /^\d\d:\d\d$/.test(t)),
    overdue: flag('notify.overdue', n.overdue), dueToday: flag('notify.dueToday', n.dueToday), waiting: flag('notify.waiting', n.waiting),
    weekly: flag('notify.weekly', n.weekly),
  })
  // A history is only ever added to: both sides' entries are kept.
  if (set('recurHistory')) recurHistory.set([...recurHistory.get(), ...set('recurHistory')!])
}

/**
 * Keys this version of the web shares that an earlier one did not: the first time, they are taken from the file
 * whatever its time, so this device never writes its defaults over what the phone chose before it knew them.
 */
const SCHEMA = 3
const SCHEMA_KEY = 'omni.settingsSchema'
/** The keys each version added, so a device takes from the file only the keys it did not share before. */
const NEW_KEYS_BY_SCHEMA: [number, string[]][] = [
  [2, [
    'futureCount', 'skippedFuture', 'dismissedSuggestions', 'reviewedTasks', 'focus.countdown', 'sleep.tonight', 'sleep.tonight.wake',
    'sleep.wake.watch', 'savedFilters', 'foldedGroups', 'foldedColumns', 'appearance.font', 'appearance.scale', 'appearance.theme',
    'appearance.palette', 'notify.task', 'notify.digestTimes', 'notify.overdue', 'notify.dueToday', 'notify.waiting', 'notify.weekly',
    'recurHistory',
  ]],
  [3, ['focus.countdowns']],
]

/** The morning summary's settings are only read here, so they are compared apart from what the web writes. */
const readOnlyPrint = () => JSON.stringify(notifyChoice.get())

/**
 * The Tasks page and the Kanban keep saved filters and folds without telling this file. A change there follows a tap,
 * a typed key or a form sent, or the page being left: each is a moment to look, and a change goes to the file.
 */
let othersSeen = othersPrint()
let watching = false
function watchOthers() {
  if (watching) return
  watching = true
  const look = () => setTimeout(() => {
    const now = othersPrint()
    if (now === othersSeen) return
    othersSeen = now
    settingsBus.changed()
  }, 0)
  for (const type of ['click', 'change', 'submit', 'keyup']) document.addEventListener(type, look, true)
  document.addEventListener('visibilitychange', look)
}

const same = (a: Value | undefined, b: Value | undefined) => JSON.stringify(a ?? null) === JSON.stringify(b ?? null)

/** The settings file of one vault on Drive: read when the page loads, written when a shared choice changes here. */
export class SettingsSync {
  private fileId: string | null = null
  /** Whether the file was read once in this visit: no write goes out before, so this device never overwrites Android's choices with its defaults. */
  private pulled = false
  private wantPush = false

  constructor(private drive: Drive, private rootId: string, private ensureFolders: (path: string) => Promise<string>) {
    watchOthers()
  }

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
    const s = file?.data.settings
    const newer = savedAt > Number(get(SYNCED_KEY) ?? 0)
    const was = Number(get(SCHEMA_KEY) ?? 1)
    const firstTime = was < SCHEMA
    const fresh = NEW_KEYS_BY_SCHEMA.filter(([v]) => v > was).flatMap(([, keys]) => keys)
    if (file && s && (newer || firstTime)) {
      const before = JSON.stringify(localSettings()) + readOnlyPrint()
      applySettings(newer ? s : Object.fromEntries(Object.entries(s).filter(([k]) => fresh.includes(k))))
      if (newer) put(SYNCED_KEY, String(savedAt))
      changed = JSON.stringify(localSettings()) + readOnlyPrint() !== before
    }
    put(SCHEMA_KEY, String(SCHEMA))
    // What was just taken in is not a change made here.
    othersSeen = othersPrint()
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
