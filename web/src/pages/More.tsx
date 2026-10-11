import { useState } from 'preact/hooks'
import type { PageProps } from '../Home'
import { URGENT_RULES, urgentRule } from '../settings'
import { PageHead } from '../ui/PageHead'

/**
 * The bottom bar's "More", as on Android: a page like Settings with what no tab holds, the assistant, settings,
 * reloading the note and what counts as urgent.
 */
export function MorePage(p: PageProps) {
  const [urgent, setUrgent] = useState(urgentRule.get)
  const row = (label: string, onClick: () => void, hl = false) => (
    <button class={`more-row${hl ? ' hl' : ''}`} onClick={onClick}>
      <span class="grow">{label}</span>
      <span class="chev" aria-hidden="true">›</span>
    </button>
  )
  return (
    <main class="page more">
      <PageHead title="เพิ่มเติม" />
      <section class="fcard more-card">{row('ผู้ช่วย', () => p.onNavigate('assistant'))}</section>
      <section class="fcard more-card">
        {row('ตั้งค่า', () => p.onNavigate('settings'), true)}
      </section>
      <section class="fcard more-group" aria-label="งานที่นับว่า ด่วน">
        <h2 class="more-title">งานที่นับว่า "ด่วน"</h2>
        <div class="chips">
          {URGENT_RULES.map(([rule, label]) => (
            <button key={rule} class={`chip${urgent === rule ? ' on' : ''}`} aria-pressed={urgent === rule} onClick={() => { urgentRule.set(rule); setUrgent(rule) }}>{label}</button>
          ))}
        </div>
      </section>
      <section class="fcard more-card">{row('โหลดใหม่', p.onReload)}</section>
    </main>
  )
}
