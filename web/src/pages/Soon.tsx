import { PageHead } from '../ui/PageHead'

/** A page not built on the web yet. */
export function SoonPage({ title, onTasks }: { title: string; onTasks: () => void }) {
  return (
    <main class="page">
      <PageHead title={title} />
      <section class="group empty">
        <p>หน้านี้กำลังทำบนเว็บ ระหว่างนี้ใช้ในแอป Android ได้</p>
        <button class="ghost" onClick={onTasks}>ไปหน้างาน</button>
      </section>
    </main>
  )
}
