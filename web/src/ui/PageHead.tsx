import type { ComponentChildren } from 'preact'

/**
 * A page's title and tools. On narrow screens the bottom bar has room for the five main pages only,
 * so Settings sits at the end of each page's header there instead of in the sidebar.
 */
export function PageHead({ title, children }: { title: string; children?: ComponentChildren }) {
  return (
    <header class="head">
      <h1>{title}</h1>
      <div class="head-tools">
        {children}
        <button class="ghost narrow-only" onClick={() => { location.hash = '/settings' }}>ตั้งค่า</button>
      </div>
    </header>
  )
}
