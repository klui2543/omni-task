// Omni Ygdrasil for Obsidian: every Omni project drawn as one tree on an Excalidraw page, kept in step with the task
// note. The tree is the shared code's (the same as the Android and web apps'); this plugin only draws it, reads what
// the owner does on the drawing, and writes the task note through the shared code. It also opens the whole Omni Task
// app in a tab (omniView.ts), reading and writing the vault directly.
import { App, ItemView, Modal, Notice, Plugin, PluginSettingTab, Setting, TFile, WorkspaceLeaf, normalizePath } from 'obsidian'
import * as core from './core'
import { limbAt, shapes, type Role, type Shape } from './draw'
import { OMNI_VIEW, OmniView } from './omniView'
import { PLAIN_FILTER, type Edited, type ProjectsIn, type Task, type YgFilter, type YgOut } from './types'

const VIEW = 'omni-ygdrasil'
const MARK = 'ygdrasil'

interface Settings {
  drawingPath: string
  autoRedraw: boolean
  autoLink: boolean
  /** Whether the Omni Task tab opens by itself when Obsidian starts. */
  openOmniOnStart: boolean
  filter: YgFilter
  folded: string[]
}

const DEFAULTS: Settings = {
  drawingPath: `${core.omniDirPath}/Ygdrasil.excalidraw.md`,
  autoRedraw: true,
  autoLink: true,
  openOmniOnStart: false,
  filter: PLAIN_FILTER,
  folded: [],
}

/** What the tree was drawn from, kept until the next drawing. */
interface Drawn {
  out: YgOut
  tasks: Map<string, Task>
  origin: { x: number; y: number }
  /** Ids of the drawn task rings, where each was put. */
  rings: Map<string, { key: string; tag: string; x: number; y: number }>
  /** Ids of the drawn waits. */
  links: Map<string, { blocker: string; waiting: string }>
}

// The Excalidraw plugin's scripting API (ExcalidrawAutomate), used loosely: only what is called here.
/* eslint-disable @typescript-eslint/no-explicit-any */
type EA = any
type El = any

export default class OmniYgdrasil extends Plugin {
  settings: Settings = DEFAULTS
  drawn: Drawn | null = null
  busy = false
  private redrawTimer: number | null = null
  /** Elements already turned down (a wait that would loop), so they are not tried again. */
  private refused = new Set<string>()
  private lastSelection = ''

  async onload() {
    this.settings = { ...DEFAULTS, ...(await this.loadData()) }
    this.settings.filter = { ...PLAIN_FILTER, ...this.settings.filter }
    this.registerView(VIEW, (leaf) => new YgView(leaf, this))
    this.addRibbonIcon('git-fork', 'Omni Ygdrasil', () => this.open())
    // The Omni Task app in a tab of its own.
    this.registerView(OMNI_VIEW, (leaf) => new OmniView(leaf))
    this.addRibbonIcon('list-checks', 'Omni Task', () => this.openOmni())
    this.addCommand({ id: 'open-omni', name: 'เปิด Omni Task', callback: () => this.openOmni() })
    this.app.workspace.onLayoutReady(() => { if (this.settings.openOmniOnStart) void this.openOmni() })
    this.addSettingTab(new YgSettings(this.app, this))

    this.addCommand({ id: 'open', name: 'เปิดหน้า Ygdrasil', callback: () => this.open() })
    this.addCommand({ id: 'redraw', name: 'วาดต้นไม้ใหม่', callback: () => this.redraw() })
    this.addCommand({ id: 'tick', name: 'ติ๊กงานที่เลือก', callback: () => this.tickSelected() })
    this.addCommand({ id: 'add-task', name: 'เพิ่มงานในกิ่งที่เลือก', callback: () => this.addToSelected() })
    this.addCommand({ id: 'text-to-task', name: 'แปลงข้อความที่เลือกเป็นงาน', callback: () => this.textToTask() })
    this.addCommand({ id: 'move', name: 'ย้ายงานที่เลือกไปกิ่งอื่น', callback: () => this.moveSelected() })
    this.addCommand({ id: 'fold', name: 'พับหรือกางกิ่งที่เลือก', callback: () => this.foldSelected() })

    // The note changed (ticked on the phone and brought by Obsidian Sync, or edited here): the tree is drawn again.
    this.registerEvent(this.app.vault.on('modify', (f) => {
      if (this.settings.autoRedraw && f instanceof TFile && this.watches(f.path)) this.scheduleRedraw()
    }))
    // What the owner does on the drawing: arrows between tasks, tasks dropped on another branch, waits deleted.
    this.registerInterval(window.setInterval(() => this.watchDrawing(), 800))
  }

