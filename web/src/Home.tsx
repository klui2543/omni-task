import { useEffect, useMemo, useState } from 'preact/hooks'
import { config } from './config'
import { AuthExpired, Drive } from './drive'
import { SettingsPage } from './pages/Settings'
import { SoonPage } from './pages/Soon'
import { TasksPage } from './pages/Tasks'
import { Shell, TABS, usePage } from './Shell'
import type { Task } from './types'
import { Snapshot, Vault, VaultError } from './vault'

/** What every page gets: the note as last read, and a way to change it. */
export interface PageProps {
  vault: Vault
  snapshot: Snapshot | null
  busy: boolean
  run: (action: () => Promise<unknown>) => Promise<void>
  tick: (t: Task) => void
}

export function Main(p: { drive: Drive; vaultId: string; onSignIn: () => void; onSignOut: () => void; onChangeVault: () => void }) {
  const vault = useMemo(() => new Vault(p.drive, p.vaultId, config.taskFileId), [p.drive, p.vaultId])
  const [page, navigate] = usePage()
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [busy, setBusy] = useState(false)
  const [syncedAt, setSyncedAt] = useState<Date | null>(null)
  const [message, setMessage] = useState('')
  const [needSignIn, setNeedSignIn] = useState(false)
  const [closing, setClosing] = useState<Task | null>(null)

  /** Runs one action against the vault, reads the note again, and turns each way it can fail into a plain message. */
  const run = async (action: () => Promise<unknown>) => {
    setBusy(true)
    setMessage('')
    try {
      const res = (await action()) as { ok?: boolean; error?: string } | undefined
      if (res && res.ok === false) {
        setMessage(
          res.error === 'conflict' ? 'บรรทัดนี้ถูกแก้จากที่อื่นไปแล้ว จึงโหลดใหม่ให้ ลองอีกครั้ง'
            : res.error === 'rule' ? 'อ่านรอบวนซ้ำของงานนี้ไม่ได้ จึงยังไม่ได้ติ๊ก'
            : res.error === 'empty' ? 'พิมพ์ชื่องานก่อน' : 'ทำรายการไม่สำเร็จ',
        )
      }
      setSnapshot(await vault.load())
      setSyncedAt(new Date())
      setNeedSignIn(false)
    } catch (e) {
      if (e instanceof AuthExpired) setNeedSignIn(true)
      else if (e instanceof VaultError) setMessage(e.message)
      else setMessage(`เชื่อมต่อ Google Drive ไม่ได้ (${(e as Error).message})`)
    } finally {
      setBusy(false)
    }
  }
  const reload = () => run(async () => undefined)

  useEffect(() => { reload() }, [vault])

  /**
   * Ticks a task; a parent with open subtasks first asks whether to tick them too, as on Android.
   * A repeating task never asks: it moves on to its next date with its subtasks opened again.
   */
  const tick = (t: Task) => {
    if (t.open && !t.recurrence && snapshot?.tasks.some((c) => c.parent === t.key && c.open)) setClosing(t)
    else run(() => vault.toggle(t))
  }

  const props: PageProps = { vault, snapshot, busy, run, tick }
  const title = TABS.find(([id]) => id === page)?.[1] ?? ''

  return (
    <Shell page={page} onNavigate={navigate} sync={{ state: needSignIn ? 'off' : busy ? 'busy' : 'ok', at: syncedAt }} onReload={reload}>
      {needSignIn && (
        <div class="banner" role="alert">
          หมดเวลาเข้าสู่ระบบ <button class="link" onClick={p.onSignIn}>เข้าสู่ระบบอีกครั้ง</button>
        </div>
      )}
      {message && <div class="banner" role="alert">{message}</div>}

      {page === 'tasks' ? <TasksPage {...props} />
        : page === 'settings' ? <SettingsPage busy={busy} onReload={reload} onChangeVault={p.onChangeVault} onSignOut={p.onSignOut} />
        : <SoonPage title={title} onTasks={() => navigate('tasks')} />}

      {closing && (
        <div class="scrim" onClick={() => setClosing(null)}>
          <div class="dialog" role="dialog" aria-modal="true" aria-label="งานย่อยยังค้าง" onClick={(e) => e.stopPropagation()}>
            <h2>งานย่อยของ "{closing.title}" ยังค้างอยู่</h2>
            <button class="primary" onClick={() => { const t = closing; setClosing(null); run(() => vault.toggle(t, true)) }}>ติ๊กงานย่อยด้วย</button>
            <button class="ghost" onClick={() => { const t = closing; setClosing(null); run(() => vault.toggle(t)) }}>ติ๊กแค่งานนี้</button>
            <button class="link" onClick={() => setClosing(null)}>ยกเลิก</button>
          </div>
        </div>
      )}
    </Shell>
  )
}
