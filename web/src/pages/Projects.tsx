import { useEffect, useMemo, useState } from 'preact/hooks'
import { keptListTags, setIgnoredTags } from '../core'
import type { PageProps } from '../Home'
import type { BranchOp, BranchOut, BranchState, ListNote, ProjectEditResult, ProjectOut, Task } from '../types'
import { EditPanel } from '../ui/EditPanel'
import { CreateListDialog, IconPickerDialog, IncludeDialog, NameDialog, RenameProjectDialog } from '../ui/projects/ProjectDialogs'
import { ListDetail } from '../ui/projects/ListDetail'
import { MindMap } from '../ui/projects/MindMap'
import { PLAIN_FILTER, Ygdrasil } from '../ui/projects/Ygdrasil'
import { ProjectHome } from '../ui/projects/ProjectHome'
import { ProjectOverview } from '../ui/projects/ProjectOverview'
import { MODES, MODE_LABEL, ViewsPage, type ViewMode } from './Views'
import { listsSeeded, renamedProject, toggleIn, useProjectsLocal, useYgdrasilLocal, withProject } from '../ui/projects/projectState'
import { QuickAdd } from './Tasks'

type Dialog =
  | { k: 'rename' }
  | { k: 'create' }
  | { k: 'include' }
  | { k: 'icon' }
  | { k: 'deleteList' }
  | { k: 'name'; title: string; done: (name: string) => void }

/**
 * Projects and lists, as Android's tab of the same name: project cards, a project's overview with its mind map
 * and ordered tasks, and the list notes (Bucket list, Watch list...). Text is changed by the shared code; the
 * choices of order, stars, branch states and task order stay on this device.
 */
