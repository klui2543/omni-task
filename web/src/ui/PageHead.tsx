import type { ComponentChildren } from 'preact'

/** A page's title and tools. On narrow screens Settings is on the More page (the bar's last button), as on Android. */
export function PageHead({ title, children }: { title: string; children?: ComponentChildren }) {
  return (
    <header class="head">
      <h1>{title}</h1>
      <div class="head-tools">
        {children}
      </div>
    </header>
  )
}
