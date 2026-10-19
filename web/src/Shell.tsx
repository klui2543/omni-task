import type { ComponentChildren } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'
import { pendingQuestion } from './assistantState'
import { host } from './host'
import './parity.css'
import { useMenuDrag } from './ui/menuDrag'
import './ui/obsidianMenu.css'

export type Page = 'focus' | 'tasks' | 'projects' | 'assistant' | 'settings'

/** The sidebar on wide screens: every destination (the assistant included; there is no More there). Settings sits apart at its foot. */
export const TABS: [Page, string][] = [
  ['focus', 'โฟกัส'],
  ['tasks', 'งาน'],
  ['projects', 'โปรเจกต์/ลิสต์'],
  ['assistant', 'ผู้ช่วย'],
]
/** The tabs at the bottom of a narrow screen, as the Android app's: Focus, Tasks, Projects and Settings (which holds the rest). */
const BAR: [Page, string][] = [
  ['focus', 'โฟกัส'],
  ['tasks', 'งาน'],
  ['projects', 'โปรเจกต์'],
  ['settings', 'ตั้งค่า'],
]
/** Ready questions that float up above the line while it is typed in, worded so the assistant reads them right. */
const PRESETS = [
  'วันนี้มีอะไรบ้าง', 'พรุ่งนี้มีอะไรบ้าง', 'จัดลำดับงานทั้งหมดให้หน่อย', 'สัปดาห์หน้ามีอะไรบ้าง', 'วางแผนสัปดาห์หน้าให้หน่อย', 'เดือนหน้ามีอะไรบ้าง',
]
const PAGES: Page[] = ['focus', 'tasks', 'projects', 'assistant', 'settings']

