import { useState } from 'preact/hooks'
import { taskFilePath } from '../core'
import { askOnDone } from '../Home'

/** For now the vault and account; notifications, appearance and the rest follow Android's Settings next. */
export function SettingsPage(p: { busy: boolean; onReload: () => void; onChangeVault: () => void; onSignOut: () => void }) {
  const [ask, setAsk] = useState(askOnDone.get)
  return (
    <main class="page">
      <header class="head">
        <h1>ตั้งค่า</h1>
      </header>
      <section class="group">
        <h2 class="section-title">Vault</h2>
        <div class="setting">
          <div>
            <div>ไฟล์งาน</div>
            <div class="muted small">{taskFilePath}</div>
          </div>
          <button class="ghost" disabled={p.busy} onClick={p.onReload}>โหลดใหม่</button>
        </div>
        <div class="setting">
          <div>เปลี่ยน vault</div>
          <button class="ghost" onClick={p.onChangeVault}>เลือกใหม่</button>
        </div>
      </section>
      <section class="group">
        <h2 class="section-title">งานที่เสร็จ</h2>
        <label class="setting">
          <div>
            <div>ถามเมื่อติ๊กเสร็จ</div>
            <div class="muted small">เก็บเข้าคลัง ลบ หรือไว้ก่อน</div>
          </div>
          <input type="checkbox" role="switch" class="switch" checked={ask} onChange={(e) => { askOnDone.set(e.currentTarget.checked); setAsk(e.currentTarget.checked) }} />
        </label>
      </section>
      <section class="group">
        <h2 class="section-title">บัญชี Google</h2>
        <div class="setting">
          <div>ออกจากระบบ</div>
          <button class="ghost danger" onClick={p.onSignOut}>ออกจากระบบ</button>
        </div>
      </section>
    </main>
  )
}
