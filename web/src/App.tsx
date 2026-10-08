import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { Auth } from './auth'
import { config } from './config'
import { today } from './core'
import { AuthExpired, Drive, DriveFile } from './drive'
import type { Bucket, Task } from './types'
import { Vault, VaultError } from './vault'

type Stage = 'setup' | 'signin' | 'vault' | 'ready'

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

export function App() {
  const [clientId, setClientId] = useState(config.clientId)
  const [vaultId, setVaultId] = useState(config.vaultId)
  const [signedIn, setSignedIn] = useState(false)
  const auth = useMemo(() => (clientId ? new Auth(clientId) : null), [clientId])
  const drive = useMemo(() => (auth ? new Drive(() => auth.getToken()) : null), [auth])

  const stage: Stage = !clientId ? 'setup' : !signedIn ? 'signin' : !vaultId ? 'vault' : 'ready'

  const signIn = async () => {
    await auth!.getToken('select_account')
    setSignedIn(true)
  }

  if (stage === 'setup') return <Setup onSave={(id) => { config.clientId = id; setClientId(id) }} />
  if (stage === 'signin') return <SignIn onSignIn={signIn} onReset={() => { config.clientId = null; setClientId(null) }} />
  if (stage === 'vault')
    return <PickVault drive={drive!} onPick={(id) => { config.vaultId = id; setVaultId(id) }} />
  return (
    <Main
      drive={drive!}
      vaultId={vaultId!}
      onSignIn={signIn}
      onSignOut={() => { auth!.signOut(); setSignedIn(false) }}
      onChangeVault={() => { config.vaultId = null; setVaultId(null) }}
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

function SignIn({ onSignIn, onReset }: { onSignIn: () => Promise<void>; onReset: () => void }) {
  const [error, setError] = useState('')
  return (
    <main class="card center">
      <h1>Omni Task</h1>
      <p>อ่านและเขียนงานใน Obsidian vault ของคุณบน Google Drive โดยตรง</p>
      <button class="primary" onClick={() => onSignIn().catch((e) => setError(String(e.message ?? e)))}>
        เข้าสู่ระบบด้วย Google
      </button>
      {error && <p class="error" role="alert">เข้าสู่ระบบไม่สำเร็จ ({error})</p>}
      <button class="link" onClick={onReset}>เปลี่ยน Client ID</button>
    </main>
  )
}

function PickVault({ drive, onPick }: { drive: Drive; onPick: (id: string) => void }) {
  const [name, setName] = useState('')
  const [found, setFound] = useState<{ file: DriveFile; where: string }[] | null>(null)
  const [error, setError] = useState('')

  const search = async () => {
    setError('')
    try {
      const folders = await drive.findFolders(name.trim())
      const withPlace = await Promise.all(
        folders.map(async (file) => {
          const parent = file.parents?.[0] ? await drive.get(file.parents[0], 'name').catch(() => null) : null
          return { file, where: parent?.name ?? 'My Drive' }
        }),
      )
      setFound(withPlace)
    } catch (e) {
      setError(String((e as Error).message))
    }
  }

  return (
    <main class="card center">
      <h1>เลือก vault</h1>
      <p>พิมพ์ชื่อโฟลเดอร์ของ Obsidian vault ใน Google Drive</p>
      <form onSubmit={(e) => { e.preventDefault(); search() }}>
        <input aria-label="ชื่อโฟลเดอร์ vault" value={name} onInput={(e) => setName(e.currentTarget.value)} />
        <button class="primary" disabled={!name.trim()}>ค้นหา</button>
      </form>
      {error && <p class="error" role="alert">{error}</p>}
      {found?.length === 0 && <p>ไม่พบโฟลเดอร์ชื่อนี้</p>}
      <ul class="plain">
        {found?.map(({ file, where }) => (
          <li key={file.id}>
            <button class="row" onClick={() => onPick(file.id)}>
              <strong>{file.name}</strong>
              <span class="muted">อยู่ใน {where}</span>
            </button>
          </li>
        ))}
      </ul>
    </main>
  )
}

function Main(p: { drive: Drive; vaultId: string; onSignIn: () => Promise<void>; onSignOut: () => void; onChangeVault: () => void }) {
  const vault = useMemo(() => new Vault(p.drive, p.vaultId), [p.drive, p.vaultId])
  const [tasks, setTasks] = useState<Task[] | null>(null)
  const [showDone, setShowDone] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [needSignIn, setNeedSignIn] = useState(false)
  const [sentence, setSentence] = useState('')
  const input = useRef<HTMLInputElement>(null)

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
          หมดเวลาเข้าสู่ระบบ <button onClick={() => p.onSignIn().then(() => run(async () => undefined))}>เข้าสู่ระบบอีกครั้ง</button>
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
                <ul class="plain">{list.map((t) => <TaskRow key={t.key} task={t} busy={busy} onToggle={() => run(() => vault.toggle(t))} />)}</ul>
              </section>
            )
          })}
          {open.length === 0 && <p class="muted pad">ไม่มีงานค้าง</p>}
          <button class="link" onClick={() => setShowDone(!showDone)}>
            {showDone ? 'ซ่อน' : 'ดู'}งานที่เสร็จวันนี้ ({doneToday.length})
          </button>
          {showDone && <ul class="plain">{doneToday.map((t) => <TaskRow key={t.key} task={t} busy={busy} onToggle={() => run(() => vault.toggle(t))} />)}</ul>}
        </>
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
