package app.omnitask.quest

import app.omnitask.model.tr

/**
 * The fixed content of Omni Quest: stats, tribes, jobs, equipment, skills. Nothing here changes while playing;
 * what the owner chose and owns is in [QuestState], what the vault earned is in [QuestXp].
 * See `docs/rpg-design.md`.
 */
enum class Stat(val short: String, private val th: String, private val en: String) {
    STR("STR", "พลังกาย", "Strength"),
    INT("INT", "สติปัญญา", "Intellect"),
    AGI("AGI", "ความว่องไว", "Agility"),
    VIT("VIT", "ความอึด", "Vitality"),
    LUK("LUK", "โชค", "Luck"),
    DEX("DEX", "ความแม่นยำ", "Dexterity"),
    ;

    val label get() = tr(th, en)
}

/**
 * The five tribes. Each stands for one part of the owner's real life ([realm]); tasks from that part grow its
 * [stat] ([QuestXp.realmOf] decides which part a task belongs to). [color] is the tribe's colour as 0xAARRGGBB.
 */
enum class Tribe(
    private val th: String,
    private val en: String,
    private val elementTh: String,
    private val elementEn: String,
    val stat: Stat,
    val mascot: String,
    private val mascotTh: String,
    private val mascotEn: String,
    private val realmTh: String,
    private val realmEn: String,
    val color: Long,
) {
    IGNIS("อัคนี", "Ignis", "ไฟ", "Fire", Stat.STR, "โฮมุระ", "ลูกมังกรไฟตัวกลม", "a round baby fire dragon",
        "ร่างกายและสุขภาพ", "Body and health", 0xFFE8603C),
    LUNA("จันทรา", "Luna", "แสงดาว", "Starlight", Stat.INT, "มิมิ", "นกฮูกดาวสวมแว่น", "a star owl in glasses",
        "สร้างระบบและงานใช้สมอง", "Building systems, deep work", 0xFF7C6CF0),
    ZEPHYR("วายุ", "Zephyr", "ลม", "Wind", Stat.AGI, "ฟุ้ง", "จิ้งจอกหางเมฆ", "a cloud-tailed fox",
        "เอกสาร ธุระ และงานสั้น", "Paperwork, errands, quick tasks", 0xFF3FB8AF),
    AQUA("ธารา", "Aqua", "น้ำ", "Water", Stat.LUK, "ปุยฟอง", "วาฬน้อยลอยฟ้า", "a little sky whale",
        "เรียนรู้ การเงิน และลงทุนอนาคต", "Learning, money, investing in the future", 0xFF3D8BFD),
    SYLVA("พฤกษา", "Sylva", "ไม้", "Wood", Stat.VIT, "ต้นกล้า", "กวางน้อยเขาเป็นกิ่งไม้", "a fawn with branch antlers",
        "นิสัยและกิจวัตร", "Habits and routines", 0xFF5DAE4B),
    ;

    val label get() = tr(th, en)
    val element get() = tr(elementTh, elementEn)
    val mascotLook get() = tr(mascotTh, mascotEn)
    val realm get() = tr(realmTh, realmEn)
}

/** Novice until level [QuestWorld.JOB_LEVEL], then one of the first jobs. */
enum class Job(private val th: String, private val en: String, val main: Stat, private val styleTh: String, private val styleEn: String) {
    NOVICE("Novice", "Novice", Stat.VIT, "นักผจญภัยมือใหม่", "A new adventurer"),
    SWORDSMAN("นักดาบ", "Swordsman", Stat.STR, "ระยะประชิด ทนทาน", "Close range, sturdy"),
    MAGE("จอมเวท", "Mage", Stat.INT, "เวทธาตุ ดาเมจหมู่", "Elemental magic, hits many"),
    ARCHER("นักธนู", "Archer", Stat.DEX, "ระยะไกล เร็ว", "Long range, fast"),
    ACOLYTE("นักบวช", "Acolyte", Stat.LUK, "ฟื้นฟู บัฟทีม", "Heals, buffs the party"),
    WARDEN("ผู้พิทักษ์", "Warden", Stat.VIT, "ป้องกัน เรียกสัตว์", "Guards, calls beasts"),
    ;

