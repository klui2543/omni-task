# Omni สั่งซิงก์ Obsidian แบบไม่มีหน้าจอ (Obsidian Headless ใน Termux)

## คำของ Klui (คำต่อคำ, 13 พ.ย. 2569 เวลาไทย)
- "Can we make obsidian open in background for us? Right now it open over Omni everytime and have to close and back to Omni again"
- "But the sync to Google drive not that often" / "But this way broke our system once" (ถอนทางอ่านจาก Drive)
- "Or make it open in background silently?" / "Then find better solutions"
- "But it make source not centralized and not that real-time like obsidian" (ถอนทางตู้จดหมาย Drive)
- เลือก "ไม่เอา คิดต่อ" กับทางเด้งแล้วกลับเอง
- เลือก "ลองเลย" กับคำถาม "ลองใช้ตัวซิงก์ Obsidian แบบไม่มีหน้าจอบนมือถือไหม"

## ต้นตอ (ยืนยันแล้ว)
`TaskViewModel.openObsidianIfBehind` เปิด `obsidian://open` ทับ Omni เมื่อ Claude log ในเครื่องเก่ากว่ารอบเลขาล่าสุด (ครั้งละรอบ) เพราะแอป Obsidian ซิงก์ได้เฉพาะตอนอยู่หน้าจอ

## ขอบเขต
มือถือรัน Obsidian Headless (`ob`, npm `obsidian-headless` ของ Dynalist Inc. ตัวเดียวกับที่เลขาใช้) ใน Termux ซิงก์โฟลเดอร์ vault ของตัวเองผ่าน Obsidian Sync และ Omni สั่งมันผ่าน Termux RUN_COMMAND แบบเบื้องหลัง:
1. Omni กลับมาหน้าจอ: สั่งซิงก์ เสร็จแล้วอ่าน vault ใหม่ ไม่เปิดแอป Obsidian
2. Omni ออกจากหน้าจอ: สั่งซิงก์ เพื่อส่งสิ่งที่ Omni เขียนขึ้นไป
3. ทางใหม่ค่าตั้งต้นปิด: ทำงานเฉพาะเมื่อเครื่องมี Termux และ Klui อนุญาตสิทธิ์ RUN_COMMAND แล้ว (ตัวอ่านกลางตัวเดียว) ไม่มีก็ทำแบบเดิมทุกอย่าง
4. ซิงก์ไม่สำเร็จ: บอก Klui เป็นข้อความ (ไม่กลืนเงียบ) ไม่เปิด Obsidian (รอบ 2: แอป Obsidian ซิงก์โฟลเดอร์ของตัวเอง ไม่ใช่ OmniVault จึงช่วยไม่ได้)
6. สวิตช์เปิดเฉพาะเมื่อโฟลเดอร์ที่ Omni อ่านคือ OmniVault (รอบ 2) ก่อน Klui ย้ายโฟลเดอร์ ทุกอย่างเหมือนเดิม
5. ชุดคำสั่งติดตั้งใน Termux ให้ Klui วางทีเดียว (ไฟล์ `docs/termux-sync-setup.sh`)

## ไม่ทำ
- ไม่เปลี่ยนหน้าตาแอป ไม่เพิ่มปุ่มหรือหน้าตั้งค่า (ขอสิทธิ์ใช้กล่องของระบบ)
- ไม่แตะทาง Google Drive ของโฟลเดอร์ Omni ไม่แตะรูปแบบโน้ต ไม่ลบอะไร
- ไม่แก้ปลั๊กอิน Obsidian ไม่แก้คู่มือเลขา
- ไม่รันซิงก์ต่อเนื่องเป็นค่าตั้งต้น (แบตเตอรี่) ไม่ตั้ง Termux:Boot

## ระดับเสี่ยง: กลาง
Omni ไม่เปลี่ยนวิธีเขียนโน้ต แต่ `ob` เขียนโฟลเดอร์ vault ใหม่บนมือถือ ถ้าพัง: ข้อมูลไม่สด หรือ Omni ยังเด้ง Obsidian เหมือนเดิม
