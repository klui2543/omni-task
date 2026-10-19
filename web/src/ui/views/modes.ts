export const MODES = ['kanban', 'matrix', 'gantt', 'calendar'] as const
export type ViewMode = (typeof MODES)[number]
export const MODE_LABEL: Record<ViewMode, string> = { kanban: 'Kanban', matrix: 'Matrix', gantt: 'Timeline', calendar: 'ปฏิทิน' }
