import { useEffect, useMemo, useState } from 'preact/hooks'
import { setIgnoredTags } from '../core'
import type { PageProps } from '../Home'
import type { BranchOp, BranchOut, BranchState, ListNote, ProjectEditResult, ProjectOut, Task } from '../types'
import { EditPanel } from '../ui/EditPanel'
import { CreateListDialog, IconPickerDialog, IncludeDialog, NameDialog, RenameProjectDialog } from '../ui/projects/ProjectDialogs'
import { ListDetail } from '../ui/projects/ListDetail'
import { MindMap } from '../ui/projects/MindMap'
import { ProjectHome } from '../ui/projects/ProjectHome'
import { ProjectOverview } from '../ui/projects/ProjectOverview'
import { ViewsPage, type ViewMode } from './Views'
import { listsSeeded, renamedProject, toggleIn, useProjectsLocal } from '../ui/projects/projectState'
import { QuickAdd } from './Tasks'

type Dialog =
  | { k: 'rename' }
  | { k: 'create' }
  | { k: 'include' }
  | { k: 'icon' }
  | { k: 'name'; title: string; done: (name: string) => void }

/**
 * Projects and lists, as Android's tab of the same name: project cards, a project's overview with its mind map
 * and ordered tasks, and the list notes (Bucket list, Watch list...). Text is changed by the shared code; the
 * choices of order, stars, branch states and task order stay on this device.
 */
