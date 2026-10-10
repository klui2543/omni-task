import type { Bucket, Query, Task } from './types'

/** The list's filters, grouping and sorting as the Tasks page keeps them; the search box is added on top. */
export type ListQuery = Required<Omit<Query, 'text' | 'branches'>>

export const DEFAULT_QUERY: ListQuery = {
  statuses: ['TODO', 'IN_PROGRESS'],
  priorities: [],
  tags: [],
  buckets: [],
  kinds: [],
  groupBy: 'DATE',
  sorts: [{ by: 'DUE', ascending: true }],
}

// The words of Android's filter sheet and sort dialog, in its order.
export const KINDS: [string, string][] = [['NORMAL', 'ปกติ'], ['WAITING', 'มีคนรอ'], ['FUTURE', 'ลงทุนอนาคต'], ['SOMEDAY', 'พักไว้ก่อน']]
export const BUCKETS: [Bucket, string][] = [
  ['TODAY', 'วันนี้'], ['OVERDUE', 'เลยกำหนด'], ['THIS_WEEK', 'สัปดาห์นี้'], ['NEXT_WEEK', 'สัปดาห์หน้า'], ['FUTURE', 'อนาคต'], ['NO_DATE', 'ไม่มีวันที่'],
]
export const PRIORITIES: [Task['priority'], string][] = [
  ['HIGHEST', 'สูงสุด'], ['HIGH', 'สูง'], ['MEDIUM', 'กลาง'], ['NONE', 'ปกติ'], ['LOW', 'ต่ำ'], ['LOWEST', 'ต่ำสุด'],
]
export const STATUSES: [Task['status'], string][] = [['TODO', 'ยังไม่เริ่ม'], ['IN_PROGRESS', 'กำลังทำ'], ['DONE', 'เสร็จ'], ['CANCELLED', 'ยกเลิก']]
export const GROUPS: [ListQuery['groupBy'], string][] = [
  ['DATE', 'วันที่'], ['NOTE', 'โน้ต'], ['PRIORITY', 'ความสำคัญ'], ['TAG', 'Tag'], ['STATUS', 'สถานะ'], ['NONE', 'ไม่จัดกลุ่ม'],
]
export const SORTS: [string, string][] = [
  ['DUE', 'ครบกำหนด'], ['SCHEDULED', 'วันนัดทำ'], ['START', 'วันเริ่ม'], ['PRIORITY', 'ความสำคัญ'], ['CREATED', 'วันที่สร้าง'],
  ['STATUS', 'สถานะ'], ['PROJECT', 'โปรเจกต์'], ['NOTE', 'โน้ต'], ['TITLE', 'ชื่องาน'],
]

export const labelOf = (pairs: [string, string][], key: string) => pairs.find(([k]) => k === key)?.[1] ?? key

/** The colour dot of each priority, as on the tick rings. */
export const PRIORITY_DOT: Record<Task['priority'], string> = {
  HIGHEST: 'var(--red)', HIGH: 'var(--amber)', MEDIUM: 'var(--blue)', NONE: 'var(--faint)', LOW: 'var(--faint)', LOWEST: 'var(--faint)',
}

/** How many filters are on, for the "กรอง 2" on the button. Statuses count only when changed from the default. */
export const filterCount = (q: ListQuery) =>
  q.kinds.length + q.buckets.length + q.priorities.length + q.tags.length +
  (sameSet(q.statuses, DEFAULT_QUERY.statuses) ? 0 : 1)

const sameSet = (a: string[], b: string[]) => a.length === b.length && a.every((x) => b.includes(x))

const KEY = 'omni.query'
const SAVED_KEY = 'omni.filters'

/** The query is remembered on this device, so a reload keeps the same list. */
export const storedQuery = {
  get(): ListQuery {
    try {
      const raw = localStorage.getItem(KEY)
      return raw ? { ...DEFAULT_QUERY, ...JSON.parse(raw) } : DEFAULT_QUERY
    } catch {
      return DEFAULT_QUERY
    }
  },
  set(q: ListQuery) {
    try { localStorage.setItem(KEY, JSON.stringify(q)) } catch { /* just not kept */ }
  },
}

export type SavedFilter = { name: string; query: ListQuery }

/** Filters saved by name, as Android's saved filters (kept on this device). */
export const savedFilters = {
  get(): SavedFilter[] {
    try { return JSON.parse(localStorage.getItem(SAVED_KEY) ?? '[]') } catch { return [] }
  },
  set(list: SavedFilter[]) {
    try { localStorage.setItem(SAVED_KEY, JSON.stringify(list)) } catch { /* just not kept */ }
  },
}

export const toggleIn = <T,>(list: T[], v: T) => (list.includes(v) ? list.filter((x) => x !== v) : [...list, v])
