# Omni Quest: บรีฟงานภาพตัวละคร

เอกสารนี้ใช้ให้ AI สร้างภาพหรือให้นักวาดทำตามได้เลย อิงข้อ 3, 5, 9, 10, 14 ของ `rpg-design.md` และตารางตัวละครใน `rpg-story.md` ภาพที่ได้มาใช้ในแอปทั้ง Android, เว็บ และปลั๊กอิน Obsidian

## 1. ภาพอ้างอิงจากเจ้าของ

เจ้าของส่งภาพจากเกมเหล่านี้มาเป็นแนวทาง (ไม่เก็บภาพไว้ใน repo เพราะเป็นภาพของเกมอื่น ใช้แค่เป็นแนว ไม่ลอกตัวละคร ชื่อ ชุด หรือฉาก)

| เกม | เอาอะไรมาใช้ |
|---|---|
| Rune Factory 3 (DS) และ Rune Factory 4 Special | ภาพตัวละครครึ่งตัวตอนคุย กล่องข้อความแบบกระดาษ ป้ายชื่อ ไอคอนอารมณ์ |
| Rune Factory (DS) ฉากสนาม | สไปรต์ตัวเล็กเดินในฉาก แถบ HP ใต้ตัว แถบไอคอนสกิลกับรูปหน้าเพื่อนด้านบน |
| Katekyo Hitman Reborn! DS Flame Rumble XX | สไปรต์พิกเซลตอนต่อสู้ เอฟเฟกต์เปลวไฟขนาดใหญ่ แถบพลังเอียง เมนูสกิลจอล่างที่มีรูปหน้า |

สรุปคือ **งานภาพมีสองชั้นที่ไม่เหมือนกัน** และต้องทำแยกกัน: ภาพวาดอนิเมะตอนคุย (ข้อ 3) กับสไปรต์พิกเซลตอนออกสำรวจและต่อสู้ (ข้อ 4) ในเกมต้นแบบตัวละครตัวเดียวกันก็มีสัดส่วนสองแบบนี้เป็นปกติ

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

สีหลักใช้กับชุด ป้ายชื่อ และเปลวไฟของตัวละครเผ่านั้น ไม่ต้องให้ทั้งตัวเป็นสีเดียว (ในภาพอ้างอิง ชุดมีหลายสี แต่มีสีเด่นสีเดียว)

## 3. ชั้นที่ 1: ภาพตอนคุย (แนว Rune Factory 4)

**ลักษณะ** (ดูจากภาพอ้างอิง)

- ภาพครึ่งตัวถึงเอว หันเฉียงเล็กน้อย ตัวละครสองตัวยืนซ้ายขวาของจอ
- เส้นบางสีน้ำตาลเข้ม ลงสี cel สองระดับ มีไฮไลต์นุ่มที่ผม แก้มแดงอ่อน
- ตาโตมีประกายหลายจุด สีชุดสดแต่นุ่ม แนวพาสเทล
- ชุดแฟนตาซีมีรายละเอียด: ขอบผ้า เข็มกลัด ริบบิ้น ขนนก หมวก ผ้าคาดหัว
- สัดส่วนอนิเมะ หัวไม่โตเกินจริง

**Prompt แม่แบบ** (ภาษาอังกฤษ ไม่ใส่ชื่อเกมใน prompt เพื่อไม่ให้ได้ภาพที่ลอกเกมต้นแบบ)

```
Anime fantasy RPG dialogue portrait, waist-up, slight three-quarter view.
[CHARACTER DESCRIPTION from section 6].
Clean thin dark-brown lineart, soft two-tone cel shading, gentle glossy highlights on the hair,
large expressive eyes with several catchlights, light blush on the cheeks,
pastel but saturated fantasy costume with ornate details (trim, brooches, ribbons, feathers),
main costume color [MAIN HEX], expression: [EXPRESSION].
Plain white background, no text, no frame, no watermark, no logo.
```

