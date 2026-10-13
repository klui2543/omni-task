# Omni Quest: ให้ ChatGPT ช่วยออกแบบหน้าจอ

ใช้คู่กับภาพตัวอย่างที่ Claude ทำไว้ (canvas "Omni Quest หน้าตาเกม") ภาพตัวอย่างนั้นบอกว่า**แต่ละหน้ามีอะไรบ้าง** ส่วน ChatGPT ช่วยหา**หน้าตาที่สวยและรู้สึกเป็นเกม**

## วิธีใช้

1. เปิดแชทใหม่ใน ChatGPT แล้วใช้แชทเดียวกันตลอด
2. ส่ง prompt 1 ก่อน จะได้ภาพหน้าหลัก 3 แนว เลือกแนวที่ชอบ (หรือบอกให้ผสม)
3. ส่ง prompt 2 ถึง 4 ทีละอัน เพื่อให้ได้หน้าอื่นในแนวเดียวกัน
4. ส่ง prompt 5 ให้ ChatGPT สรุปเป็นสเปก แล้วคัดลอกสเปกนั้นกับภาพทั้งหมดมาให้ Claude
5. Claude จะแปลงเป็นโค้ด Compose ในแอปจริง

**ข้อควรรู้:** ตัวหนังสือไทยในภาพของ ChatGPT มักเพี้ยน ไม่ต้องสนใจ ให้ดูแค่การจัดวาง สี และความรู้สึก ข้อความจริงในแอปมีอยู่แล้ว

ถ้าอยากลองแนวอื่นทั้งชุด พิมพ์ต่อท้ายได้เลย เช่น "make it feel more like a cozy storybook" หรือ "more like a pixel-art JRPG menu"

---

## Prompt 1: หน้าหลัก 3 แนว

```
I am designing the screens of a small idle RPG that lives inside my personal task app on Android. Please draw phone UI mockups (portrait, 390 x 844, Android, no status bar).

THE GAME
"Omni Quest". My character grows from tasks I really finish. Finished tasks give XP, gold and stats. A guild master posts daily quests made from my real tasks and calendar. While I time a task, my party fights monsters automatically. The world is a giant world tree, Ygdrasil, covered by a grey "Mist of Later" that turns unfinished things into monsters.

HARD CONSTRAINTS
- The art will be STATIC PORTRAITS only (character, mascot, NPC, monster, each a single still image). No animated sprites, no tile maps. Motion comes only from simple UI effects: bobbing, a lunge, a shake, floating damage numbers.
- It sits inside a calm productivity app with a dark theme: background #0D0F14, cards #151821, card border #222736, text #F3F4F8, muted text #AEB3C2, accent lavender #A99CFF, success lime #B9F26B, gold #FFC266, danger #FF8A80. You may add a game-flavoured layer on top, but it must not clash with the rest of the app.
- The player picks one of five tribes; the tribe colour tints the hero frame, XP bar and active tab: fire #E8603C, starlight #7C6CF0, wind #3FB8AF, water #3D8BFD, wood #5DAE4B. Use starlight for this mockup.
- UI text is Thai (font Sarabun for body, Kanit for headings). Never use the em dash or the middle dot anywhere.
- Touch targets at least 44 px. Readable at arm's length.

THE MAIN SCREEN CONTAINS (top to bottom)
1. Top bar: back button, title "Omni Quest", gold balance pill (1,284).
2. Character card: hero portrait, name, Lv 14, tribe, job (Mage), XP bar 744/1,200 with "today +86", six stats (INT 18, AGI 11, VIT 9, STR 6, LUK 8, DEX 12).
3. Mascot speech bubble: a small star owl in glasses named Mimi giving one line of task advice.
4. A button "Adventure while you work" that starts a timed task.
5. Tabs: Quests (2), Story (1), Gear, Shop, Skills, Hero.
6. Quest list: "Today's main quest 4/4 done, claim 60", "Bounty: the task I postponed 4 times, 60", "Focus 50 minutes, 20/50", "Shift duty, claim 50".

Give me THREE clearly different visual directions for this same screen, side by side, each with a short name:
A) Clean: almost the plain app, game feel only from the portrait, the tribe colour and small touches.
B) Fantasy guild: parchment or wood-trimmed panels, ornate frames around portraits, a quest board feeling, still dark.
C) Modern gacha: glossy cards, bold rarity-style frames, strong glow on the hero, big numbers.
```

## Prompt 2: หน้าผจญภัยระหว่างจับเวลางาน

```
Use direction [A / B / C, or describe the mix you want]. Draw the "Adventure while you work" screen in the same style.

It shows while I time a real task:
1. Top: minimise button, title.
2. Timer card: the task name and a big timer 00:37:12.
3. Battle stage (about 230 px tall): zone name "Thin Mist Woods", my hero portrait and the mascot on the left, a monster portrait "Backlog slime" on the right, a floating damage number "128" over the monster, the monster HP bar below. Show in a small side strip how one attack beat looks: hero lunges right, monster flashes and shakes, number floats up and fades.
4. Three stat tiles: defeated 23, gold 146, last item drop (an item icon).
5. Bottom: a big lime button "Task done" and a quiet outline button "Stop timer (keep the loot)".
```

## Prompt 3: ฉากเนื้อเรื่อง

```
Same style. Draw a story scene screen, visual-novel style.

- A background image of the guild hall fills the top two thirds.
- The speaker is shown as a large half-body portrait standing on the dialogue box: Master Orrin, an old adventurer with a hurt leg, kind but blunt. The listener (the mascot Mimi) stands smaller on the other side and is dimmed because it is not speaking.
- Dialogue box at the bottom: a name tag "Orrin", two or three lines of Thai text, "5 of 9" and "tap to continue".
- Top: chapter label "Chapter 1: Dawn of the Guild", scene title, close button.
```

## Prompt 4: ทางเข้าเกมจากแอปปกติ

```
Same style, but this one is mostly the normal task app. Draw the app's Focus (home) screen with a small Omni Quest entry card near the top.

- Header: date and the title "Focus".
- The Omni Quest card: a small hero portrait, "Omni Quest Lv 14", a thin XP bar, and a gold pill "2 rewards". It should invite a tap but must not shout over the real work below.
- Below it: the normal app content (a row of four numbers: overdue 2, events today 3, until bed 6:40, free time 2.5 h; then a list of today's tasks).
- Bottom navigation with five text tabs: Focus, Tasks, Projects, Assistant, Settings.
```

## Prompt 5: สรุปเป็นสเปก (ส่งท้ายสุด)

```
Now write the design spec for the direction we settled on, so a developer can rebuild it in Jetpack Compose. Plain text, no images. Include:
1. Colours as hex: page, cards, borders, frames, text levels, accents, and how the tribe colour is applied.
2. Type: font, size and weight for title, heading, body, caption, big numbers.
3. Spacing and shape: screen padding, gap between cards, corner radius of cards, buttons, chips, portrait frames.
4. Each component on each screen: size, border, fill, shadow or glow, states (selected, disabled, claimable).
5. The battle effects: what moves, how far, how long, easing.
6. The size and aspect ratio each portrait image should be drawn at (hero, mascot, NPC half-body, monster, item icon, scene background), so I can order the art to fit.
Do not use the em dash or the middle dot anywhere in the spec.
```