export function ProjectsPage(p: PageProps) {
  const [local, setLocal] = useProjectsLocal()
  const [notes, setNotes] = useState<ListNote[] | null>(() => p.vault.peekLists() ?? null)
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
  // Project Ygdrasil: every project in one tree.
  const [world, setWorld] = useState(false)
  // A project opened from Ygdrasil: its back button goes back to the tree, not to the project cards.
  const [fromWorld, setFromWorld] = useState(false)
  const [yg, setYg] = useYgdrasilLocal(PLAIN_FILTER)

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

  // The page shows before the list notes are read (the first visit), with the list tags kept from the last read.
  const shownState = useMemo(() => (notes ? local : { ...local, listTags: keptListTags() }), [notes, local])
  const out = useMemo(() => (p.snapshot ? p.snapshot.projects(notes ?? [], shownState) : null), [p.snapshot, notes, shownState])
  const tree = useMemo(
    () => (world && p.snapshot ? p.snapshot.ygdrasil(notes ?? [], shownState, yg.filter, yg.folded) : null),
    [world, p.snapshot, notes, shownState, yg],
  )
  useEffect(() => { if (out && notes) setIgnoredTags(out.ignored) }, [out])
  // Never chosen: the projects every first tag named so far are kept as the choice (once the list notes are read,
  // so their tags are not taken for projects), and from then on a tag is a project only when made one here.
  useEffect(() => {
    if (out?.seed && notes && !local.projectTags) setLocal((s) => (s.projectTags ? s : { ...s, projectTags: out.seed }))
  }, [out])
  const setProject = (tag: string, on: boolean) => setLocal((s) => withProject(s, tag, on, out?.seed ?? []))

  const noteTasks = useMemo(() => new Map((out?.noteTasks ?? []).map((t) => [t.key, t])), [out])
  const task = (key: string): Task | undefined => p.snapshot?.byKey.get(key) ?? noteTasks.get(key)
  const project = out?.projects.find((x) => x.name === openName)
  const list = out?.lists.find((x) => x.path === openPath)
  // A project or list that is gone (renamed away, note deleted) sends the page back to the main screen.
  useEffect(() => {
    // (Not while an action is still running: a rename shows the new name a moment before the note is read again.)
    if (out && !p.busy && ((openName && !project) || (openPath && notes && !list))) { setOpenName(null); setOpenPath(null); setMapping(false); setCategory(null) }
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

  const home = () => { setProjectView(null); setOpenName(null); setOpenPath(null); setMapping(false); setCategory(null); setSelected(null); setWorld(false); setFromWorld(false) }
  const back = () => {
    if (fromWorld) { setFromWorld(false); home(); setWorld(true); return }
    if (mapping) setMapping(false)
    else home()
  }

  // ---- Ygdrasil: adding from the whole tree, and the way into one project's mind map ----
  const ygAddTask = (tag: string, name: string) =>
    setDialog({ k: 'name', title: `งานใหม่ใน ${name}`, done: (title) => { setDialog(null); p.run(() => p.vault.addTagged(tag, title)) } })
  const ygAddBranch = (projectName: string, path: string, name: string) =>
    setDialog({
      k: 'name', title: `กิ่งใหม่ใต้ ${name}`,
      done: (branchName) => {
        setDialog(null)
        const r = p.snapshot!.branchChange(local.branches, { op: 'add', project: projectName, path, value: branchName })
        if (r.ok) setLocal((s) => ({ ...s, branches: r.states }))
      },
    })
  const ygMove = (t: Task, from: string, tag: string) =>
    p.run(async () => {
      const res = await p.vault.change(p.fresh(t), { op: 'moveTo', value: tag, field: from })
      if (res.ok) say(`ย้าย "${t.title}" ไป #${tag} แล้ว`)
      return res
    })
  // A project or branch dropped on another (or on the trunk): its tag moves in every note, its branch states go along.
  const ygGraft = (from: string, to: string) => {
    const [project, ...rest] = from.split('/')
    const r = p.snapshot!.branchChange(local.branches, { op: 'graft', project, path: rest.join('/'), value: to })
    if (!r.ok || !r.oldTag || !r.newTag) return
    p.run(async () => {
      const res = await p.vault.renameTag(r.oldTag!, r.newTag!)
      if (res.ok) {
        // A branch dropped on the trunk becomes a project of its own.
        setLocal((s) => ({ ...(s.projectTags ? withProject(s, r.newTag!.split('/')[0], true, []) : s), branches: r.states }))
        say(to === '' ? `#${r.newTag} เป็นโปรเจกต์ของตัวเองแล้ว` : `ย้าย #${r.oldTag} ไปไว้ใต้ #${to} แล้ว (${res.changed ?? 0} บรรทัด)`)
      }
      return res
    })
  }
  const ygOpenProject = (name: string) => { setWorld(false); setFromWorld(true); setOpenName(name); setMapSelect(''); setMapping(true) }
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
  const deleteList = () => {
    const note = list && noteOf(list)
    setDialog(null)
    if (!note || !list) return
    const name = list.name
    home()
    p.run(async () => {
      const res = await p.vault.deleteList(note, list.tag)
      if (res.ok) say(`ลบ ${name} แล้ว`)
      return res
    })
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
        <EditPanel {...p} task={current} onOpen={openTask} onClose={() => setSelected(null)} noteName={list?.path.split('/').pop()} />
      </aside>
    </>
  )

  const screen = !out ? (
    <p class="muted pad">กำลังโหลด...</p>
  ) : world && tree ? (
    <Ygdrasil
      out={tree} task={task} busy={p.busy} filter={yg.filter} folded={yg.folded}
      onFilter={(filter) => setYg({ filter })} onFolded={(folded) => setYg({ folded })}
      onBack={home} onTick={p.tick} onOpen={openTask}
      onAddTask={ygAddTask} onAddBranch={ygAddBranch} onOpenProject={ygOpenProject} onMove={ygMove} onGraft={ygGraft}
    />
  ) : project && mapping ? (
    <MindMap
      project={project} task={task} busy={p.busy} select={mapSelect}
      onBack={back} onTick={p.tick} onOpen={openTask}
      onState={setState} onAddBranch={addBranch} onAddTask={addBranchTask} onRename={renameBranch} onDelete={deleteBranch}
    />
  ) : project ? (
    <>
      {/* The same header as the project's views (Kanban, Matrix...), so the switch between them stays in one place. */}
      <header class="head vhead">
        <button class="ghost" onClick={back}>‹ กลับ</button>
        <h1>{project.name}</h1>
        <div class="seg big" role="tablist" aria-label="มุมมองของโปรเจกต์">
          <button role="tab" aria-selected>ภาพรวม</button>
          {MODES.map((m) => <button key={m} role="tab" aria-selected={false} onClick={() => setProjectView(m)}>{MODE_LABEL[m]}</button>)}
        </div>
        <span class="grow" />
        <div class="head-tools">
          <button class="chip tool" onClick={() => setDialog({ k: 'rename' })}>เปลี่ยนชื่อโปรเจกต์</button>
          <button
            class="chip tool" title={`#${project.name} กลับเป็นแท็กธรรมดา งานยังอยู่ครบ`}
            onClick={() => { const name = project.name; setProject(name, false); home(); say(`#${name} เป็นแท็กธรรมดาแล้ว`) }}
          >เลิกเป็นโปรเจกต์</button>
        </div>
      </header>
      <ProjectOverview
        project={project} task={task} selected={current?.key} busy={p.busy}
        onTick={p.tick} onOpen={openTask} onMap={() => { setMapSelect(''); setMapping(true) }}
        onStrict={(on) => setStrict(project, on)} onOrder={(ordered) => reorder(project, ordered)}
      />
    </>
  ) : list ? (
    <>
      <header class="head">
        <button class="ghost" onClick={back}>‹ กลับ</button>
        <div class="grow">
          <h1>{list.name}</h1>
          <span class="small muted">แตะวงกลมเพื่อติ๊กว่าทำหรือดูแล้ว</span>
        </div>
        <button class="ghost small-btn danger" onClick={() => setDialog({ k: 'deleteList' })}>ลบรายการ</button>
      </header>
      <ListDetail
        list={list} task={task} selected={current?.key} busy={p.busy} category={category} onCategory={setCategory}
        onTick={p.tick} onOpen={openTask} onAdd={addItem} onInclude={() => setDialog({ k: 'include' })} onIcon={() => setDialog({ k: 'icon' })}
      />
    </>
  ) : (
    <ProjectHome
      projects={out.projects} lists={out.lists} others={out.others} arranging={arranging} onArrange={setArranging}
      onMakeProject={(tag) => setProject(tag, true)}
      onOrder={(names) => setLocal((s) => ({ ...s, order: names }))}
      onStar={(n) => setLocal((s) => ({ ...s, starred: toggleIn(s.starred, n) }))}
      onOpen={(n) => { setOpenName(n); setMapping(false) }} onOpenList={(path) => { setOpenPath(path); setCategory(null) }}
      onCreateList={() => setDialog({ k: 'create' })} onYgdrasil={() => setWorld(true)}
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
      <main class={`page pj${mapping || world ? ' mapping' : ''}`}>
        {notice && (
          <section class="notice" role="status">
            <span class="grow">{notice}</span>
            <button class="link quiet" onClick={() => setNotice('')}>ปิด</button>
          </section>
        )}
        {screen}

        {adding && <QuickAdd {...p} onClose={() => setAdding(false)} />}

        {dialog?.k === 'rename' && project && <RenameProjectDialog project={project} busy={p.busy} onRename={renameProject} onClose={() => setDialog(null)} />}
        {dialog?.k === 'create' && <CreateListDialog busy={p.busy} onCreate={createList} onClose={() => setDialog(null)} />}
        {dialog?.k === 'deleteList' && list && (() => {
          const own = list.items.filter((i) => i.key.startsWith(list.key + '#')).length
          const pulled = list.items.length - own
          return (
            <div class="scrim" onClick={() => setDialog(null)}>
              <div class="dialog" role="alertdialog" aria-modal="true" aria-label={`ลบ ${list.name}?`} onClick={(e) => e.stopPropagation()}>
                <h2>ลบ {list.name}?</h2>
                <p class="muted">ลบไฟล์ {list.path.split('/').pop()} พร้อม {own} รายการในนั้น (ไปอยู่ในถังขยะของ Google Drive)</p>
                {pulled > 0 && <p class="muted">งานที่ดึงมาจากโน้ตอื่น {pulled} งานจะอยู่ที่เดิม แค่เอา #{list.tag} ออก</p>}
                <div class="dialog-actions">
                  <button class="ghost" onClick={() => setDialog(null)}>ยกเลิก</button>
                  <button class="primary danger-fill" disabled={p.busy} onClick={deleteList}>ลบ</button>
                </div>
              </div>
            </div>
          )
        })()}
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