  onunload() {
    if (this.redrawTimer) window.clearTimeout(this.redrawTimer)
  }

  async save() {
    await this.saveData(this.settings)
  }

  /** Shows the Omni Task tab, opening it in a new tab of the main area when there is none (one is enough). */
  async openOmni() {
    const ws = this.app.workspace
    const leaf = ws.getLeavesOfType(OMNI_VIEW)[0] ?? ws.getLeaf('tab')
    await leaf.setViewState({ type: OMNI_VIEW, active: true })
    ws.revealLeaf(leaf)
  }

  // ---- The vault ----

  private watches(path: string) {
    return path === core.taskFilePath || path === core.legacyTaskFilePath || path.endsWith('/omni-settings.json')
  }

  noteFile(): TFile | null {
    for (const p of [core.taskFilePath, core.legacyTaskFilePath]) {
      const f = this.app.vault.getAbstractFileByPath(normalizePath(p))
      if (f instanceof TFile) return f
    }
    return null
  }

  /** The choices Android and the web keep in omni-settings.json: which tags are projects, order, stars, branch states, own kinds. */
  private async projectsState(): Promise<ProjectsIn> {
    const state: ProjectsIn = { order: [], starred: [], branches: [], kindTags: [] }
    for (const dir of [core.omniDirPath, core.legacyOmniDirPath]) {
      const f = this.app.vault.getAbstractFileByPath(normalizePath(`${dir}/omni-settings.json`))
      if (!(f instanceof TFile)) continue
      try {
        const s = JSON.parse(await this.app.vault.read(f)).settings ?? {}
        state.order = (s.projectOrder?.string ?? '').split('\n').filter((x: string) => x)
        state.starred = s.starredProjects?.set ?? []
        state.branches = s.branchStates?.set ?? []
        if (Array.isArray(s.projectTags?.set)) state.projectTags = s.projectTags.set
        state.kindTags = (s.customKinds?.set ?? []).map((e: string) => e.split('\t')).filter((p: string[]) => p.length === 3 && p[2].trim()).map((p: string[]) => p[2])
      } catch { /* a settings file being written; the defaults do */ }
      break
    }
    return state
  }

  /** The list notes (Bucket list...), whose tags are not projects: every note in the Omni folder, as the apps read them. */
  private async listNotes() {
    const out: { key: string; path: string; text: string }[] = []
    for (const dir of [core.omniDirPath, core.legacyOmniDirPath]) {
      for (const f of this.app.vault.getMarkdownFiles()) {
        if (f.parent?.path === normalizePath(dir) && !f.path.endsWith('.excalidraw.md') && !this.watches(f.path)) {
          out.push({ key: f.path, path: f.path, text: await this.app.vault.cachedRead(f) })
        }
      }
    }
    return out
  }