    val label get() = tr(th, en)
    val style get() = tr(styleTh, styleEn)
}

enum class Slot(private val th: String, private val en: String) {
    WEAPON("อาวุธ", "Weapon"), ARMOR("ชุดเกราะ", "Armor"), ACCESSORY("เครื่องประดับ", "Accessory");

    val label get() = tr(th, en)
}

/** One piece of equipment. [price] 0 means it only drops from battles; [minLevel] gates wearing it, not buying. */
data class Item(
    val id: String,
    private val th: String,
    private val en: String,
    val slot: Slot,
    val atk: Int = 0,
    val matk: Int = 0,
    val def: Int = 0,
    val bonus: Map<Stat, Int> = emptyMap(),
    val price: Int = 0,
    val minLevel: Int = 1,
    val tribe: Tribe? = null,
    val icon: String = "",
) {
    val name get() = tr(th, en)
    val sellPrice get() = (if (price > 0) price else 40 * minLevel) / 2
}

enum class SkillKind { ACTIVE, PASSIVE }

/** A skill; [tribe] null means anyone may learn it, [job] null means any job. [power] is percent of attack. */
data class Skill(
    val id: String,
    private val th: String,
    private val en: String,
    val kind: SkillKind,
    val minLevel: Int,
    val power: Int,
    val tribe: Tribe? = null,
    val job: Job? = null,
    private val noteTh: String = "",
    private val noteEn: String = "",
) {
    val name get() = tr(th, en)
    val note get() = tr(noteTh, noteEn)
}

object QuestWorld {
    const val JOB_LEVEL = 10
    const val MAX_LEVEL = 99
    const val ACTIVE_SLOTS = 4
    const val PASSIVE_SLOTS = 2

