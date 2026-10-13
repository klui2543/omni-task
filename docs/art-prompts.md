# Omni Quest: ชุด prompt สำหรับสั่ง ChatGPT วาดภาพ

ใช้ทีละขั้นใน **แชทเดียวกันตลอด** เพื่อให้ ChatGPT จำลายเส้นได้ ได้ภาพแล้วดาวน์โหลดตั้งชื่อไฟล์ตามที่กำหนด แล้วส่งทั้งหมดให้ Claude ใส่ลงแอป

## กติกาที่ใช้กับทุกภาพ

- ภาพละ 1 ตัว พื้นหลังโปร่งใส (ยกเว้นฉากหลังโซน)
- ไม่มีตัวหนังสือในภาพ
- ถ้าภาพไหนลายเส้นเพี้ยนจากภาพอ้างอิง ให้สั่งใหม่ว่า "Redo, closer to the style reference"
- ส่งเป็น PNG ก็ได้ Claude จะแปลงเป็น WebP และย่อขนาดเอง

## ขั้นที่ 1: วางสไตล์ (ส่งก่อนทุกอย่าง)

```
You are the art director of a cute fantasy idle RPG called "Omni Quest".
Style for every image in this chat:
- Chibi proportions, head about 1/2 of body height, big expressive eyes
- Clean dark-brown outlines (not black), soft cel shading with one shadow tone, small rim light
- Warm, gentle colors, slightly pastel, cozy and cute but still cool
- 3/4 front view, full body, centered, standing on nothing
- Transparent background, no text, no logos, no frame
- Square 1024x1024, the subject fills about 80% of the height
First, draw a style reference sheet: one small round baby fire dragon mascot, orange-red (#E8603C), smiling.
Every later image must match this sheet exactly in line, shading and proportions.
```

ได้แล้วตั้งชื่อ `mascot_ignis.png` (ถ้าชอบลายเส้น ใช้เป็นตัวแรกเลย ถ้าไม่ชอบ สั่งใหม่จนพอใจก่อนไปต่อ)

## ขั้นที่ 2: Mascot 5 ตัว

สั่งทีละข้อ ขึ้นต้นด้วย `Same style as the reference sheet.`

