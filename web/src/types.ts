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
  start: string | null
  reminderOn: 'DUE' | 'SCHEDULED' | null
  linkNames: string[]
  attachmentNames: string[]
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
  /** For a delete or archive: where the block was and its lines, so it can be put back. */
  cutIndex?: number | null
  cutLines?: string[] | null
}

/** One change from the edit panel, as the shared WebCore.editTask takes it. */
export type EditOp =
  | { op: 'date'; field: 'DUE' | 'SCHEDULED' | 'START'; value: string | null }
  | { op: 'priority'; value: Task['priority'] }
  | { op: 'recurrence'; value: string | null }
  | { op: 'reminder'; value: string | null; on: 'DUE' | 'SCHEDULED' }
  | { op: 'addTag' | 'removeTag'; value: string }
  | { op: 'status'; value: Task['status'] }
  | { op: 'kind'; value: Kind }
  | { op: 'describe'; value: string }
  | { op: 'subtask'; value: string }
  | { op: 'quadrant'; value: Quadrant; field: 'TWO_DAYS' | 'THREE_DAYS' | 'THIS_WEEK' }

export type Kind = 'NORMAL' | 'WAITING' | 'FUTURE' | 'SOMEDAY'

/** What was chosen on this device and what the calendar holds, for the Focus page; see WebFocus.StateIn. */
export interface FocusIn {
  now: string
  skippedToday: string[]
  dismissed: string[]
  reviewed: Record<string, string>
  futureCount: number
  countdown: string | null
  tonightBed: { evening: string; time: string } | null
  profile: string | null
  events: { id: number; title: string; begin: string; end: string; allDay: boolean }[]
}

export interface PlanItem {
  type: 'task' | 'event' | 'now'
  time?: string
  key?: string
  title?: string
  lead?: string
  late: boolean
  extra?: string
  blocked: boolean
  range?: string
}

export interface Pick {
  key: string
  title: string
  sub: string
}

/** The Focus page as the shared logic builds it; see WebFocus.Out. */
export interface FocusOut {
  ring: { done: number; total: number; overdue: number; events: number }
  third: { kind: 'night' | 'free'; minutes: number; night?: { bedAt: string; wakeAt: string; toBed: number; sleep: number; because?: string } }
  countdown?: { key: string; title: string; text: string; late: boolean }
  notices: string[]
  plan: { label: string; late: boolean; tasks: number; items: PlanItem[] }[]
  suggestions: { id: string; key: string; kind: 'RAISE_PRIORITY' | 'SOFT_DATE' | 'MARK_FUTURE'; title: string; text: string }[]
  waiting: { key: string; who?: string; age?: number }[]
  future: string[]
  futureAge: Record<string, number>
  futureChosen: Pick[]
  futureCandidates: Pick[]
  review: { key: string; kind: Kind; age?: number; last?: string }[]
  countdownChoices: Pick[]
  bedtime: string
  evening: string
  softDate: string
}

/* ---------- Views ---------- */

export type Quadrant = 'DO' | 'PLAN' | 'QUICK' | 'LATER'

/** What the Views page asks of the shared logic; see WebViews.In. */
export interface ViewsIn {
  today: string
  query: Query
  hideDone: boolean
  urgent: 'TWO_DAYS' | 'THREE_DAYS' | 'THIS_WEEK'
  /** First day of the Gantt range. */
  ganttFirst: string
}

/** Task keys by column, quadrant, calendar day and Gantt bar; see WebViews.Out. */
export interface ViewsOut {
  shown: number
  kanban: { status: Task['status']; keys: string[] }[]
  matrix: { id: Quadrant; label: string; urgent: boolean; important: boolean; keys: string[] }[]
  /** What to say when a task is dragged across the urgent line. */
  sideways: string
  /** Tasks on the calendar; [at] is when the reminder fires (yyyy-MM-ddTHH:mm). */
  calendar: { key: string; at?: string }[]
  gantt: { project: string; none: boolean; spans: { key: string; start: string; end: string }[] }[]
  progress: Record<string, { done: number; total: number }>
}
