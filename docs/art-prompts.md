# Omni Quest: ชุด prompt สำหรับสั่ง ChatGPT วาดภาพ (ฉบับละเอียด)

## วิธีใช้

1. เปิดแชทใหม่ใน ChatGPT แล้ว**ใช้แชทเดียวกันตลอดทั้ง 67 ภาพ**
2. ส่ง **prompt 0 (Style Bible)** ก่อนทุกอย่าง แล้วแก้จนพอใจลายเส้น
3. สั่งทีละ prompt ตามลำดับ คัดลอกทั้งกล่องได้เลย ทุก prompt มีบรรทัดสไตล์อยู่ในตัวแล้ว
4. ดาวน์โหลดแล้วตั้งชื่อไฟล์ตามหัวข้อ (เช่น `mascot_luna.png`)
5. ภาพไหนเพี้ยนให้ส่ง **prompt แก้ (ท้ายเอกสาร)**
6. ส่งไฟล์ให้ Claude เป็นชุดได้ ไม่ต้องครบทีเดียว

**กติกาที่ใช้กับทุกภาพ:**
- ภาพละ 1 ตัว พื้นหลังโปร่งใส (ยกเว้นฉากหลังโซน)
- ไม่มีตัวหนังสือในภาพ
- ส่งเป็น PNG ได้เลย Claude จะแปลงเป็น WebP และย่อขนาดเอง

---

## 0. Style Bible (ส่งเป็นอันแรก)

```
You are the lead artist of a cute fantasy idle RPG called "Omni Quest". The world is a giant world tree called Ygdrasil, slowly covered by a grey "Mist of Later" that turns unfinished things into monsters. Finished work becomes golden "dawnlight" that heals the tree.

Lock this art style for EVERY image in this chat:
- Proportions: chibi, head about 1/2 of total body height, big round expressive eyes with two white highlights, small nose, small mouth, rounded hands and feet.
- Line: clean, even, medium-weight DARK BROWN outlines (#3A2A24), never pure black, slightly thinner inner lines.
- Shading: soft cel shading, exactly one shadow tone per color plus a thin warm rim light on one side. No gradients on skin, no heavy texture, no painterly brush strokes.
- Color: warm and gentle, slightly pastel, saturated accents only on magic and important items. Every character carries a small touch of its tribe color.
- Lighting: soft morning light from the upper left.
- Mood: cozy, cute, but still cool and brave. Never creepy, never gory.
- Composition: single subject, full body, 3/4 front view facing slightly left, centered, the subject fills about 80% of the canvas height, nothing cropped.
- Background: fully transparent (PNG). No ground shadow, no floor, no scenery, no frame, no text, no letters, no logos, no watermark, no UI.
- Canvas: square 1024x1024.

Now draw the STYLE REFERENCE: "Homura", the fire tribe mascot. A small, very round baby dragon, body orange-red (#E8603C) with a cream belly (#FFE3C2), two tiny stubby horns, small bat-like wings that are too small to fly, a short tail with a little flame at the tip (yellow-orange #FFB347), happy open-mouth smile showing one tiny fang, rosy cheeks, sitting upright with both little arms raised as if cheering.
Every later image must match this reference exactly in outline color and weight, shading, eye style and proportions.
```

ได้แล้วตั้งชื่อ **`mascot_ignis.png`** ถ้ายังไม่ชอบลายเส้น ให้สั่งปรับตรงนี้จนพอใจก่อน เพราะทุกภาพจะอิงจากภาพนี้

---

## 1. Mascot ประจำเผ่า (อีก 4 ตัว)

### `mascot_luna.png` (มิมิ นกฮูกดาว เผ่าจันทรา)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Mimi", the moon tribe mascot. A small, round owl whose feathers look like the night sky: deep violet (#7C6CF0) fading to indigo at the back, with tiny scattered white and pale-yellow stars on the wings. Large round glasses with thin gold frames (#E8C170). Big gentle half-closed eyes, a wise and slightly sleepy smile. A small crescent-moon shaped tuft of feathers on the head. Standing on two tiny feet, one wing raised to adjust the glasses. A few tiny sparkles float around it.
```

### `mascot_zephyr.png` (ฟุ้ง จิ้งจอกหางเมฆ เผ่าวายุ)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Fung", the wind tribe mascot. A small playful fox, fur teal (#3FB8AF) with a white chest and white tips on the ears. Its tail is a big fluffy white cloud that curls upward, with two small swirl lines of wind around it. Bright mischievous eyes, open grin. Caught mid-hop with all four paws off the ground, leaning forward as if dashing.
```

