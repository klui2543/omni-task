import type { PageProps } from '../../Home'
import type { Task } from '../../types'
import { QuickAdd } from '../QuickAdd'

/**
 * Quick add from the "+" on a folded Kanban column: the full quick-add sheet (the sentence read as it is typed,
 * "#" for lists and tags, the usual hints, voice, asking the assistant), with the new task starting in that
 * column's status, as on Android.
 */
export function StatusQuickAdd(p: PageProps & { status: Task['status']; statusLabel: string; onClose: () => void }) {
  return <QuickAdd {...p} />
}
