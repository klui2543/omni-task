// The vault logic shared with the Android app, compiled from Kotlin (see ../scripts/sync-core.mjs).
import { WebApi } from './kotlin/OmniTask-shared.mjs'
import type { EditOp, EditResult, FocusIn, FocusOut, Query, Task, TaskList, ViewsIn, ViewsOut } from './types'

const api = WebApi.getInstance()

/** Today as YYYY-MM-DD in the device's own time zone. */
export const today = () => new Date().toLocaleDateString('en-CA')

export const taskFilePath: string = api.taskFile()

export const loadTasks = (fileKey: string, path: string, text: string): Task[] =>
  JSON.parse(api.loadTasks(fileKey, path, text, today()))

/** The list as Android shows it for [query]: groups of task keys, plus each parent's subtask progress. */
export const listTasks = (fileKey: string, path: string, text: string, query: Query): TaskList =>
  JSON.parse(api.list(fileKey, path, text, today(), JSON.stringify(query)))

/** The Focus page for the note: brief, day plan, suggestions and review queue, from what this device chose. */
export const focus = (fileKey: string, path: string, text: string, state: FocusIn): FocusOut =>
  JSON.parse(api.focus(fileKey, path, text, JSON.stringify(state)))

const result = (json: string): EditResult => JSON.parse(json)

export const toggle = (text: string, t: Task, withSubtasks: boolean) =>
  result(api.toggle(text, t.raw, t.lineIndex, today(), withSubtasks))
export const addTask = (text: string, sentence: string) => result(api.addTask(text, sentence, today()))

export const editTask = (text: string, t: Task, op: EditOp) =>
  result(api.editTask(text, t.raw, t.lineIndex, today(), JSON.stringify(op)))

/** Takes the task out with its whole block; the result carries what was cut, for undo or the archive. */
export const cutTask = (text: string, t: Task) => result(api.cut(text, t.raw, t.lineIndex))

export const restoreBlock = (text: string, index: number, lines: string[]) =>
  result(api.restore(text, index, JSON.stringify(lines)))

/** The archive note with [lines] added under this month's heading. */
export const archiveAppend = (archive: string, lines: string[]): string =>
  api.archiveAppend(archive, JSON.stringify(lines), today())

/** A repeat rule in words, or null when the Tasks plugin would not read it. */
export const describeRule = (rule: string): string | null => api.describeRule(rule) ?? null

export const isConflictCopy = (name: string): boolean => api.isConflictCopy(name)

export const archiveFilePath: string = api.archiveFile()

// Where the app's notes live, and where they lived before the Omni folder moved into the back-office folder.
// Until the owner moves them, the old places are still read.
export const legacyTaskFilePath: string = api.legacyTaskFile()
export const legacyArchiveFilePath: string = api.legacyArchiveFile()
export const omniDirPath: string = api.omniDir()
export const legacyOmniDirPath: string = api.legacyOmniDir()
export const profileFilePath: string = api.profileFile()
export const legacyProfileFilePath: string = api.legacyProfileFile()

/** The archive note with an archived block taken back out; null when it is no longer there. */
export const archiveRemove = (archive: string, lines: string[]): string | null =>
  api.archiveRemove(archive, JSON.stringify(lines)) ?? null

/* ---------- Views ---------- */


/** Kanban columns, Matrix quadrants, calendar tasks and Gantt bars for the note, under the list's filters. */
export const views = (fileKey: string, path: string, text: string, state: ViewsIn): ViewsOut =>
  JSON.parse(api.views(fileKey, path, text, JSON.stringify(state)))

/** Quick add from a Kanban column: the new task starts in [status]. */
export const addTaskInStatus = (text: string, sentence: string, status: Task['status']) =>
  result(api.addTaskInStatus(text, sentence, today(), status))

/* ---------- Projects ---------- */

import type { BranchOp, BranchResult, ProjectEditResult, ProjectsIn, ProjectsOut } from './types'

const TAGS_KEY = 'omni.ignoredTags'

/**
 * Tags that are lists or their categories never name a project. The shared logic keeps them as a setting, so
 * they are remembered on this device and applied at start, before any note is read.
 */
export const setIgnoredTags = (tags: string[]) => {
  try { localStorage.setItem(TAGS_KEY, JSON.stringify(tags)) } catch { /* just not kept */ }
}
try {
  const saved = JSON.parse(localStorage.getItem(TAGS_KEY) ?? '[]')
  if (Array.isArray(saved) && saved.length) api.projectIgnoreTags(JSON.stringify(saved))
} catch { /* start without them */ }

const edited = (json: string): ProjectEditResult => JSON.parse(json)

/** Projects, branches and lists from the TaskForge note, the list notes and what this device chose. */
export const projects = (fileKey: string, path: string, text: string, notes: { key: string; path: string; text: string }[], state: ProjectsIn): ProjectsOut =>
  JSON.parse(api.projects(fileKey, path, text, JSON.stringify(notes), JSON.stringify(state), today()))

/** A project or branch tag renamed in every task line of [text]; `changed` counts the lines. */
export const renameTag = (text: string, old: string, name: string) => edited(api.projectRenameTag(text, old, name))
export const cleanProjectName = (name: string): string => api.projectCleanName(name)
export const addTagged = (text: string, tag: string, title: string) => edited(api.projectAddTagged(text, tag, title, today()))
export const chainTasks = (text: string, ordered: Task[], on: boolean) =>
  edited(api.projectChain(text, JSON.stringify(ordered.map((t) => ({ raw: t.raw, lineIndex: t.lineIndex }))), on))
export const branchChange = (fileKey: string, path: string, text: string, states: string[], op: BranchOp): BranchResult =>
  JSON.parse(api.projectBranchChange(fileKey, path, text, JSON.stringify(states), JSON.stringify(op)))

export const listStarters = (): { path: string; text: string }[] => JSON.parse(api.listStarters())
export const listIconThemes = (): { label: string; emoji: string[] }[] => JSON.parse(api.listIconThemes())
export const createListNote = (name: string, icon: string, categories: string[]) => edited(api.listCreate(name, icon, JSON.stringify(categories)))
export const updateListNote = (text: string, path: string, icon: string, categories: string[]) =>
  edited(api.listUpdate(text, path, icon, JSON.stringify(categories)))
export const addListItem = (text: string, path: string, title: string, category: string | null) =>
  edited(api.listAddItem(text, path, title, category ?? undefined, today()))
export const includeInList = (text: string, tasks: Task[], tag: string, category: string | null) =>
  edited(api.listInclude(text, JSON.stringify(tasks.map((t) => ({ raw: t.raw, lineIndex: t.lineIndex }))), tag, category ?? undefined))
