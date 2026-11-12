#!/usr/bin/env node
// ตัวเตือนประตู /work และตัวแนะนำสกิล (Klui อนุมัติ 11 พ.ย. 2569 "install 3 repos" และเลือก 12 พ.ย. "ทาง 2 + ดักคำ")
// UserPromptSubmit ทำ 2 อย่าง ข้อความที่พิมพ์ออกไปถึง AI เท่านั้น Klui ไม่เห็น
// 1. ข้อความแรกของห้องที่ไม่ได้ขึ้นต้นด้วย / ให้ AI เรียกสกิล work แล้วบอกทางในบรรทัดแรก (ครั้งเดียวต่อห้อง)
// 2. ทุกข้อความที่มีคำในตาราง HINTS ให้ AI พิจารณาขึ้นบรรทัด "💡 ถัดไปลอง: /สกิล" (สกิลละครั้งต่อห้อง ข้อความละไม่เกิน 1 สกิล)
// มี 3 สำเนาเหมือนกันใน siriraj-rx-shift, omni-task, pd_notify (.claude/hooks/work-door.cjs) แก้ต้องแก้ทั้ง 3
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');

// เรียงตามลำดับความสำคัญ ข้อความหนึ่งเจอหลายหมวด ใช้หมวดแรกที่ยังไม่เคยแนะนำในห้องนี้
const HINTS = [
  { skill: 'diagnose', why: 'มีของพังที่อาจยังไม่รู้สาเหตุ ควรทำเทสที่แดงก่อนเดา',
    words: ['พัง', 'บั๊ก', 'ไม่ทำงาน', 'ไม่ขึ้น', 'ผิดปกติ', 'error', 'bug', 'broken', 'not working', 'crash'] },
  { skill: 'grill-me', why: 'ไอเดียหรือแผนยังไม่ชัด ควรซักทีละข้อก่อนลงมือ',
    words: ['ไอเดีย', 'ไม่แน่ใจ', 'ยังไม่ชัด', 'ยังคิดไม่ออก', 'idea', 'not sure', 'brainstorm'] },
  { skill: 'spec-review', why: 'จะรับงาน ควรให้ผู้ตรวจเทียบกับคำสั่งก่อน',
    words: ['เสร็จยัง', 'เสร็จหรือยัง', 'ตรงที่สั่ง', 'ตรวจงาน', 'ตรวจให้', 'is it done', 'review'] },
  { skill: 'retro', why: 'ห้องนี้สะดุด ควรทบทวนว่าควรซ่อมกติกาตรงไหน',
    words: ['หลงทาง', 'ยากกว่าที่ควร', 'วนอยู่', 'แก้หลายรอบแล้ว', 'lost in', 'going in circles'] },
  { skill: 'save', why: 'กำลังจะพักหรือปิดห้อง ควรบันทึกสถานะลง Claude log',
    words: ['พอแค่นี้', 'พักก่อน', 'ปิดห้อง', 'เดี๋ยวมาต่อ', 'ไว้ต่อพรุ่งนี้', "that's all", 'stop here'] },
];

function readLines(file) {
  try { return fs.readFileSync(file, 'utf8').split('\n').filter(Boolean); } catch (e) { return []; }
}

function pickHint(prompt, used) {
  const text = prompt.toLowerCase();
  for (const h of HINTS) {
    if (used.includes(h.skill)) continue;
    const word = h.words.find(w => text.includes(w.toLowerCase()));
    if (word) return { skill: h.skill, why: h.why, word };
  }
  return null;
}

let raw = '';
process.stdin.on('data', d => { raw += d; });
process.stdin.on('end', () => {
  let input;
  try { input = JSON.parse(raw || '{}'); } catch (e) {
    process.stderr.write('work-door: อ่านข้อมูลเข้าไม่ได้ จึงไม่เตือนรอบนี้ (' + e.message + ')\n');
    return;
  }
  const prompt = String(input.prompt || '').trim();
  const sid = String(input.session_id || '').replace(/[^A-Za-z0-9_-]/g, '_');
  const out = [];

  // 1. ประตู /work
  let doorDue = true;
  if (!sid) {
    // ไม่มีรหัสห้อง: จำไม่ได้ว่าเตือนแล้ว จึงเตือนทุกข้อความ ดีกว่าเงียบถาวร
    process.stderr.write('work-door: ไม่มีรหัสห้อง จะเตือนทุกข้อความ\n');
  } else {
    const marker = path.join(os.tmpdir(), 'klui-work-door-' + sid);
    if (fs.existsSync(marker)) doorDue = false;    // เตือนแล้ว หรือเปิดประตูแล้ว
    else {
      try { fs.writeFileSync(marker, new Date().toISOString()); } catch (e) {
        process.stderr.write('work-door: เขียนตัวจำไม่ได้ อาจเตือนซ้ำ (' + e.message + ')\n');
      }
    }
  }
  if (prompt.startsWith('/')) return;              // Klui เรียกคำสั่ง / เอง ไม่ต้องเตือนและไม่ต้องแนะนำ
  if (doorDue) {
    out.push(
      'ตัวเตือนประตู /work: ข้อความแรกของห้องนี้ Klui ไม่ได้เปิดด้วย /work ' +
      'ก่อนทำอะไร ให้เรียกสกิล work ด้วยเครื่องมือ Skill (ชื่ออาจเป็น anthropic-skills:work) ' +
      'แล้วขึ้นบรรทัด "ทาง: ... เพราะ ... | ส่งต่อ: ... | AI ที่แนะนำ: ..." ก่อน ' +
      'ถ้าห้องนี้ไม่มีสกิล work ให้บอก Klui ตรง ๆ ว่าไม่มี'
    );
  }

  // 2. แนะนำสกิล (ข้อความแรกที่มีบรรทัดทางอยู่แล้ว ไม่ต้องแนะนำซ้ำ)
  if (!doorDue) {
    const hintFile = sid ? path.join(os.tmpdir(), 'klui-skill-hint-' + sid) : '';
    const used = hintFile ? readLines(hintFile) : [];
    const hint = pickHint(prompt, used);
    if (hint) {
      if (hintFile) {
        try { fs.appendFileSync(hintFile, hint.skill + '\n'); } catch (e) {
          process.stderr.write('work-door: เขียนตัวจำสกิลไม่ได้ อาจแนะนำซ้ำ (' + e.message + ')\n');
        }
      }
      out.push(
        'ตัวแนะนำสกิล: ข้อความของ Klui มีคำว่า "' + hint.word + '" อาจเข้ากับสกิล ' + hint.skill +
        ' (' + hint.why + ') ถ้าเข้ากับงานจริงและห้องนี้มีสกิลนั้น ' +
        'ให้ขึ้นบรรทัดเดียวท้ายคำตอบว่า "💡 ถัดไปลอง: /' + hint.skill + ' เพราะ <เหตุ 1 ประโยค>" ' +
        'ถ้าไม่เข้า หรือกำลังใช้สกิลนั้นอยู่แล้ว ไม่ต้องใส่'
      );
    }
  }

  if (out.length) process.stdout.write(out.join('\n') + '\n');
});