  /** Changes the task note through the shared code; says why when it could not. */
  async edit(change: (text: string) => Edited): Promise<boolean> {
    const file = this.noteFile()
    if (!file) { new Notice('ไม่พบโน้ตงาน Omni note.md'); return false }
    let error: string | undefined
    await this.app.vault.process(file, (text) => {
      const r = change(text)
      if (!r.ok || r.text === undefined) { error = r.error ?? 'error'; return text }
      return r.text
    })
    if (error) {
      new Notice(
        error === 'cycle' ? 'โยงไม่ได้: งานจะรอกันเป็นวง'
        : error === 'self' ? 'โยงงานเข้ากับตัวเองไม่ได้'
        : error === 'conflict' ? 'บรรทัดงานถูกแก้จากที่อื่น ลองอีกครั้ง'
        : 'บันทึกไม่ได้',
      )
      return false
    }
    return true
  }

  // ---- Excalidraw ----

  ea(): EA | null {
    const api = (window as any).ExcalidrawAutomate
    if (!api) { new Notice('ต้องติดตั้งและเปิดปลั๊กอิน Excalidraw ก่อน'); return null }
    return api.getAPI ? api.getAPI() : api
  }

  /** The open drawing of the tree, if it is open. */
  drawingView(): any {
    let found: any = null
    this.app.workspace.iterateAllLeaves((leaf) => {
      const v: any = leaf.view
      if (!found && v?.getViewType?.() === 'excalidraw' && v.file?.path === normalizePath(this.settings.drawingPath)) found = v
    })
    return found
  }

  /** Opens the side panel and the drawing (made the first time), then draws the tree. */
  async open() {
    await this.showPanel()
    const ea = this.ea()
    if (!ea) return
    let file = this.app.vault.getAbstractFileByPath(normalizePath(this.settings.drawingPath))
    if (!(file instanceof TFile)) {
      const path = normalizePath(this.settings.drawingPath)
      const folder = path.split('/').slice(0, -1).join('/')
      const name = path.split('/').pop()!.replace(/\.md$/, '')
      const made: string = await ea.create({ filename: name, foldername: folder, onNewPane: false, silent: true })
      this.settings.drawingPath = made
      await this.save()
      file = this.app.vault.getAbstractFileByPath(made)
    }
    if (file instanceof TFile) await this.app.workspace.getLeaf(false).openFile(file)
    window.setTimeout(() => this.redraw(), 600)
  }

  async showPanel() {
    if (this.app.workspace.getLeavesOfType(VIEW).length) return
    const leaf = this.app.workspace.getRightLeaf(false)
    await leaf?.setViewState({ type: VIEW, active: true })
  }

  scheduleRedraw() {
    if (this.redrawTimer) window.clearTimeout(this.redrawTimer)
    this.redrawTimer = window.setTimeout(() => { this.redrawTimer = null; this.redraw() }, 700)
  }

  /** Draws the tree again where it stood: Omni's own boxes and lines are replaced, everything the owner drew stays. */
  async redraw() {
    const view = this.drawingView()
    const file = this.noteFile()
    if (!view || !file || this.busy) { this.refreshPanel(); return }
    const ea = this.ea()
    if (!ea) return
    this.busy = true
    try {
      const text = await this.app.vault.read(file)
      const [state, notes] = await Promise.all([this.projectsState(), this.listNotes()])
      const out = core.ygdrasil(file.path, text, notes, state, this.settings.filter, this.settings.folded)
      const tasks = new Map(core.loadTasks(file.path, text).map((t) => [t.key, t]))

      ea.setView(view)
      ea.clear()
      const old: El[] = ea.getViewElements().filter((e: El) => e.customData?.omni === MARK)
      // The tree stays where the owner moved it: the trunk's box says where it was.
      const trunkSpot = out.spots.find((s) => s.kind === 'TRUNK')!
      const oldTrunk = old.find((e) => e.customData?.kind === 'trunk' && e.type === 'rectangle')
      const prev = this.drawn?.out.spots.find((s) => s.kind === 'TRUNK')
      const origin = oldTrunk && prev
        ? { x: oldTrunk.x - prev.x, y: oldTrunk.y - (prev.y - prev.h / 2) }
        : oldTrunk ? { x: oldTrunk.x - trunkSpot.x, y: oldTrunk.y - (trunkSpot.y - trunkSpot.h / 2) } : { x: 0, y: 0 }
      if (old.length) ea.deleteViewElements(old)

      const drawn: Drawn = { out, tasks, origin, rings: new Map(), links: new Map() }
      paint(ea, shapes(out, (k) => tasks.get(k), origin), drawn)
      await ea.addElementsToView(false, true, false)
      this.drawn = drawn
    } catch (e) {
      console.error('Omni Ygdrasil', e)
      new Notice('วาดต้นไม้ไม่ได้: ' + (e as Error).message)
    } finally {
      this.busy = false
      this.refreshPanel()
    }
  }

