// What the shared code answers with (see shared/.../web/WebYgdrasil.kt and WebCore.TaskDto); the web app has the same shapes.

export type BranchState = 'ACTIVE' | 'TRYING' | 'CHOSEN' | 'PARKED'

export interface YgFilter {
  show: 'OPEN' | 'ALL' | 'DONE'
  dates: 'ANY' | 'OVERDUE' | 'TODAY' | 'WEEK' | 'UNDATED'
  priorities: string[]
  blockedOnly: boolean
  starredOnly: boolean
  parked: boolean
  links: boolean
  text: string
}

export const PLAIN_FILTER: YgFilter = { show: 'OPEN', dates: 'ANY', priorities: [], blockedOnly: false, starredOnly: false, parked: false, links: true, text: '' }

export interface YgSpot {
  kind: 'TRUNK' | 'LIMB' | 'TASK' | 'MORE'
  x: number
  y: number
  w: number
  h: number
  tag?: string
  project?: string
  path?: string
  name?: string
  state?: BranchState
  done: number
  total: number
  key?: string
  count: number
  folded: boolean
}

export interface YgEdge {
  kind: 'BRANCH' | 'TASK' | 'LINK' | 'LINK_ACROSS'
  d: string
  state?: BranchState
  /** For a wait: the keys of the task waited for and of the one waiting. */
  blocker?: string
  waiting?: string
}

export interface YgOut {
  spots: YgSpot[]
  edges: YgEdge[]
  width: number
  height: number
  matched: number
  worlds: number
  links: number
  tags: string[]
}

export interface Task {
  key: string
  raw: string
  title: string
  status: 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED'
  open: boolean
  priority: string
  tags: string[]
  project?: string
  lineIndex: number
}

/** What a change to the note answers: the whole new text, or why nothing changed. */
export interface Edited {
  ok: boolean
  text?: string
  error?: string
  id?: string
}

/** The choices Android and the web keep in omni-settings.json that the tree reads. */
export interface ProjectsIn {
  order: string[]
  starred: string[]
  branches: string[]
  kindTags: string[]
}
