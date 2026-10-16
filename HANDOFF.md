# ส่งต่องาน: month widget กดแล้วหน่วง

ไฟล์นี้อยู่บน branch `ccr-91df3fce-n8kmu4` เท่านั้น ลบทิ้งก่อน merge เข้า main

## สถานะ branch

- ฐานคือ main ที่ 78c79ea (PR #54 squash merge แล้ว) ต่อด้วย merge commit ประวัติเก่า (force push ถูกปฏิเสธ) แล้วตามด้วยงานใหม่
- APK ทดลองล่าสุด: 0.2.202 (run 38853482469, commit 66964e6) เจ้าของลงแล้ว
- ยังไม่ merge และห้าม merge จนกว่าเจ้าของจะบอก

งานใหม่ที่ยังไม่เข้า main:
1. แตะวันนี้บน widget แล้วขึ้นกรอบฟ้า (`MonthNav.tapDay` เก็บวันนี้ไว้เป็น picked, `MonthSource.view` รับ `nav.picked`)
2. ปุ่มโหลดใหม่บนหัว widget (`ic_w_refresh`, `EXTRA_RELOAD` ใน `MonthTapReceiver`)
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
- ส่งการแตะผ่าน receiver ของเราเองด้วย `FLAG_RECEIVER_FOREGROUND` (`MonthTapReceiver`)

ขั้นต่อไปที่แนะนำ:
1. ขอ log ใหม่จากเจ้าของ (บน 0.2.202) ดู `restricted=` และ `reason=11` ว่ายังโดนจำกัดอยู่ไหม และดูว่ามีแถบเตือนบนหน้ากรอบหรือไม่
2. ถ้า `restricted=false` แต่ยังเป็นก้อน: ต้นเหตุไม่ใช่การจำกัด ให้ดูช่วงเวลาระหว่าง "tap received" กับ "drew widget" และ "loaded the vault" ที่ซ้อนกัน (การโหลด vault ใช้ได้ถึง 3.4 วินาที และอาจบล็อกงานวาด)
3. ทางเลือกที่ยังไม่ได้ลอง: ทำให้การแตะเปลี่ยนเฉพาะกรอบเลือกโดยไม่ต้องให้แอปตอบ เช่น ใช้ `RemoteViews` แบบ `setOnCheckedChangeListener` ของ RadioButton/CompoundButton (Android 12 ขึ้นไป, launcher สลับสถานะเองทันที) ให้แต่ละช่องเป็นปุ่มเลือกแบบกลุ่ม แล้วให้แอปมาเติมรายการทีหลัง ตรวจกับ Glance 1.1.1 ว่ารองรับ `RadioButton`/`CheckBox` แบบนี้หรือไม่ก่อน
4. ทำ mock ก่อนเขียนโค้ดถ้าหน้าตาเปลี่ยน

## กฎที่ต้องจำ

- ตอบเจ้าของเป็นภาษาไทย สั้น บอก model และ effort ที่แนะนำทุกครั้ง
- ห้ามใช้ em dash และ middle dot ในข้อความที่เจ้าของอ่าน
- build และเทสผ่าน GitHub Actions เท่านั้น (`build.yml` สั่งบน branch ได้, push เข้า main ออก release ให้ Obtainium)
- ส่ง APK เป็นไฟล์: ดาวน์โหลด artifact `omni-task` ด้วย `gh api repos/klui2543/omni-task/actions/artifacts/<id>/zip` แล้วส่งด้วย SendUserFile
- คอมไพล์ shared ในเครื่องได้ด้วย `gradle :shared:jvmTest`, ส่วน app ต้องพึ่ง CI (ระวังตัวแปรชื่อ `app` บังชื่อ package `app.omnitask`)
