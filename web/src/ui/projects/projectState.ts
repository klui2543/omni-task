import { useRef, useState } from 'preact/hooks'
import type { ProjectsIn } from '../../types'

// What the owner chose on the Projects page, kept on this device. Android keeps the same choices in its settings
// file (omni-settings.json); the web does not read or write that file yet, so these stay here for now:
// the order of projects, the starred ones, the state of each branch, each project's task order and "do in order".

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
})

export const toggleIn = (list: string[], v: string) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v])
