import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { Auth } from './auth'
import { config } from './config'
import { today } from './core'
import { AuthExpired, Drive, DriveFile } from './drive'
import type { Bucket, Task } from './types'
import { FoundVault, Vault, VaultError, findVaults } from './vault'

type Stage = 'setup' | 'signin' | 'vault' | 'ready'

const DRAFT_KEY = 'omni.draft'
const sessionStorageGet = (k: string) => {
  try {
    return sessionStorage.getItem(k) ?? ''
  } catch {
    return ''
  }
}
const sessionStorageSet = (k: string, v: string) => {
  try {
    if (v) sessionStorage.setItem(k, v)
    else sessionStorage.removeItem(k)
  } catch {
    /* the draft is just not kept */
  }
}

const BUCKETS: [Bucket, string][] = [
  ['OVERDUE', 'เลยกำหนด'],
  ['TODAY', 'วันนี้'],
  ['THIS_WEEK', 'สัปดาห์นี้'],
  ['NEXT_WEEK', 'สัปดาห์หน้า'],
  ['FUTURE', 'อนาคต'],
  ['NO_DATE', 'ไม่มีวันที่'],
]

const PRIORITY: Record<string, string> = { HIGHEST: '🔺', HIGH: '⏫', MEDIUM: '🔼', LOW: '🔽', LOWEST: '⏬' }

const shortDate = (iso: string) =>
  new Date(iso + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })

/** Google's answers to a quiet sign-in it cannot serve; they only mean the owner has to tap the button. */
const QUIET_ERRORS = ['interaction_required', 'login_required', 'consent_required']

export function App({ authError }: { authError: string | null }) {
  const [clientId, setClientId] = useState(config.clientId)
  const [vaultId, setVaultId] = useState(config.vaultId)
  const [signedIn, setSignedIn] = useState(false)
  const auth = useMemo(() => (clientId ? new Auth(clientId) : null), [clientId])
  const drive = useMemo(
    () =>
      auth
        ? new Drive(() => {
            const token = auth.token
            if (!token) throw new AuthExpired()
            return token
          })
        : null,
    [auth],
  )
  const hasToken = signedIn || auth?.token != null
  const renewing = auth != null && !hasToken && auth.shouldRenewSilently

  // The token ran out since last time: renew it with a quiet trip to Google instead of asking.
  useEffect(() => {
    if (renewing) auth!.signIn('none')
  }, [renewing])

  const stage: Stage = !clientId ? 'setup' : !hasToken ? 'signin' : !vaultId ? 'vault' : 'ready'
  const signIn = () => auth!.signIn(auth!.shouldRenewSilently ? 'none' : 'select_account')

  if (stage === 'setup') return <Setup onSave={(id) => { config.clientId = id; setClientId(id) }} />
  if (renewing) return <main class="card center"><p class="muted">กำลังเข้าสู่ระบบ...</p></main>
  if (stage === 'signin')
    return (
      <SignIn
        error={authError && !QUIET_ERRORS.includes(authError) ? authError : null}
        onSignIn={() => auth!.signIn('select_account')}
        onReset={() => { config.clientId = null; setClientId(null) }}
      />
    )
  if (stage === 'vault')
    return (
      <PickVault
        drive={drive!}
        onPick={(rootId, fileId) => { config.taskFileId = fileId; config.vaultId = rootId; setVaultId(rootId) }}
      />
    )
  return (
    <Main
      drive={drive!}
      vaultId={vaultId!}
      onSignIn={signIn}
      onSignOut={() => { auth!.signOut(); setSignedIn(false); location.reload() }}
      onChangeVault={() => { config.vaultId = null; config.taskFileId = null; setVaultId(null) }}
    />
  )
}

function Setup({ onSave }: { onSave: (id: string) => void }) {
  const [value, setValue] = useState('')
  return (
    <main class="card center">
      <h1>Omni Task</h1>
      <p>วาง Google OAuth Client ID ของคุณเพื่อเชื่อมกับ Google Drive (ทำครั้งเดียวต่อเครื่อง)</p>
      <input
        aria-label="Client ID"
        placeholder="xxxxxxxx.apps.googleusercontent.com"
        value={value}
        onInput={(e) => setValue(e.currentTarget.value.trim())}
      />
      <button class="primary" disabled={!value.includes('.')} onClick={() => onSave(value)}>บันทึก</button>
    </main>
  )
}

function SignIn({ error, onSignIn, onReset }: { error: string | null; onSignIn: () => void; onReset: () => void }) {
  return (
    <main class="card center">
      <h1>Omni Task</h1>
      <p>อ่านและเขียนงานใน Obsidian vault ของคุณบน Google Drive โดยตรง</p>
      <button class="primary" onClick={onSignIn}>เข้าสู่ระบบด้วย Google</button>
      {error && <p class="error" role="alert">เข้าสู่ระบบไม่สำเร็จ ({error})</p>}
      <button class="link" onClick={onReset}>เปลี่ยน Client ID</button>
    </main>
  )
}