  private refreshPanel() {
    for (const leaf of this.app.workspace.getLeavesOfType(VIEW)) (leaf.view as YgView).render()
  }

  // ---- Reading the drawing ----

  /** The tree's own elements that are selected on the drawing, by what they are. */
  selected(): Role[] {
    const view = this.drawingView()
    if (!view) return []
    const ea = this.ea()
    if (!ea) return []
    ea.setView(view)
    const seen = new Set<string>()
    const roles: Role[] = []
    for (const e of ea.getViewSelectedElements() as El[]) {
      const c = e.customData
      if (c?.omni !== MARK) continue
      const id = JSON.stringify(c.role)
      if (!seen.has(id)) { seen.add(id); roles.push(c.role as Role) }
    }
    return roles
  }

  /** Every little while: arrows drawn between two tasks become waits, tasks dropped on a branch move, deleted waits go. */
  private async watchDrawing() {
    const drawn = this.drawn
    const view = this.drawingView()
    if (!drawn || !view || this.busy) return
    const ea = (window as any).ExcalidrawAutomate?.getAPI?.()
    if (!ea) return
    ea.setView(view)
    const elements: El[] = ea.getViewElements()
    const byId = new Map(elements.map((e) => [e.id, e]))
    const taskOf = (id: string | undefined) => {
      const c = id ? byId.get(id)?.customData : undefined
      return c?.omni === MARK && c.role?.kind === 'task' ? drawn.tasks.get(c.role.key) : undefined
    }

    // 1. An arrow the owner drew from one task to another: the second waits for the first.
    if (this.settings.autoLink) {
      for (const e of elements) {
        if (e.type !== 'arrow' || e.customData?.omni === MARK || e.isDeleted || this.refused.has(e.id)) continue
        const blocker = taskOf(e.startBinding?.elementId)
        const waiting = taskOf(e.endBinding?.elementId)
        if (!blocker || !waiting) continue
        this.busy = true
        const ok = await this.edit((text) => core.link(text, blocker, waiting))
        this.busy = false
        if (ok) {
          ea.deleteViewElements([e])
          new Notice(`"${waiting.title}" รอ "${blocker.title}" แล้ว`)
          return this.redraw()
        }
        this.refused.add(e.id)
      }
    }

    // 2. A wait Omni drew that the owner deleted: the wait is taken out of the note.
    for (const [id, l] of drawn.links) {
      if (byId.get(id) && !byId.get(id).isDeleted) continue
      const blocker = drawn.tasks.get(l.blocker)
      const waiting = drawn.tasks.get(l.waiting)
      drawn.links.delete(id)
      if (!blocker || !waiting) continue
      this.busy = true
      const ok = await this.edit((text) => core.unlink(text, blocker, waiting))
      this.busy = false
      if (ok) { new Notice(`"${waiting.title}" ไม่ต้องรอ "${blocker.title}" แล้ว`); return this.redraw() }
    }

    // 3. A task dragged onto another branch's box moves there.
    for (const [id, r] of drawn.rings) {
      const e = byId.get(id)
      if (!e || e.isDeleted) continue
      if (Math.hypot(e.x - r.x, e.y - r.y) < 24) continue
      const tag = limbAt(drawn.out, e.x + e.width / 2, e.y + e.height / 2, drawn.origin)
      const task = drawn.tasks.get(r.key)
      r.x = e.x
      r.y = e.y
      if (!tag || tag === r.tag || !task) continue
      this.busy = true
      const ok = await this.edit((text) => core.moveTo(text, task, r.tag, tag))
      this.busy = false
      if (ok) { new Notice(`ย้าย "${task.title}" ไป #${tag} แล้ว`); return this.redraw() }
    }
    // The panel follows the selection, drawn again only when it changed (so typing in the search box is not lost).
    const sig = JSON.stringify(this.selected())
    if (sig !== this.lastSelection) { this.lastSelection = sig; this.refreshPanel() }
  }

