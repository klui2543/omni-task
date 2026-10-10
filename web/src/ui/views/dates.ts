import type { FocusIn } from '../../types'

// Dates on the Views page are plain YYYY-MM-DD strings in the device's own time zone, so they sort as text and
// never depend on the zone the browser runs in. Names are written out here, since the browser's Thai calendar
// would count the year in the Buddhist era.

const pad = (n: number) => String(n).padStart(2, '0')
export const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
export const parseDay = (s: string) => new Date(+s.slice(0, 4), +s.slice(5, 7) - 1, +s.slice(8, 10))
export const addDays = (s: string, n: number) => {
  const d = parseDay(s)
  d.setDate(d.getDate() + n)
  return iso(d)
}
export const daysBetween = (a: string, b: string) => Math.round((parseDay(b).getTime() - parseDay(a).getTime()) / 86_400_000)
/** 0 for Monday to 6 for Sunday: weeks run Monday to Sunday. */
export const weekdayIndex = (s: string) => (parseDay(s).getDay() + 6) % 7
export const mondayOf = (s: string) => addDays(s, -weekdayIndex(s))
export const firstOfMonth = (s: string) => s.slice(0, 8) + '01'
export const addMonths = (s: string, n: number) => {
  const d = parseDay(firstOfMonth(s))
  d.setMonth(d.getMonth() + n)
  return iso(d)
}

export const WEEKDAYS = ['จ', 'อ', 'พ', 'พฤ', 'ศ', 'ส', 'อา']
export const MONTHS_SHORT = ['ม.ค.', 'ก.พ.', 'มี.ค.', 'เม.ย.', 'พ.ค.', 'มิ.ย.', 'ก.ค.', 'ส.ค.', 'ก.ย.', 'ต.ค.', 'พ.ย.', 'ธ.ค.']
export const MONTHS_LONG = ['มกราคม', 'กุมภาพันธ์', 'มีนาคม', 'เมษายน', 'พฤษภาคม', 'มิถุนายน', 'กรกฎาคม', 'สิงหาคม', 'กันยายน', 'ตุลาคม', 'พฤศจิกายน', 'ธันวาคม']
const DAYS_LONG = ['อาทิตย์', 'จันทร์', 'อังคาร', 'พุธ', 'พฤหัสบดี', 'ศุกร์', 'เสาร์']

/** "8 ต.ค." */
export const shortDate = (s: string) => `${+s.slice(8, 10)} ${MONTHS_SHORT[+s.slice(5, 7) - 1]}`
/** "พฤหัสบดี 8 ตุลาคม" */
export const longDay = (s: string) => `${DAYS_LONG[parseDay(s).getDay()]} ${+s.slice(8, 10)} ${MONTHS_LONG[+s.slice(5, 7) - 1]}`
/** "พฤหัสบดี 8 ต.ค." */
export const mediumDay = (s: string) => `${DAYS_LONG[parseDay(s).getDay()]} ${shortDate(s)}`
/** "ตุลาคม 2026" */
export const monthTitle = (s: string) => `${MONTHS_LONG[+s.slice(5, 7) - 1]} ${s.slice(0, 4)}`

/** "8 ต.ค.", "8 ถึง 10 ต.ค." or "29 ก.ย. ถึง 5 ต.ค."; the year only when it is not this year. */
export function rangeText(a: string, b: string, today: string) {
  const year = a.slice(0, 4) !== today.slice(0, 4) || b.slice(0, 4) !== today.slice(0, 4) ? ` ${b.slice(0, 4)}` : ''
  if (a === b) return shortDate(a) + year
  const from = a.slice(0, 7) === b.slice(0, 7) ? String(+a.slice(8, 10)) : shortDate(a)
  return `${from} ถึง ${shortDate(b)}${year}`
}

export type Ev = FocusIn['events'][number]
export const evBeginDay = (e: Ev) => e.begin.slice(0, 10)
/** The last day an event touches; one ending at midnight belongs to the day before. */
export const evLastDay = (e: Ev) => {
  const a = evBeginDay(e)
  const b = e.end.slice(11, 16) === '00:00' ? addDays(e.end.slice(0, 10), -1) : e.end.slice(0, 10)
  return b < a ? a : b
}
/** Whether the event covers any part of the day; an event with no length counts on the day it starts. */
export const evCovers = (e: Ev, d: string) => {
  const from = d + 'T00:00'
  const to = addDays(d, 1) + 'T00:00'
  return e.begin < to && (e.end > from || (e.end <= e.begin && e.begin >= from))
}
/** All-day events, and timed ones a day or longer, go in the strip instead of filling whole columns. */
export const evInStrip = (e: Ev) => e.allDay || new Date(e.end).getTime() - new Date(e.begin).getTime() >= 86_400_000
export const evClock = (e: Ev) => `${e.begin.slice(11, 16)} ถึง ${e.end.slice(11, 16)}`
/** "ทั้งวัน" or "10:00 ถึง 11:00". */
export const evTimes = (e: Ev) => (e.allDay ? 'ทั้งวัน' : evClock(e))
