import { sweep } from './assistantCore'
import { today } from './core'
import type { Vault } from './vault'

// Choices made on the Settings page that stay on this device (Android keeps the same in its synced settings file,
// which the web does not read until the owner decides how settings sync).

const read = (key: string): string | null => {
  try { return localStorage.getItem(key) } catch { return null }
}
const write = (key: string, value: string | null) => {
  try { if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value) } catch { /* just not kept */ }
}

/** How many days finished tasks wait before moving to the archive note; 0 turns it off. Android's default is 7. */
export const ARCHIVE_DAYS = [0, 1, 3, 7, 14, 30]
export const DEFAULT_ARCHIVE_DAYS = 7
const DAYS_KEY = 'omni.archiveDays'
const SWEPT_KEY = 'omni.archiveSweptOn'

export const archiveDays = {
  get(): number {
    const n = Number(read(DAYS_KEY))
    return read(DAYS_KEY) !== null && ARCHIVE_DAYS.includes(n) ? n : DEFAULT_ARCHIVE_DAYS
  },
  /** A new choice sweeps again at the next load, as on Android. */
  set(days: number) {
    write(DAYS_KEY, String(days))
    write(SWEPT_KEY, null)
  },
}

/**
 * Once a day, moves finished tasks closed at least [archiveDays] days ago from the TaskForge note to the archive note,
 * with their whole blocks. Project work stays. Returns the titles moved.
 */
export async function sweepIfDue(vault: Vault): Promise<string[]> {
  const days = archiveDays.get()
  const day = today()
  if (days <= 0 || read(SWEPT_KEY) === day) return []
  const moved = await vault.sweep((live, archive) => sweep(live, archive, days))
  write(SWEPT_KEY, day)
  return moved
}
