# ส่งต่องาน: month widget กดแล้วหน่วง

ไฟล์นี้อยู่บน branch `ccr-91df3fce-n8kmu4` เท่านั้น ลบทิ้งก่อน merge เข้า main

## สถานะ branch

- ฐานคือ main ที่ 78c79ea (PR #54 squash merge แล้ว) ต่อด้วย merge commit ประวัติเก่า (force push ถูกปฏิเสธ) แล้วตามด้วยงานใหม่
- APK ทดลองล่าสุด: 0.2.202 (run 38853482469, commit 66964e6) เจ้าของลงแล้ว
- ยังไม่ merge และห้าม merge จนกว่าเจ้าของจะบอก

งานใหม่ที่ยังไม่เข้า main:
1. แตะวันนี้บน widget แล้วขึ้นกรอบฟ้า (`MonthNav.tapDay` เก็บวันนี้ไว้เป็น picked, `MonthSource.view` รับ `nav.picked`)
2. ปุ่มโหลดใหม่บนหัว widget (`ic_w_refresh`, `EXTRA_RELOAD` ใน `MonthTapService`)
3. `CalendarPrint` ใน `widget/CalendarWatch.kt`: ปฏิทินซิงก์แล้ว widget รีเฟรชเฉพาะเมื่อ event หรือปฏิทินเปลี่ยนจริง ตัวเฝ้าดูรอให้นิ่ง 5 วินาที นานสุด 20 วินาที
4. แถบชื่องานแบบ Samsung Calendar ใช้ painter ตัวเดียวกันทั้ง widget ปฏิทินในแอป และหน้ากรอบ: นัดฟ้า งานเขียว งานเลยกำหนดและวันหยุดแดง, +n มุมขวาบน, Emoji มุมซ้ายบน (`MonthData.Mark.late`)
5. หน้ากรอบและ Emoji (`ui/MonthScreen.kt`) วาดตามค่าแบบช่องวันแล้ว แตะวันเดียวแสดงรายการทั้งวัน เลือกหลายวันแสดงสรุปทีละวัน (`DayLines`, `DayLine`) mock: https://claude.ai/artifact/GQc2S2SsD67q9MG2obkejL

## ปัญหาที่ยังค้าง: กดแล้วหน่วง

อาการบนมือถือ Samsung ของเจ้าของ: แตะวันบน widget แล้วค้างหลายวินาที จากนั้นกระโดดไปหลายวันทีเดียว

สิ่งที่รู้จาก log รอบก่อน (แชร์จากหน้ากรอบ ปุ่ม "แชร์บันทึกการทำงานของ widget"):
- แอปตอบแต่ละการแตะใน 90 ถึง 130 ms ไม่ใช่ตัวช้า
- การแตะมาถึงแอปเป็นก้อนหลังเงียบ 5 ถึง 8 วินาที คือระบบกัก broadcast ไว้
- `warm job stopped by the system, reason=11` คือ STOP_REASON_BACKGROUND_RESTRICTION แอปถูกจำกัดการทำงานเบื้องหลัง
- ตอนเจ้าของตั้งแบตเตอรี่เป็น "ไม่จำกัด" อาการหายไปช่วงหนึ่ง แล้วกลับมา (Samsung อาจจับกลับเข้า "แอปที่หลับ")
- 0.2.197 ขึ้นไปมีแถบเตือนบนหน้ากรอบเมื่อ `isBackgroundRestricted` และ log บอก `restricted=... optimised=...`

สิ่งที่ลองไปแล้ว (อย่าลองซ้ำ):
- วาดใหม่ทั้งก้อนทุกการแตะ (แย่ลง)
- เก็บ composition ไว้ในแอป (`MonthLive.keep`) และเรียก session ของ Glance ผ่าน reflection (`GlanceSessions`)
- expedited job กันแอปเข้า cached (`WidgetWarm`)
- ส่งการแตะผ่าน receiver ของเราเองด้วย `FLAG_RECEIVER_FOREGROUND` (`MonthTapReceiver`, ลบแล้ว)

## log บน 0.2.202 (16 ต.ค.)

- `restricted=false optimised=false` ตลอด แต่ `warm job stopped by the system, reason=11` ที่ 5.0 วินาทีหลัง job เริ่มพอดี คือ Samsung หยุด job เองแม้แอปไม่ถูกจำกัด (น่าจะเป็นตัว freeze ของ Samsung)
- การแตะยังมาเป็นก้อน: 5 ครั้งใน 8 ms หลังเงียบ 5.6 วินาที (ทันทีหลัง job ถูกหยุด) และ 9 ครั้งหลังเงียบ 5.6 วินาที (หลังกดโหลดใหม่) ช่วงอื่นตอบทีละครั้งใน 90 ถึง 130 ms
- บั๊กใน `WidgetWarm`: พอระบบหยุด job แล้ว coroutine ยังวนต่อ `running` ค้างเป็น true การแตะต่อมาจึงไม่ขอ job ใหม่เลย (ไม่สำคัญแล้ว เพราะลบ `WidgetWarm` ทิ้ง)

## รอบนี้ (ยังไม่ได้ลองบนเครื่องเจ้าของ)

- การแตะทุกอย่างบน widget (วัน, ‹ ›, หัวเดือน, โหลดใหม่) เปลี่ยนจาก broadcast เป็น `PendingIntent.getForegroundService` ไปที่ `MonthTapService` (shortService) ระบบยอมให้เริ่ม FGS เพราะ launcher อยู่หน้าจอตอนกด และแอปที่มี FGS จะไม่ถูก freeze
- service อยู่ต่อ 3 วินาทีหลังการแตะครั้งสุดท้ายแล้วจบเอง (`LINGER_MS`), จบด้วย `stopSelf(lastStart)` กันชนกับการแตะที่กำลังมา
- ผลข้างเคียง: Android เลื่อนแจ้งเตือนของ FGS ไป 10 วินาที แตะสั้น ๆ จึงไม่เห็นอะไร แต่แตะนานกว่านั้น หรือแตะรอบใหม่ภายใน 2 นาที จะมีแจ้งเตือนเงียบ "กำลังเปิดวันที่แตะบนปฏิทิน" ในแถบแจ้งเตือนจนกว่า service จบ
- log ใหม่: `front=` แทน `warm=`, `Stalls` เขียน `the app did not run for N ms` (แอปถูก freeze หรือจอดับ) และ `main thread was busy for N ms` ช่วง 1 นาทีหลังการแตะ ถ้ายังหน่วงอยู่ บรรทัดเหล่านี้จะบอกว่าค้างที่ไหน
- ถ้าเจ้าของรับแจ้งเตือนไม่ได้: ทางเลือกต่อไปคือ activity ล่องหน (แบบ `InvisibleActionTrampolineActivity` ของ Glance) แต่เสี่ยงหน้าจอหลักกระพริบหรือ stack widget รีเซ็ต

ขั้นต่อไปที่แนะนำ:
1. ให้เจ้าของลอง APK ใหม่ แล้วขอ log ดูว่าการแตะยังมาเป็นก้อนไหม, `front=true` ระหว่างแตะไหม, มี `foreground failed` หรือ `did not run` หรือไม่
2. ถามเจ้าของว่าแจ้งเตือนเงียบตอนแตะรบกวนไหม
3. ทางเลือกที่ยังไม่ได้ลอง: ให้ launcher สลับกรอบเลือกเองทันทีด้วย CompoundButton ของ `RemoteViews` (Android 12 ขึ้นไป) แล้วให้แอปเติมรายการทีหลัง
4. ทำ mock ก่อนเขียนโค้ดถ้าหน้าตาเปลี่ยน

## กฎที่ต้องจำ

- ตอบเจ้าของเป็นภาษาไทย สั้น บอก model และ effort ที่แนะนำทุกครั้ง
- ห้ามใช้ em dash และ middle dot ในข้อความที่เจ้าของอ่าน
- build และเทสผ่าน GitHub Actions เท่านั้น (`build.yml` สั่งบน branch ได้, push เข้า main ออก release ให้ Obtainium)
- ส่ง APK เป็นไฟล์: ดาวน์โหลด artifact `omni-task` ด้วย `gh api repos/klui2543/omni-task/actions/artifacts/<id>/zip` แล้วส่งด้วย SendUserFile
- คอมไพล์ shared ในเครื่องได้ด้วย `gradle :shared:jvmTest`, ส่วน app ต้องพึ่ง CI (ระวังตัวแปรชื่อ `app` บังชื่อ package `app.omnitask`)