  // ---- Actions on what is selected ----

  async tickSelected() {
    const keys = this.selected().filter((r): r is Extract<Role, { kind: 'task' }> => r.kind === 'task').map((r) => r.key)
    if (!keys.length) { new Notice('เลือกงานบนภาพก่อน'); return }
    for (const key of keys) {
      const t = this.drawn?.tasks.get(key)
      if (t) await this.edit((text) => core.toggle(text, t))
    }
    this.scheduleRedraw()
  }

  private selectedLimb() {
    return this.selected().find((r): r is Extract<Role, { kind: 'limb' }> => r.kind === 'limb')
  }

  async addToSelected() {
    const limb = this.selectedLimb()
    if (!limb) { new Notice('เลือกโปรเจกต์หรือกิ่งบนภาพก่อน'); return }
    new TaskModal(this.app, `งานใหม่ใน #${limb.tag}`, '', [limb.tag], limb.tag, async (title, tag) => {
      if (await this.edit((text) => core.addTagged(text, tag, title))) this.scheduleRedraw()
    }).open()
  }

  /** A sticky note or any text the owner wrote becomes a task in the branch they pick; the text can go from the drawing. */
  async textToTask() {
    const view = this.drawingView()
    const ea = view && this.ea()
    if (!ea) return
    ea.setView(view)
    const text: El | undefined = (ea.getViewSelectedElements() as El[]).find((e) => e.type === 'text' && e.customData?.omni !== MARK)
    if (!text) { new Notice('เลือกข้อความหรือโน้ตแปะบนภาพก่อน'); return }
    const tags = this.drawn?.out.tags ?? []
    if (!tags.length) { new Notice('ยังไม่มีโปรเจกต์บนต้นไม้'); return }
    const title = String(text.rawText ?? text.text ?? '').replace(/\s+/g, ' ').trim()
    new TaskModal(this.app, 'แปลงเป็นงาน', title, tags, this.selectedLimb()?.tag ?? tags[0], async (t, tag, removeText) => {
      if (!(await this.edit((note) => core.addTagged(note, tag, t)))) return
      if (removeText) {
        ea.setView(view)
        const container = text.containerId ? ea.getViewElements().filter((e: El) => e.id === text.containerId) : []
        ea.deleteViewElements([text, ...container])
      }
      this.scheduleRedraw()
    }, true).open()
  }

  async moveSelected() {
    const task = this.selected().find((r): r is Extract<Role, { kind: 'task' }> => r.kind === 'task')
    const t = task && this.drawn?.tasks.get(task.key)
    if (!task || !t) { new Notice('เลือกงานบนภาพก่อน'); return }
    const tags = (this.drawn?.out.tags ?? []).filter((x) => x !== task.tag)
    new PickModal(this.app, `ย้าย "${t.title}" ไป`, tags, async (tag) => {
      if (await this.edit((text) => core.moveTo(text, t, task.tag, tag))) this.scheduleRedraw()
    }).open()
  }

