import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { config } from './config'
import { AuthExpired, Drive } from './drive'
import { FocusPage } from './pages/Focus'
import { SettingsPage } from './pages/Settings'
import { AssistantPage } from './pages/Assistant'
import { ProjectsPage } from './pages/Projects'
import { ViewsPage } from './pages/Views'
import { TasksPage } from './pages/Tasks'
import { createEvent, readEvents, type NewEvent } from './calendar'
import { calendarCache } from './calendarCache'
import { logDone } from './assistantCore'
import { sweepIfDue, sweptNotice } from './settingsDevice'
import { Page, Shell, usePage } from './Shell'
import type { Task } from './types'
import { Cut, Snapshot, Vault, VaultError } from './vault'

/** What every page gets: the note as last read, and a way to change it. */
export interface PageProps {
  vault: Vault
  snapshot: Snapshot | null
  busy: boolean
  run: (action: () => Promise<unknown>) => Promise<void>
  tick: (t: Task) => void
  /** Deletes a task with its block; the bar at the bottom offers undo. */
  remove: (t: Task) => void
  /** The task as last read, for an action that waited behind another one. */
  fresh: (t: Task) => Task
  /** Reads the note again. */
  onReload: () => void
  onNavigate: (page: Page) => void
  onChangeVault: () => void
  onSignOut: () => void
  /** Asks Google for read access to the calendar (a trip to Google and back). */
  onConnectCalendar: () => void
  /** Reads Google Calendar between two moments; fails with CalendarError when it is not allowed or not switched on. */
  readCalendar: (from: Date, to: Date) => ReturnType<typeof readEvents>
  /** The events last read for a range, however old (undefined when never read): a page shows them while it reads again. */
  peekCalendar: (from: Date, to: Date) => Awaited<ReturnType<typeof readEvents>> | undefined
  /** Asks Google for the permission to add events to the calendar (a trip to Google and back); asked only when the owner first uses it. */
  onAllowCalendarWrite: () => void
  /** Adds an event to the owner's calendar and returns its page in Google Calendar; fails with CalendarError when not allowed or not switched on. */
  addCalendarEvent: (e: NewEvent) => Promise<string>
}

const ASK_KEY = 'omni.askOnDone'
/** Whether ticking a task done asks to archive or delete it, as Android's setting of the same name. */
export const askOnDone = {
  get: () => { try { return localStorage.getItem(ASK_KEY) !== 'no' } catch { return true } },
  set: (on: boolean) => { try { localStorage.setItem(ASK_KEY, on ? 'yes' : 'no') } catch { /* just not kept */ } },
}

/**
 * Asks "archive or delete?" only where Android does: a top-level task that does not repeat, is not project
 * work, and has no work still open under it.
 */
const offersFinish = (t: Task, tasks: Task[], withSubtasks: boolean) => {
  if (!askOnDone.get() || !t.open || t.parent || t.recurrence || t.project) return false
  const below = (key: string): Task[] => tasks.filter((c) => c.parent === key).flatMap((c) => [c, ...below(c.key)])
  return withSubtasks ? below(t.key).every((c) => !c.open || c.parent === t.key) : below(t.key).every((c) => !c.open)
}

