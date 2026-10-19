// Omni Task in a tab of Obsidian: the whole web app (web/src), drawn in a shadow root so its styles and Obsidian's
// never touch, reading and writing the vault directly (vaultFs.ts). Obsidian Sync then takes every change to the
// other devices; when it brings one in, or a note is typed in the editor, the app reads the notes again.
import { ItemView, TFile, type TAbstractFile, type WorkspaceLeaf } from 'obsidian'
import { host, memoryAddress } from '../../web/src/host'
import type { Mounted } from '../../web/src/embed'
import type { Google } from './google'
import { vaultFs } from './vaultFs'

export const OMNI_VIEW = 'omni-task'

const FONT_ID = 'omni-task-font'

/** The app's default font (Sarabun), from Google Fonts as on the web; offline, the system's Thai font serves. */
function loadFont() {
  if (document.getElementById(FONT_ID)) return
  const link = document.createElement('link')
  link.id = FONT_ID
  link.rel = 'stylesheet'
  link.href = 'https://fonts.googleapis.com/css2?family=Sarabun:wght@400;500;600&display=swap'
  document.head.appendChild(link)
}

/** What a note change means to the app: notes, and the settings file Android and the web share. */
const matters = (path: string) => /\.md$/i.test(path) || path.endsWith('/omni-settings.json')

export class OmniView extends ItemView {
  /** The tab the app is drawn in: one at a time, since the app keeps one page per window. */
  private static live: OmniView | null = null
  private mounted: Mounted | null = null
  private listeners = new Set<() => void>()
  private timer: number | null = null

  constructor(leaf: WorkspaceLeaf, private google: Google) {
    super(leaf)
  }

  getViewType() { return OMNI_VIEW }
  getDisplayText() { return 'Omni Task' }
  getIcon() { return 'list-checks' }

  async onOpen() {
    const el = this.contentEl
    el.empty()
    el.addClass('omni-task-view')
    const other = OmniView.live
    if (other && other !== this) {
      // A second tab (a split, say) points to the one that has the app.
      const note = el.createDiv({ cls: 'omni-task-elsewhere' })
      note.createDiv({ text: 'Omni Task เปิดอยู่ในอีกแท็บแล้ว' })
      note.createEl('button', { text: 'ไปที่แท็บนั้น', cls: 'mod-cta' }).addEventListener('click', () => {
        this.app.workspace.revealLeaf(other.leaf)
        this.leaf.detach()
      })
      return
    }
    OmniView.live = this
    // Obsidian on a phone takes a swipe as opening its side panes and a pull down as its command palette. A swipe,
    // pull or drag that starts in the app (scrolling, the mind map, a card dragged) stays the app's.
    for (const type of ['touchstart', 'touchmove', 'touchend', 'touchcancel'] as const) {
      el.addEventListener(type, (e) => e.stopPropagation(), { passive: true })
    }
    const shadow = el.createDiv({ cls: 'omni-task-host' }).attachShadow({ mode: 'open' })
    const frame = document.createElement('div')
    frame.className = 'omni-frame'
    const root = document.createElement('div')
    root.className = 'omni-root'
    root.lang = 'th'
    frame.append(root)
    shadow.append(frame)
    loadFont()

    const vault = this.app.vault
    // Obsidian's id for this vault, so what the app keeps on the device (the same storage for every vault on a
    // computer) stays apart per vault.
    const rootId = `obsidian:${(this.app as unknown as { appId?: string }).appId ?? vault.getName()}`
    // The look is put on this tab: the app is told where it is before its first file is loaded.
    host.embed({
      root, frame, scope: shadow, address: memoryAddress(), vaultName: vault.getName(),
      dark: () => document.body.classList.contains('theme-dark'),
      // The encoder is loaded the first time a picture is attached, not with Obsidian.
      webp: async (image, quality) => (await import('./webp')).webp(image, quality),
      google: this.google,
      watch: (fn) => {
        this.listeners.add(fn)
        return () => { this.listeners.delete(fn) }
      },
    })
    const [{ mountOmni }, { apply }] = await Promise.all([import('../../web/src/embed'), import('../../web/src/settingsAppearance')])
    // Closed while the app was loading.
    if (OmniView.live !== this) return
    this.mounted = mountOmni({ scope: shadow, frame, root, fs: vaultFs(this.app), rootId })

    // A note changed from outside the app: read again, once things settle.
    const changed = (f: TAbstractFile, old?: string) => {
      if (!this.mounted || !(f instanceof TFile) || !(matters(f.path) || (old && matters(old)))) return
      if (this.mounted.drive.isOwnWrite(f.path)) return
      if (this.timer) window.clearTimeout(this.timer)
      this.timer = window.setTimeout(() => { this.timer = null; this.listeners.forEach((fn) => fn()) }, 600)
    }
    this.registerEvent(vault.on('modify', (f) => changed(f)))
    this.registerEvent(vault.on('create', (f) => changed(f)))
    this.registerEvent(vault.on('delete', (f) => changed(f)))
    this.registerEvent(vault.on('rename', (f, old) => changed(f, old)))
    // Obsidian's theme changed: "follow the system" follows it.
    this.registerEvent(this.app.workspace.on('css-change', () => apply()))

    // A linked note opens here, in a new tab, instead of going out to the system and back (obsidian://open).
    root.addEventListener('click', (e) => {
      const a = (e.composedPath()[0] as Element | undefined)?.closest?.('a[href^="obsidian://open"]') as HTMLAnchorElement | null
      if (!a) return
      const file = new URL(a.href).searchParams.get('file')
      if (!file) return
      e.preventDefault()
      void this.app.workspace.openLinkText(file, '', 'tab')
    }, true)
  }

  async onClose() {
    if (OmniView.live === this) OmniView.live = null
    if (this.timer) window.clearTimeout(this.timer)
    this.listeners.clear()
    this.mounted?.unmount()
    this.mounted = null
  }
}