function PickVault({ drive, onPick }: { drive: Drive; onPick: (rootId: string, fileId: string | null) => void }) {
  const [found, setFound] = useState<FoundVault[] | null>(null)
  const [browsing, setBrowsing] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    findVaults(drive).then(setFound, (e) => { setError(String((e as Error).message)); setFound([]) })
  }, [drive])

  if (browsing) return <BrowseFolders drive={drive} onPick={(id) => onPick(id, null)} onBack={() => setBrowsing(false)} />

  return (
    <main class="card center">
      <h1>เลือก vault</h1>
      {found === null && <p class="muted">กำลังหา TaskForge.md ใน Google Drive...</p>}
      {error && <p class="error" role="alert">{error}</p>}
      {found?.length === 0 && !error && <p>ไม่พบ TaskForge.md ใน Drive ลองเลือกโฟลเดอร์ vault เอง</p>}
      {found && found.length > 0 && <p>พบ TaskForge.md ใน Drive แตะเพื่อเลือก</p>}
      <ul class="plain">
        {found?.map((v) => (
          <li key={v.fileId}>
            <button class="row" onClick={() => onPick(v.rootId, v.fileId)}>
              <strong>{v.rootName}</strong>
              <span class="muted">{v.path}</span>
            </button>
          </li>
        ))}
      </ul>
      {found !== null && <button class="link" onClick={() => setBrowsing(true)}>เลือกโฟลเดอร์ vault เอง</button>}
    </main>
  )
}

/** Opens folders one by one from My Drive, for when the search does not find the vault. */
function BrowseFolders({ drive, onPick, onBack }: { drive: Drive; onPick: (id: string) => void; onBack: () => void }) {
  const [trail, setTrail] = useState<{ id: string; name: string }[]>([{ id: 'root', name: 'My Drive' }])
  const [folders, setFolders] = useState<DriveFile[] | null>(null)
  const [error, setError] = useState('')
  const here = trail[trail.length - 1]

  useEffect(() => {
    setFolders(null)
    drive.folders(here.id).then(setFolders, (e) => setError(String((e as Error).message)))
  }, [here.id])

  return (
    <main class="card">
      <h1>เลือกโฟลเดอร์ vault</h1>
      <nav class="trail" aria-label="ตำแหน่ง">
        {trail.map((f, i) => (
          <button key={f.id} class="link" disabled={i === trail.length - 1} onClick={() => setTrail(trail.slice(0, i + 1))}>{f.name}</button>
        ))}
      </nav>
      {here.id !== 'root' && (
        <button class="primary" onClick={() => onPick(here.id)}>ใช้โฟลเดอร์ "{here.name}" เป็น vault</button>
      )}
      {error && <p class="error" role="alert">{error}</p>}
      {folders === null && !error && <p class="muted">กำลังโหลด...</p>}
      {folders?.length === 0 && <p class="muted">ไม่มีโฟลเดอร์ย่อย</p>}
      <ul class="plain">
        {folders?.map((f) => (
          <li key={f.id}>
            <button class="row folder" onClick={() => setTrail([...trail, { id: f.id, name: f.name }])}>📁 {f.name}</button>
          </li>
        ))}
      </ul>
      <button class="link" onClick={onBack}>กลับไปหน้าค้นหา</button>
    </main>
  )
}

