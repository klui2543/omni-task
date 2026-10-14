// Task reminders without a server. A browser cannot ring at a set time while it is closed (an iPad not at all), so
// while the page is open it looks, every 30 seconds, when it comes back to the screen and at each reminder's time,
// for the reminders that rang since it last looked: the same ones Android's alarms ring (shared AlarmPlan, via
// WebNotify). Each shows once, as a banner in the page (ui/Reminders.tsx) and, when allowed, as a notification of the
// system; one that rang while the page was away shows when it is back. "อีก 1 ชั่วโมง" and "พรุ่งนี้" put off that
// reminder only, kept on this device, as Android's one-off alarm. Done, put off, closed and opened go into the
// behavior log as Android's notification buttons do. The app's badge counts the work overdue or due today.
import { WebNotifyApi } from './kotlin/OmniTask-shared.mjs'
import './assistantVault'
import { learn } from './behavior'
import { localIso } from './calendar'
import { notifyChoice } from './settingsNotify'
import type { Task } from './types'
import { branchStates } from './ui/projects/projectState'
import { Snapshot, Vault } from './vault'

const api = WebNotifyApi.getInstance()

/** One reminder that rang: the task line it is for, and when. [id] is Android's alarm id (or "snooze:" before it). */
export interface Reminder {
  id: string
  key: string
  raw: string
  title: string
  text: string
  at: string
}

/* ---------- The vault the pages show ---------- */

// Home makes the vault and reads it; the reminders follow each note it shows, from outside the class (as
// assistantVault.ts adds to it), so vault.ts and Home.tsx are left as they are.
let vault: Vault | null = null
let snapshot: Snapshot | null = null
const shown = (v: Vault, s: Snapshot) => {
  vault = v
  snapshot = s
  queueMicrotask(check)
}
const proto = Vault.prototype as unknown as {
  load(): Promise<Snapshot>
  takeWritten(): Snapshot | null
  withOtherNotes(s: Snapshot): Snapshot
}
const load = proto.load
proto.load = async function (this: Vault) {
  const s = await load.call(this)
  shown(this, s)
  return s
}
const takeWritten = proto.takeWritten
proto.takeWritten = function (this: Vault) {
  const s = takeWritten.call(this)
  if (s) shown(this, s)
  return s
}
const withOtherNotes = proto.withOtherNotes
proto.withOtherNotes = function (this: Vault, s: Snapshot) {
  const out = withOtherNotes.call(this, s)
  shown(this, out)
  return out
}

/* ---------- What this device keeps ---------- */

const KEY = 'omni.reminders'

interface Kept {
  /** When the page last looked, local `yyyy-MM-ddTHH:mm`. */
  since?: string
  /** Reminders already shown, "id@at", so each shows once. */
  fired: string[]
  /** Reminders put off, each to ring again at its own time. */
  snoozed: Reminder[]
  /** The banners on screen, kept through a reload until the owner acts on them. */
  showing: Reminder[]
}

const kept = (): Kept => {
  try {
    return { fired: [], snoozed: [], showing: [], ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }
  } catch {
    return { fired: [], snoozed: [], showing: [] }
  }
}
const keep = (k: Kept) => {
  try { localStorage.setItem(KEY, JSON.stringify(k)) } catch { /* just not kept */ }
}

const listeners = new Set<() => void>()
/** Calls [l] whenever the banners change; returns the way to stop. */
export const onReminders = (l: () => void) => {
  listeners.add(l)
  return () => { listeners.delete(l) }
}
const changed = () => listeners.forEach((l) => l())

/** The banners to show. */
export const showing = (): Reminder[] => kept().showing

/* ---------- Looking ---------- */

/** A reminder that rang while the page was away is shown when it is back, up to this long after. */
const LOOK_BACK_MS = 12 * 3600_000
const minutes = (d: Date, m: number) => new Date(d.getTime() + m * 60_000)
const atOf = (iso: string) => new Date(iso.length === 16 ? iso : iso.slice(0, 16))

let badge = -1
let next: ReturnType<typeof setTimeout> | undefined

/** Looks for reminders that rang and sets the badge; safe to call often. */
export function check() {
  const s = snapshot
  if (!s) return
  const now = new Date()
  const nowIso = localIso(now)
  const k = kept()
  // The first look starts now; after that, from the last look, not more than half a day back.
  const floor = localIso(new Date(now.getTime() - LOOK_BACK_MS))
  const since = !k.since ? localIso(minutes(now, -1)) : k.since < floor ? floor : k.since
  let out: { badge: number; reminders: Reminder[] }
  try {
    out = JSON.parse(api.reminders(s.noteKey(), s.path, s.noteText(), JSON.stringify({ now: nowIso, since, until: localIso(minutes(now, 24 * 60)), branches: branchStates() })))
  } catch {
    return
  }
  setBadge(out.badge)

  const on = notifyChoice.remindersOn()
  const open = (r: Reminder) => s.tasks.some((t) => t.open && t.raw === r.raw)
  const fresh: Reminder[] = []
  if (on) {
    for (const r of out.reminders) {
      if (r.at > nowIso || k.fired.includes(`${r.id}@${r.at}`)) continue
      k.fired.push(`${r.id}@${r.at}`)
      fresh.push(r)
    }
    // A reminder put off rings again at its time, unless the task was done or changed meanwhile (as Android's alarm).
    for (const r of k.snoozed.filter((x) => x.at <= nowIso)) if (open(r)) fresh.push(r)
    k.snoozed = k.snoozed.filter((x) => x.at > nowIso)
  }
  // A banner whose task was done or changed elsewhere goes (the test one has no task).
  const before = k.showing.length
  k.showing = [...k.showing.filter((r) => !r.raw || open(r)), ...fresh.filter((r) => !k.showing.some((x) => x.id === r.id))]
  k.fired = k.fired.filter((f) => f.slice(f.lastIndexOf('@') + 1) > floor)
  k.since = nowIso
  keep(k)
  fresh.forEach(notifySystem)
  if (fresh.length || k.showing.length !== before) changed()

  // The next reminder to come is looked for at its time too, not only at the next 30 second look.
  clearTimeout(next)
  const coming = [...out.reminders.map((r) => r.at), ...k.snoozed.map((r) => r.at)].filter((a) => a > nowIso).sort()[0]
  if (on && coming) next = setTimeout(check, Math.min(Math.max(atOf(coming).getTime() - now.getTime() + 500, 1000), 30 * 60_000))
}

