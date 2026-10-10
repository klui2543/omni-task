import type { PageProps } from '../../Home'
import type { Task, ViewsOut } from '../../types'
import { shortDate } from './dates'

/** What every view gets from the Views page. */
export interface ViewCtx {
  p: PageProps
  out: ViewsOut
  task: (key: string) => Task | undefined
  today: string
  open: (t: Task) => void
  /** The key of the task whose edit panel is open. */
  selectedKey?: string
}

type Tone = 'plain' | 'red' | 'accent' | 'teal' | 'lime' | 'amber'
export interface Pill {
  text: string
  tone: Tone
}

/**
 * The small labels on a Kanban card, one line: subtask progress, due date (scheduled only when there is no due
 * date), in progress, reminder, repeat, image and note counts. No tags, as on Android's compact cards.
 */
export function compactMeta(t: Task, today: string, progress?: { done: number; total: number }): Pill[] {
  const out: Pill[] = []
  if (progress) out.push({ text: `${progress.done}/${progress.total}`, tone: progress.done === progress.total ? 'lime' : 'plain' })
  if (t.status === 'DONE' && t.done) out.push({ text: `เสร็จ ${shortDate(t.done)}`, tone: 'lime' })
  if (t.due) {
    const late = t.open && t.due < today
    out.push({ text: t.due === today ? 'ครบวันนี้' : late ? `เลย ${shortDate(t.due)}` : `ครบ ${shortDate(t.due)}`, tone: late ? 'red' : 'plain' })
  } else if (t.scheduled) {
    out.push({ text: t.scheduled === today ? 'นัดวันนี้' : `นัด ${shortDate(t.scheduled)}`, tone: 'plain' })
  }
  if (t.status === 'IN_PROGRESS') out.push({ text: 'กำลังทำ', tone: 'accent' })
  if (t.reminder) out.push({ text: t.reminder, tone: 'plain' })
  if (t.repeatText) out.push({ text: t.repeatText, tone: 'plain' })
  if (t.attachments) out.push({ text: `รูป ${t.attachments}`, tone: 'plain' })
  if (t.links) out.push({ text: `โน้ต ${t.links}`, tone: 'plain' })
  return out
}
