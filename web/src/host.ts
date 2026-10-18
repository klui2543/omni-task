// Where the app runs: its own page in a browser (signed in to Google, the vault read through Drive), or a tab inside
// Obsidian (the Omni Ygdrasil plugin), where the vault is read directly and there is no Google sign-in. The page asks
// this file for what differs, so the rest of the app reads the same in both.

/**
 * The page's address (#/tasks, #/projects/...). In a browser it is the real one, so reload and back keep their
 * place; inside Obsidian it is kept in memory, since the address of Obsidian's own window is not the app's.
 */
export interface Address {
  /** The address now, from the '#'. */
  hash(): string
  /** A new step, as `location.hash = to`: the listeners hear it. */
  assign(to: string): void
  /** A new step the listeners do not hear, as history.pushState. */
  push(to: string, state: unknown): void
  /** This step changed, as history.replaceState. */
  replace(to: string, state: unknown): void
  /** One step back, as history.back(): the listeners hear it. */
  back(): void
  /** The state saved with this step. */
  state(): unknown
  /** Calls [fn] on every step the listeners hear; returns the way to stop. */
  listen(fn: () => void): () => void
}

const browserAddress: Address = {
  hash: () => location.hash,
  assign: (to) => { location.hash = to },
  push: (to, state) => history.pushState(state, '', to),
  replace: (to, state) => history.replaceState(state, '', to),
  back: () => history.back(),
  state: () => history.state,
  listen(fn) {
    addEventListener('hashchange', fn)
    addEventListener('popstate', fn)
    return () => { removeEventListener('hashchange', fn); removeEventListener('popstate', fn) }
  },
}

/** An address kept in memory, with its own history of steps. */
export function memoryAddress(start = '#/focus'): Address {
  const steps: { to: string; state: unknown }[] = [{ to: start, state: null }]
  const listeners = new Set<() => void>()
  const tell = () => listeners.forEach((fn) => fn())
  const norm = (to: string) => (to.startsWith('#') ? to : '#' + to)
  return {
    hash: () => steps[steps.length - 1].to,
    assign(to) {
      if (norm(to) === this.hash()) return
      steps.push({ to: norm(to), state: null })
      tell()
    },
    push: (to, state) => { steps.push({ to: norm(to), state }) },
    replace: (to, state) => { steps[steps.length - 1] = { to: norm(to), state } },
    back() {
      if (steps.length > 1) steps.pop()
      tell()
    },
    state: () => steps[steps.length - 1].state,
    listen(fn) {
      listeners.add(fn)
      return () => { listeners.delete(fn) }
    },
  }
}

/** What Obsidian hands the app when it opens it in a tab (see obsidian/src/omniView.ts). */
export interface Embedding {
  /** The element that stands for the page: the theme's attributes and the font go on it, the scrolling happens in it. */
  root: HTMLElement
  /** The tab's box around [root], which holds the app's sheets, bars and toasts (its fixed layer). */
  frame: HTMLElement
  /** Where the app's elements are looked up by point or selector (the shadow root that keeps Obsidian's styles out). */
  scope: ShadowRoot
  address: Address
  /** The vault's name, for the settings page. */
  vaultName: string
  /** Whether Obsidian shows its dark theme: the app's "follow the system" follows Obsidian. */
  dark: () => boolean
  /** WebP from pixels, where the WebView cannot write it itself (Obsidian on the iPad). */
  webp?: (image: ImageData, quality: number) => Promise<ArrayBuffer>
  /** Calls [fn] when a note changed in the vault from outside the app (Obsidian Sync, the editor); returns the way to stop. */
  watch?: (fn: () => void) => () => void
}

let embedding: Embedding | null = null

export const host = {
  /** Whether the app runs in a tab inside Obsidian. */
  get obsidian(): boolean {
    return embedding !== null
  },
  /** Google sign-in, Drive and Calendar: only on the web page. */
  get google(): boolean {
    return embedding === null
  },
  get embedding(): Embedding | null {
    return embedding
  },
  /** Called by the plugin before the app is drawn. */
  embed(e: Embedding | null) {
    embedding = e
  },
  get address(): Address {
    return embedding?.address ?? browserAddress
  },
  /** The page's root element, where the look is set (see settingsAppearance.ts). */
  root(): HTMLElement {
    return embedding?.root ?? document.documentElement
  },
  /** The element that holds the whole app, for a class over all of it (dragging). */
  body(): HTMLElement {
    return embedding?.root ?? document.body
  },
  /** The app's element under a point of the screen. */
  at(x: number, y: number): Element | null {
    return (embedding?.scope ?? document).elementFromPoint(x, y)
  },
  /** The first of the app's elements matching [selector]. */
  find(selector: string): Element | null {
    return (embedding?.scope ?? document).querySelector(selector)
  },
  /**
   * Where the app's fixed layer starts on the screen. Inside Obsidian the tab holds the app's sheets and bars, so
   * something placed under the pointer is moved by the tab's own place.
   */
  origin(): { left: number; top: number } {
    if (!embedding) return { left: 0, top: 0 }
    const r = embedding.frame.getBoundingClientRect()
    return { left: r.left, top: r.top }
  },
  /** What a key or tap really landed on (inside Obsidian the event is seen from outside the app's shadow root). */
  target(e: Event): EventTarget | null {
    return e.composedPath?.()[0] ?? e.target
  },
  /** Where the notes live, for messages to the owner. */
  get store(): string {
    return embedding ? 'vault' : 'Google Drive'
  },
}
