import { taskFilePath } from '../core'

/** For now the vault and account; notifications, appearance and the rest follow Android's Settings next. */
export function SettingsPage(p: { busy: boolean; onReload: () => void; onChangeVault: () => void; onSignOut: () => void }) {
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
        <h2 class="section-title">บัญชี Google</h2>
        <div class="setting">
          <div>ออกจากระบบ</div>
          <button class="ghost danger" onClick={p.onSignOut}>ออกจากระบบ</button>
        </div>
      </section>
    </main>
  )
}
