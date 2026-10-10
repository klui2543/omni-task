/** The card that asks to read Google Calendar, and the reason when it could not be read; as on the Focus page. */
export function CalendarConnect(p: { connected: boolean; note: string; onConnect: () => void }) {
  return (
    <>
      {!p.connected && (
        <section class="fcard connect">
          <div class="grow">
            <div class="strong">เชื่อมต่อ Google Calendar</div>
            <div class="muted small">ให้เว็บอ่านนัดและเวรจาก Google Calendar เพื่อจัดเวลาว่าง และให้ผู้ช่วยลงนัดให้ได้</div>
          </div>
          <button class="primary" onClick={p.onConnect}>อนุญาต</button>
        </section>
      )}
      {p.note && <section class="notice" role="alert"><span class="grow">{p.note}</span></section>}
    </>
  )
}
