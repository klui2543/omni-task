import { useEffect, useMemo, useState } from 'preact/hooks'
import { Auth } from './auth'
import { config } from './config'
import { AuthExpired, Drive, DriveFile } from './drive'
import { Main } from './Home'
import { FoundVault, findVaults } from './vault'

type Stage = 'setup' | 'signin' | 'vault' | 'ready'

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
