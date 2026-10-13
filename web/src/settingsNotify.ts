// Android's notification settings (its `notify.*` keys), as far as the web uses them: whether task reminders ring
// (shared both ways through the settings file, see settingsSync.ts) and what the morning summary holds and when it
// comes (set in the Android app; the web only reads them, for the assistant's summary card).
import { settingsBus } from './settingsBus'

export interface NotifyChoice {
  /** Task reminders on (Android's `notify.task`); undefined until chosen here or read from Android. */
  task?: boolean
  /** The summary times, `HH:mm` (Android's `notify.digestTimes`). */
  digestTimes: string[]
  overdue: boolean
  dueToday: boolean
  waiting: boolean
  weekly: boolean
}

const KEY = 'omni.notify'
const DEFAULT: NotifyChoice = { digestTimes: ['06:30'], overdue: true, dueToday: true, waiting: true, weekly: true }

export const notifyChoice = {
  get(): NotifyChoice {
    try {
      return { ...DEFAULT, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }
    } catch {
      return DEFAULT
    }
  },
  /** Keeps [c]; [shared] when the owner changed a choice that travels to Android. */
  set(c: NotifyChoice, shared = false) {
    try { localStorage.setItem(KEY, JSON.stringify(c)) } catch { /* just not kept */ }
    if (shared) settingsBus.changed()
  },
  /** Whether task reminders ring; on unless turned off here or on Android, as Android's default. */
  remindersOn: () => notifyChoice.get().task !== false,
}
