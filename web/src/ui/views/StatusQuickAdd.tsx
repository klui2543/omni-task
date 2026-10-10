import { useEffect, useRef, useState } from 'preact/hooks'
import type { PageProps } from '../../Home'
import type { Task } from '../../types'

/**
 * Quick add from the "+" on a folded Kanban column: the sentence is read as on the Tasks page (dates, times, tags
 * and priority from the words) and the new task starts in that column's status, as on Android.
 */
export function StatusQuickAdd(p: PageProps & { status: Task['status']; statusLabel: string; onClose: () => void }) {
  const [sentence, setSentence] = useState('')
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])

  const submit = (e: Event) => {
    e.preventDefault()
    const s = sentence
    if (!s.trim()) return
    p.run(async () => {
      const res = await p.vault.addInStatus(s, p.status)
      if (res.ok) p.onClose()
      return res
    })
  }

  return (
    <div class="scrim sheet-scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <form class="dialog quick" role="dialog" aria-modal="true" aria-label="เพิ่มงาน" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <h2>เพิ่มงาน</h2>
        <span class="muted small">เริ่มที่สถานะ {p.statusLabel}</span>
        <input
          ref={input}
          class="quick-input"
          aria-label="งานใหม่"
          placeholder="เช่น ส่งรายงาน พรุ่งนี้ 9:00 #รอ/พี่เอ"
          value={sentence}
          onInput={(e) => setSentence(e.currentTarget.value)}
        />
        <div class="dialog-actions">
          <button type="button" class="ghost" onClick={p.onClose}>ยกเลิก</button>
          <button class="primary" disabled={p.busy || !sentence.trim()}>เพิ่ม</button>
        </div>
      </form>
    </div>
  )
}
