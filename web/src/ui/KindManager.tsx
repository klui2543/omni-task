import { useState } from 'preact/hooks'
import { applyKindTags, kindsAdd, kindsRemove, kindsToggleHidden, kindsView } from '../core'
import { customKindTags, firstGlyph, kindsStore } from '../kinds'
import type { KindsView } from '../types'

/**
 * Android's kind manager as a dialog over the edit panel: make kinds of your own (a name and an emoji), forget them,
 * and hide or show the built-in ones. The same choices as the "ประเภทงาน" card in Settings, kept on this device and
 * shared with Android; [onChange] lets the panel show the new choices at once.
 */
export function KindManager(p: { onChange: () => void; onClose: () => void }) {
  const [view, setView] = useState(() => kindsView(kindsStore.get()))
  const [name, setName] = useState('')
  const [emoji, setEmoji] = useState('')
  const [error, setError] = useState('')
  const keep = (v: KindsView) => { kindsStore.set(v.state); setView(v); applyKindTags(customKindTags(v.state)); p.onChange() }
  const tag = name.trim() ? kindsAdd(view.state, name, '').tag : ''
  const add = (e: Event) => {
    e.preventDefault()
    const r = kindsAdd(view.state, name, firstGlyph(emoji))
    if (!r.ok) { setError(r.error === 'taken' ? 'มีประเภทนี้อยู่แล้ว' : 'ใส่ชื่อประเภท'); return }
    keep(r.view); setName(''); setEmoji(''); setError('')
  }
  return (
    <div class="scrim" onClick={p.onClose} onKeyDown={(e) => e.key === 'Escape' && p.onClose()}>
      <section class="dialog kind-manager" role="dialog" aria-modal="true" aria-label="ประเภทงาน" onClick={(e) => e.stopPropagation()}>
        <h2>ประเภทงาน</h2>
        <div class="kind-manager-body">
          <span class="set-note">ประเภทที่มากับแอป</span>
          {view.builtin.filter((b) => b.id !== 'NORMAL').map((b) => (
            <div key={b.id} class="set-row">
              <span class={`grow${b.hidden ? ' faint' : ''}`}>
                <span>{b.label}</span>
                <span class="set-note">{b.tag ? '#' + b.tag : ''}</span>
              </span>
              <button class="pillbtn" aria-label={`${b.hidden ? 'แสดง' : 'ซ่อน'} ${b.label}`} onClick={() => keep(kindsToggleHidden(view.state, b.id))}>{b.hidden ? 'แสดง' : 'ซ่อน'}</button>
            </div>
          ))}
          <span class="set-note">ซ่อนแล้วการ์ดของประเภทนั้นในหน้าโฟกัสจะหายไปด้วย</span>
          <span class="set-note kinds-sub">ประเภทของคุณ</span>
          {view.custom.length === 0 && <span class="set-note">ยังไม่มี</span>}
          {view.custom.map((c) => (
            <div key={c.tag} class="set-row">
              <span class="grow"><span>{c.label}</span><span class="set-note">#{c.tag}</span></span>
              <button class="pillbtn danger" aria-label={`ลบ ${c.label}`} onClick={() => keep(kindsRemove(view.state, c.tag))}>ลบ</button>
            </div>
          ))}
          <form class="kind-add" onSubmit={add}>
            <input class="field kind-emoji" aria-label="อีโมจิของประเภท" placeholder="🏷️" value={emoji} onInput={(e) => setEmoji(firstGlyph(e.currentTarget.value))} />
            <input class="field grow" aria-label="ชื่อประเภทใหม่" placeholder="ชื่อประเภทใหม่" value={name} onInput={(e) => { setName(e.currentTarget.value); setError('') }} />
            <button class="pillbtn" disabled={!name.trim()}>เพิ่ม</button>
          </form>
          {tag && <span class="set-note">จะติดแท็ก #{tag}</span>}
          {error && <span class="error" role="alert">{error}</span>}
        </div>
        <div class="dialog-actions"><button class="primary" onClick={p.onClose}>เสร็จ</button></div>
      </section>
    </div>
  )
}
