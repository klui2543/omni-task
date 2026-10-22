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
  /** The vault path of the note the task is in. */
  note?: string | null
  /** Today's round of a repeating task whose earlier round is still open; ticking it moves the same line past today. */
  todayCopy?: boolean
  /** The task's 🆔, and the ids it waits on (⛔). */
  id?: string | null
  dependsOn?: string[]
  /** The list note the task is an item of, by name (only list items read for the filter have one). */
  list?: string | null
  /** The linked notes on a line of their own under the task (the ones the edit panel can take off). */
  linkLines?: string[]
}

/** The list's filters, grouping and sorting, as the shared TaskQuery takes them. */
export interface Query {
  statuses?: Task['status'][]
  priorities?: Task['priority'][]
  tags?: string[]
  buckets?: Bucket[]
  kinds?: string[]
  /** Note names (without .md) and list names to show, as Android's filter sheet. */
  notes?: string[]
  lists?: string[]
  text?: string
  groupBy?: 'DATE' | 'NOTE' | 'PRIORITY' | 'TAG' | 'STATUS' | 'NONE'
  sorts?: { by: string; ascending: boolean }[]
  /** Branch states ("project\tpath\tSTATE"): tasks in a parked branch are left out. */
  branches?: string[]
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
  /** Blocked tasks by key, each with the title of the open task it waits on. */
  waiting?: Record<string, string>
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
  /** To another project or branch: [field] is the tag the task had. */
  | { op: 'moveTo'; value: string; field: string }
  /** A task with no project given one: the tag goes in front, its own tags stay. */
  | { op: 'homeTo'; value: string }
  | { op: 'status'; value: Task['status'] }
  | { op: 'kind'; value: Kind; who?: string; custom?: string[] }
  | { op: 'customKind'; value: string; custom: string[] }
  | { op: 'describe'; value: string }
  | { op: 'subtask'; value: string }
  /** A linked note (the link text) or an attached image (its file name), added or taken off. */
  | { op: 'link' | 'unlink' | 'image' | 'unimage'; value: string }
  /** The description went to the note [value]: its lines give way to a link. */
  | { op: 'moveDescription'; value: string }
  /** The direct subtasks in a new order: their lines as a JSON list. */
  | { op: 'order'; value: string }
  | { op: 'quadrant'; value: Quadrant; field: 'TWO_DAYS' | 'THREE_DAYS' | 'THIS_WEEK' }
  /** Why the task is still open: a dated line under it (Android's stuck log). */
  | { op: 'stuck'; value: string }

export type Kind = 'NORMAL' | 'WAITING' | 'FUTURE' | 'SOMEDAY'

/** What was chosen on this device and what the calendar holds, for the Focus page; see WebFocus.StateIn. */
export interface FocusIn {
  now: string
  skippedToday: string[]
  dismissed: string[]
  reviewed: Record<string, string>
  futureCount: number
  /** The single countdown (a task title) kept before there could be several. */
  countdown?: string | null
  /** The pinned countdowns, as their pin keys (see Countdown.Pin). */
  countdowns: string[]
  tonightBed: { evening: string; time: string } | null
  tonightWake?: { evening: string; time: string } | null
  /** Tonight's wake time was set on the watch (from Android's settings file). */
  wakeFromWatch?: boolean
  profile: string | null
  events: { id: number; title: string; begin: string; end: string; allDay: boolean; link?: string }[]
  /** The year ahead, read when the countdown picker opens. */
  pickEvents?: FocusIn['events']
  branches?: string[]
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
  /** For an event: its page in Google Calendar. */
  link?: string
  /** For a task: already ticked off; it stays in the plan, greyed. */
  done?: boolean
  /** For an event: where it stands against the clock. */
  phase?: 'past' | 'active' | 'upcoming'
  /** For an active event: how far into it the clock is, 0 to 1. */
  progress?: number
  /** For a task late or open a long while: [extra] starts with when it last moved, and [weeks] has a bar a week. */
  stuck?: boolean
  weeks?: StuckWeek[]
}

export interface Pick {
  key: string
  title: string
  sub: string
}

/** A row of the countdown picker: ticking saves [pin], or takes it away when [on]; [group] heads its rows. */
export interface CountdownChoice {
  pin: string
  title: string
  sub: string
  text: string
  late: boolean
  on: boolean
  group: string
}

/** The Focus page as the shared logic builds it; see WebFocus.Out. */
export interface FocusOut {
  ring: { done: number; total: number; overdue: number; events: number }
  /** Until bed at night, the free time left in the daytime; [night] is the night the sleep card plans, there all day. */
  third: {
    kind: 'night' | 'free'
    minutes: number
    night?: { bedAt: string; wakeAt: string; toBed: number; sleep: number; because?: string; becauseAt?: string; wakePicked?: boolean; fromWatch?: boolean; tomorrow?: boolean }
  }
  /** The pinned countdowns, soonest first; [key] is the task's when it is one. */
  countdowns: { pin: string; key?: string; title: string; text: string; late: boolean; event: boolean }[]
  notices: string[]
  plan: { label: string; late: boolean; tasks: number; items: PlanItem[] }[]
  suggestions: { id: string; key: string; kind: 'RAISE_PRIORITY' | 'SOFT_DATE' | 'MARK_FUTURE'; title: string; text: string }[]
  waiting: { key: string; who?: string; age?: number }[]
  future: string[]
  futureAge: Record<string, number>
  futureChosen: Pick[]
  futureCandidates: Pick[]
  review: { key: string; kind: Kind; age?: number; last?: string }[]
  /** The countdown picker's two tabs. */
  countdownTasks: CountdownChoice[]
  countdownEvents: CountdownChoice[]
  bedtime: string
  evening: string
  /** The usual wake time or tonight's pick. */
  wake: string
  softDate: string
  /** Until noon after a night: the minutes of sleep it gave. */
  lastNight?: number
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
  /** A project's own views: only the dated subtasks of its own tasks come onto its calendar. */
  scoped?: boolean
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
  gantt: { project: string; none: boolean; spans: { key: string; start: string; end: string; stale?: number | null; waitKey?: string | null; waitTitle?: string | null }[] }[]
  progress: Record<string, { done: number; total: number }>
}

/* ---------- Projects ---------- */

/** What was chosen on this device for the Projects page; see WebProjects.StateIn. */
export interface ProjectsIn {
  order: string[]
  starred: string[]
  /** Branch states as Android saves them: "project\tpath\tSTATE". */
  branches: string[]
  taskOrder: Record<string, string[]>
  strict: string[]
  /** Tags of the owner's own task kinds, which never name a project. */
  kindTags?: string[]
  /** List tags as last read, used only while the list notes are read again. */
  listTags?: string[]
  /** The tags the owner made projects; missing until chosen once, when every first tag still names one. */
  projectTags?: string[]
}

export type BranchState = 'ACTIVE' | 'TRYING' | 'CHOSEN' | 'PARKED'

export interface BranchOut {
  path: string
  name: string
  tag: string
  depth: number
  parentPath: string
  state: BranchState
  done: number
  count: number
  /** The tasks tagged exactly with this branch, by key. */
  own: string[]
  /** The paths of the sub-branches. */
  children: string[]
}

export interface OrderOut {
  key: string
  pos: number
  next: boolean
  locked: boolean
  /** Which line goes under the title: waits for task [pos], waits for [waitingOn], or next, date and subtasks. */
  meta: 'strict' | 'waiting' | 'plain'
  waitingOn?: string
  date?: string
  subDone?: number
  subTotal?: number
}

export interface ProjectOut {
  name: string
  total: number
  done: number
  pct: number
  overdue: number
  blocked: string[]
  next?: { key: string; title: string }
  starred: boolean
  strict: boolean
  renameLines: number
  renameFiles: number
  branches: BranchOut[]
  order: OrderOut[]
  finished: string[]
}

export interface ListItemOut {
  key: string
  done: boolean
  tags: string[]
  sub: string
}

export interface ListOut {
  /** The list note's file id. */
  key: string
  name: string
  path: string
  icon: string
  emoji: string
  categories: string[]
  tag: string
  done: number
  total: number
  items: ListItemOut[]
}

/** The Projects page as the shared logic builds it; see WebProjects.Out. */
export interface ProjectsOut {
  projects: ProjectOut[]
  lists: ListOut[]
  noteTasks: Task[]
  parked: string[]
  ignored: string[]
  /** Tags that are not projects, which the owner can make one, most used first. */
  others: { name: string; count: number }[]
  /** When the owner never chose: the projects as every first tag named them, to keep as the choice. */
  seed?: string[]
}

/** What Project Ygdrasil shows; see Ygdrasil.Filter in the shared code. */
export interface YgFilter {
  /** The task list's query (types, dates, priority, status, tags), as the Tasks page sends it. */
  query: Query
  blockedOnly: boolean
  starredOnly: boolean
  parked: boolean
  /** Someday work (#สักวัน), shown greyed with its label. */
  someday: boolean
  links: boolean
}

/** Something placed on the tree: [x] is its left edge, [y] its centre line. */
export interface YgSpot {
  kind: 'TRUNK' | 'LIMB' | 'TASK' | 'MORE'
  x: number
  y: number
  w: number
  h: number
  /** The limb's tag (for a task or "more" line, the limb holding it). */
  tag?: string
  project?: string
  path?: string
  name?: string
  state?: BranchState
  done: number
  total: number
  key?: string
  /** "And N more", the tasks under a folded limb, or every task shown (the trunk). */
  count: number
  folded: boolean
  /** A someday task (#สักวัน), drawn greyed with its label. */
  someday?: boolean
}

export interface YgEdge {
  kind: 'BRANCH' | 'TASK' | 'LINK' | 'LINK_ACROSS'
  /** An SVG path. */
  d: string
  state?: BranchState
}

export interface YgOut {
  spots: YgSpot[]
  edges: YgEdge[]
  width: number
  height: number
  matched: number
  worlds: number
  links: number
  /** Every limb's tag, for folding them all. */
  tags: string[]
  /** Tasks that passed the filter with no project, for the tray under the tree. */
  strays: YgStray[]
}

/** A group in the tray of tasks with no project: its tag (none: no tag at all) and its task keys in each order. */
export interface YgStray {
  tag?: string
  due: string[]
  old: string[]
  az: string[]
}

/** A list note read from Drive: its id, path in the vault and text. */
export interface ListNote {
  id: string
  path: string
  text: string
}

export interface ProjectEditResult extends Omit<EditResult, "error"> {
  error?: string | null
  changed?: number
  path?: string | null
}

export interface BranchResult {
  ok: boolean
  states: string[]
  error?: 'missing' | 'empty' | 'hasTasks' | 'unknown' | null
  oldTag?: string | null
  newTag?: string | null
  path?: string | null
}

export type BranchOp = { op: 'set' | 'choose' | 'add' | 'rename' | 'delete' | 'moveProject' | 'graft'; project: string; path?: string; value?: string }

/* ---------- Task kinds ---------- */

/** What this device keeps about kinds, in Android's form; see WebKinds.StateIn. */
export interface KindsState {
  /** The owner's own kinds as "name\temoji\ttag". */
  custom: string[]
  /** Built-in kinds (TaskKind names) hidden from the choices and from Focus. */
  hidden: string[]
}

export interface KindBuiltin { id: Kind; label: string; tag: string | null; hidden: boolean }
export interface KindCustom { name: string; emoji: string; tag: string; label: string }
export interface KindsView { state: KindsState; builtin: KindBuiltin[]; custom: KindCustom[] }
export interface KindsAdd { ok: boolean; error?: 'empty' | 'taken' | null; tag: string; view: KindsView }
export interface KindPicker {
  current: Kind
  customTag: string | null
  who: string | null
  hint: string | null
  builtin: KindBuiltin[]
  custom: KindCustom[]
}

/** A list a quick-add "#word" can send the task to; see WebQuickAdd. */
export interface QuickList {
  path: string
  name: string
  emoji: string
  categories: string[]
}

/** One choice for the "#word" being typed: a list (and one of its categories) or a tag in use. */
export interface QuickPick {
  list?: QuickList | null
  category?: string | null
  tag?: string | null
}

/** What a quick-add sentence is read as, shown before it is saved. */
export interface QuickPreview {
  title: string
  due?: string | null
  time?: string | null
  tags: string[]
  priority: Task['priority']
  priorityLabel?: string | null
  /** The "#word" being typed, without the #. */
  token?: string | null
  picks: QuickPick[]
}

/* ---------- Stuck log (WebStuck) ---------- */

/** What one of a task's weeks held: nothing, a note, or a finished subtask. */
export type StuckWeek = 'NONE' | 'NOTE' | 'DONE'

/** One point on a task's path: made, a note, a subtask finished; [text] is what is shown beside the dot. */
export interface StuckEvent {
  day: string
  step: 'START' | 'NOTE' | 'DONE'
  text: string
}

export interface StuckTask {
  key: string
  title: string
  /** Late, open 21 days or more, or already explained: the path goes up front. */
  matters: boolean
  notes: number
  last: string | null
  events: StuckEvent[]
  /** When it last moved, or that nothing is noted yet. */
  status: string
  weeks: StuckWeek[]
  done: number
  total: number
  age: number | null
}

export interface StuckView {
  /** Open a long while with nothing written, the one waiting longest first. */
  toAsk: { key: string; title: string; age: number | null }[]
  /** Every task with notes, its path without the start, the most recently explained first. */
  log: { key: string; title: string; open: boolean; age: number | null; events: StuckEvent[] }[]
}
