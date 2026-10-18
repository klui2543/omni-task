import type { ComponentChildren } from 'preact'
import { useEffect, useState } from 'preact/hooks'
import { useStored } from './views/hooks'
import { MODES, MODE_LABEL, type ViewMode } from './views/modes'
import { host } from '../host'

export const LAYOUTS = ['list', ...MODES] as const
export type Layout = (typeof LAYOUTS)[number]
const LABEL: Record<Layout, string> = { list: 'รายการ', ...MODE_LABEL }

/**
 * How the Tasks tab is shown: the list or one of the views, remembered on this device. The Views tab's address
 * (#/views) still opens the tasks, in the view last used there.
 */
export function useLayout() {
  const [viewMode, setViewMode] = useStored<ViewMode>('omni.viewMode', 'kanban', MODES)
  const [stored, setStored] = useStored<Layout>('omni.tasksLayout', 'list', LAYOUTS)
  const [layout, setLayout] = useState<Layout>(() => (host.address.hash() === '#/views' ? viewMode : stored))
  // The old address, on a fresh load or followed while on this page: show that view and leave the address as Tasks'.
  const showViews = () => {
    if (host.address.hash() !== '#/views') return
    setLayout(viewMode)
    setStored(viewMode)
    host.address.replace('#/tasks', null)
  }
  useEffect(() => {
    showViews()
    return host.address.listen(showViews)
  })
  return [
    layout,
    (l: Layout) => {
      setLayout(l)
      setStored(l)
      if (l !== 'list') setViewMode(l)
    },
  ] as const
}

/**
 * One row: the filter, the views to choose from (one at a time) and, at the end, what applies to the chosen one
 * (sorting and grouping for the list, sorting for Kanban, what counts as urgent for Matrix), as on Android.
 */
export function LayoutBar(p: { layout: Layout; onLayout: (l: Layout) => void; filter: { count: number; onOpen: () => void }; end?: ComponentChildren }) {
  return (
    <div class="toolbar layout-bar">
      <button class={`chip tool${p.filter.count ? ' on' : ''}`} onClick={p.filter.onOpen}>
        กรอง{p.filter.count ? ` ${p.filter.count}` : ''}
      </button>
      <div class="chips layout-chips" role="tablist" aria-label="มุมมอง">
        {LAYOUTS.map((l) => (
          <button key={l} role="tab" aria-selected={l === p.layout} class={`chip tool${l === p.layout ? ' on' : ''}`} onClick={() => p.onLayout(l)}>{LABEL[l]}</button>
        ))}
      </div>
      {p.end}
    </div>
  )
}