/** The app's badge: the work overdue or due today, where the browser has one (an app added to the home screen). */
function setBadge(n: number) {
  if (n === badge) return
  badge = n
  const nav = navigator as Navigator & { setAppBadge?: (n: number) => Promise<void>; clearAppBadge?: () => Promise<void> }
  try {
    if (n > 0) nav.setAppBadge?.(n)?.catch(() => {})
    else nav.clearAppBadge?.()?.catch(() => {})
  } catch { /* no badge here */ }
}

/* ---------- The system's notification ---------- */

export type Permission = 'granted' | 'denied' | 'default' | 'unsupported'

export const permission = (): Permission => ('Notification' in window ? Notification.permission : 'unsupported')

export async function askPermission(): Promise<Permission> {
  if (!('Notification' in window)) return 'unsupported'
  try { return await Notification.requestPermission() } catch { return permission() }
}

/** Shows [r] as a notification of the system when it is allowed: through the service worker (an app added to the home screen), or the page. */
async function notifySystem(r: { id: string; raw: string; title: string; text: string }) {
  if (permission() !== 'granted') return
  const options = { body: r.text, tag: r.id, data: { raw: r.raw } }
  try {
    const reg = 'serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration() : undefined
    if (reg) return await reg.showNotification(r.title, options)
    const n = new Notification(r.title, options)
    n.onclick = () => { window.focus(); if (r.raw) learn.opened(r.raw) }
  } catch { /* the banner in the page is still there */ }
}

async function closeSystem(id: string) {
  try {
    const reg = 'serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration() : undefined
    for (const n of (await reg?.getNotifications({ tag: id })) ?? []) n.close()
  } catch { /* nothing to close */ }
}

/** A notification to see that they work: in the page, and from the system when allowed. */
export function test() {
  const r: Reminder = { id: 'test', key: '', raw: '', title: 'ทดสอบการแจ้งเตือน', text: 'ถ้าเห็นข้อความนี้ แปลว่าการแจ้งเตือนใช้ได้แล้ว', at: localIso(new Date()) }
  const k = kept()
  k.showing = [...k.showing.filter((x) => x.id !== r.id), r]
  keep(k)
  changed()
  notifySystem(r)
}

/* ---------- What the owner does with a reminder ---------- */

const drop = (r: Reminder) => {
  const k = kept()
  k.showing = k.showing.filter((x) => x.id !== r.id)
  keep(k)
  changed()
  closeSystem(r.id)
}

/** Puts [r] off by [minutes]: that reminder only rings again then (the task line keeps its time). */
export function snooze(r: Reminder, minutesLater: number) {
  learn.snoozed(r.raw, minutesLater)
  const k = kept()
  const base = r.id.replace(/^snooze:/, '')
  k.snoozed = [...k.snoozed.filter((x) => x.id !== 'snooze:' + base), { ...r, id: 'snooze:' + base, at: localIso(minutes(new Date(), minutesLater)) }]
  keep(k)
  drop(r)
}

/** Closed without acting, as a notification swiped away. */
export function dismiss(r: Reminder) {
  if (r.raw) learn.dismissed(r.raw)
  drop(r)
}

/**
 * Ticks the task off (a repeating one moves on to its next date), then shows the note as written on every page, as
 * Home does after its own actions. False when the task is no longer there to tick.
 */
export async function done(r: Reminder): Promise<boolean> {
  const v = vault
  const t: Task | undefined = snapshot?.tasks.find((x) => x.open && x.raw === r.raw) ?? snapshot?.byKey.get(r.key)
  if (!v || !t || !t.open) {
    drop(r)
    return false
  }
  const res = await v.toggle(t)
  if (!res.ok) return false
  drop(r)
  try {
    const s = v.takeWritten() ?? (await v.load())
    v.onPreview?.(s)
  } catch { /* the next reload shows it */ }
  return true
}

/* ---------- Looking often enough ---------- */

let started = false
/** Starts looking: every 30 seconds, and whenever the page comes back (a timer does not run while the iPad sleeps). */
export function start() {
  if (started) return
  started = true
  setInterval(check, 30_000)
  const back = () => { if (document.visibilityState === 'visible') check() }
  document.addEventListener('visibilitychange', back)
  window.addEventListener('focus', back)
  window.addEventListener('pageshow', back)
  // A notification of the system tapped while the page is open (public/sw.js says which).
  try {
    navigator.serviceWorker?.addEventListener('message', (e: MessageEvent) => {
      if (e.data?.type === 'omni-reminder-open' && typeof e.data.raw === 'string' && e.data.raw) learn.opened(e.data.raw)
    })
    navigator.serviceWorker?.startMessages?.()
  } catch { /* no service worker here */ }
}