export function Main(p: { drive: Drive; vaultId: string; onSignIn: () => void; onConnectCalendar: () => void; onAllowCalendarWrite: () => void; onSignOut: () => void; onChangeVault: () => void }) {
  const vault = useMemo(() => new Vault(p.drive, p.vaultId, config.taskFileId), [p.drive, p.vaultId])
  const calendar = useMemo(() => calendarCache(p.drive), [p.drive])
  const [page, navigate] = usePage()
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [busy, setBusy] = useState(false)
  const [syncedAt, setSyncedAt] = useState<Date | null>(null)
  const [message, setMessage] = useState('')
  const [needSignIn, setNeedSignIn] = useState(false)
  const [closing, setClosing] = useState<Task | null>(null)
  const [finished, setFinished] = useState<Task | null>(null)
  const [undo, setUndo] = useState<Cut | null>(null)

  // The done prompt waits ten seconds, the undo bar six, as on Android.
  useEffect(() => {
    if (!finished) return
    const t = setTimeout(() => setFinished(null), 10_000)
    return () => clearTimeout(t)
  }, [finished])
  useEffect(() => {
    if (!undo) return
    const t = setTimeout(() => setUndo(null), 6_000)
    return () => clearTimeout(t)
  }, [undo])

  /** Runs one action against the vault, reads the note again, and turns each way it can fail into a plain message. */
  // Actions run one after another, each on the note as the one before left it, so quick taps never race.
  const queue = useRef<Promise<void>>(Promise.resolve())
  const pending = useRef(0)
  const latest = useRef<Snapshot | null>(null)
  latest.current = snapshot
  const run = (action: () => Promise<unknown>) => {
    pending.current++
    setBusy(true)
    const next = queue.current.then(() => runNow(action)).finally(() => {
      if (--pending.current === 0) setBusy(false)
    })
    queue.current = next
    return next
  }
  /** The task as last read: an edit queued behind another one works on the line that one left. */
  const fresh = (t: Task) => {
    const now = latest.current?.byKey.get(t.key)
    return now && now.title === t.title ? now : t
  }
  const runNow = async (action: () => Promise<unknown>) => {
    setMessage('')
    try {
      const res = (await action()) as { ok?: boolean; error?: string; notice?: string } | undefined
      if (res && res.ok === false) {
        setMessage(
          res.error === 'conflict' ? 'บรรทัดนี้ถูกแก้จากที่อื่นไปแล้ว จึงโหลดใหม่ให้ ลองอีกครั้ง'
            : res.error === 'rule' ? 'อ่านรอบวนซ้ำของงานนี้ไม่ได้ จึงยังไม่ได้ติ๊ก'
            : res.error === 'empty' ? 'พิมพ์ชื่องานก่อน' : 'ทำรายการไม่สำเร็จ',
        )
      }
      const loaded = await vault.load()
      latest.current = loaded
      setSnapshot(loaded)
      setSyncedAt(new Date())
      setNeedSignIn(false)
      // A plain notice from the action (e.g. "moved 3 finished tasks to the archive"), shown once the note is read.
      if (res?.ok && res.notice) setMessage(res.notice)
    } catch (e) {
      if (e instanceof AuthExpired) setNeedSignIn(true)
      else if (e instanceof VaultError) setMessage(e.message)
      else setMessage(`เชื่อมต่อ Google Drive ไม่ได้ (${(e as Error).message})`)
    }
  }
  // A reload asks Drive and Google for everything again; the pages keep showing what they have until it is read.
  const reload = () => { vault.clearCaches(); calendar.stale(); return run(async () => undefined) }

  // The first walk of the vault ends in the background: its notes join the task note as last read, without a
  // spinner, a message cleared or the task note read again.
  useEffect(() => {
    vault.onIndexed = () => {
      queue.current = queue.current.then(() => {
        if (!latest.current) return
        const joined = vault.withOtherNotes(latest.current)
        // Nothing found beyond the task note: the page is left as it is (a drag in progress is not disturbed).
        if (joined.notes.length === 0 && joined.conflicts.length === latest.current.conflicts.length) return
        latest.current = joined
        setSnapshot(joined)
      })
    }
    return () => { vault.onIndexed = null }
  }, [vault])

  // Once a day finished tasks move to the archive note (Settings: archive days); the note is read again only if some moved.
  useEffect(() => {
    reload()
    sweepIfDue(vault).then((moved) => { if (moved.length > 0) run(async () => ({ ok: true, notice: sweptNotice(moved.length) })) }, () => {})
  }, [vault])

  /**
   * Ticks a task; a parent with open subtasks first asks whether to tick them too, as on Android.
   * A repeating task never asks: it moves on to its next date with its subtasks opened again.
   */
  const tick = (t: Task) => {
    if (t.open && !t.recurrence && snapshot?.tasks.some((c) => c.parent === t.key && c.open)) setClosing(t)
    else close(t, false)
  }
  const close = (t: Task, withSubtasks: boolean) => {
    const ask = snapshot != null && offersFinish(t, snapshot.tasks, withSubtasks)
    run(async () => {
      const res = await vault.toggle(fresh(t), withSubtasks)
      if (res.ok && t.open) logDone(t.title)
      if (res.ok && ask) setFinished(t)
      return res
    })
  }
  /** Deletes or archives a task; the cut is kept so the bar can put it back. */
  const takeOut = (t: Task, archive: boolean) =>
    run(async () => {
      setFinished(null)
      // The task was ticked since it was read, so it is looked up again by its place in the note.
      const res = archive ? await vault.archive(fresh(t)) : await vault.remove(fresh(t))
      if (res.ok && res.cut) setUndo(res.cut)
      return res
    })
  const finishedNow = finished && snapshot?.tasks.find((c) => c.lineIndex === finished.lineIndex && c.title === finished.title)

  const props: PageProps = {
    vault, snapshot, busy, run, tick, remove: (t) => takeOut(t, false), fresh,
    onReload: reload, onNavigate: navigate, onChangeVault: p.onChangeVault, onSignOut: p.onSignOut,
    onConnectCalendar: p.onConnectCalendar, readCalendar: calendar.read, peekCalendar: calendar.peek,
    onAllowCalendarWrite: p.onAllowCalendarWrite,
    addCalendarEvent: async (e) => { const link = await createEvent(p.drive, e); calendar.stale(); return link },
  }

  return (
    <Shell page={page} onNavigate={navigate} sync={{ state: needSignIn ? 'off' : busy ? 'busy' : 'ok', at: syncedAt }} onReload={reload}>
      {needSignIn && (
        <div class="banner" role="alert">
          หมดเวลาเข้าสู่ระบบ <button class="link" onClick={p.onSignIn}>เข้าสู่ระบบอีกครั้ง</button>
        </div>
      )}
      {message && <div class="banner" role="alert">{message}</div>}
      {snapshot && snapshot.conflicts.length > 0 && (
        <div class="banner" role="alert">
          พบสำเนาจากการซิงก์ชนกัน: {snapshot.conflicts.join(', ')}
          <br />
          <span class="small">เปิดเทียบกับโน้ตต้นฉบับใน Obsidian แล้วลบสำเนาทิ้ง ระหว่างนี้แอปไม่อ่านสำเนา</span>
        </div>
      )}

      {page === 'focus' ? <FocusPage {...props} />
        : page === 'tasks' ? <TasksPage {...props} />
        : page === 'views' ? <ViewsPage {...props} />
        : page === 'projects' ? <ProjectsPage {...props} />
        : page === 'assistant' ? <AssistantPage {...props} />
        : <SettingsPage {...props} />}

      {closing && (
        <div class="scrim" onClick={() => setClosing(null)}>
          <div class="dialog" role="dialog" aria-modal="true" aria-label="งานย่อยยังค้าง" onClick={(e) => e.stopPropagation()}>
            <h2>งานย่อยของ "{closing.title}" ยังค้างอยู่</h2>
            <button class="primary" onClick={() => { const t = closing; setClosing(null); close(t, true) }}>ติ๊กงานย่อยด้วย</button>
            <button class="ghost" onClick={() => { const t = closing; setClosing(null); close(t, false) }}>ติ๊กแค่งานนี้</button>
            <button class="link" onClick={() => setClosing(null)}>ยกเลิก</button>
          </div>
        </div>
      )}

      {finishedNow && !finishedNow.open && (
        <div class="toast" role="status">
          <div class="toast-text">
            <div class="ellipsis">เสร็จแล้ว: {finishedNow.title}</div>
            <div class="muted small">เก็บเข้าคลัง หรือลบออกจากโน้ตเลยไหม</div>
          </div>
          <div class="toast-actions">
            <button class="link quiet" onClick={() => setFinished(null)}>ไว้ก่อน</button>
            <button class="link danger" disabled={busy} onClick={() => takeOut(finishedNow, false)}>ลบ</button>
            <button class="link" disabled={busy} onClick={() => takeOut(finishedNow, true)}>เก็บเข้าคลัง</button>
          </div>
        </div>
      )}
      {undo && !finished && (
        <div class="toast" role="status">
          <div class="toast-text ellipsis">{undo.archived ? `ย้าย "${undo.title}" เข้าคลังแล้ว` : `ลบ "${undo.title}" แล้ว`}</div>
          <div class="toast-actions">
            <button class="link" disabled={busy} onClick={() => { const c = undo; setUndo(null); run(() => vault.undo(c)) }}>เลิกทำ</button>
          </div>
        </div>
      )}
    </Shell>
  )
}
