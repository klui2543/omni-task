export type Bucket = 'OVERDUE' | 'TODAY' | 'THIS_WEEK' | 'NEXT_WEEK' | 'FUTURE' | 'NO_DATE'

export interface Task {
  key: string
  raw: string
  title: string
  status: 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
  open: boolean
  priority: 'HIGHEST' | 'HIGH' | 'MEDIUM' | 'NONE' | 'LOW' | 'LOWEST'
  created: string | null
  scheduled: string | null
  due: string | null
  done: string | null
  recurrence: string | null
  reminder: string | null
  tags: string[]
  project: string | null
  description: string
  bucket: Bucket
  lineIndex: number
  parent: string | null
}

/** What the shared Kotlin logic answers to an edit: the whole new note text, or why nothing changed. */
export interface EditResult {
  ok: boolean
  text?: string | null
  error?: 'conflict' | 'rule' | 'empty' | null
  message?: string | null
}