function Main(p: { drive: Drive; vaultId: string; onSignIn: () => void; onSignOut: () => void; onChangeVault: () => void }) {
  const vault = useMemo(() => new Vault(p.drive, p.vaultId, config.taskFileId), [p.drive, p.vaultId])
  const [tasks, setTasks] = useState<Task[] | null>(null)
  const [showDone, setShowDone] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [needSignIn, setNeedSignIn] = useState(false)
  // The draft survives the trip to Google when the sign-in runs out while typing.
  const [sentence, setSentenceState] = useState(() => sessionStorageGet(DRAFT_KEY))
  const setSentence = (v: string) => { setSentenceState(v); sessionStorageSet(DRAFT_KEY, v) }
  const [closing, setClosing] = useState<Task | null>(null)
  const input = useRef<HTMLInputElement>(null)

  /** Ticks a task; a parent with open subtasks first asks whether to tick them too, as on Android. */
  const tick = (t: Task) => {
    if (t.open && tasks?.some((c) => c.parent === t.key && c.open)) setClosing(t)
    else run(() => vault.toggle(t))
  }

  /** Runs one action against the vault and turns each way it can fail into a plain message. */
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
      setTasks(await vault.load())
      setNeedSignIn(false)
    } catch (e) {
      if (e instanceof AuthExpired) setNeedSignIn(true)
      else if (e instanceof VaultError) setMessage(e.message)
      else setMessage(`เชื่อมต่อ Google Drive ไม่ได้ (${(e as Error).message})`)
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => { run(async () => undefined) }, [vault])

  const open = tasks?.filter((t) => t.open) ?? []
  const doneToday = tasks?.filter((t) => t.status === 'DONE' && t.done === today()) ?? []

  return (
    <main class="app">
      <header>
        <div>
          <h1>Omni Task</h1>
          <span class="muted">{new Date().toLocaleDateString('th-TH', { weekday: 'long', day: 'numeric', month: 'long' })}</span>
        </div>
        <div class="actions">
          <button aria-label="โหลดใหม่" disabled={busy} onClick={() => run(async () => undefined)}>↻</button>
          <details>
            <summary aria-label="เมนู">⋯</summary>
            <div class="menu">
              <button onClick={p.onChangeVault}>เปลี่ยน vault</button>
              <button onClick={p.onSignOut}>ออกจากระบบ</button>
            </div>
          </details>
        </div>
      </header>

      <form
        class="quick"
        onSubmit={(e) => {
          e.preventDefault()
          const s = sentence
          if (!s.trim()) return
          run(async () => {
            const res = await vault.add(s)
            if (res.ok) setSentence('')
            return res
          })
          input.current?.focus()
        }}
      >
        <input
          ref={input}
          aria-label="เพิ่มงาน"
          placeholder="เพิ่มงาน เช่น ส่งรายงาน พรุ่งนี้ 9:00 #งาน"
          value={sentence}
          onInput={(e) => setSentence(e.currentTarget.value)}
        />
        <button class="primary" disabled={busy || !sentence.trim()}>เพิ่ม</button>
      </form>

      {needSignIn && (
        <div class="banner" role="alert">
          หมดเวลาเข้าสู่ระบบ <button onClick={p.onSignIn}>เข้าสู่ระบบอีกครั้ง</button>
        </div>
      )}
      {message && <div class="banner" role="alert">{message}</div>}

      {tasks === null ? (
        <p class="muted pad">กำลังโหลด...</p>
      ) : (
        <>
          {BUCKETS.map(([bucket, label]) => {
            const list = open.filter((t) => t.bucket === bucket)
            if (!list.length) return null
            return (
              <section key={bucket}>
                <h2>{label} <span class="count">{list.length}</span></h2>
                <ul class="plain">{list.map((t) => <TaskRow key={t.key} task={t} busy={busy} onToggle={() => tick(t)} />)}</ul>
              </section>
            )
          })}
          {open.length === 0 && <p class="muted pad">ไม่มีงานค้าง</p>}
          <button class="link" onClick={() => setShowDone(!showDone)}>
            {showDone ? 'ซ่อน' : 'ดู'}งานที่เสร็จวันนี้ ({doneToday.length})
          </button>
          {showDone && <ul class="plain">{doneToday.map((t) => <TaskRow key={t.key} task={t} busy={busy} onToggle={() => tick(t)} />)}</ul>}
        </>
      )}

      {closing && (
        <div class="scrim" onClick={() => setClosing(null)}>
          <div class="dialog" role="dialog" aria-modal="true" aria-label="งานย่อยยังค้าง" onClick={(e) => e.stopPropagation()}>
            <h2>งานย่อยของ "{closing.title}" ยังค้างอยู่</h2>
            <button class="primary" onClick={() => { const t = closing; setClosing(null); run(() => vault.toggle(t, true)) }}>ติ๊กงานย่อยด้วย</button>
            <button onClick={() => { const t = closing; setClosing(null); run(() => vault.toggle(t)) }}>ติ๊กแค่งานนี้</button>
            <button class="link" onClick={() => setClosing(null)}>ยกเลิก</button>
          </div>
        </div>
      )}
    </main>
  )
}

function TaskRow({ task, busy, onToggle }: { task: Task; busy: boolean; onToggle: () => void }) {
  const late = task.bucket === 'OVERDUE'
  return (
    <li class={`task${task.parent ? ' sub' : ''}${task.open ? '' : ' done'}`}>
      <button
        class="check"
        role="checkbox"
        aria-checked={!task.open}
        aria-label={`${task.open ? 'ติ๊กเสร็จ' : 'ยกเลิกการติ๊ก'} ${task.title}`}
        disabled={busy}
        onClick={onToggle}
      >
        {task.open ? '' : '✓'}
      </button>
      <div class="body">
        <div class="title">{PRIORITY[task.priority] && <span>{PRIORITY[task.priority]} </span>}{task.title}</div>
        <div class="meta">
          {task.due && <span class={late ? 'late' : ''}>📅 {shortDate(task.due)}</span>}
          {task.reminder && <span>⏰ {task.reminder}</span>}
          {task.recurrence && <span>🔁 {task.recurrence}</span>}
          {task.tags.filter((t) => !t.startsWith('remind-at-')).map((t) => <span key={t} class="tag">#{t}</span>)}
        </div>
      </div>
    </li>
  )
}
