import { today } from '../core'
import type { Task } from '../types'

type Tone = 'plain' | 'red' | 'accent' | 'teal' | 'lime' | 'amber'
interface Meta {
  text: string
  tone: Tone
}

const shortDate = (iso: string) =>
  new Date(iso + 'T00:00').toLocaleDateString('th-TH', { day: 'numeric', month: 'short' })

/** The small labels under a task title, in the order and wording of Android's task rows. */
export function metaOf(t: Task, progress?: { done: number; total: number }): Meta[] {
  const day = today()
  const out: Meta[] = []
  if (progress) out.push({ text: `${progress.done}/${progress.total}`, tone: progress.done === progress.total ? 'lime' : 'plain' })
  if (t.due) {
    const late = t.open && t.due < day
    const text = t.due === day ? 'ครบวันนี้' : late ? `เลย ${shortDate(t.due)}` : `ครบ ${shortDate(t.due)}`
    out.push({ text, tone: late ? 'red' : 'plain' })
  }
  if (t.scheduled) out.push({ text: t.scheduled === day ? 'นัดวันนี้' : `นัด ${shortDate(t.scheduled)}`, tone: 'plain' })
  if (t.status === 'IN_PROGRESS') out.push({ text: 'กำลังทำ', tone: 'accent' })
  if (t.reminder) out.push({ text: t.reminder, tone: 'plain' })
  if (t.repeatText) out.push({ text: t.repeatText, tone: 'plain' })
  for (const tag of t.tags) {
    if (tag.startsWith('remind-at-')) continue
    out.push(tag === t.project ? { text: tag, tone: 'teal' } : { text: `#${tag}`, tone: 'accent' })
  }
  if (t.attachments) out.push({ text: `รูป ${t.attachments}`, tone: 'plain' })
  if (t.links) out.push({ text: `โน้ต ${t.links}`, tone: 'plain' })
  return out
}

const TINT: Record<Task['priority'], string> = {
  HIGHEST: 'var(--red)',
  HIGH: 'var(--amber)',
  MEDIUM: 'var(--blue)',
  NONE: 'var(--faint)',
  LOW: 'var(--faint)',
  LOWEST: 'var(--faint)',
}

/** The round tick: its ring takes the priority colour, half filled while in progress, green once done. */
export function Check({ task, disabled, onToggle }: { task: Task; disabled?: boolean; onToggle: () => void }) {
  const state = task.status === 'DONE' ? 'done' : task.status === 'CANCELLED' ? 'cancelled' : task.status === 'IN_PROGRESS' ? 'prog' : ''
  return (
    <button
      class="check"
      role="checkbox"
      aria-checked={!task.open}
      aria-label={`${task.open ? 'ติ๊กเสร็จ' : 'ยกเลิกการติ๊ก'} ${task.title}`}
      disabled={disabled}
      onClick={onToggle}
    >
      <span class={`ring ${state}`} style={{ '--tint': TINT[task.priority] }} />
    </button>
  )
}

export function TaskRow(p: {
  task: Task
  progress?: { done: number; total: number }
  busy: boolean
  selected?: boolean
  onToggle: () => void
  onOpen?: () => void
}) {
  const t = p.task
  const meta = metaOf(t, p.progress)
  return (
    <li class={`trow${t.open ? '' : ' done'}${p.selected ? ' selected' : ''}`}>
      <Check task={t} disabled={p.busy} onToggle={p.onToggle} />
      <div class="trow-body" onClick={p.onOpen}>
        <span class="ttitle">{t.title}</span>
        {t.preview && <span class="tpreview">{t.preview}</span>}
        {meta.length > 0 && (
          <span class="pills">
            {meta.map((m, i) => <span key={i} class={`pill ${m.tone}`}>{m.text}</span>)}
          </span>
        )}
      </div>
    </li>
  )
}
