// The vault logic shared with the Android and web apps, compiled from Kotlin (see ../scripts/sync-core.mjs).
import { WebApi } from './kotlin/OmniTask-shared.mjs'
import type { Edited, ProjectsIn, Task, YgFilter, YgOut } from './types'

const api = WebApi.getInstance()

/** Today as YYYY-MM-DD in the device's own time zone. */
export const today = () => new Date().toLocaleDateString('en-CA')

export const taskFilePath: string = api.taskFile()
export const legacyTaskFilePath: string = api.legacyTaskFile()
export const omniDirPath: string = api.omniDir()
export const legacyOmniDirPath: string = api.legacyOmniDir()

export const loadTasks = (path: string, text: string): Task[] => JSON.parse(api.loadTasks(path, path, text, today()))

/** Every project in one tree, placed by the shared code; [notes] are the list notes, whose tags are not projects. */
export const ygdrasil = (
  path: string, text: string, notes: { key: string; path: string; text: string }[], state: ProjectsIn, filter: YgFilter, folded: string[],
): YgOut =>
  JSON.parse(api.ygdrasil(path, path, text, JSON.stringify(notes), JSON.stringify(state), JSON.stringify(filter), JSON.stringify(folded), today()))

const ref = (t: Task) => JSON.stringify({ raw: t.raw, lineIndex: t.lineIndex })

export const toggle = (text: string, t: Task): Edited => JSON.parse(api.toggle(text, t.raw, t.lineIndex, today(), false, false))

/** The task moves to another project or branch: its project tag [from] becomes [tag] where it stands. */
export const moveTo = (text: string, t: Task, from: string, tag: string): Edited =>
  JSON.parse(api.editTask(text, t.raw, t.lineIndex, today(), JSON.stringify({ op: 'moveTo', value: tag, field: from })))

/** A new task in a project or branch: [title] is read like quick add (dates, priority) and gets the tag. */
export const addTagged = (text: string, tag: string, title: string): Edited => JSON.parse(api.projectAddTagged(text, tag, title, today()))

/** [waiting] waits for [blocker] (🆔 and ⛔); refused as `cycle`, `self` or `conflict`. */
export const link = (text: string, blocker: Task, waiting: Task): Edited => JSON.parse(api.ygdrasilLink(text, ref(blocker), ref(waiting)))

export const unlink = (text: string, blocker: Task, waiting: Task): Edited => JSON.parse(api.ygdrasilUnlink(text, ref(blocker), ref(waiting)))
