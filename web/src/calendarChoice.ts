const read = (key: string): string | null => {
  try { return localStorage.getItem(key) } catch { return null }
}
const write = (key: string, value: string | null) => {
  try { if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value) } catch { /* just not kept */ }
}

const CAL_KEY = 'omni.calendars.hidden'

/**
 * Google calendars the owner switched off in Omni, by id (a calendar added later shows by default). Android keeps the
 * same choice for the phone's calendars. It stays on this device.
 */
export const hiddenCalendars = {
  get(): string[] {
    try {
      const v = JSON.parse(read(CAL_KEY) ?? '[]')
      return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []
    } catch {
      return []
    }
  },
  set(ids: string[]) {
    write(CAL_KEY, ids.length ? JSON.stringify([...new Set(ids)].sort()) : null)
  },
}
