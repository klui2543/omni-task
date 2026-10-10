// Choices the owner makes once and every page reads, kept on this device (Android keeps the same choices in its
// settings file). Pages read them through these helpers so a change shows up everywhere.

export type UrgentRule = 'TWO_DAYS' | 'THREE_DAYS' | 'THIS_WEEK'

/** How soon a task must be due to count as urgent in the Matrix, as Android's "ด่วน = ..." menu. */
export const URGENT_RULES: [UrgentRule, string][] = [['TWO_DAYS', 'ภายใน 2 วัน'], ['THREE_DAYS', 'ภายใน 3 วัน'], ['THIS_WEEK', 'ภายในสัปดาห์นี้']]

const URGENT_KEY = 'omni.urgent'

export const urgentRule = {
  get(): UrgentRule {
    try {
      const v = localStorage.getItem(URGENT_KEY)
      return URGENT_RULES.some(([k]) => k === v) ? (v as UrgentRule) : 'THIS_WEEK'
    } catch {
      return 'THIS_WEEK'
    }
  },
  set(rule: UrgentRule) {
    try { localStorage.setItem(URGENT_KEY, rule) } catch { /* just not kept */ }
  },
}
