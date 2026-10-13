import { useRef, useState } from 'preact/hooks'
import type { ProjectsIn, YgFilter } from '../../types'
import { settingsBus } from '../../settingsBus'
import { applyProjectTags } from '../../core'

// What the owner chose on the Projects page: the order of projects, the starred ones, the state of each branch, each
// project's task order and "do in order". Kept on this device and shared with Android through its settings file
// (omni-settings.json, see settingsSync.ts).

const KEY = 'omni.projects'
const SEEDED_KEY = 'omni.listsSeeded'

const EMPTY: ProjectsIn = { order: [], starred: [], branches: [], taskOrder: {}, strict: [] }

const load = (): ProjectsIn => {
  try {
    return { ...EMPTY, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }
  } catch {
    return EMPTY
  }
}

export function useProjectsLocal(): [ProjectsIn, (change: (s: ProjectsIn) => ProjectsIn) => void] {
  const [state, setState] = useState<ProjectsIn>(load)
  // Changes made in the same moment each start from the one before.
  const latest = useRef(state)
  latest.current = state
  return [
    state,
    (change) => {
      const next = change(latest.current)
      latest.current = next
      setState(next)
      try { localStorage.setItem(KEY, JSON.stringify(next)) } catch { /* just not kept */ }
      applyProjectTags(next.projectTags)
      settingsBus.changed()
    },
  ]
}

/** Whether the starter lists were written already on this device (Android keeps the same flag). */
export const listsSeeded = {
  get: () => { try { return localStorage.getItem(SEEDED_KEY) === 'yes' } catch { return false } },
  set: () => { try { localStorage.setItem(SEEDED_KEY, 'yes') } catch { /* just not kept */ } },
}

/** A project renamed: its place in every choice moves to the new name. */
export const renamedProject = (s: ProjectsIn, old: string, name: string): ProjectsIn => ({
  ...s,
  order: s.order.map((n) => (n === old ? name : n)),
  starred: s.starred.map((n) => (n === old ? name : n)),
  taskOrder: Object.fromEntries(Object.entries(s.taskOrder).map(([k, v]) => [k === old ? name : k, v])),
  strict: s.strict.map((n) => (n === old ? name : n)),
  projectTags: s.projectTags?.map((n) => (n === old ? name : n)),
})

/** [tag] made a project, or (off) a plain tag again; [seed] is the choice to start from when none was made yet. */
export const withProject = (s: ProjectsIn, tag: string, on: boolean, seed: string[]): ProjectsIn => {
  const now = (s.projectTags ?? seed).filter((n) => n !== tag)
  return { ...s, projectTags: on ? [...now, tag] : now }
}

export const toggleIn = (list: string[], v: string) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v])

/** The branch states chosen on this device; pages other than Projects leave out the tasks in parked branches. */
export const branchStates = (): string[] => load().branches

const YG_KEY = 'omni.ygdrasil'

/** Project Ygdrasil's filter and folded limbs, kept on this device between visits. */
export function useYgdrasilLocal(plain: YgFilter): [{ filter: YgFilter; folded: string[] }, (change: Partial<{ filter: YgFilter; folded: string[] }>) => void] {
  const [state, setState] = useState(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(YG_KEY) ?? '{}')
      return { filter: { ...plain, ...(saved.filter ?? {}) } as YgFilter, folded: Array.isArray(saved.folded) ? (saved.folded as string[]) : [] }
    } catch {
      return { filter: plain, folded: [] as string[] }
    }
  })
  const latest = useRef(state)
  latest.current = state
  return [
    state,
    (change) => {
      const next = { ...latest.current, ...change }
      latest.current = next
      setState(next)
      try { localStorage.setItem(YG_KEY, JSON.stringify(next)) } catch { /* just not kept */ }
    },
  ]
}