| ไฟล์ | prompt |
|---|---|
| `mascot_ignis.png` | (ได้จากขั้นที่ 1) |
| `mascot_luna.png` | A small round owl made of night sky with tiny stars on its feathers, wearing round glasses, violet (#7C6CF0), wise and sleepy smile |
| `mascot_zephyr.png` | A small fox whose tail is a fluffy white cloud, teal (#3FB8AF), playful, mid-hop |
| `mascot_aqua.png` | A tiny baby whale floating in the air with a few bubbles, blue (#3D8BFD), happy |
| `mascot_sylva.png` | A small fawn whose antlers are young tree branches with leaves, green (#5DAE4B), gentle |

## ขั้นที่ 3: ตัวละครในเรื่อง 4 ตัว

| ไฟล์ | prompt |
|---|---|
| `npc_orrin.png` | Master Orrin, old guild master and retired adventurer, kind grey beard, warm robe, leaning on a wooden cane because of an injured leg, friendly laugh |
| `npc_leo.png` | Leo, teen swordsman of the fire tribe, spiky red hair, confident grin, simple light armor with orange accents, sword on shoulder |
| `npc_celine.png` | Celine, young mage of the moon tribe, long silver hair, calm face, holding a thick blue notebook and a pen, violet robe |
| `boss_king.png` | The King of Later, a tall figure made of grey mist wearing a golden crown, soft sleepy smile, gentle but eerie, hands open as if inviting you to rest |

## ขั้นที่ 4: ตัวผู้เล่น 10 แบบ (เผ่าละ 2 แบบ)

prompt: `Same style. Young adventurer, <แบบ>, outfit in <สี> with <ธาตุ> details, holding a simple wooden training branch, ready pose.`

| ไฟล์ | แบบ | สี | ธาตุ |
|---|---|---|---|
| `hero_ignis_a.png` / `hero_ignis_b.png` | short hair boy / ponytail girl | orange-red | fire |
| `hero_luna_a.png` / `hero_luna_b.png` | short hair boy / long hair girl | violet | starlight |
| `hero_zephyr_a.png` / `hero_zephyr_b.png` | messy hair boy / twin-tail girl | teal | wind |
| `hero_aqua_a.png` / `hero_aqua_b.png` | wavy hair boy / bob hair girl | blue | water |
| `hero_sylva_a.png` / `hero_sylva_b.png` | short hair boy / braided hair girl | green | leaf |

## ขั้นที่ 5: มอนสเตอร์ 14 ตัว

prompt: `Same style. A cute but slightly mischievous monster: <คำอธิบาย>.`

| ไฟล์ | ชื่อในเกม | คำอธิบาย |
|---|---|---|
| `foe_mist_1.png` | สไลม์งานค้าง | a grey-green slime with tiny unfinished to-do notes stuck inside it |
| `foe_mist_2.png` | ภูตผัดวัน | a small floating mist ghost yawning |
| `foe_mist_3.png` | เห็ดง่วงนอน | a sleepy mushroom wearing a nightcap |
| `foe_cave_1.png` | ค้างคาวแจ้งเตือน | a bat with a red notification dot on its head, shouting |
| `foe_cave_2.png` | ก้อนหินลืมตอบ | a stone golem with a speech bubble that is empty |
| `foe_cave_3.png` | งูอีเมล | a snake whose body is made of envelopes |
| `foe_tower_1.png` | กระดาษบินได้ | a sheet of paper with paper wings and angry eyes |
| `foe_tower_2.png` | ตราประทับยักษ์ | a giant rubber stamp with little legs |
| `foe_tower_3.png` | แฟ้มหลงทาง | a lost office folder with confused eyes |
| `foe_castle_1.png` | อัศวินพรุ่งนี้ | a knight in dusky armor holding a calendar shield |
| `foe_castle_2.png` | นาฬิกาเลื่อนนัด | an alarm clock creature with a snooze button nose |
| `foe_castle_3.png` | เงาเดดไลน์ | a dark shadow creature with glowing red eyes, a little scary |
| `foe_crown_1.png` | มังกรหมอก | a dragon made of mist |
| `foe_crown_3.png` | ผู้เฝ้าราก | a guardian made of tree roots |

(ราชาผัดวันในโซนยอดยกดราซิลใช้ `boss_king.png` จากขั้นที่ 3)

## ขั้นที่ 6: ฉากหลังโซน 5 ภาพ

prompt: `Same style, but a wide 16:9 landscape background (1536x864), no characters, soft and not busy so characters stay readable: <ฉาก>.`

| ไฟล์ | ฉาก |
|---|---|
| `bg_mist.png` | a misty forest under a giant tree, pale morning light |
| `bg_cave.png` | a cave with glowing crystals and many small blinking lights |
| `bg_tower.png` | inside a tall tower made of stacked paper, spiral stairs |
| `bg_castle.png` | a dusky castle hall full of clocks |
| `bg_crown.png` | the top of a giant world tree above the clouds, golden sunrise |

## ขั้นที่ 7: ไอคอนไอเทม 19 ชิ้น

prompt: `Same style. A single game item icon, front view, centered, transparent background: <ไอเทม>.`

| ไฟล์ | ไอเทม |
|---|---|
| `icon_w-stick.png` | a wooden training branch |
| `icon_w-knife.png` | a traveler's short knife |
| `icon_w-sword.png` | an iron guild sword |
| `icon_w-rod.png` | a starlight magic rod |
| `icon_w-bow.png` | a bow made of cloud and wood |
| `icon_w-mace.png` | a healing water mace |
| `icon_w-axe.png` | an ember axe with small flames |
| `icon_w-staff.png` | a staff made of tree roots |
| `icon_w-dawn.png` | a golden dawn sword, glowing, legendary |
| `icon_a-shirt.png` | a simple cotton shirt |
| `icon_a-leather.png` | guild leather armor |
| `icon_a-robe.png` | a violet moon robe |
| `icon_a-mail.png` | an ember chainmail |
| `icon_a-mist.png` | a cloak of thinning mist, legendary |
| `icon_c-badge.png` | a sunrise guild badge |
| `icon_c-clover.png` | a four-leaf clover pendant |
| `icon_c-hourglass.png` | a small focus hourglass |
| `icon_c-ring.png` | a wanderer's silver ring |
| `icon_c-leaf.png` | a glowing golden leaf, legendary |

## รวมทั้งหมด

58 ภาพ (Mascot 5, ตัวละคร 4, ผู้เล่น 10, มอนสเตอร์ 14, ฉาก 5, ไอคอน 19) ไม่จำเป็นต้องครบในครั้งเดียว ส่งมาเป็นชุดได้ Claude จะใส่ภาพที่มี และภาพที่ยังไม่มีจะใช้ของชั่วคราวไปก่อน
