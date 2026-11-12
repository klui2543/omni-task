#!/usr/bin/env node
// ตัวเตือนประตู /work และตัวแนะนำสกิล (Klui อนุมัติ 11 พ.ย. 2569 "install 3 repos" และเลือก 12 พ.ย. "ทาง 2 + ดักคำ")
// UserPromptSubmit ทำ 2 อย่าง ข้อความที่พิมพ์ออกไปถึง AI เท่านั้น Klui ไม่เห็น
// 1. ข้อความแรกของห้องที่ไม่ได้ขึ้นต้นด้วย / ให้ AI เรียกสกิล work แล้วบอกทางในบรรทัดแรก (ครั้งเดียวต่อห้อง)
// 2. ตั้งแต่ข้อความที่ 2 ของห้อง ข้อความที่มีคำในตาราง HINTS ให้ AI พิจารณาขึ้นบรรทัด "💡 ถัดไปลอง: /สกิล"
//    (สกิลละครั้งต่อห้อง ข้อความละไม่เกิน 1 สกิล เฉพาะสกิลที่ห้องนี้มี ห้องที่ไม่มีรหัสห้องได้แค่ข้อ 1)
// มี 3 สำเนาเหมือนกันใน siriraj-rx-shift, omni-task, pd_notify (.claude/hooks/work-door.cjs) แก้ต้องแก้ทั้ง 3
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');

// เรียงตามลำดับความสำคัญ ข้อความหนึ่งเจอหลายหมวด ใช้หมวดแรกที่ยังไม่เคยแนะนำในห้องนี้และห้องนี้มีสกิลนั้น
// คำไทยจับแบบมีอยู่ในข้อความ จึงเลือกคำยาวที่ไม่ซ้อนกับคำประจำวัน (เช่น "พัง" เดี่ยวติด "พังงา", "แผน" เดี่ยวติด "แผนก")
// คำอังกฤษจับทั้งคำ ("bug" ไม่ติด "debug")
const HINTS = [
  { skill: 'grill-me', why: 'ไอเดียหรือแผนยังไม่ชัด ควรซักทีละข้อก่อนลงมือ',
    words: ['ไอเดีย', 'วางแผน', 'แผนใหม่', 'แผนนี้', 'ไม่แน่ใจ', 'ยังไม่ชัด', 'ยังคิดไม่ออก', 'idea', 'not sure', 'brainstorm'] },
  { skill: 'diagnose', why: 'มีของพังที่อาจยังไม่รู้สาเหตุ ควรทำเทสที่แดงก่อนเดา',
    words: ['พังอีก', 'พังแล้ว', 'ยังพัง', 'พังอยู่', 'บั๊ก', 'ใช้ไม่ได้', 'ผิดปกติ', 'error', 'bug', 'broken', 'not working', 'crash'] },
  { skill: 'spec-review', why: 'จะรับงาน ควรให้ผู้ตรวจเทียบกับคำสั่งก่อน',
    words: ['เสร็จยัง', 'เสร็จหรือยัง', 'ตรงที่สั่ง', 'ช่วยตรวจงาน', 'is it done'] },
  { skill: 'retro', why: 'ห้องนี้สะดุด ควรทบทวนว่าควรซ่อมกติกาตรงไหน',
    words: ['หลงทาง', 'ยากกว่าที่ควร', 'วนไปวนมา', 'แก้หลายรอบแล้ว', 'lost in', 'going in circles'] },
  { skill: 'save', why: 'กำลังจะพักหรือปิดห้อง ควรบันทึกสถานะลง Claude log',
    words: ['พอแค่นี้', 'เดี๋ยวมาต่อ', 'ไว้ต่อพรุ่งนี้', 'ปิดห้องนี้', "that's all", 'that\u2019s all', 'stop here'] },
];

function readLines(file) {
  try { return fs.readFileSync(file, 'utf8').split('\n').filter(Boolean); } catch (e) { return []; }
}

function hasWord(text, w) {
  const lw = w.toLowerCase();
  if (/^[\x00-\x7f]+$/.test(lw) && /^[a-z]/.test(lw)) {
    const esc = lw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp('\\b' + esc + '\\b').test(text);
  }
  return text.includes(lw);
}

// ห้องนี้มีสกิลหรือคำสั่งนี้ไหม: ดูใน repo และในเครื่อง (สกิลระดับบัญชีซิงก์ลง ~/.claude/skills/synced/<บัญชี>/)
function skillAvailable(skill) {
  const home = path.join(os.homedir(), '.claude');
  const proj = path.join(process.env.CLAUDE_PROJECT_DIR || process.cwd(), '.claude');
  const direct = [
    path.join(proj, 'skills', skill), path.join(proj, 'commands', skill + '.md'),
    path.join(home, 'skills', skill), path.join(home, 'commands', skill + '.md'),
  ];
  if (direct.some(p => fs.existsSync(p))) return true;
  const synced = path.join(home, 'skills', 'synced');
  try {
    return fs.readdirSync(synced).some(d => fs.existsSync(path.join(synced, d, skill)));
  } catch (e) { return false; }
}

function pickHint(prompt, used, available) {
  const text = prompt.toLowerCase();
  for (const h of HINTS) {
    if (used.includes(h.skill)) continue;
    const word = h.words.find(w => hasWord(text, w));
    if (!word) continue;
    if (!available(h.skill)) {
      process.stderr.write('work-door: ห้องนี้ไม่มีสกิล ' + h.skill + ' จึงไม่แนะนำ\n');
      continue;
    }
    return { skill: h.skill, why: h.why, word };
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
  if (!input || typeof input !== 'object') input = {};
  const prompt = String(input.prompt || '').trim();
  // คำสั่ง / ของ Klui เช่น "/diagnose ..." (ไม่นับ path อย่าง /home/...)
  const cmd = /^\/([A-Za-z0-9_:-]+)(\s|$)/.exec(prompt);
  const cmdSkill = cmd ? cmd[1].split(':').pop().toLowerCase() : '';
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
  const hintFile = sid ? path.join(os.tmpdir(), 'klui-skill-hint-' + sid) : '';
  if (cmd) {
    // Klui เรียกคำสั่งเอง: ไม่เตือนและไม่แนะนำ และจดไว้ว่าสกิลนี้ใช้แล้ว จะได้ไม่แนะนำซ้ำ
    if (hintFile) { try { fs.appendFileSync(hintFile, cmdSkill + '\n'); } catch (e) { process.stderr.write('work-door: จดคำสั่งไม่ได้ (' + e.message + ')\n'); } }
    return;
  }
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
    const used = hintFile ? readLines(hintFile) : [];
    const hint = pickHint(prompt, used, skillAvailable);
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
