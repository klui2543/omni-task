import type { ListOut, ProjectOut } from '../../types'
import { useSortable } from './Sortable'

/** The ring colours of the project cards, in Android's order. */
const RING = ['var(--accent)', 'var(--amber)', '#2f9e95', 'var(--blue)', 'var(--red)']

function Card(p: {
  project: ProjectOut
  index: number
  arranging: boolean
  sort?: ReturnType<typeof useSortable>
  onOpen: () => void
  onStar: () => void
}) {
  const pr = p.project
  const color = RING[p.index % RING.length]
  return (
    <div class="pj-card" {...(p.sort ? p.sort.row(pr.name) : {})}>
      {p.arranging && p.sort && (
        <button class="pj-handle" aria-label={`ลากเพื่อย้าย ${pr.name}`} {...p.sort.handle(pr.name)}>⋮⋮</button>
      )}
      <button class="pj-card-body" disabled={p.arranging} onClick={p.onOpen}>
        <span class="pj-ring" role="img" aria-label={`เสร็จ ${pr.pct}%`} style={{ background: `conic-gradient(${color} 0 ${pr.pct}%, var(--border) ${pr.pct}% 100%)` }}>
          <span class="pj-ring-in">{pr.pct}%</span>
        </span>
        <span class="pj-card-text">
          <span class="pj-name">{pr.name}</span>
          <span class="small muted">เสร็จ {pr.done} จาก {pr.total} งาน</span>
          <span class="pills">
            {pr.next && <span class="pill">ถัดไป: {pr.next.title}</span>}
            {pr.overdue > 0 && <span class="pill red">เลยกำหนด {pr.overdue}</span>}
            {pr.blocked.length > 0 && <span class="pill amber">ติดรองานอื่น {pr.blocked.length}</span>}
          </span>
        </span>
      </button>
      <button class={`pj-star${pr.starred ? ' on' : ''}`} aria-label={pr.starred ? `เอาดาวออก ${pr.name}` : `ติดดาว ${pr.name}`} aria-pressed={pr.starred} onClick={p.onStar}>★</button>
    </div>
  )
}

/** The main screen: project cards (starred first, then the owner's order) and the list notes below them. */
export function ProjectHome(p: {
  projects: ProjectOut[]
  lists: ListOut[]
  arranging: boolean
  onArrange: (on: boolean) => void
  onOrder: (names: string[]) => void
  onStar: (name: string) => void
  onOpen: (name: string) => void
  onOpenList: (path: string) => void
  onCreateList: () => void
}) {
  const sort = useSortable(p.projects.map((x) => x.name), p.onOrder)
  const byName = new Map(p.projects.map((x, i) => [x.name, { project: x, index: i }]))
  const shown = p.arranging ? sort.order.map((n) => byName.get(n)).filter((x): x is NonNullable<typeof x> => !!x) : p.projects.map((x, i) => ({ project: x, index: i }))
  return (
    <>
      <header class="head">
        <div class="grow">
          <h1>โปรเจกต์/ลิสต์</h1>
          <span class="small muted">ติดดาวให้ขึ้นบนสุด กดจัดลำดับเพื่อลากขึ้นลง</span>
        </div>
        {p.projects.length > 1 && (
          <button class={p.arranging ? 'primary' : 'ghost'} aria-pressed={p.arranging} onClick={() => p.onArrange(!p.arranging)}>
            {p.arranging ? 'เสร็จ' : 'จัดลำดับ'}
          </button>
        )}
        <button class="ghost narrow-only" onClick={() => { location.hash = '/settings' }}>ตั้งค่า</button>
      </header>

      {p.projects.length === 0 && <section class="group empty">ยังไม่มีงานที่ติด Tag</section>}
      <div class={`pj-grid${p.arranging ? ' arranging' : ''}`} ref={sort.ref as preact.Ref<HTMLDivElement>}>
        {shown.map(({ project, index }) => (
          <Card key={project.name} project={project} index={index} arranging={p.arranging} sort={p.arranging ? sort : undefined}
            onOpen={() => p.onOpen(project.name)} onStar={() => p.onStar(project.name)} />
        ))}
      </div>

      {!p.arranging && (
        <>
          <h2 class="pj-h2">รายการ</h2>
          <div class="pj-grid pj-lists">
            {p.lists.map((l) => (
              <button key={l.path} class="pj-list-card" onClick={() => p.onOpenList(l.path)}>
                <span class="pj-tile">{l.emoji}</span>
                <span class="grow stack">
                  <span class="strong">{l.name}</span>
                  <span class="small muted">ทำแล้ว {l.done} จาก {l.total}</span>
                </span>
                <span class="faint">›</span>
              </button>
            ))}
            <button class="pj-new" onClick={p.onCreateList}>+ สร้างรายการใหม่</button>
          </div>
        </>
      )}
    </>
  )
}
