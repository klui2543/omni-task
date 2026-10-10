import type { PageProps } from '../Home'
import { PageHead } from '../ui/PageHead'

/** Placeholder until the Views page (Kanban, Matrix, Gantt, month, week) is built. */
export function ViewsPage(p: PageProps) {
  return (
    <main class="page">
      <PageHead title="มุมมอง" />
      <section class="group empty">
        <p>หน้านี้กำลังทำบนเว็บ ระหว่างนี้ใช้ในแอป Android ได้</p>
        <button class="ghost" onClick={() => p.onNavigate('tasks')}>ไปหน้างาน</button>
      </section>
    </main>
  )
}