const pageOf = (hash: string): Page => {
  const name = hash.replace(/^#\/?/, '')
  // The Views tab was merged into Tasks (its address still opens the tasks, in the last view).
  if (name === 'views') return 'tasks'
  // More was folded into Settings.
  if (name === 'more') return 'settings'
  return PAGES.includes(name as Page) ? (name as Page) : 'focus'
}

/** The open page, kept in the address (#/tasks) so reloading and the back button stay on it. */
export function usePage(): [Page, (p: Page) => void] {
  const [page, setPage] = useState(() => pageOf(host.address.hash()))
  useEffect(() => {
    const onHash = () => setPage(pageOf(host.address.hash()))
    const stop = host.address.listen(onHash)
    // A change made between the first render and this listener would otherwise be missed.
    onHash()
    return stop
  }, [])
  return [page, (p) => host.address.assign('#/' + p)]
}

/** [waiting]: notes with edits made offline that are not on Drive yet. */
export type Sync = { state: 'ok' | 'busy' | 'off' | 'offline'; at: Date | null; waiting?: number }

const hhmm = (d: Date) => d.toLocaleTimeString('th-TH', { hour: '2-digit', minute: '2-digit', hour12: false })

/**
 * The line to type a question for the assistant, with Send once something is typed and the ready questions floating
 * up above it while it is typed in ([below] it in the side bar, so the line does not move under a tap). The assistant
 * asks it as soon as the tasks are there, as it does for a question sent from quick add.
 */
function AskLine(p: { class: string; below?: boolean; onNavigate: (p: Page) => void }) {
  const [question, setQuestion] = useState('')
  const [typing, setTyping] = useState(false)
  const send = (q: string) => {
    pendingQuestion.set(q)
    setQuestion('')
    setTyping(false)
    p.onNavigate('assistant')
  }
  const ask = (e: Event) => {
    e.preventDefault()
    const q = question.trim()
    if (q) send(q)
  }
  const presets = typing && (
    <div class={`as-chips ${p.below ? 'side-presets' : 'dock-presets'}`} role="toolbar" aria-label="คำถามสำเร็จรูป">
      {PRESETS.map((q) => <button key={q} type="button" class="as-chip" onMouseDown={(e) => e.preventDefault()} onClick={() => send(q)}>{q}</button>)}
    </div>
  )
  return (
    <>
      {!p.below && presets}
      <form class={p.class} onSubmit={ask}>
        <input aria-label="ถามผู้ช่วย" placeholder="ถามผู้ช่วย" enterkeyhint="send" value={question} onInput={(e) => setQuestion((e.target as HTMLInputElement).value)} onFocus={() => setTyping(true)} onBlur={() => setTyping(false)} />
        {question.trim() && <button type="submit" aria-label="ส่งถึงผู้ช่วย">→</button>}
      </form>
      {p.below && presets}
    </>
  )
}

/**
 * A sidebar on wide screens (computer, iPad across) and a floating bar at the bottom on narrow ones
 * (iPad upright, phone), as on Android: a line to type a question for the assistant (left out on the assistant's own
 * page), and under it the tabs as words around the add button in the middle. The side bar has the add button at its
 * top, as Android's side rail does, and the line to the assistant under it, so both are on every page at every width.
 * A thin bar runs along the top while the vault is read or written.
 */
export function Shell(p: { page: Page; onNavigate: (p: Page) => void; onAdd: () => void; sync: Sync; onReload: () => void; children: ComponentChildren }) {
  const syncText = p.sync.state === 'off' ? 'ต้องเข้าสู่ระบบใหม่'
    : p.sync.state === 'busy' ? (host.obsidian ? 'กำลังอัปเดต...' : 'กำลังซิงก์...')
    : p.sync.state === 'offline' ? (p.sync.waiting ? `ออฟไลน์ รอส่ง ${p.sync.waiting} โน้ต` : 'ออฟไลน์')
    : p.sync.at ? `${host.obsidian ? 'อัปเดตล่าสุด' : 'ซิงก์ล่าสุด'} ${hhmm(p.sync.at)}` : ''
  // The side menu inside Obsidian on a narrow tab.
  const [menu, setMenu] = useState(false)
  const shell = useRef<HTMLDivElement>(null)
  // A drag to the right pulls the menu out, a drag to the left pushes it back (menuDrag.ts).
  useMenuDrag(shell, menu, setMenu, !!host.obsidian)
  return (
    <div ref={shell} class={host.obsidian ? (menu ? 'shell om om-open' : 'shell om') : 'shell'}>
      {p.sync.state === 'busy' && <div class="top-progress" role="progressbar" aria-label="กำลังซิงก์" />}
      <nav class="side" aria-label="เมนูหลัก">
        <div class="brand">
          <div class="brand-name">Omni Task</div>
          <div class="brand-sub"><span class={`dot ${p.sync.state}`} />{host.obsidian ? 'อยู่ใน Obsidian' : 'เชื่อม Google Drive'}</div>
        </div>
        <div class="side-add">
          <button class="primary" onClick={p.onAdd}>+ เพิ่มงาน</button>
          {p.page !== 'assistant' && <AskLine class="side-ask" below onNavigate={p.onNavigate} />}
        </div>
        <div class="side-items">
          {TABS.map(([page, label]) => (
            <button key={page} class="side-item" aria-current={p.page === page ? 'page' : undefined} onClick={() => p.onNavigate(page)}>{label}</button>
          ))}
        </div>
        <div class="side-foot">
          <button class="side-item" aria-current={p.page === 'settings' ? 'page' : undefined} onClick={() => p.onNavigate('settings')}>ตั้งค่า</button>
          <button class="sync" aria-label="โหลดใหม่" title="โหลดใหม่" onClick={p.onReload}>{syncText}</button>
        </div>
      </nav>
      <div class="content">
        {/* Inside Obsidian on a narrow tab: the menu button beside the page's title (the side menu opens from it). */}
        {host.obsidian && (
          <button class="om-burger" aria-label="เมนู" aria-expanded={menu} onClick={() => setMenu(true)}>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
          </button>
        )}
        {/* Upright on the iPad the side bar is hidden: being offline still shows, at the top, and a tap reads again. */}
        {p.sync.state === 'offline' && (
          <button class="sync-chip" aria-label="โหลดใหม่" title="โหลดใหม่" onClick={p.onReload}><span class="dot offline" />{syncText}</button>
        )}
        {p.children}
      </div>
      {/*
        Inside Obsidian on a narrow tab Obsidian's own bar sits at the bottom, so there is no bottom bar: a side menu
        holds the pages and the assistant, and adding a task floats above Obsidian's bar.
      */}
      {host.obsidian && <button class="om-fab" aria-label="เพิ่มงาน" onClick={p.onAdd}>+</button>}
      {host.obsidian && (
        <>
          <div class="om-scrim" onClick={() => setMenu(false)} />
          <nav class="om-drawer" aria-label="เมนูหลัก" aria-hidden={!menu} inert={!menu} onKeyDown={(e) => { if (e.key === 'Escape') setMenu(false) }}>
            <div class="brand">
              <div class="brand-name">Omni Task</div>
              <div class="brand-sub"><span class={`dot ${p.sync.state}`} />อยู่ใน Obsidian</div>
            </div>
            <button class="primary" onClick={() => { setMenu(false); p.onAdd() }}>+ เพิ่มงาน</button>
            {p.page !== 'assistant' && <AskLine class="side-ask" below onNavigate={(to) => { setMenu(false); p.onNavigate(to) }} />}
            <div class="side-items">
              {TABS.map(([page, label]) => (
                <button key={page} class="side-item" aria-current={p.page === page ? 'page' : undefined} onClick={() => { setMenu(false); p.onNavigate(page) }}>{label}</button>
              ))}
            </div>
            <div class="side-foot">
              <button class="side-item" aria-current={p.page === 'settings' ? 'page' : undefined} onClick={() => { setMenu(false); p.onNavigate('settings') }}>ตั้งค่า</button>
              <button class="sync" aria-label="โหลดใหม่" title="โหลดใหม่" onClick={p.onReload}>{syncText}</button>
            </div>
          </nav>
        </>
      )}
      <div class={host.obsidian ? 'dock om-hidden' : 'dock'}>
        {p.page !== 'assistant' && <AskLine class="dock-ask" onNavigate={p.onNavigate} />}
        <nav class="bar" aria-label="เมนูหลัก">
          {BAR.map(([page, label], i) => [
            // Adding a task, in the middle of the tabs where it always was.
            i === 2 && <button key="add" class="bar-add" aria-label="เพิ่มงาน" onClick={p.onAdd}>+</button>,
            <button key={page} aria-current={p.page === page ? 'page' : undefined} onClick={() => p.onNavigate(page)}>{label}</button>,
          ])}
        </nav>
      </div>
    </div>
  )
}