  async foldSelected() {
    const limb = this.selectedLimb()
    if (!limb) { new Notice('เลือกโปรเจกต์หรือกิ่งบนภาพก่อน'); return }
    const f = this.settings.folded
    this.settings.folded = f.includes(limb.tag) ? f.filter((x) => x !== limb.tag) : [...f, limb.tag]
    await this.save()
    await this.redraw()
  }
}

/** Adds the shapes to ExcalidrawAutomate's workbench, marking each and noting the task rings and waits it drew. */
function paint(ea: EA, list: Shape[], drawn: Drawn) {
  const groups = new Map<string, string[]>()
  for (const s of list) {
    ea.style.strokeColor = 'stroke' in s ? s.stroke : s.color
    ea.style.backgroundColor = 'fill' in s ? s.fill : 'transparent'
    ea.style.fillStyle = 'solid'
    ea.style.strokeWidth = 'width' in s ? s.width : 1
    ea.style.strokeStyle = 'dashed' in s && s.dashed ? 'dashed' : 'solid'
    ea.style.roughness = 1
    ea.style.roundness = s.type === 'rect' ? { type: 3 } : null
    let id: string
    switch (s.type) {
      case 'rect': id = ea.addRect(s.x, s.y, s.w, s.h); break
      case 'ellipse': id = ea.addEllipse(s.x, s.y, s.w, s.h); break
      case 'text':
        ea.style.fontSize = s.size
        ea.style.fontFamily = 5
        id = ea.addText(s.x, s.y, s.text)
        break
      case 'line':
        id = s.arrow ? ea.addArrow(s.points, { startArrowHead: null, endArrowHead: 'arrow' }) : ea.addLine(s.points)
        break
    }
    ea.addAppendUpdateCustomData(id, { omni: MARK, kind: s.role.kind, role: s.role })
    if (s.group) groups.set(s.group, [...(groups.get(s.group) ?? []), id])
    if (s.type === 'ellipse' && s.role.kind === 'task') drawn.rings.set(id, { key: s.role.key, tag: s.role.tag, x: s.x, y: s.y })
    if (s.role.kind === 'link') drawn.links.set(id, { blocker: s.role.blocker, waiting: s.role.waiting })
  }
  for (const ids of groups.values()) if (ids.length > 1) ea.addToGroup(ids)
}

// ---- Dialogs ----

class TaskModal extends Modal {
  constructor(
    app: App, private heading: string, private title: string, private tags: string[], private tag: string,
    private done: (title: string, tag: string, removeText: boolean) => void, private fromText = false,
  ) { super(app) }

  onOpen() {
    const { contentEl } = this
    contentEl.createEl('h3', { text: this.heading })
    let title = this.title
    let tag = this.tag
    let removeText = true
    new Setting(contentEl).setName('ชื่องาน').setDesc('ใส่วันหรือความสำคัญได้แบบเพิ่มงานด่วน เช่น "พรุ่งนี้" หรือ "ด่วน"')
      .addText((t) => { t.setValue(title).onChange((v) => { title = v }); window.setTimeout(() => t.inputEl.focus(), 50) })
    new Setting(contentEl).setName('โปรเจกต์และกิ่ง').addDropdown((d) => {
      for (const x of this.tags) d.addOption(x, '#' + x)
      d.setValue(tag).onChange((v) => { tag = v })
    })
    if (this.fromText) new Setting(contentEl).setName('ลบข้อความออกจากภาพหลังเพิ่มงาน').addToggle((t) => t.setValue(removeText).onChange((v) => { removeText = v }))
    new Setting(contentEl).addButton((b) => b.setButtonText('เพิ่มงาน').setCta().onClick(() => {
      if (!title.trim()) return
      this.close()
      this.done(title.trim(), tag, removeText)
    }))
  }

  onClose() { this.contentEl.empty() }
}

class PickModal extends Modal {
  constructor(app: App, private heading: string, private tags: string[], private done: (tag: string) => void) { super(app) }

