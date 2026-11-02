# FEATURE_MAP.md: แผนที่หน้าจอของ Omni (ให้ AI รู้ว่าฟีเจอร์อยู่ตรงไหน เปิดอย่างไร อยู่ไฟล์ไหน)

กติกา: ทุกครั้งที่เพิ่มหรือแก้หน้าจอ ต้องอัปเดตไฟล์นี้ (แกนกลางหัวข้อ 8) แอป Android ไม่มี selector แบบเว็บ ให้ระบุด้วยข้อความบนปุ่มที่ผู้ใช้เห็น (ภาษาไทยตามโหมดใหม่) ข้อที่ยังไม่ได้เปิดของจริงดูติด [ยังไม่ยืนยัน]
ฉบับแรก 2026-11-02 เขียนจากการอ่านโค้ด ยังไม่ได้ลองบนมือถือ

## โครง 3 ส่วน
| ส่วน | อยู่ที่ | สร้างและเทส |
|---|---|---|
| แอป Android (Kotlin, Compose) | `app/src/main/java/app/omnitask/` (`ui/` หน้าจอ, `widget/` วิดเจ็ตและการ์ดหน้าล็อก, `notify/` เตือน, `data/` อ่านเขียน vault) | GitHub Actions `Build APK` เท่านั้น ไม่มี SDK ในเครื่อง |
| ตรรกะ vault ร่วม (KMP) | `shared/src/commonMain/kotlin/app/omnitask/` (`data/` บรรทัดงาน, `model/` แผนวัน โฟกัส เลขา โปรเจกต์, `drive/` Google Drive) | `gradle :shared:allTests` (JVM และ JS, 86 ไฟล์เทส) |
| เว็บและปลั๊กอิน Obsidian | `web/src/pages/` (Focus, Tasks, Projects, Assistant, Settings, Views) และ `obsidian/` | `npm run preview` ใน `obsidian/` |

## แถบล่างของแอป (โหมดใหม่, `ui/OmniTaskApp.kt` enum `Screen` และ `DockItem`)
| ปุ่ม | หน้าจอ | ไฟล์หลัก | ตรรกะใน shared |
|---|---|---|---|
| วันนี้ | แผนวันแบบไทม์ไลน์ ช่วงนอน เหตุการณ์ปฏิทิน สัปดาห์หน้า และสำรับการ์ด (ทำต่อไป ค้าง เลขาให้ตัดสิน) | `ui/FocusScreen.kt`, `ui/DayFillUi.kt`, `ui/CalendarTimeline.kt`, `ui/CardDeck.kt`, `ui/LookAheadUi.kt`, `ui/SleepUi.kt` | `model/DayPlan.kt`, `DayFill.kt`, `Planner.kt`, `NextUp.kt`, `Focus.kt`, `LookAhead.kt` |
| งาน | รายการงานทั้งหมด ค้นหา กรอง แก้ไข | `ui/TasksScreen.kt`, `ui/EditSheet.kt`, `ui/FilterBody.kt`, `ui/SearchResults.kt` | `data/TaskLine.kt` (รูปแบบโทเค็นของบรรทัดงาน) |
| + (กลาง) | เพิ่มงานเร็ว ชิปโฟลเดอร์ วันที่ กดค้าง = จดด้วยเสียง | `ui/QuickAddSheet.kt`, `ui/AddDetailPage.kt`, `ui/VoiceUi.kt` | `model/QuickAdd.kt` |
| โปรเจกต์ | โฟลเดอร์และลิสต์ (ลิสต์เป็นโฟลเดอร์) ต้นไม้ Ygdrasil ย้ายงาน พับ ย้อน | `ui/ProjectsScreen.kt`, `ui/FolderUi.kt`, `ui/ListsUi.kt`, `ui/YgdrasilScreen.kt`, `ui/BranchTree.kt`, `ui/MindMap.kt` | `model/Projects.kt`, `Folders.kt`, `ListFolders.kt`, `ProjectNames.kt`, `GroupMove.kt` |
| เลขา | แชตเลขาตามคู่มือใน vault สำรับการ์ดตัดสิน โหมดทบทวน เลขาถาม ข้อเสนอโปรเจกต์ | `ui/SecretaryUi.kt`, `ui/DecisionDeck.kt`, `ui/ReviewUi.kt`, `ui/SecretaryAskUi.kt`, `ui/AssistantScreen.kt` | `model/AiAssist.kt`, `AssistantMl.kt`, `Insight.kt`, `Learned.kt` (คู่มือ: `เลขา คู่มือ.md`, `Claude log.md`, `เลขาถาม.md`) |
| เฟือง (บนหน้าวันนี้) | ตั้งค่า: บัญชี Drive ปฏิทิน การเตือน หน้าล็อก สุขภาพ | `ui/SettingsScreen.kt`, `ui/NotifySettingsScreen.kt`, `ui/CalendarConnect.kt`, `ui/AiSettings.kt`, `HealthRationaleActivity.kt` | `model/Profile.kt` |

## นอกแอป
| ฟีเจอร์ | ไฟล์ | ผู้ใช้เห็นที่ไหน |
|---|---|---|
| วิดเจ็ตวันนี้และสัปดาห์ (แบบปฏิทิน Google) | `widget/Widgets.kt`, `TodayWeek.kt`, `WidgetData.kt` | หน้าโฮม OPPO |
| วิดเจ็ตเดือน แตะวัน | `widget/MonthWidget.kt`, `MonthTapActivity.kt`, `MonthTapService.kt` | หน้าโฮม |
| การ์ดหน้าล็อก (แผนวันนี้ 4 แถว ขยายเป็นรายการวัน) | `widget/LockCard.kt`, `LockCardNotifier.kt`, layout `lock_card*` | หน้าล็อก (สวิตช์ในตั้งค่า) |
| เตือนตามเวลา ⏰ 🎯 และนาฬิกาข้อมือ | `notify/Scheduler.kt`, `AlarmReceiver.kt`, `WatchAlarm.kt`, `WatchListener.kt` | การแจ้งเตือน |

## วิธีตรวจรับที่ Klui ทำได้
- ติดตั้ง APK จากห้องแชต เปิดหน้าที่แก้ ทำตามรายการ `apk/test-list-<รุ่น>.md` ที่ส่งคู่กัน
- ของจริงในโน้ต: เปิด `Omni note.md` ใน Obsidian ดูว่าบรรทัดงานยังอยู่ครบและรูปแบบโทเค็นไม่เพี้ยน [ยังไม่ยืนยัน ว่าทุกฟีเจอร์มีรายการนี้]