    val items: List<Item> = listOf(
        // Weapons
        Item("w-stick", "กิ่งไม้ฝึกหัด", "Training branch", Slot.WEAPON, atk = 3, price = 0, icon = "🪵"),
        Item("w-knife", "มีดสั้นนักเดินทาง", "Traveler's knife", Slot.WEAPON, atk = 8, bonus = mapOf(Stat.AGI to 1), price = 120, icon = "🗡️"),
        Item("w-sword", "ดาบเหล็กกิลด์", "Guild iron sword", Slot.WEAPON, atk = 16, bonus = mapOf(Stat.STR to 2), price = 380, minLevel = 8, icon = "⚔️"),
        Item("w-rod", "คทาแสงดาว", "Starlight rod", Slot.WEAPON, atk = 4, matk = 18, bonus = mapOf(Stat.INT to 2), price = 380, minLevel = 8, tribe = Tribe.LUNA, icon = "🪄"),
        Item("w-bow", "ธนูหางเมฆ", "Cloudtail bow", Slot.WEAPON, atk = 15, bonus = mapOf(Stat.DEX to 2, Stat.AGI to 1), price = 420, minLevel = 10, tribe = Tribe.ZEPHYR, icon = "🏹"),
        Item("w-mace", "คทาธารารักษา", "Healing tide mace", Slot.WEAPON, atk = 10, matk = 10, bonus = mapOf(Stat.LUK to 2), price = 420, minLevel = 10, tribe = Tribe.AQUA, icon = "🔱"),
        Item("w-axe", "ขวานเปลวเพลิง", "Ember axe", Slot.WEAPON, atk = 26, bonus = mapOf(Stat.STR to 3), price = 900, minLevel = 20, tribe = Tribe.IGNIS, icon = "🪓"),
        Item("w-staff", "ไม้เท้ารากยกดราซิล", "Ygdrasil root staff", Slot.WEAPON, atk = 12, matk = 24, bonus = mapOf(Stat.VIT to 3), price = 900, minLevel = 20, tribe = Tribe.SYLVA, icon = "🌿"),
        Item("w-dawn", "ดาบรุ่งอรุณ", "Dawnblade", Slot.WEAPON, atk = 40, matk = 20, bonus = mapOf(Stat.STR to 3, Stat.INT to 3), minLevel = 25, icon = "🌅"),
        // Armor
        Item("a-shirt", "เสื้อผ้าฝ้าย", "Cotton shirt", Slot.ARMOR, def = 2, price = 0, icon = "👕"),
        Item("a-leather", "เกราะหนังกิลด์", "Guild leather", Slot.ARMOR, def = 8, bonus = mapOf(Stat.VIT to 1), price = 150, icon = "🦺"),
        Item("a-robe", "เสื้อคลุมจันทรา", "Moon robe", Slot.ARMOR, def = 6, matk = 6, bonus = mapOf(Stat.INT to 2), price = 400, minLevel = 8, icon = "🥋"),
        Item("a-mail", "เกราะโซ่อัคนี", "Ember chainmail", Slot.ARMOR, def = 16, bonus = mapOf(Stat.STR to 1, Stat.VIT to 2), price = 600, minLevel = 15, icon = "🛡️"),
        Item("a-mist", "ผ้าคลุมหมอกจาง", "Thinning-mist cloak", Slot.ARMOR, def = 22, bonus = mapOf(Stat.AGI to 3), minLevel = 18, icon = "🧥"),
        // Accessories
        Item("c-badge", "เข็มกลัดกิลด์รุ่งอรุณ", "Dawn Guild badge", Slot.ACCESSORY, def = 1, bonus = mapOf(Stat.LUK to 1), price = 0, icon = "📛"),
        Item("c-clover", "จี้ใบโคลเวอร์", "Clover pendant", Slot.ACCESSORY, bonus = mapOf(Stat.LUK to 3), price = 250, minLevel = 5, icon = "🍀"),
        Item("c-hourglass", "นาฬิกาทรายนักโฟกัส", "Focus hourglass", Slot.ACCESSORY, bonus = mapOf(Stat.DEX to 3, Stat.INT to 1), price = 350, minLevel = 8, icon = "⏳"),
        Item("c-ring", "แหวนนักเดินทาง", "Wanderer's ring", Slot.ACCESSORY, bonus = mapOf(Stat.AGI to 2, Stat.DEX to 2), price = 500, minLevel = 12, icon = "💍"),
        Item("c-leaf", "ใบไม้แห่งยกดราซิล", "Leaf of Ygdrasil", Slot.ACCESSORY, def = 5, bonus = mapOf(Stat.VIT to 4, Stat.LUK to 2), minLevel = 15, icon = "🍃"),
    )

    /** What every adventurer starts with, already worn. */
    val starter = listOf("w-stick", "a-shirt", "c-badge")

    /** What can drop in idle battles, by the level of the zone. */
    val drops = listOf("w-dawn", "a-mist", "c-leaf")

    fun item(id: String?): Item? = items.firstOrNull { it.id == id }

