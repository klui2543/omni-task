# ปุ่มแตะซิงก์ (แทนการเปิด Obsidian เอง)

แบบงาน: ซ่อม (Obsidian เด้งทับ Omni ทุกครั้งที่กลับเข้าแอป)

## คำของ Klui
- 13 พ.ย.: "Can we make obsidian open in background for us? Right now it open over Omni everytime and have to close and back to Omni again"
- 14 พ.ย.: "ไม่เวิร์ค Termux กินแบตมาก" (ถอดทาง Termux)
- 14 พ.ย. เลือกการ์ด "ไม่เด้ง แตะเอง": Omni ไม่เปิด Obsidian เองอีก มีที่ให้แตะเปิดซิงก์เอง
- 14 พ.ย. เลือก mock แบบ ค "ปุ่มซิงก์": ปุ่ม ⟳ ข้างฟันเฟือง มีจุดส้มเมื่อยังไม่ซิงก์ (https://claude.ai/artifact/VzxRC3S3qyAJJigXWYYRYT)

## ทำ
- ถอดของทาง Termux ทั้งหมด (revert 4 commit)
- Omni ไม่เปิด Obsidian เองเมื่อกลับเข้าแอป
- หน้าวันนี้ (โหมดใหม่) มีปุ่ม ⟳ ก่อนฟันเฟือง แตะแล้วเปิด Obsidian (vault ของ Omni)
- จุดส้มบนปุ่มเมื่อ `Secretary.behind` (log ในเครื่องเก่ากว่ารอบเลขาที่ควรมี และ health.md ก็เก่ากว่า)
- เปิด Obsidian ไม่ได้: บอกด้วยข้อความเดิมที่มีอยู่แล้ว

## ไม่ทำ
- ไม่แตะการ์ดเตือน "เลขาไม่ได้บันทึก N วัน"
- ไม่เพิ่มปุ่มในหน้าวันนี้แบบเก่า (ไม่มี mock)
- ไม่ merge ไม่เปิด PR
