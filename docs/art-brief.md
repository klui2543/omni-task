# Omni Quest: บรีฟงานภาพตัวละคร

เอกสารนี้ใช้ให้ AI สร้างภาพหรือให้นักวาดทำตามได้เลย อิงข้อ 3, 9, 10, 14 ของ `rpg-design.md` และตารางตัวละครใน `rpg-story.md` ภาพที่ได้มาใช้ในแอปทั้ง Android, เว็บ และปลั๊กอิน Obsidian

## 1. สไตล์ที่ต้องการ

**แนวภาพ:** เกมต่อสู้ 2D ญี่ปุ่นจากการ์ตูนแอ็กชันแนวโชเน็น (แรงบันดาลใจคือเกม Katekyo Hitman Reborn! DS Flame Rumble แต่ทำเป็นแฟนตาซี) เอาแค่ "ความรู้สึกของภาพ" ไม่ลอกตัวละคร ชื่อ ชุด ฉาก หรือภาพจากเกมต้นแบบ

ลักษณะภาพ (ผมสรุปจากความรู้ทั่วไปของแนวนี้ ไม่ได้ยืนยันกับภาพจากเกมจริง ถ้ามีภาพอ้างอิงที่ชอบ ใส่เข้าไปแทนข้อนี้ได้)

- เส้นขอบหนาเข้มสม่ำเสมอ ลงสีเรียบแบบ cel เงาแข็งสีเดียวต่อชิ้น ไม่มีไล่สีนุ่ม
- ตัวละครมนุษย์สัดส่วนอนิเมะ 6 ถึง 7 หัว ผอมสูง ผมทรงแหลมโดดเด่น ตาโตคม ท่าทางมีพลัง
- เปลวไฟเป็นจุดเด่นของภาพ: เปลวเล็กลุกที่หน้าผากหรือมือ เปลวรอบอาวุธ สีตามเผ่า (ในเรื่องคือ "แสงอรุณ" ที่เกิดจากงานที่ทำเสร็จจริง)
- ฉากหลังโปร่งใสหรือพื้นเรียบ มีเส้นเร่งความเร็วได้เฉพาะภาพโปรโมต
- Mascot สัตว์เป็นแบบ SD (หัวใหญ่ ตัวสั้น ตาโต) แต่ใช้เส้นขอบ เงา และเปลวไฟชุดเดียวกับมนุษย์ จะได้อยู่ภาพเดียวกันได้

**ห้าม:** ตัวหนังสือในภาพ ลายน้ำ โลโก้ ตัวละครหรืออาวุธที่เหมือนเกมอื่นอย่างชัดเจน (Ragnarok รวมอยู่ด้วยตามข้อ 14) ชุดสูทแบบมาเฟีย ปืน

## 2. สีประจำเผ่า

| เผ่า | ธาตุ | สีหลัก | สีเปลวไฟ (แกน / ขอบ) |
|---|---|---|---|
| อัคนี | ไฟ | `#ff8a5b` | `#fff1b8` / `#ff6a3a` |
| จันทรา | แสงดาว | `#8c93ff` | `#eef0ff` / `#8c93ff` |
| วายุ | ลม | `#67d6b5` | `#eafff7` / `#67d6b5` |
| ธารา | น้ำ | `#5cc8ff` | `#e4f6ff` / `#5cc8ff` |
| พฤกษา | ไม้ | `#8bd17c` | `#f0ffd9` / `#8bd17c` |
| กิลด์ (ออร์ริน) | รุ่งอรุณ | `#ffc83d` | `#fff6d0` / `#ffc83d` |
| หมอก (ราชาผัดวัน) | หมอก | `#bfb2e0` | `#f1ecfa` / `#a58cff` |

เส้นขอบทุกภาพใช้สี `#1a1424`

## 3. Prompt แม่แบบ (ภาษาอังกฤษ ใช้ได้กับเครื่องมือส่วนใหญ่)

```
2D Japanese shonen fighting-game character art, fantasy setting.
[CHARACTER DESCRIPTION from section 4].
Thick clean dark outline (#1a1424), flat cel shading with one hard cool-toned shadow shape,
stylish dynamic ready-to-fight pose, a small flame burning on the forehead and flame wrapping
the weapon or hands, flame colors: core [CORE HEX], edge [EDGE HEX], character main color [MAIN HEX].
Sharp expressive eyes, spiky stylish silhouette, anime proportions [PROPORTION].
Full body, transparent background, centered, no text, no watermark, no logo.
```

ส่วน `[PROPORTION]`: มนุษย์ใช้ `6 to 7 heads tall`, Mascot ใช้ `super-deformed chibi, 2 heads tall, big head, short body`

**Negative prompt:** `text, watermark, logo, signature, photorealistic, 3d render, gradient shading, blurry, extra fingers, modern firearms, suit and tie`

## 4. รายละเอียดตัวละครทีละตัว

ใส่ข้อความในช่อง `[CHARACTER DESCRIPTION]` ของแม่แบบ

### Mascot ประจำเผ่า (SD)

| ตัวละคร | เผ่า | คำอธิบายสำหรับ prompt |
|---|---|---|
| โฮมุระ | อัคนี | `a round baby fire dragon, chubby body, small golden horns, tiny wings, cream belly, small flame at the tail tip, cheerful and eager` |
| มิมิ | จันทรา | `a star owl wearing round golden glasses, indigo feathers with pale starlight belly, tiny star sparkle above the head, calm and studious` |
| ฟุ้ง | วายุ | `a small fox with a tail shaped like a fluffy cloud, mint green fur, white muzzle and cheeks, playful and quick` |
| ปุยฟอง | ธารา | `a baby sky whale floating on small clouds, light blue skin, pale belly, tiny water spout, gentle and sleepy` |
| ต้นกล้า | พฤกษา | `a young fawn with antlers shaped like leafy branches, tan coat with cream spots, small leaves sprouting from the antlers, shy and kind` |