**Negative prompt:** `text, watermark, logo, signature, photorealistic, 3d render, thick outline, chibi, extra fingers, cropped head, modern clothes, firearms`

**ชุดสีหน้าต่อตัว** (หน้าเดียวกัน ชุดเดียวกัน ท่าเดียวกัน เปลี่ยนแค่หน้า): ปกติ, ยิ้ม, หัวเราะตาปิด, โกรธ, เศร้า, ตกใจ, เขิน

ไอคอนอารมณ์ (เครื่องหมายโกรธ หยดเหงื่อ โน้ตเพลง ประกาย) ไม่ต้องวาดลงในภาพ แอปวางทับให้เอง

**ขนาด:** PNG พื้นโปร่ง สูง 1536 px กว้าง 1024 px ตัวละครยืนชิดขอบล่าง ทุกตัวขนาดหัวใกล้กัน

Mascot สัตว์ก็มีภาพตอนคุยด้วย แต่เป็นตัว SD เต็มตัว ลายเส้นและการลงสีเดียวกับมนุษย์

## 4. ชั้นที่ 2: สไปรต์ตอนออกสำรวจและต่อสู้ (แนว Flame Rumble กับ Rune Factory DS)

**ลักษณะ** (ดูจากภาพอ้างอิง)

- พิกเซลอาร์ตตัวเล็ก มนุษย์สูงประมาณ 4 ถึง 5 หัว (ไม่ใช่ 7 หัวเหมือนภาพตอนคุย) Mascot แบบ SD 2 หัว
- เส้นขอบ 1 พิกเซลสีเข้ม แต่ละวัสดุมี 3 ถึง 4 ระดับสี แสงมาจากซ้ายบน
- ทรงผมกับสีผมต้องจำได้แม้ตัวเล็ก (ในภาพอ้างอิงแยกตัวละครได้ด้วยผมกับสีชุด)
- ท่าต่อสู้มีพลัง ตัวเอียง ขาเปิด แขนยื่นออก

**ขนาด:** มนุษย์สูง 56 ถึง 64 px, Mascot สูง 32 ถึง 40 px บนผืนผ้าใบขนาดเท่ากันทุกเฟรม จุดยึดที่กึ่งกลางเท้า แอปขยาย 3 ถึง 4 เท่าแบบไม่เบลอ แต่ละตัวใช้สีไม่เกิน 24 สี

**ท่าที่ต้องการ** (เป็น sprite sheet แนวนอน 1 ไฟล์ต่อท่า):

| ท่า | เฟรม | ใช้ตอน |
|---|---|---|
| idle | 4 | ยืนรอ หายใจ |
| run | 6 | ออกสำรวจ |
| attack | 6 | ฟันหรือร่ายเวท |
| hit | 2 | โดนโจมตี |
| win | 4 | ภารกิจสำเร็จ |
| rest | 2 | พัก นอน (ไม่ลงโทษการพัก ข้อ 1.3) |

**เอฟเฟกต์เปลวไฟ** (หัวใจของแนว Flame Rumble ทำเป็นไฟล์แยกจากตัวละคร):

- เปลวเล็กที่หน้าผากหรือมือ วนซ้ำ 4 เฟรม
- เปลวพันอาวุธตอนโจมตี 6 เฟรม
- ท่าใหญ่: ลำเปลวไฟหรือระเบิดขนาดครึ่งจอ 8 เฟรม แกนเป็นสีขาวเหลือง ขอบเป็นสีเผ่า
- ทุกเอฟเฟกต์ทำ 5 สีตามเผ่า (เปลี่ยนแค่สี ใช้รูปเดียวกันได้)

**ทำยังไง:** พิกเซลอาร์ตที่สวยระดับภาพอ้างอิงทำด้วย AI วาดภาพทั่วไปได้ยาก (ได้ภาพไม่คม สีเยอะ เฟรมไม่ต่อกัน) เรียงตามคุณภาพ:

1. จ้างนักวาดพิกเซลอาร์ต ใช้เอกสารนี้เป็นบรีฟ (ได้ผลแน่นอนที่สุด)
2. ซื้อ asset pack พิกเซลที่แยกชิ้นส่วนได้ (ตรงกับข้อ 14) แล้วเปลี่ยนสีผมกับชุดตามตัวละคร
3. ใช้เครื่องมือ AI ที่ทำพิกเซลอาร์ตโดยเฉพาะ แล้วเก็บรายละเอียดเองทีละเฟรม

ผมทำสไปรต์ด้วยโค้ดเองแล้วไม่ผ่าน จึงไม่แนะนำทางนั้น

## 5. หน้าจอที่ภาพจะไปอยู่ (ผมทำในแอปเอง)

ส่วนนี้เป็นงาน UI ไม่ต้องวาด ผมสร้างด้วยโค้ดได้ตามภาพอ้างอิง

- **กล่องข้อความ:** กระดาษสีครีม (`#f4e9c8`) กรอบลวดลายสีเขียวน้ำทะเล (`#2f8f9d`) ป้ายชื่อเป็นริบบิ้นสีเผ่าของคนพูด มีลูกศรสามเหลี่ยมสีส้มมุมขวาล่างบอกว่ากดต่อได้
- **ภาพคุย:** ตัวละครสองตัวยืนซ้ายขวา คนที่พูดสว่าง อีกคนมืดลงเล็กน้อย ไอคอนอารมณ์ลอยข้างหัว
- **ฉากสนาม:** สไปรต์เดินบนพื้น แถบ HP ใต้ตัว แถบด้านบนมีรูปหน้าเพื่อนกับไอคอนสกิล
- **ฉากต่อสู้:** แถบพลังเอียงด้านบนพร้อมชื่อ เมนูสกิลด้านล่างเป็นปุ่มยาวมีรูปหน้า (แนวจอล่างของ Flame Rumble)

## 6. รายละเอียดตัวละคร

ใส่ข้อความในช่อง `[CHARACTER DESCRIPTION]` ใช้ข้อความเดียวกันทั้งภาพตอนคุยและบรีฟสไปรต์ จะได้หน้าตาตรงกัน

### Mascot ประจำเผ่า (SD)

| ตัวละคร | เผ่า | คำอธิบาย |
|---|---|---|
| โฮมุระ | อัคนี | `a round baby fire dragon, chubby body, small golden horns, tiny wings, cream belly, small flame at the tail tip, cheerful and eager` |
| มิมิ | จันทรา | `a small star owl wearing round golden glasses, indigo feathers with a pale starlight belly, a tiny star sparkle above the head, calm and studious` |
| ฟุ้ง | วายุ | `a small fox with a tail shaped like a fluffy cloud, mint green fur, white muzzle and cheeks, playful and quick` |
| ปุยฟอง | ธารา | `a baby sky whale floating on small clouds, light blue skin, pale belly, a tiny water spout, gentle and sleepy` |
| ต้นกล้า | พฤกษา | `a young fawn with antlers shaped like leafy branches, tan coat with cream spots, small leaves sprouting from the antlers, shy and kind` |

### NPC และตัวร้าย

| ตัวละคร | เผ่า | คำอธิบาย |
|---|---|---|
| ลีโอ | อัคนี | `a cheerful boastful young swordsman, spiky red hair with a cloth headband, confident grin, light fantasy armor over an orange tunic, a gold shoulder guard, brown boots, a long sword, a small flame on the left fist, loves to compete and to eat` |
| เซลีน | จันทรา | `a quiet observant young mage, long silver hair with a crescent hairpin, indigo and white robe with star trim, a staff topped with a crescent moon, a small notebook at the hip, calm sharp eyes, speaks little` |
| มาสเตอร์ออร์ริน | กิลด์ | `a kind elderly guild master and former adventurer, long white beard and bushy eyebrows, warm gold and brown robe with a sunrise emblem, leans on a cane because of an injured leg, hearty laughing face` |
| ราชาผัดวัน | หมอก | `a tall elegant figure made of drifting lavender fog wearing a gold crown, half-closed sleepy gentle eyes, soft persuasive smile, long flowing robe that fades into mist at the hem, never aggressive, no weapon` |