  onOpen() {
    const { contentEl } = this
    contentEl.createEl('h3', { text: this.heading })
    const list = contentEl.createDiv({ cls: 'omni-yg-pick' })
    for (const tag of this.tags) {
      list.createEl('button', { text: '#' + tag }).addEventListener('click', () => { this.close(); this.done(tag) })
    }
  }

  onClose() { this.contentEl.empty() }
}

// ---- The side panel ----

const SHOWS: [YgFilter['show'], string][] = [['OPEN', 'ยังไม่เสร็จ'], ['ALL', 'ทั้งหมด'], ['DONE', 'เสร็จแล้ว']]
const DATES: [YgFilter['dates'], string][] = [['ANY', 'ทุกวัน'], ['OVERDUE', 'เลยกำหนด'], ['TODAY', 'ถึงวันนี้'], ['WEEK', 'ภายใน 7 วัน'], ['UNDATED', 'ไม่มีวัน']]

class YgView extends ItemView {
  constructor(leaf: WorkspaceLeaf, private plugin: OmniYgdrasil) { super(leaf) }

  getViewType() { return VIEW }
  getDisplayText() { return 'Omni Ygdrasil' }
  getIcon() { return 'git-fork' }

  async onOpen() { this.render() }

  render() {
    const p = this.plugin
    const el = this.contentEl
    el.empty()
    el.addClass('omni-yg')
    el.createEl('h4', { text: 'Omni Ygdrasil' })

    const status = el.createDiv({ cls: 'omni-yg-card' })
    const out = p.drawn?.out
    if (!p.drawingView()) {
      status.createDiv({ text: 'หน้า Ygdrasil ยังไม่ได้เปิด' })
      status.createEl('button', { text: 'เปิดหน้า Ygdrasil', cls: 'mod-cta' }).addEventListener('click', () => p.open())
    } else {
      status.createDiv({ cls: 'omni-yg-ok', text: out ? 'ตรงกับโน้ตแล้ว' : 'กำลังวาด' })
      if (out) status.createDiv({ text: `${out.worlds} โปรเจกต์, ${out.matched} งาน${out.links ? `, งานที่รอกัน ${out.links} เส้น` : ''}` })
      status.createEl('button', { text: 'วาดต้นไม้ใหม่' }).addEventListener('click', () => p.redraw())
    }

    // Filters
    const f = p.settings.filter
    const set = async (change: Partial<YgFilter>) => { p.settings.filter = { ...f, ...change }; await p.save(); await p.redraw() }
    el.createEl('h6', { text: 'ตัวกรอง' })
    const chips = (items: [string, string, boolean, () => void][]) => {
      const row = el.createDiv({ cls: 'omni-yg-chips' })
      for (const [, label, on, click] of items) {
        const b = row.createEl('button', { text: label, cls: on ? 'omni-yg-chip is-on' : 'omni-yg-chip' })
        b.setAttr('aria-pressed', String(on))
        b.addEventListener('click', click)
      }
    }
    chips(SHOWS.map(([v, label]) => [v, label, f.show === v, () => set({ show: v })]))
    chips(DATES.map(([v, label]) => [v, label, f.dates === v, () => set({ dates: v })]))
    chips([
      ['b', 'ติดรองานอื่น', f.blockedOnly, () => set({ blockedOnly: !f.blockedOnly })],
      ['s', 'เฉพาะติดดาว', f.starredOnly, () => set({ starredOnly: !f.starredOnly })],
      ['p', 'รวมกิ่งที่พับเก็บ', f.parked, () => set({ parked: !f.parked })],
      ['l', 'เส้นงานที่รอกัน', f.links, () => set({ links: !f.links })],
    ])
    const search = el.createEl('input', { type: 'search', placeholder: 'ค้นชื่องานหรือ tag', cls: 'omni-yg-search' })
    search.value = f.text
    search.addEventListener('change', () => set({ text: search.value }))

    // What is selected on the drawing
    el.createEl('h6', { text: 'ที่เลือกบนภาพ' })
    const sel = p.selected()
    const box = el.createDiv({ cls: 'omni-yg-card' })
    const limb = sel.find((r) => r.kind === 'limb')
    const tasks = sel.filter((r) => r.kind === 'task')
    if (limb && limb.kind === 'limb') {
      box.createDiv({ cls: 'omni-yg-strong', text: '#' + limb.tag })
      const row = box.createDiv({ cls: 'omni-yg-chips' })
      row.createEl('button', { text: '+ งาน', cls: 'mod-cta' }).addEventListener('click', () => p.addToSelected())
      row.createEl('button', { text: p.settings.folded.includes(limb.tag) ? 'กาง' : 'พับ' }).addEventListener('click', () => p.foldSelected())
    } else if (tasks.length) {
      box.createDiv({ text: tasks.length === 1 ? (p.drawn?.tasks.get((tasks[0] as { key: string }).key)?.title ?? '') : `${tasks.length} งาน` })
      const row = box.createDiv({ cls: 'omni-yg-chips' })
      row.createEl('button', { text: 'ติ๊กเสร็จ', cls: 'mod-cta' }).addEventListener('click', () => p.tickSelected())
      if (tasks.length === 1) row.createEl('button', { text: 'ย้ายไปกิ่งอื่น' }).addEventListener('click', () => p.moveSelected())
    } else {
      box.createDiv({ cls: 'omni-yg-muted', text: 'เลือกกล่องโปรเจกต์ กิ่ง หรืองานบนภาพ' })
      box.createEl('button', { text: 'แปลงข้อความที่เลือกเป็นงาน' }).addEventListener('click', () => p.textToTask())
    }

    const help = el.createDiv({ cls: 'omni-yg-muted' })
    help.setText('ลากลูกศรจากงานหนึ่งไปอีกงาน: งานปลายลูกศรรองานต้นลูกศร ลบเส้นประเพื่อเลิกรอ ลากงานไปวางบนกล่องกิ่งอื่นเพื่อย้าย ลายมือของคุณไม่ถูกแตะ')
  }
}

