import type { PageProps } from '../Home'
import { PageHead } from '../ui/PageHead'

/** Placeholder until the Projects and Lists page is built. */
export function ProjectsPage(p: PageProps) {
  return (
    <main class="page">
      <PageHead title="โปรเจกต์/ลิสต์" />
      <section class="group empty">
        <p>หน้านี้กำลังทำบนเว็บ ระหว่างนี้ใช้ในแอป Android ได้</p>
        <button class="ghost" onClick={() => p.onNavigate('tasks')}>ไปหน้างาน</button>
      </section>
    </main>
  )
}
