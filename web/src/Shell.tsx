import type { ComponentChildren } from 'preact'
import { useEffect, useState } from 'preact/hooks'

export type Page = 'focus' | 'tasks' | 'views' | 'projects' | 'assistant' | 'settings'

/** The main pages in Android's tab order; Settings sits apart at the foot of the sidebar. */
export const TABS: [Page, string][] = [
  ['focus', 'โฟกัส'],
  ['tasks', 'งาน'],
  ['views', 'มุมมอง'],
  ['projects', 'โปรเจกต์/ลิสต์'],
  ['assistant', 'ผู้ช่วย'],
]
const PAGES: Page[] = [...TABS.map(([p]) => p), 'settings']

const pageOf = (hash: string): Page => {
  const name = hash.replace(/^#\/?/, '') as Page
  return PAGES.includes(name) ? name : 'tasks'
}

/** The open page, kept in the address (#/tasks) so reloading and the back button stay on it. */
export function usePage(): [Page, (p: Page) => void] {
  const [page, setPage] = useState(() => pageOf(location.hash))
  useEffect(() => {
    const onHash = () => setPage(pageOf(location.hash))
    addEventListener('hashchange', onHash)
    return () => removeEventListener('hashchange', onHash)
  }, [])
  return [page, (p) => { location.hash = '/' + p }]
}

export type Sync = { state: 'ok' | 'busy' | 'off'; at: Date | null }

const hhmm = (d: Date) => d.toLocaleTimeString('th-TH', { hour: '2-digit', minute: '2-digit', hour12: false })

/**
 * A sidebar on wide screens (computer, iPad across) and a floating bar at the bottom on narrow ones
 * (iPad upright, phone), as in the mockups. Text only, no icons, like the Android app's labels.
 */
export function Shell(p: { page: Page; onNavigate: (p: Page) => void; sync: Sync; onReload: () => void; children: ComponentChildren }) {
  const syncText = p.sync.state === 'off' ? 'ต้องเข้าสู่ระบบใหม่' : p.sync.state === 'busy' ? 'กำลังซิงก์...' : p.sync.at ? `ซิงก์ล่าสุด ${hhmm(p.sync.at)}` : ''
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
      <nav class="bar" aria-label="เมนูหลัก">
        {TABS.map(([page, label]) => (
          <button key={page} aria-current={p.page === page ? 'page' : undefined} onClick={() => p.onNavigate(page)}>{label}</button>
        ))}
      </nav>
    </div>
  )
}