### ผู้เล่น (paper-doll)

ไม่ให้ AI วาดทั้งตัว ทำเป็นชิ้นส่วนที่ซ้อนกันได้ตามข้อ 5 ของ `rpg-design.md`: รูปร่าง, สีผิว, ทรงผม, ตา, ปาก, ชุด, อาวุธ, เครื่องประดับ, ออร่าเวทตามธาตุ ต้องมีทั้งสองชั้น (ภาพตอนคุยแบบชิ้นส่วน และสไปรต์แบบชิ้นส่วน) จึงเหมาะกับ asset pack ที่แยกชิ้นมาแล้วมากที่สุด

## 7. ขั้นตอนให้ภาพออกมาเป็นชุดเดียวกัน

1. ทำ **ตัวต้นแบบ** ก่อนตัวเดียว (แนะนำลีโอ เพราะมีเปลวไฟ ดาบ และหน้าครบ) ทั้งภาพตอนคุยหน้าปกติ 1 ภาพ และสไปรต์ท่า idle 1 ชุด ลองจนพอใจ แล้วเก็บเป็นภาพอ้างอิงหลัก
2. ตัวต่อไปทุกตัวให้ใส่ภาพอ้างอิงหลักควบคู่กับ prompt แม่แบบ
3. ภาพที่ผ่านแล้วเก็บเป็นภาพอ้างอิงของตัวนั้น ทำสีหน้าและท่าอื่นโดยอ้างอิงภาพนี้ หน้ากับชุดจะไม่เพี้ยน
4. ตรวจก่อนรับ: ลายเส้นหนาเท่ากันทุกตัว สีเด่นตรงกับตารางเผ่า ไม่มีตัวหนังสือ พื้นโปร่ง (ถ้าเครื่องมือไม่รองรับ ใช้พื้นเรียบแล้วลบพื้นทีหลัง) สไปรต์ทุกเฟรมขนาดเท่ากัน

## 8. ไฟล์ที่ส่งมอบ

- ภาพตอนคุย: `<id>_talk_<สีหน้า>.png` เช่น `leo_talk_normal.png`, `leo_talk_angry.png` (สีหน้า: `normal` `smile` `laugh` `angry` `sad` `surprised` `shy`)
- สไปรต์: `<id>_<ท่า>.png` เช่น `leo_idle.png` (sheet แนวนอน) พร้อมบอกขนาดเฟรม
- เอฟเฟกต์: `fx_<ชื่อ>_<เผ่า>.png` เช่น `fx_burst_fire.png`
- `id`: `homura` `mimi` `fung` `puifong` `tonkla` `leo` `seline` `orrin` `raja`
- ต้นฉบับ PNG เก็บที่ `art/source/` ในแอปจะแปลงภาพตอนคุยเป็น WebP ตามกฎของโปรเจกต์ (สไปรต์เก็บ PNG เพื่อให้พิกเซลคม)
- บันทึกต่อภาพ: เครื่องมือหรือผู้วาด วันที่ prompt และใบอนุญาต ไว้ใน `licenses/art.md` เหมือนฟอนต์

## 9. ภาพแทนชั่วคราวระหว่างรอ

ตามข้อ 14 ระหว่างพัฒนาใช้รูปทรงเรียบสีตามเผ่าแทน (วงกลมสีเผ่ากับอักษรชื่อย่อ) เมื่อภาพจริงมา ให้ตั้งชื่อไฟล์ตามข้อ 8 ตอนทำในแอป ผมจะให้โหลดภาพตามชื่อนี้ ถ้าไม่มีไฟล์ก็ใช้ภาพแทนชั่วคราว
