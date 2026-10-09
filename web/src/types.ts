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
  /** The description's first line. */
  preview: string | null
  /** The repeat rule in words, e.g. ทุกวัน. */
  repeatText: string | null
  attachments: number
  links: number
}

/** The list's filters, grouping and sorting, as the shared TaskQuery takes them. */
export interface Query {
  statuses?: Task['status'][]
  priorities?: Task['priority'][]
  tags?: string[]
  buckets?: Bucket[]
  kinds?: string[]
  text?: string
  groupBy?: 'DATE' | 'NOTE' | 'PRIORITY' | 'TAG' | 'STATUS' | 'NONE'
  sorts?: { by: string; ascending: boolean }[]
}

export interface Group {
  label: string
  /** How the heading is coloured: overdue warns, today stands out, the rest stay quiet. */
  tone: 'ALERT' | 'ACCENT' | 'PLAIN' | 'MUTED'
  keys: string[]
}

export interface TaskList {
  groups: Group[]
  /** Done and total subtasks per parent key. */
  progress: Record<string, { done: number; total: number }>
}

/** What the shared Kotlin logic answers to an edit: the whole new note text, or why nothing changed. */
export interface EditResult {
  ok: boolean
  text?: string | null
  error?: 'conflict' | 'rule' | 'empty' | null
  message?: string | null
}