export function ProjectsPage(p: PageProps) {
  const [local, setLocal] = useProjectsLocal()
  const [notes, setNotes] = useState<ListNote[] | null>(null)
  const [openName, setOpenName] = useState<string | null>(null)
  const [openPath, setOpenPath] = useState<string | null>(null)
  const [mapping, setMapping] = useState(false)
  // Inside a project: its overview (null) or one of its own views.
  const [projectView, setProjectView] = useState<ViewMode | null>(null)
  const [arranging, setArranging] = useState(false)
  const [category, setCategory] = useState<string | null>(null)
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [selected, setSelected] = useState<{ key: string; title: string } | null>(null)
  const [notice, setNotice] = useState('')
  const [adding, setAdding] = useState(false)
  const [mapSelect, setMapSelect] = useState<string | undefined>()

  // The list notes are read again whenever the task note is (every action reloads it).
  useEffect(() => {
    if (!p.snapshot) return
    let live = true
    p.vault.lists().then(
      (n) => live && setNotes(n),
      () => live && setNotes((prev) => prev ?? []),
    )
    return () => { live = false }
  }, [p.snapshot, p.vault])
  const [listsRead, setListsRead] = useState(false)
  useEffect(() => { if (notes) setListsRead(true) }, [notes])

  // The first time the lists are opened on this device with none in the vault, the two starter lists are written, as on Android.
  useEffect(() => {
    if (listsRead && notes && notes.length === 0 && !listsSeeded.get()) {
      listsSeeded.set()
      p.run(() => p.vault.seedLists())
    }
  }, [listsRead])

  useEffect(() => {
    if (!notice) return
    const t = setTimeout(() => setNotice(''), 6_000)
    return () => clearTimeout(t)
  }, [notice])

  const out = useMemo(() => (p.snapshot && notes ? p.snapshot.projects(notes, local) : null), [p.snapshot, notes, local])
  useEffect(() => { if (out) setIgnoredTags(out.ignored) }, [out])

  const noteTasks = useMemo(() => new Map((out?.noteTasks ?? []).map((t) => [t.key, t])), [out])
  const task = (key: string): Task | undefined => p.snapshot?.byKey.get(key) ?? noteTasks.get(key)
  const project = out?.projects.find((x) => x.name === openName)
  const list = out?.lists.find((x) => x.path === openPath)
  // A project or list that is gone (renamed away, note deleted) sends the page back to the main screen.
  useEffect(() => {
    // (Not while an action is still running: a rename shows the new name a moment before the note is read again.)
    if (out && !p.busy && ((openName && !project) || (openPath && !list))) { setOpenName(null); setOpenPath(null); setMapping(false); setCategory(null) }
  }, [out, p.busy])

  const everything = [...(p.snapshot?.tasks ?? []), ...noteTasks.values()]
  const current: Task | null = selected
    ? (() => {
        const found = task(selected.key)
        return found?.title === selected.title ? found : everything.find((t) => t.title === selected.title) ?? null
      })()
    : null
  const openTask = (t: Task) => setSelected({ key: t.key, title: t.title })
  const say = (text: string) => setNotice(text)

  const home = () => { setProjectView(null); setOpenName(null); setOpenPath(null); setMapping(false); setCategory(null); setSelected(null) }
  const back = () => (mapping ? setMapping(false) : home())
  const noteOf = (l: { path: string }) => notes?.find((n) => n.path === l.path)

  // ---- Branch states: decided by the shared code, kept on this device ----
  const branch = (op: BranchOp) => {
    const r = p.snapshot!.branchChange(local.branches, op)
    if (r.ok) setLocal((s) => ({ ...s, branches: r.states }))
    return r
  }
  const setState = (b: BranchOut, s: BranchState) => {
    if (!project) return
    branch({ op: s === 'CHOSEN' ? 'choose' : 'set', project: project.name, path: b.path, value: s })
  }
  const addBranch = (parent: BranchOut) =>
    setDialog({ k: 'name', title: `กิ่งใหม่ใต้ ${parent.name}`, done: (name) => { branch({ op: 'add', project: project!.name, path: parent.path, value: name }); setDialog(null) } })
  const addBranchTask = (b: BranchOut) =>
    setDialog({
      k: 'name', title: `งานใหม่ใน ${b.name}`,
      done: (title) => { setDialog(null); p.run(() => p.vault.addTagged(b.tag, title)) },
    })
  const renameBranch = (b: BranchOut) =>
    setDialog({
      k: 'name', title: `ชื่อใหม่ของ ${b.name}`,
      done: (name) => {
        setDialog(null)
        const r = p.snapshot!.branchChange(local.branches, { op: 'rename', project: project!.name, path: b.path, value: name })
        if (!r.ok || !r.oldTag || !r.newTag) return
        // The tags in the notes change first; the states move along once that is written, as on Android.
        p.run(async () => {
          const res = await p.vault.renameTag(r.oldTag!, r.newTag!)
          if (res.ok) {
            setLocal((s) => ({ ...s, branches: r.states }))
            setMapSelect(r.path ?? undefined)
          }
          return res
        })
      },
    })
  const deleteBranch = (b: BranchOut) => {
    const r = branch({ op: 'delete', project: project!.name, path: b.path })
    if (r.error === 'hasTasks') say('ยังมีงานในกิ่งนี้ ย้ายหรือลบงานก่อน')
  }

  // ---- Projects ----
  const renameProject = (name: string) => {
    const old = project!.name
    setDialog(null)
    p.run(async () => {
      const res = await p.vault.renameTag(old, name)
      if (res.ok) {
        const moved = p.snapshot!.branchChange(local.branches, { op: 'moveProject', project: old, value: name })
        setLocal((s) => ({ ...renamedProject(s, old, name), branches: moved.states }))
        setOpenName(name)
        say(`เปลี่ยนชื่อเป็น #${name} แล้ว (${res.changed ?? 0} บรรทัด)`)
      }
      return res
    })
  }
  const orderOf = (pr: ProjectOut) => pr.order.map((o) => task(o.key)).filter((t): t is Task => !!t)
  const setStrict = (pr: ProjectOut, on: boolean) => {
    setLocal((s) => ({ ...s, strict: on ? [...s.strict.filter((n) => n !== pr.name), pr.name] : s.strict.filter((n) => n !== pr.name) }))
    const ordered = orderOf(pr).map(p.fresh)
    p.run(() => p.vault.chain(ordered, on))
  }
  const reorder = (pr: ProjectOut, ordered: Task[]) => {
    setLocal((s) => ({ ...s, taskOrder: { ...s.taskOrder, [pr.name]: ordered.map((t) => t.title) } }))
    if (pr.strict) p.run(() => p.vault.chain(ordered.map(p.fresh), true))
  }

  // ---- Lists ----
  /** Runs a change to a list note; the expected ways it can fail are said here, the rest by the page's own banner. */
  const listAction = (action: () => Promise<ProjectEditResult>) =>
    p.run(async () => {
      const res = await action()
      if (!res.ok && res.error === 'exists') { say('มีรายการชื่อนี้แล้ว'); return undefined }
      return res
    })
  const createList = (name: string, icon: string, categories: string[]) => {
    setDialog(null)
    listAction(() => p.vault.createList(name, icon, categories))
  }
  const addItem = (title: string, cat: string | null) => {
    const note = list && noteOf(list)
    if (note) listAction(() => p.vault.addListItem(note, title, cat))
  }
  const setIcon = (emoji: string) => {
    const note = list && noteOf(list)
    setDialog(null)
    if (note && list) listAction(() => p.vault.updateList(note, emoji, list.categories))
  }
  const include = (picked: Task[], cat: string | null) => {
    if (!list) return
    setDialog(null)
    p.run(async () => {
      const res = await p.vault.includeInList(picked.map(p.fresh), list.tag, cat)
      if (res.ok) {
        const n = res.changed ?? 0
        say(n === picked.length ? `เพิ่ม ${n} งานเข้า ${list.name} แล้ว` : `เพิ่มได้ ${n} จาก ${picked.length} งาน ไฟล์อาจถูกแก้จากที่อื่น`)
      }
      return res
    })
  }
  const pool = list && p.snapshot
    ? (() => {
        const have = new Set(list.items.map((i) => i.key))
        const parked = new Set(out!.parked)
        return p.snapshot.tasks.filter((t) => t.open && !have.has(t.key) && !parked.has(t.key))
      })()
    : []

  const pane = current && (
    <>
      <div class="pane-scrim" onClick={() => setSelected(null)} />
      <aside class="pane" role="dialog" aria-label="แก้ไขงาน">
        <EditPanel {...p} task={current} onOpen={openTask} onClose={() => setSelected(null)} />
      </aside>
    </>
  )

  const screen = !out ? (
    <p class="muted pad">กำลังโหลด...</p>
  ) : project && mapping ? (
    <MindMap
      project={project} task={task} busy={p.busy} select={mapSelect}
      onBack={back} onTick={p.tick} onOpen={openTask}
      onState={setState} onAddBranch={addBranch} onAddTask={addBranchTask} onRename={renameBranch} onDelete={deleteBranch}
    />
  ) : project ? (
    <>
      <header class="head">
        <button class="ghost" onClick={back}>‹ กลับ</button>
        <div class="grow">
          <h1>{project.name}</h1>
          <span class="small muted">ติ๊กงานต้นทางเพื่อปลดล็อกงานที่รออยู่</span>
        </div>
        <button class="ghost" onClick={() => setDialog({ k: 'rename' })}>เปลี่ยนชื่อโปรเจกต์</button>
      </header>
      <ProjectOverview
        project={project} task={task} selected={current?.key} busy={p.busy}
        onTick={p.tick} onOpen={openTask} onMap={() => { setMapSelect(''); setMapping(true) }}
        onStrict={(on) => setStrict(project, on)} onOrder={(ordered) => reorder(project, ordered)} onView={setProjectView}
      />
    </>
  ) : list ? (
    <>
      <header class="head">
        <button class="ghost" onClick={back}>‹ กลับ</button>
        <div class="grow">
          <h1>{list.name}</h1>
          <span class="small muted">แตะเพื่อติ๊กว่าทำหรือดูแล้ว</span>
        </div>
      </header>
      <ListDetail
        list={list} task={task} selected={current?.key} busy={p.busy} category={category} onCategory={setCategory}
        onTick={p.tick} onOpen={openTask} onAdd={addItem} onInclude={() => setDialog({ k: 'include' })} onIcon={() => setDialog({ k: 'icon' })}
      />
    </>
  ) : (
    <ProjectHome
      projects={out.projects} lists={out.lists} arranging={arranging} onArrange={setArranging}
      onOrder={(names) => setLocal((s) => ({ ...s, order: names }))}
      onStar={(n) => setLocal((s) => ({ ...s, starred: toggleIn(s.starred, n) }))}
      onOpen={(n) => { setOpenName(n); setMapping(false) }} onOpenList={(path) => { setOpenPath(path); setCategory(null) }}
      onCreateList={() => setDialog({ k: 'create' })}
    />
  )

  // A view needs the whole page (Kanban and Gantt scroll sideways), so it replaces the overview.
  if (project && projectView) {
    return (
      <ViewsPage
        {...p}
        scope={{ project: project.name, mode: projectView, setMode: setProjectView, onOverview: () => setProjectView(null) }}
      />
    )
  }

  return (
    <div class={`split${current ? ' with-pane' : ''}`}>
      <main class={`page pj${mapping ? ' mapping' : ''}`}>
        {notice && (
          <section class="notice" role="status">
            <span class="grow">{notice}</span>
            <button class="link quiet" onClick={() => setNotice('')}>ปิด</button>
          </section>
        )}
        {screen}

        {!mapping && <button class="fab narrow-only" aria-label="เพิ่มงาน" onClick={() => setAdding(true)}>+</button>}
        {adding && <QuickAdd {...p} onClose={() => setAdding(false)} />}

        {dialog?.k === 'rename' && project && <RenameProjectDialog project={project} busy={p.busy} onRename={renameProject} onClose={() => setDialog(null)} />}
        {dialog?.k === 'create' && <CreateListDialog busy={p.busy} onCreate={createList} onClose={() => setDialog(null)} />}
        {dialog?.k === 'icon' && list && <IconPickerDialog selected={list.emoji} onPick={setIcon} onClose={() => setDialog(null)} />}
        {dialog?.k === 'include' && list && (
          <IncludeDialog list={list} pool={pool} startCategory={category} busy={p.busy} onAdd={include} onClose={() => setDialog(null)} />
        )}
        {dialog?.k === 'name' && <NameDialog title={dialog.title} onDone={dialog.done} onClose={() => setDialog(null)} />}
      </main>
      {pane}
    </div>
  )
}
