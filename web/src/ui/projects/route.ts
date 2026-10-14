import { useEffect, useState } from 'preact/hooks'
import { MODES, type ViewMode } from '../views/modes'

/**
 * Where on the Projects page the owner is, kept in the address (#/projects/project/<name>/map) so the browser's back
 * steps out of it as Android's back does, and each screen can be linked to. [world] is Ygdrasil, or, with a
 * [project], a project opened from it (its back goes to the tree again).
 */
export interface ProjectRoute {
  world: boolean
  project?: string
  list?: string
  map?: boolean
  view?: ViewMode
}

const BASE = '#/projects'
export const HOME: ProjectRoute = { world: false }

export function parseRoute(hash: string): ProjectRoute {
  if (!hash.startsWith(BASE + '/')) return HOME
  const parts = hash.slice(BASE.length + 1).split('/').map((s) => { try { return decodeURIComponent(s) } catch { return s } })
  const [head, name, sub] = parts
  if (head === 'list' && name) return { world: false, list: name }
  if (head === 'ygdrasil' && !name) return { world: true }
  if ((head === 'project' || head === 'ygdrasil') && name) {
    return {
      world: head === 'ygdrasil', project: name,
      ...(sub === 'map' ? { map: true } : MODES.includes(sub as ViewMode) ? { view: sub as ViewMode } : {}),
    }
  }
  return HOME
}

export function formatRoute(r: ProjectRoute): string {
  const enc = encodeURIComponent
  if (r.list) return `${BASE}/list/${enc(r.list)}`
  if (r.project) return `${BASE}/${r.world ? 'ygdrasil' : 'project'}/${enc(r.project)}${r.map ? '/map' : r.view ? '/' + r.view : ''}`
  return r.world ? `${BASE}/ygdrasil` : BASE
}

/** One step back, in the order of Android's BackHandler on this tab. */
export function parentOf(r: ProjectRoute): ProjectRoute {
  if (!r.project) return HOME
  if (r.map) return r.world ? { world: true } : { world: false, project: r.project }
  if (r.view) return { world: r.world, project: r.project }
  return r.world ? { world: true } : HOME
}

/**
 * The route from the address, and the ways to change it: [go] adds a step the browser's back undoes, [replace]
 * changes this step (a switch between a project's views), [back] steps out as the on-screen "‹ กลับ" does: through
 * the browser's history when that step came from there, so the two backs never disagree.
 */
export function useProjectRoute() {
  const [route, setRoute] = useState(() => parseRoute(location.hash))
  useEffect(() => {
    const read = () => setRoute(parseRoute(location.hash))
    addEventListener('hashchange', read)
    addEventListener('popstate', read)
    return () => { removeEventListener('hashchange', read); removeEventListener('popstate', read) }
  }, [])
  const go = (r: ProjectRoute) => {
    const to = formatRoute(r)
    if (to === location.hash) return
    history.pushState({ back: location.hash }, '', to)
    setRoute(parseRoute(to))
  }
  const replace = (r: ProjectRoute) => {
    const to = formatRoute(r)
    history.replaceState(history.state, '', to)
    setRoute(parseRoute(to))
  }
  const back = () => {
    const to = parentOf(route)
    const state = history.state as { back?: string } | null
    if (state?.back === formatRoute(to)) history.back()
    else replace(to)
  }
  return { route, go, replace, back }
}

/** The page to show for the address: a deeper address inside Projects (#/projects/...) is still the Projects page. */
export function useProjectsAddress<P extends string>(page: P): P | 'projects' {
  const [deep, setDeep] = useState(() => location.hash.startsWith(BASE + '/'))
  useEffect(() => {
    const read = () => setDeep(location.hash.startsWith(BASE + '/'))
    addEventListener('hashchange', read)
    addEventListener('popstate', read)
    return () => { removeEventListener('hashchange', read); removeEventListener('popstate', read) }
  }, [])
  return deep ? 'projects' : page
}
