package app.omnitask.quest

import app.omnitask.model.tr

/**
 * The mascot's line on the character page: the same facts for every tribe, each mascot leading with what its
 * tribe cares about (`docs/rpg-design.md`, section 9). Rule-based, offline.
 */
object MascotVoice {

    data class Facts(
        val overdue: Int,
        val dueToday: Int,
        val focusMinutes: Int,
        val boss: String?,
        val claimable: Int,
        val asleep: Boolean,
    )

    fun line(mascot: Tribe, f: Facts): String {
        val name = mascot.mascot
        if (f.asleep) return tr("$name: ดึกแล้ว พักก่อนนะ เดี๋ยวพรุ่งนี้ค่อยลุยกัน", "$name: It's late. Rest now, we go again tomorrow.")
        if (f.claimable > 0) return tr("$name: มีรางวัลรออยู่ที่กิลด์ ${f.claimable} อัน ไปรับเลย!", "$name: ${f.claimable} rewards wait at the guild!")
        return when (mascot) {
            Tribe.IGNIS -> when {
                f.boss != null -> tr("$name: บอส \"${f.boss}\" ยังอยู่! เลือกก้าวแรกแล้วลุยเลย", "$name: \"${f.boss}\" still stands! Pick the first step and charge.")
                f.dueToday > 0 -> tr("$name: วันนี้มีงานรอ ${f.dueToday} อัน เริ่มอันที่ยากสุดก่อน!", "$name: ${f.dueToday} tasks today. Start with the hardest!")
                else -> tr("$name: ว่างอยู่ใช่ไหม ไปขยับร่างกายสักหน่อยดีไหม", "$name: Free now? How about moving your body a bit?")
            }
            Tribe.LUNA -> when {
                f.focusMinutes < Quests.FOCUS_MINUTES -> tr("$name: ฮู้ว จับเวลาทำงานยากสักช่วงไหม วันนี้ได้ ${f.focusMinutes} นาทีแล้ว", "$name: Hoo, time a deep-work block? ${f.focusMinutes} minutes so far.")
                f.dueToday > 0 -> tr("$name: เหลือ ${f.dueToday} อัน ลองเรียงลำดับก่อนลงมือนะ", "$name: ${f.dueToday} left. Put them in order first.")
                else -> tr("$name: วันนี้ดีมาก ลองวางแผนพรุ่งนี้ไว้สักนิดไหม", "$name: Good day. Plan a little of tomorrow?")
            }
            Tribe.ZEPHYR -> when {
                f.dueToday > 0 -> tr("$name: งานสั้นเก็บได้เลย ทีละอันเร็วๆ เหลือ ${f.dueToday} อัน", "$name: Sweep the quick ones, ${f.dueToday} left.")
                f.overdue > 0 -> tr("$name: มีงานค้าง ${f.overdue} อัน หยิบอันที่ใช้เวลาน้อยสุดก่อน", "$name: ${f.overdue} overdue. Grab the shortest first.")
                else -> tr("$name: โล่งแล้ว! ลมพัดสบายเลย", "$name: All clear! A nice breeze.")
            }
            Tribe.AQUA -> when {
                f.overdue > 2 -> tr("$name: งานค้างเยอะ ไม่เป็นไร เลือกเลื่อนบางอันไปวันที่ทำได้จริงนะ", "$name: Lots overdue, it's fine. Move some to a day you really can.")
                f.dueToday > 0 -> tr("$name: ค่อยๆ ไหลไปทีละอัน เหลือ ${f.dueToday} อัน", "$name: Flow through them one by one, ${f.dueToday} left.")
                else -> tr("$name: วันนี้ได้อ่านหรือเรียนอะไรใหม่บ้างหรือยัง", "$name: Read or learned something new today?")
            }
            Tribe.SYLVA -> when {
                f.dueToday > 0 -> tr("$name: ทำทีละนิดทุกวันก็โตได้ เหลือ ${f.dueToday} อัน", "$name: A little every day grows a tree. ${f.dueToday} left.")
                f.overdue > 0 -> tr("$name: งานค้าง ${f.overdue} อัน รดน้ำทีละต้นนะ", "$name: ${f.overdue} overdue. Water one plant at a time.")
                else -> tr("$name: กิจวัตรวันนี้ครบไหม ต้นไม้เติบโตจากราก", "$name: Routine done today? Trees grow from the roots.")
            }
        }
    }
}