### `mascot_aqua.png` (ปุยฟอง วาฬน้อยลอยฟ้า เผ่าธารา)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Puifong", the water tribe mascot. A tiny round baby whale floating in the air, body blue (#3D8BFD) with a light-blue belly (#BFE0FF) and soft lighter stripes under the chin. A small fountain of water sprays from its blowhole and forms a tiny rainbow arc. Three or four round bubbles float around it. Happy closed-eye smile, little fins spread out as if swimming through the air.
```

### `mascot_sylva.png` (ต้นกล้า กวางน้อย เผ่าพฤกษา)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Tonkla", the wood tribe mascot. A small gentle fawn, fur warm light brown with cream spots on the back, a leafy green (#5DAE4B) scarf. Instead of antlers it has two young tree branches with a few fresh green leaves and one tiny pink flower. Calm, kind eyes and a soft smile. Standing on four slim legs, head slightly tilted.
```

---

## 2. ตัวละครในเรื่อง

### `npc_orrin.png` (มาสเตอร์ออร์ริน หัวหน้ากิลด์)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Master Orrin", the old guild master of the Dawn Guild and a retired adventurer. Short and sturdy, a long fluffy grey beard, bushy grey eyebrows, warm twinkling eyes, a big friendly laugh with the mouth open. Wears a long warm brown-and-amber robe with a sunrise emblem (a half sun with rays) embroidered on the chest, a worn leather belt with a small pouch. Leans on a sturdy wooden cane because his right leg was injured long ago; the right leg has a simple wrapped bandage visible below the robe. One hand raised in a welcoming wave.
```

### `npc_leo.png` (ลีโอ นักดาบเผ่าอัคนี)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Leo", a teenage swordsman of the fire tribe. Spiky messy red hair (#D9472B) with a few lighter orange strands, amber eyes, a confident cocky grin with one eyebrow raised, a small bandage on the cheek. Light armor: a red-orange tunic, a single steel shoulder plate on the left shoulder, brown gloves and boots, an orange scarf flowing behind him. Rests a slightly too-big steel sword on his right shoulder, left hand on his hip. Small sparks of fire around the sword blade.
```

### `npc_celine.png` (เซลีน จอมเวทเผ่าจันทรา)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Celine", a young mage of the moon tribe. Long straight silver hair (#DCDDE8) with a small crescent-moon hair clip, calm violet eyes, a quiet serious face with a faint small smile. Wears a long violet robe (#7C6CF0) with silver star embroidery on the hems and a high collar. Holds a thick dark-blue notebook open in her left hand and a feather pen in her right hand, as if writing an observation. A few tiny star sparkles float near the notebook.
```

### `boss_king.png` (ราชาผัดวัน)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"The King of Later", the main villain. A tall chibi figure made entirely of soft grey-lavender mist (#B9B4C9) whose lower body fades into curling wisps instead of legs. Wears a heavy golden crown slightly tilted, and a flowing cape that dissolves into mist at the edges. His face is calm with a gentle sleepy smile and half-closed eyes, kind-looking but eerie. Both hands open and held out as if invitingly saying "rest, there is no hurry". A small pocket watch with no hands hangs from a chain at his waist. Gentle and unsettling, never scary or monstrous.
```

---

## 3. ตัวผู้เล่น: ระบบแต่งทรงผมและชุด (19 ภาพ)

ตัวผู้เล่นประกอบจากชิ้นที่ซ้อนกัน: **ตัวพื้นฐาน + ทรงผม + ชุด + อาวุธ** ในเกมเลือกทรงผม 8 แบบ, สีผม 6 สี, สีผิว 4 สี และชุด 10 ชุดได้อิสระ

**วิธีที่ใช้ได้ผลกับ ChatGPT:** อย่าสั่งวาดชิ้นลอยๆ เพราะตำแหน่งจะไม่ตรงกัน ให้**อัปโหลดภาพตัวพื้นฐานกลับเข้าไปทุกครั้ง** แล้วสั่ง "แต่งให้ตัวนี้" Claude จะเทียบกับตัวพื้นฐานเพื่อตัดเอาเฉพาะผมหรือชุดออกมา แล้วจัดตำแหน่งให้พอดีเอง

