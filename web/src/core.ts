// The vault logic shared with the Android app, compiled from Kotlin (see ../scripts/sync-core.mjs).
import { WebApi } from './kotlin/OmniTask-shared.mjs'
import type { EditOp, EditResult, Query, Task, TaskList } from './types'

const api = WebApi.getInstance()

/** Today as YYYY-MM-DD in the device's own time zone. */
export const today = () => new Date().toLocaleDateString('en-CA')

export const taskFilePath: string = api.taskFile()

export const loadTasks = (fileKey: string, path: string, text: string): Task[] =>
  JSON.parse(api.loadTasks(fileKey, path, text, today()))

/** The list as Android shows it for [query]: groups of task keys, plus each parent's subtask progress. */
export const listTasks = (fileKey: string, path: string, text: string, query: Query): TaskList =>
  JSON.parse(api.list(fileKey, path, text, today(), JSON.stringify(query)))

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

/** The archive note with an archived block taken back out; null when it is no longer there. */
export const archiveRemove = (archive: string, lines: string[]): string | null =>
  api.archiveRemove(archive, JSON.stringify(lines)) ?? null