    val skills: List<Skill> = listOf(
        // Anyone
        Skill("bash", "ฟาดเต็มแรง", "Bash", SkillKind.ACTIVE, 1, 150, noteTh = "โจมตีแรงขึ้นครึ่งหนึ่ง", noteEn = "Hits half again as hard"),
        Skill("first-aid", "ปฐมพยาบาล", "First aid", SkillKind.ACTIVE, 3, 0, noteTh = "ฟื้น HP เล็กน้อย", noteEn = "Heals a little"),
        Skill("focus", "จิตจดจ่อ", "Focus mind", SkillKind.PASSIVE, 5, 10, noteTh = "ดาเมจ +10% ขณะจับเวลาทำงาน", noteEn = "+10% damage while timing a task"),
        Skill("dawn-light", "แสงอรุณ", "Dawnlight", SkillKind.ACTIVE, 12, 300, noteTh = "สกิลสุดยอด ใช้พลังจากงานที่เสร็จวันนี้", noteEn = "Ultimate, fed by today's finished tasks"),
        // Tribes
        Skill("fire-bolt", "ลูกไฟ", "Fire bolt", SkillKind.ACTIVE, 2, 170, tribe = Tribe.IGNIS),
        Skill("blaze-heart", "หัวใจเปลวเพลิง", "Blazing heart", SkillKind.PASSIVE, 8, 12, tribe = Tribe.IGNIS, noteTh = "STR +12%", noteEn = "STR +12%"),
        Skill("star-fall", "ดาวตก", "Starfall", SkillKind.ACTIVE, 2, 180, tribe = Tribe.LUNA),
        Skill("moon-wisdom", "ปัญญาจันทรา", "Moon wisdom", SkillKind.PASSIVE, 8, 12, tribe = Tribe.LUNA, noteTh = "INT +12%", noteEn = "INT +12%"),
        Skill("gale", "ใบมีดวายุ", "Gale blade", SkillKind.ACTIVE, 2, 160, tribe = Tribe.ZEPHYR),
        Skill("tailwind", "ลมส่งท้าย", "Tailwind", SkillKind.PASSIVE, 8, 12, tribe = Tribe.ZEPHYR, noteTh = "AGI +12%", noteEn = "AGI +12%"),
        Skill("tide", "คลื่นยักษ์", "Tidal wave", SkillKind.ACTIVE, 2, 165, tribe = Tribe.AQUA),
        Skill("lucky-bubble", "ฟองนำโชค", "Lucky bubble", SkillKind.PASSIVE, 8, 12, tribe = Tribe.AQUA, noteTh = "เงินจากการต่อสู้ +12%", noteEn = "+12% gold from battles"),
        Skill("thorn", "หนามพฤกษา", "Thorn lash", SkillKind.ACTIVE, 2, 160, tribe = Tribe.SYLVA),
        Skill("deep-root", "รากลึก", "Deep roots", SkillKind.PASSIVE, 8, 12, tribe = Tribe.SYLVA, noteTh = "HP +12%", noteEn = "HP +12%"),
        // Jobs
        Skill("cleave", "ฟันกวาด", "Cleave", SkillKind.ACTIVE, 10, 220, job = Job.SWORDSMAN),
        Skill("meteor", "อุกกาบาต", "Meteor", SkillKind.ACTIVE, 10, 240, job = Job.MAGE),
        Skill("double-shot", "ยิงคู่", "Double shot", SkillKind.ACTIVE, 10, 230, job = Job.ARCHER),
        Skill("blessing", "พร", "Blessing", SkillKind.PASSIVE, 10, 15, job = Job.ACOLYTE, noteTh = "ทุก stat +15%", noteEn = "All stats +15%"),
        Skill("beast-call", "เรียกสัตว์พิทักษ์", "Beast call", SkillKind.ACTIVE, 10, 210, job = Job.WARDEN),
    )

    fun skill(id: String?): Skill? = skills.firstOrNull { it.id == id }

    /** Skills this character may learn now or later: no tribe or theirs, no job or theirs. */
    fun skillsFor(tribe: Tribe?, job: Job): List<Skill> =
        skills.filter { (it.tribe == null || it.tribe == tribe) && (it.job == null || it.job == job) }

    /** Choices for the paper-doll look; each part's value is an index into these lists. */
    val hairStyles = listOf("สั้น", "ยาว", "หางม้า", "ฟู", "จุก")
    val hairColors = listOf(0xFF2B2B33, 0xFF8B5A2B, 0xFFE8C170, 0xFFE86A92, 0xFF6FA8DC, 0xFFEDEDED)
    val skinTones = listOf(0xFFFFE0C7, 0xFFF4C7A1, 0xFFD9A07A, 0xFFA8714D)
    val outfitColors = listOf(0xFF4F6D8F, 0xFF8F4F5E, 0xFF4F8F6A, 0xFF8F7A4F, 0xFF6A4F8F)

    /** Mascots of other tribes join once the character reaches these levels (one of the "many camps"). */
    const val SECOND_MASCOT_LEVEL = 15
    const val ALL_MASCOTS_LEVEL = 30
}