### 3.1 `hero_base.png` (ตัวพื้นฐาน ทำก่อน)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024.

A PAPER-DOLL BASE BODY for character customization. A young chibi adventurer of neutral gender, standing straight, facing the viewer in a gentle 3/4 front view, arms held slightly away from the body with open relaxed hands (so clothes and a weapon can be added later), feet slightly apart. Light neutral skin (#FFE0C7). BALD smooth round head with no hair at all, no hat. Friendly determined face: big round eyes, small smile. Wears only a plain light-grey fitted undershirt and plain light-grey shorts, barefoot. No accessories, no weapon. Centered, the whole body fills about 80% of the canvas height.
```
ภาพนี้คือแม่แบบของทุกชิ้นในข้อ 3 ถ้ายังไม่ชอบหน้าหรือท่า ให้แก้ตรงนี้จนพอใจก่อน

### 3.2 ทรงผม 8 แบบ

**วิธีสั่ง:** อัปโหลด `hero_base.png` แล้วส่ง prompt ด้านล่าง เปลี่ยนเฉพาะ `<HAIR>` ตามตาราง

```
Edit the uploaded base character. Add ONLY the hairstyle described below. Keep EVERYTHING else pixel-identical: same pose, same face, same body, same clothes, same size, same position on the canvas, same transparent background, same 1024x1024 canvas. Do not move, redraw or restyle the body.
Draw the hair in NEUTRAL LIGHT GREY (#D8D8D8) with soft grey shading and the same dark-brown outline, so the game can tint it to any hair color. No hair accessories, no ribbons, no hat.
Hairstyle: <HAIR>
```

| ไฟล์ | `<HAIR>` |
|---|---|
| `hair_1.png` | short spiky hair, energetic, a few spikes pointing up |
| `hair_2.png` | short neat hair with a side part |
| `hair_3.png` | messy windswept medium hair blowing to one side |
| `hair_4.png` | long straight hair falling to the waist, with straight bangs |
| `hair_5.png` | a high ponytail reaching the shoulders, short side bangs |
| `hair_6.png` | twin tails on both sides of the head, each reaching the shoulders |
| `hair_7.png` | a rounded bob cut at chin length |
| `hair_8.png` | a single long braid over one shoulder, soft bangs |

ทรงผมต้องเป็น**สีเทาอ่อน**เท่านั้น เพราะเกมจะลงสีผม 6 สีให้เอง ถ้า ChatGPT ลงสีผมมาเอง ใช้ prompt แก้ "ผมไม่เป็นสีเทา" ท้ายเอกสาร

### 3.3 ชุด 10 ชุด

**วิธีสั่ง:** อัปโหลด `hero_base.png` (ตัวหัวโล้น ไม่ต้องมีผม) แล้วส่ง prompt ด้านล่าง เปลี่ยนเฉพาะ `<OUTFIT>`

```
Edit the uploaded base character. Dress it in ONLY the outfit described below, including matching shoes. Keep EVERYTHING else pixel-identical: same bald head, same face, same pose, same arm and leg positions, same size, same position on the canvas, same transparent background, same 1024x1024 canvas. The outfit must follow the body exactly and must not cover the face or the top of the head (hair is added separately). No hat, no hood up, no weapon, nothing held in the hands.
Outfit: <OUTFIT>
```

ชุด 5 ชุดแรกตรงกับชุดเกราะในร้านค้า ใส่ชุดเกราะไหนตัวละครก็เปลี่ยนชุดตามนั้น อีก 5 ชุดเป็นชุดประจำเผ่า ใช้เป็นชุดแฟชั่น

| ไฟล์ | ชุด | `<OUTFIT>` |
|---|---|---|
| `outfit_a-shirt.png` | เสื้อผ้าฝ้าย (ชุดเริ่มต้น) | a simple cream cotton tunic with short sleeves and a small patch, brown shorts, simple brown sandals |
| `outfit_a-leather.png` | เกราะหนังกิลด์ | brown leather chest armor with stitched seams and two buckles over a cream shirt, a small gold sunrise emblem on the chest, brown trousers, leather boots |
| `outfit_a-robe.png` | เสื้อคลุมจันทรา | a long violet (#7C6CF0) mage robe with silver star embroidery on the hems and a high collar, the hood DOWN on the back, soft violet shoes |
| `outfit_a-mail.png` | เกราะโซ่อัคนี | a chainmail shirt with orange-red (#E8603C) trim and a small flame emblem, steel shoulder guards, dark trousers, armored boots |
| `outfit_a-mist.png` | ผ้าคลุมหมอกจาง (ตำนาน) | a legendary flowing cloak made of soft grey-lavender mist with glowing silver edges over a dark fitted outfit, the hood DOWN, mist curling at the hem, soft dark boots |
| `outfit_ignis.png` | ชุดเผ่าอัคนี | an orange-red (#E8603C) short jacket over a cream shirt, brown shorts, wrapped forearms, sturdy boots, a small ember pendant |
| `outfit_luna.png` | ชุดเผ่าจันทรา | a violet (#7C6CF0) short cape with silver star trim over a white shirt, dark trousers, soft boots, a crescent-moon brooch |
| `outfit_zephyr.png` | ชุดเผ่าวายุ | a teal (#3FB8AF) sleeveless vest over a white shirt, a long teal scarf flying to one side, light shorts, light running shoes |
| `outfit_aqua.png` | ชุดเผ่าธารา | a blue (#3D8BFD) sailor-style top with a white collar, light flowing trousers rolled at the ankles, sandals, a small shell pendant |
| `outfit_sylva.png` | ชุดเผ่าพฤกษา | a green (#5DAE4B) tunic with leaf-pattern trim, a brown belt with a small seed pouch, brown trousers, sturdy boots |

อาวุธในมือใช้ไอคอนอาวุธจากข้อ 6 ไม่ต้องวาดเพิ่ม Claude จะวางไว้ที่มือให้เอง

### ส่งให้ Claude

ส่ง `hero_base.png` มาพร้อมทุกชิ้นในข้อ 3 เสมอ เพราะต้องใช้เทียบเพื่อแยกชิ้น ถ้าชิ้นไหนตำแหน่งตัวเลื่อนมากจนแยกไม่ได้ Claude จะบอกว่าชิ้นไหนต้องสั่งใหม่

---

## 4. มอนสเตอร์ (14 ภาพ)

ทุกตัว "น่ารักแต่ซน" ไม่น่ากลัวจริง ยกเว้นเงาเดดไลน์ที่น่ากลัวได้นิดหน่อย

### โซน 1: ป่าหมอกจาง

#### `foe_mist_1.png` (สไลม์งานค้าง)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Backlog Slime", a cute but mischievous monster. A wobbly round slime, translucent grey-green (#A9BFA0), with several tiny crumpled sticky notes and paper scraps floating visibly inside its jelly body (the notes must be blank, no writing). Droopy lazy eyes and a smug little smile, a small drip running down one side.
```

#### `foe_mist_2.png` (ภูตผัดวัน)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Procrastination Wisp", a cute but mischievous monster. A small floating ghost made of soft grey mist, round head and a wispy curling tail instead of legs, two stubby arms. It is yawning with a big open mouth and one hand covering it, eyes squeezed shut, a tiny sleeping cap. A few small "z"-shaped puffs of mist (shapes only, not letters) float above it.
```

#### `foe_mist_3.png` (เห็ดง่วงนอน)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Sleepy Mushroom", a cute but mischievous monster. A chubby mushroom with a dusty-purple cap with pale spots, a cream stem body with tiny legs, wearing a long striped nightcap draped over the cap. Eyes half closed, holding a small pillow, swaying as if about to fall asleep, a little cloud of glittering sleep spores around it.
```

### โซน 2: ถ้ำแจ้งเตือน

#### `foe_cave_1.png` (ค้างคาวแจ้งเตือน)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Ping Bat", a cute but mischievous monster. A small round dark-plum bat with big ears and wide wings, a glowing red notification dot (a plain red circle, no number) on top of its head, mouth wide open shouting, small curved sound-wave lines coming from it, eager wide eyes.
```

#### `foe_cave_2.png` (ก้อนหินลืมตอบ)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Unanswered Golem", a cute but mischievous monster. A chunky little golem made of grey stone blocks with moss on the shoulders, small glowing blue eyes, a blank awkward expression. Next to its head floats an EMPTY white speech bubble with nothing inside, and it is scratching its head as if it forgot what to say.
```

#### `foe_cave_3.png` (งูอีเมล)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Inbox Serpent", a cute but mischievous monster. A coiled snake whose long body is made of a chain of sealed paper envelopes (cream and light blue, plain, no writing, small red wax seals), head is a folded envelope with two big eyes and a forked ribbon tongue, a sly grin.
```

### โซน 3: หอคอยเอกสาร

#### `foe_tower_1.png` (กระดาษบินได้)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Flying Form", a cute but mischievous monster. A single sheet of paper with blank empty lines (no writing), folded corners forming two small paper wings, grumpy angry eyes and a pouting mouth in the middle of the sheet, flapping in the air, a paper clip on its top like a hair clip.
```

#### `foe_tower_2.png` (ตราประทับยักษ์)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Giant Stamp", a cute but mischievous monster. A big rubber stamp with a polished wooden handle as the head and a red rubber base as the body, stubby little legs, two eyes on the handle with a stern bossy expression, jumping up ready to stamp down, a small puff of red ink below it (no letters in the ink).
```

#### `foe_tower_3.png` (แฟ้มหลงทาง)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Lost Folder", a cute but mischievous monster. A manila office folder standing upright on tiny legs, a few blank papers sticking out of the top like messy hair, big confused eyes looking in two directions, a question-mark-shaped curl of paper above it (shape only), holding a tiny map upside down.
```

### โซน 4: ปราสาทผัดวัน

#### `foe_castle_1.png` (อัศวินพรุ่งนี้)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Knight of Tomorrow", a cute but mischievous monster. A small knight in dusky grey-violet armor with a plume on the helmet, only two glowing yellow eyes visible through the visor. Holds a round shield shaped like a calendar page (a blank grid, no numbers) and a sword with its tip pointing lazily down, posture relaxed as if saying "maybe tomorrow".
```

#### `foe_castle_2.png` (นาฬิกาเลื่อนนัด)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Snooze Clock", a cute but mischievous monster. A round twin-bell alarm clock creature, brass colored, with a plain clock face (no numbers, only small tick marks) as its face, sleepy eyes, a big round snooze button as its nose, tiny arms stretching as if waking up, small legs, a little ringing motion around the bells.
```

#### `foe_castle_3.png` (เงาเดดไลน์)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Deadline Shade", the scariest regular monster, but still cute and not gory. A small hooded shadow creature, deep navy-black body with soft wispy edges, two glowing red eyes and a thin crescent grin, holding a large hourglass with only a little sand left. A faint red glow around it.
```

### โซน 5: ยอดยกดราซิล

#### `foe_crown_1.png` (มังกรหมอก)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Mist Dragon", a strong late-game monster. A chibi dragon whose body is made of swirling grey-lavender mist with soft glowing white eyes, long whiskers, wings that dissolve into mist at the edges, a small golden gem on its forehead. Proud and powerful pose, but still cute.
```

#### `foe_crown_3.png` (ผู้เฝ้าราก)
```
Same Omni Quest style as the reference: chibi, dark-brown outlines, soft cel shading with one shadow tone and a warm rim light, transparent background, no text, 1024x1024, full body, centered.

"Root Warden", a strong late-game monster. A sturdy guardian made of twisted tree roots and bark, glowing green eyes in a mask-like wooden face, small moss and mushrooms on its shoulders, holding a staff made of a root. Stern but noble, protecting the world tree.
```

(ราชาผัดวันในโซนนี้ใช้ภาพ `boss_king.png`)

---

## 5. ฉากหลังโซน (5 ภาพ)

ฉากหลังต้อง**เรียบ ไม่รก** เพราะมีตัวละครยืนทับ และต้องไม่มีตัวละครในภาพ

### `bg_mist.png` (ป่าหมอกจาง)
```
Same Omni Quest art style (dark-brown outlines, soft cel shading, warm slightly pastel colors), but this is a WIDE BACKGROUND, not a character.
Size 1536x864 (16:9), no characters, no creatures, no text, simple and not busy so characters placed on top stay readable. Keep the lower third fairly flat and open as a ground area for characters to stand on.

A quiet forest at the roots of a giant world tree. Huge mossy roots in the background, soft grey mist drifting between pale trees, gentle pale-gold morning light breaking through, a few faint golden leaves glowing. Calm, slightly mysterious mood.
```

### `bg_cave.png` (ถ้ำแจ้งเตือน)
```
Same Omni Quest art style (dark-brown outlines, soft cel shading, warm slightly pastel colors), but this is a WIDE BACKGROUND, not a character.
Size 1536x864 (16:9), no characters, no creatures, no text, simple and not busy so characters placed on top stay readable. Keep the lower third fairly flat and open as a ground area for characters to stand on.

Inside a cozy dark cave with purple-blue rock walls, clusters of softly glowing teal crystals, and many tiny blinking red and white dots of light scattered on the walls like distant notifications. Cool colors with warm crystal glow.
```

### `bg_tower.png` (หอคอยเอกสาร)
```
Same Omni Quest art style (dark-brown outlines, soft cel shading, warm slightly pastel colors), but this is a WIDE BACKGROUND, not a character.
Size 1536x864 (16:9), no characters, no creatures, no text, simple and not busy so characters placed on top stay readable. Keep the lower third fairly flat and open as a ground area for characters to stand on.

Inside a tall tower built from enormous stacks of blank paper and folders, a spiral staircase of paper winding upward into soft light, a few blank sheets floating in the air, warm cream and parchment colors. No writing on any paper.
```

### `bg_castle.png` (ปราสาทผัดวัน)
```
Same Omni Quest art style (dark-brown outlines, soft cel shading, warm slightly pastel colors), but this is a WIDE BACKGROUND, not a character.
Size 1536x864 (16:9), no characters, no creatures, no text, simple and not busy so characters placed on top stay readable. Keep the lower third fairly flat and open as a ground area for characters to stand on.

A dusky castle hall in grey-violet stone, tall arched windows showing a twilight sky, many clocks of different sizes on the walls (plain faces, no numbers), soft lavender mist on the floor, dim warm candle light.
```

### `bg_crown.png` (ยอดยกดราซิล)
```
Same Omni Quest art style (dark-brown outlines, soft cel shading, warm slightly pastel colors), but this is a WIDE BACKGROUND, not a character.
Size 1536x864 (16:9), no characters, no creatures, no text, simple and not busy so characters placed on top stay readable. Keep the lower third fairly flat and open as a ground area for characters to stand on.

The very top of a giant world tree high above a sea of clouds, wide branches like platforms, golden leaves, a glorious golden sunrise on the horizon, a few wisps of grey mist curling at the edges of the clouds. Hopeful, epic but gentle.
```

---

## 6. ไอคอนไอเทม (19 ภาพ)

ไอคอนต้องดูออกตอนย่อเหลือเล็กมาก (48px) จึงต้อง**รูปทรงชัด ไม่มีรายละเอียดเล็กเกินไป**

ทุกอันขึ้นต้นด้วยบรรทัดนี้:
```
Same Omni Quest style as the reference: dark-brown outlines, soft cel shading with one shadow tone and a warm rim light. A single RPG item icon, centered, slightly tilted 30 degrees, transparent background, no text, 1024x1024. Bold simple silhouette that is still readable when shrunk to 48 pixels, thicker outline than characters, no tiny details.
```
แล้วต่อด้วยคำอธิบายของแต่ละชิ้น:

| ไฟล์ | ชื่อในเกม | คำอธิบาย (ต่อท้ายบรรทัดด้านบน) |
|---|---|---|
| `icon_w-stick.png` | กิ่งไม้ฝึกหัด | A simple wooden training branch used as a practice sword, light brown with one small leaf, a cloth grip wrapped at the bottom. |
| `icon_w-knife.png` | มีดสั้นนักเดินทาง | A traveler's short knife with a steel blade, a brown leather-wrapped handle and a small brass guard. |
| `icon_w-sword.png` | ดาบเหล็กกิลด์ | A sturdy iron sword with a straight blade, a simple crossguard and a small sunrise emblem on the pommel. |
| `icon_w-rod.png` | คทาแสงดาว | A slim magic rod of dark wood topped with a glowing violet (#7C6CF0) star crystal, tiny sparkles. |
| `icon_w-bow.png` | ธนูหางเมฆ | A light wooden bow whose two tips curl into small white clouds, teal (#3FB8AF) string and grip. |
| `icon_w-mace.png` | คทาธารารักษา | A short mace whose head is a blue (#3D8BFD) water orb with a swirling current inside, silver handle. |
| `icon_w-axe.png` | ขวานเปลวเพลิง | A hand axe with a dark iron head glowing orange-red (#E8603C) at the edge, small flames rising from it. |
| `icon_w-staff.png` | ไม้เท้ารากยกดราซิล | A tall staff made of twisted tree roots, green (#5DAE4B) leaves and a small glowing seed at the top. |
| `icon_w-dawn.png` | ดาบรุ่งอรุณ (ตำนาน) | A legendary golden sword glowing with warm dawn light, a rising-sun shaped crossguard, radiant light rays behind it. |
| `icon_a-shirt.png` | เสื้อผ้าฝ้าย | A simple folded cream cotton shirt with a small patch. |
| `icon_a-leather.png` | เกราะหนังกิลด์ | A brown leather chest armor with stitched seams, two buckles and a small sunrise emblem. |
| `icon_a-robe.png` | เสื้อคลุมจันทรา | A violet (#7C6CF0) mage robe with silver star embroidery and a high collar, shown flat. |
| `icon_a-mail.png` | เกราะโซ่อัคนี | A chainmail shirt with orange-red (#E8603C) trim and a small flame emblem on the chest. |
| `icon_a-mist.png` | ผ้าคลุมหมอกจาง (ตำนาน) | A legendary hooded cloak made of soft grey-lavender mist with glowing silver edges, partly translucent. |
| `icon_c-badge.png` | เข็มกลัดกิลด์รุ่งอรุณ | A round gold guild badge with a rising sun and rays, a small ribbon below it. |
| `icon_c-clover.png` | จี้ใบโคลเวอร์ | A four-leaf clover pendant in green glass on a thin gold chain. |
| `icon_c-hourglass.png` | นาฬิกาทรายนักโฟกัส | A small brass hourglass with glowing golden sand, a tiny chain loop on top. |
| `icon_c-ring.png` | แหวนนักเดินทาง | A silver ring with a small teal (#3FB8AF) gem and a tiny engraved wind swirl (no letters). |
| `icon_c-leaf.png` | ใบไม้แห่งยกดราซิล (ตำนาน) | A legendary single golden leaf glowing with warm light, small sparkles around it. |

---

## prompt แก้ (ใช้เมื่อภาพเพี้ยน)

**ลายเส้นไม่ตรงกับภาพอ้างอิง:**
```
Redo this image. Match the style reference (Homura, the first image) exactly: same dark-brown outline color and weight, same single-tone cel shading, same eye style, same chibi head-to-body ratio. Keep the transparent background and no text.
```

**พื้นหลังไม่โปร่งใส หรือมีพื้นหรือเงาใต้เท้า:**
```
Redo with a fully transparent background: no floor, no ground shadow, no scenery, nothing behind the subject.
```

**มีตัวหนังสือหรือตัวเลขในภาพ:**
```
Redo without any letters, numbers or writing anywhere in the image. Any paper, sign or clock must be blank.
```

**ผมไม่เป็นสีเทา (ข้อ 3.2):**
```
Redo the hair in neutral light grey (#D8D8D8) with grey shading only, no other hair color. Keep everything else identical to the uploaded base.
```

**ตัวเลื่อนหรือท่าเปลี่ยนตอนแต่งผมหรือชุด (ข้อ 3):**
```
Redo. The body moved or changed. Keep the base character exactly where it is in the uploaded image, same size, same pose, same face; only add the requested piece.
```

**ตัวโดนครอบตัด หรือไม่อยู่กลางภาพ:**
```
Redo with the whole body visible, centered, filling about 80% of the canvas height, with empty space on every side.
```

---

## สรุปไฟล์ที่ต้องได้ (67 ภาพ)

| หมวด | จำนวน | ไฟล์ |
|---|---|---|
| Mascot | 5 | `mascot_ignis`, `mascot_luna`, `mascot_zephyr`, `mascot_aqua`, `mascot_sylva` |
| ตัวละครในเรื่อง | 4 | `npc_orrin`, `npc_leo`, `npc_celine`, `boss_king` |
| ตัวผู้เล่น | 19 | `hero_base`, `hair_1` ถึง `hair_8`, `outfit_a-shirt`, `outfit_a-leather`, `outfit_a-robe`, `outfit_a-mail`, `outfit_a-mist`, `outfit_ignis`, `outfit_luna`, `outfit_zephyr`, `outfit_aqua`, `outfit_sylva` |
| มอนสเตอร์ | 14 | `foe_mist_1` ถึง `3`, `foe_cave_1` ถึง `3`, `foe_tower_1` ถึง `3`, `foe_castle_1` ถึง `3`, `foe_crown_1`, `foe_crown_3` |
| ฉากหลัง | 5 | `bg_mist`, `bg_cave`, `bg_tower`, `bg_castle`, `bg_crown` |
| ไอคอน | 19 | `icon_<รหัสไอเทม>` ตามตารางข้อ 6 |
