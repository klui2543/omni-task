import type { ComponentChildren } from 'preact'
import { useEffect, useState } from 'preact/hooks'

export type Page = 'focus' | 'tasks' | 'projects' | 'more' | 'assistant' | 'settings'

/** The sidebar on wide screens: every destination (the assistant included; there is no More there). Settings sits apart at its foot. */
export const TABS: [Page, string][] = [
  ['focus', 'โฟกัส'],
  ['tasks', 'งาน'],
  ['projects', 'โปรเจกต์/ลิสต์'],
  ['assistant', 'ผู้ช่วย'],
]
/** The bar at the bottom of a narrow screen, as the Android app's: Focus, Tasks, the add button, Projects and More (assistant and settings). */
const BAR: [Page, string][] = [
  ['focus', 'โฟกัส'],
  ['tasks', 'งาน'],
  ['projects', 'โปรเจกต์'],
  ['more', 'เพิ่มเติม'],
]
const PAGES: Page[] = ['focus', 'tasks', 'projects', 'more', 'assistant', 'settings']

const pageOf = (hash: string): Page => {
  const name = hash.replace(/^#\/?/, '')
  // The Views tab was merged into Tasks (its address still opens the tasks, in the last view).
  if (name === 'views') return 'tasks'
  return PAGES.includes(name as Page) ? (name as Page) : 'focus'
}

/** The open page, kept in the address (#/tasks) so reloading and the back button stay on it. */
export function usePage(): [Page, (p: Page) => void] {
  const [page, setPage] = useState(() => pageOf(location.hash))
  useEffect(() => {
    const onHash = () => setPage(pageOf(location.hash))
    addEventListener('hashchange', onHash)
    // A change made between the first render and this listener would otherwise be missed.
    onHash()
    return () => removeEventListener('hashchange', onHash)
  }, [])
  return [page, (p) => { location.hash = '/' + p }]
}

export type Sync = { state: 'ok' | 'busy' | 'off'; at: Date | null }

const hhmm = (d: Date) => d.toLocaleTimeString('th-TH', { hour: '2-digit', minute: '2-digit', hour12: false })

/**
 * A sidebar on wide screens (computer, iPad across) and a floating bar at the bottom on narrow ones
 * (iPad upright, phone), as in the mockups. Text only, no icons, like the Android app's labels. The assistant
 * is a round button floating at the bottom right of every page; adding a task is the middle of the bar.
 */
export function Shell(p: { page: Page; onNavigate: (p: Page) => void; onAdd: () => void; sync: Sync; onReload: () => void; children: ComponentChildren }) {
  const syncText = p.sync.state === 'off' ? 'ต้องเข้าสู่ระบบใหม่' : p.sync.state === 'busy' ? 'กำลังซิงก์...' : p.sync.at ? `ซิงก์ล่าสุด ${hhmm(p.sync.at)}` : ''
  const onMore = p.page === 'more' || p.page === 'assistant' || p.page === 'settings'
  return (
    <div class="shell">
      <nav class="side" aria-label="เมนูหลัก">
        <div class="brand">
          <div class="brand-name">Omni Task</div>
          <div class="brand-sub"><span class={`dot ${p.sync.state}`} />เชื่อม Google Drive</div>
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
      <div class="content">{p.children}</div>
      {p.page !== 'assistant' && (
        <button class="ai-fab" aria-label="เปิดผู้ช่วย" onClick={() => p.onNavigate('assistant')}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8Z" /></svg>
        </button>
      )}
      <nav class="bar" aria-label="เมนูหลัก">
        {BAR.slice(0, 2).map(([page, label]) => (
          <button key={page} aria-current={p.page === page ? 'page' : undefined} onClick={() => p.onNavigate(page)}>{label}</button>
        ))}
        <button class="bar-add" aria-label="เพิ่มงาน" onClick={p.onAdd}>+</button>
        {BAR.slice(2).map(([page, label]) => (
          <button key={page} aria-current={(page === 'more' ? onMore : p.page === page) ? 'page' : undefined} onClick={() => p.onNavigate(page)}>{label}</button>
        ))}
      </nav>
    </div>
  )
}
