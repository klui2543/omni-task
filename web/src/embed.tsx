// The app inside a tab of Obsidian (the Omni Ygdrasil plugin draws it with this file; the web page starts from
// main.tsx). The plugin hands over where to draw and how to reach the vault, and calls host.embed() before this file
// is loaded, so the look is put on the tab and not on Obsidian's own page. The app's styles come in through the
// plugin's build, scoped to the tab (see obsidian/scripts/scopeCss.mjs).
import { render } from 'preact'
import { css } from './embedCss'
import { host } from './host'
import { Main } from './Home'
import { apply } from './settingsAppearance'
import { Reminders } from './ui/Reminders'
import { connectInObsidian } from './ui/GoogleConnect'
import { VaultDrive, type VaultFs } from './vaultDrive'
import './style.css'

/**
 * The tab's own box: the app's sheets, bars and toasts are placed in it (its fixed layer), and its width decides
 * between the side menu and the bottom bar, as the screen's width does on the web.
 */
const FRAME_CSS = `
:host { display: block; height: 100%; }
.omni-frame { position: relative; height: 100%; overflow: hidden; container: omni / size; transform: translateZ(0); }
.omni-root { height: 100%; overflow-y: auto; overscroll-behavior: contain; }
`

const NOOP = () => {}

export interface Mounted {
  drive: VaultDrive
  unmount(): void
}

/**
 * Draws the app in [frame] (inside the plugin's shadow root) over the vault [fs]. [rootId] tells this vault apart
 * from the owner's other vaults in what the app keeps on the device.
 */
export function mountOmni(opts: { scope: ShadowRoot; frame: HTMLElement; root: HTMLElement; fs: VaultFs; rootId: string }): Mounted {
  const style = document.createElement('style')
  style.textContent = FRAME_CSS + css.join('\n')
  opts.scope.prepend(style)
  apply()
  const drive = new VaultDrive(opts.fs, opts.rootId, host.calendar)
  // "อนุญาต" for Google Calendar, and for adding events (both are asked at once inside Obsidian).
  const connect = () => {
    const link = host.calendar
    if (link) connectInObsidian(link)
  }
  render(
    <>
      <Main
        drive={drive}
        vaultId={drive.rootId}
        onSignIn={NOOP}
        onConnectCalendar={connect}
        onAllowCalendarWrite={connect}
        onSignOut={NOOP}
        onChangeVault={NOOP}
      />
      <Reminders />
    </>,
    opts.root,
  )
  return {
    drive,
    unmount() {
      render(null, opts.root)
      style.remove()
      host.embed(null)
    },
  }
}