ท่าที่ต้องการต่อ Mascot: ยืนปกติ, ดีใจ, หลับหรือพัก (ใช้ใน Empty state และบทพูด) และถ้าทำได้ ท่าส่งเสียงให้กำลังใจ

### NPC และตัวร้าย (สัดส่วนอนิเมะ)

| ตัวละคร | เผ่า | คำอธิบายสำหรับ prompt |
|---|---|---|
| ลีโอ | อัคนี | `a cheerful boastful young swordsman, spiky red hair, confident smirk, orange fantasy tunic with a gold shoulder guard, brown boots, a long sword, flame gauntlet on the left fist, loves to compete` |
| เซลีน | จันทรา | `a quiet observant young mage, long silver hair, indigo robe with star trim, a staff topped with a crescent moon, a small notebook at the hip, sharp calm eyes` |
| มาสเตอร์ออร์ริน | กิลด์ | `a kind elderly guild master, long white beard and bushy eyebrows, warm gold and brown robe with a sunrise emblem, walks with a cane because of an injured leg, hearty laugh, a faint golden flame on the forehead` |
| ราชาผัดวัน | หมอก | `a tall robed figure made of drifting lavender fog wearing a gold crown, half-closed sleepy gentle eyes, soft persuasive smile, no weapon, never aggressive, fog curling around the hem` |

ท่าที่ต้องการ: ยืนเตรียมสู้ (หรือยืนผ่อนคลายสำหรับออร์ริน), ดีใจ, ภาพครึ่งตัวสำหรับกล่องสนทนา

### ผู้เล่น (paper-doll)

ไม่ให้ AI วาดทั้งตัว แต่ทำเป็นชิ้นส่วนที่ซ้อนกันได้ตามข้อ 5 ของ `rpg-design.md` แยกไฟล์ต่อชิ้น: รูปร่าง, สีผิว, ทรงผม, ตา, ปาก, ชุด, อาวุธ, เครื่องประดับ, ออร่าเวทตามธาตุ ทุกชิ้นวาดบนโครงตัวละครเดียวกัน ขนาดผืนผ้าใบเท่ากัน จัดตำแหน่งตรงกัน วิธีที่ง่ายที่สุดคือซื้อ asset pack แนว chibi ที่แยกชิ้นอยู่แล้ว (ข้อ 14) แล้วให้ AI วาดเฉพาะชุดและออร่าเพิ่ม

## 5. ขั้นตอนให้ภาพออกมาเป็นชุดเดียวกัน

1. ทำ **ตัวต้นแบบสไตล์** ก่อนเพียง 1 ตัว (แนะนำลีโอ เพราะมีเปลวไฟ ดาบ และใบหน้าครบ) ลองหลายรอบจนพอใจ แล้วเก็บเป็นภาพอ้างอิงหลัก
2. ตัวละครถัดไปทุกตัวให้ใส่ภาพอ้างอิงหลักนั้น (image reference หรือ style reference) ควบคู่กับ prompt แม่แบบ
3. ได้ภาพที่ผ่านแล้ว ให้เก็บเป็นภาพอ้างอิงของตัวละครนั้น ท่าอื่นของตัวเดียวกันใช้ภาพนี้อ้างอิง เพื่อให้หน้าและชุดไม่เพี้ยน
4. ตรวจก่อนรับภาพ: เส้นขอบหนาเท่ากัน สีตรงกับตารางเผ่า ไม่มีตัวหนังสือ เปลวไฟเป็นสีของเผ่านั้น พื้นหลังโปร่งใส (ถ้าเครื่องมือไม่รองรับ ให้ใช้พื้นเรียบสีเดียวแล้วลบพื้นทีหลัง)
5. ภาพที่ใช้เร่งความเร็วหรือฉากเนื้อเรื่องทำทีหลัง ต้องใช้ตัวละครที่ผ่านขั้นตอนข้างบนแล้วเป็นอ้างอิง

## 6. รูปแบบไฟล์ที่ส่งมอบ

- PNG พื้นโปร่ง ต้นฉบับ 1024 px ด้านยาว (ภาพเต็มตัวมนุษย์ 1024 x 1536)
- ชื่อไฟล์: `<id>_<ท่า>.png` เช่น `homura_idle.png`, `homura_happy.png`, `homura_rest.png`, `leo_ready.png`, `leo_bust.png`
- `id` ใช้ตามนี้: `homura` `mimi` `fung` `puifong` `tonkla` `leo` `seline` `orrin` `raja`
- ในแอปจะย่อเป็น WebP ตามกฎของโปรเจกต์ ต้นฉบับ PNG เก็บไว้ที่ `art/source/`
- บันทึกต่อภาพ: เครื่องมือ รุ่น วันที่ prompt ที่ใช้ และใบอนุญาต ไว้ใน `licenses/art.md` (เหมือนฟอนต์)

## 7. ภาพแทนชั่วคราวระหว่างรอ

ตามข้อ 14 ระหว่างพัฒนาใช้รูปทรงเรียบสีตามเผ่าแทน (วงกลมสีเผ่ากับอักษรชื่อย่อ) ไม่ต้องรอภาพ เมื่อภาพจริงมา ให้ตั้งชื่อไฟล์ตามข้อ 6 ตอนลงมือทำในแอป ผมจะให้โหลดภาพตามชื่อนี้ ถ้าไม่มีไฟล์ก็ใช้ภาพแทนชั่วคราว