class YgSettings extends PluginSettingTab {
  constructor(app: App, private plugin: OmniYgdrasil) { super(app, plugin) }

  display() {
    const { containerEl } = this
    containerEl.empty()
    const p = this.plugin
    new Setting(containerEl).setName('หน้า Ygdrasil').setDesc('ที่อยู่ของหน้า Excalidraw ที่วาดต้นไม้')
      .addText((t) => t.setValue(p.settings.drawingPath).onChange(async (v) => { p.settings.drawingPath = v.trim(); await p.save() }))
    new Setting(containerEl).setName('วาดใหม่เองเมื่องานเปลี่ยน').setDesc('เมื่อ Omni note.md ถูกแก้ ไม่ว่าจากมือถือหรือที่นี่')
      .addToggle((t) => t.setValue(p.settings.autoRedraw).onChange(async (v) => { p.settings.autoRedraw = v; await p.save() }))
    new Setting(containerEl).setName('เปิด Omni Task เองตอนเริ่ม Obsidian').setDesc('เปิดแอปไว้ในแท็บหนึ่งทุกครั้งที่เปิด Obsidian')
      .addToggle((t) => t.setValue(p.settings.openOmniOnStart).onChange(async (v) => { p.settings.openOmniOnStart = v; await p.save() }))
    new Setting(containerEl).setName('ลูกศรระหว่างงานเป็นงานที่รอกัน').setDesc('ลากลูกศรจากงานหนึ่งไปอีกงาน แล้วเขียน 🆔 และ ⛔ ลงโน้ตให้')
      .addToggle((t) => t.setValue(p.settings.autoLink).onChange(async (v) => { p.settings.autoLink = v; await p.save() }))
  }
}
