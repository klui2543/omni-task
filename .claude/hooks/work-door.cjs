#!/usr/bin/env node
// ตัวเตือนประตู /work (Klui อนุมัติ 11 พ.ย. 2569: "install 3 repos")
// UserPromptSubmit: ข้อความแรกของห้องที่ไม่ได้ขึ้นต้นด้วย / จะได้ข้อความแนบถึง AI ครั้งเดียวต่อห้อง
// ให้เรียกสกิล work แล้วบอกทางในบรรทัดแรก Klui ไม่เห็นข้อความนี้
// มี 3 สำเนาเหมือนกันใน siriraj-rx-shift, omni-task, pd_notify (.claude/hooks/work-door.cjs) แก้ต้องแก้ทั้ง 3
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');

let raw = '';
process.stdin.on('data', d => { raw += d; });
process.stdin.on('end', () => {
  let input;
  try { input = JSON.parse(raw || '{}'); } catch (e) {
    process.stderr.write('work-door: อ่านข้อมูลเข้าไม่ได้ จึงไม่เตือนรอบนี้ (' + e.message + ')\n');
    return;
  }
  const prompt = String(input.prompt || '').trim();
  const sid = String(input.session_id || 'no-session').replace(/[^A-Za-z0-9_-]/g, '_');
  const marker = path.join(os.tmpdir(), 'klui-work-door-' + sid);
  if (fs.existsSync(marker)) return;               // เตือนแล้ว หรือเปิดประตูแล้ว
  try { fs.writeFileSync(marker, new Date().toISOString()); } catch (e) {
    process.stderr.write('work-door: เขียนตัวจำไม่ได้ อาจเตือนซ้ำ (' + e.message + ')\n');
  }
  if (prompt.startsWith('/')) return;              // Klui เปิดด้วยคำสั่ง / เอง ไม่ต้องเตือน
  process.stdout.write(
    'ตัวเตือนประตู /work: ข้อความแรกของห้องนี้ Klui ไม่ได้เปิดด้วย /work ' +
    'ก่อนทำอะไร ให้เรียกสกิล work ด้วยเครื่องมือ Skill (ชื่ออาจเป็น anthropic-skills:work) ' +
    'แล้วขึ้นบรรทัด "ทาง: ... เพราะ ... | ส่งต่อ: ... | AI ที่แนะนำ: ..." ก่อน ' +
    'ถ้าห้องนี้ไม่มีสกิล work ให้บอก Klui ตรง ๆ ว่าไม่มี\n'
  );
});
