// The vault logic shared with the Android app, compiled from Kotlin (see ../scripts/sync-core.mjs).
import { WebApi } from './kotlin/OmniTask-shared.mjs'
import type { EditResult, Query, Task, TaskList } from './types'

const api = WebApi.getInstance()

/** Today as YYYY-MM-DD in the device's own time zone. */
export const today = () => new Date().toLocaleDateString('en-CA')

export const taskFilePath: string = api.taskFile()

/** Whether a file name is a copy a sync app left after a clash, e.g. `TaskForge (conflict 2026-10-09-05-55-31).md`. */
export const isConflictCopy = (name: string): boolean => api.isConflictCopy(name)

export const loadTasks = (fileKey: string, path: string, text: string): Task[] =>
  JSON.parse(api.loadTasks(fileKey, path, text, today()))

/** The list as Android shows it for [query]: groups of task keys, plus each parent's subtask progress. */
export const listTasks = (fileKey: string, path: string, text: string, query: Query): TaskList =>
  JSON.parse(api.list(fileKey, path, text, today(), JSON.stringify(query)))

const result = (json: string): EditResult => JSON.parse(json)

export const toggle = (text: string, t: Task, withSubtasks: boolean) =>
  result(api.toggle(text, t.raw, t.lineIndex, today(), withSubtasks))
export const addTask = (text: string, sentence: string) => result(api.addTask(text, sentence, today()))
