package app.omnitask.ui

/**
 * The emoji a list wears (Bucket list, Watch list, or any list the owner makes). The list note stores the
 * emoji itself (`icon: 🏔️`); notes made before emoji keep their old icon names, which map to one here.
 */
object ListEmoji {

    const val DEFAULT = "📋"

    /** The picker, a row of ten per theme. Any other emoji can be typed in. */
    val groups: List<Pair<Pair<String, String>, List<String>>> = listOf(
        ("เที่ยว" to "Travel") to listOf("🏔️", "✈️", "🌏", "🗺️", "🧭", "⛺", "🏖️", "🚗", "🚲", "🎟️"),
        ("ดู ฟัง อ่าน" to "Watch, listen, read") to listOf("🎬", "📺", "🍿", "📚", "📖", "🎧", "🎵", "🎮", "🎨", "📷"),
        ("ชีวิต" to "Life") to listOf("❤️", "⭐", "🏆", "🎁", "🛒", "☕", "🍜", "🌿", "🏋️", "🏠"),
        ("เรียนรู้และงาน" to "Learning and work") to listOf("🎓", "💡", "✏️", "💊", "🧪", "💰", "📈", "🧘", "🐶", "📋"),
    )

    private val OLD_NAMES = mapOf(
        "mountain" to "🏔️", "plane" to "✈️", "globe" to "🌏", "map" to "🗺️", "compass" to "🧭", "tent" to "⛺",
        "camera" to "📷", "film" to "🎬", "tv" to "📺", "play" to "▶️", "book" to "📚", "headphones" to "🎧",
        "music" to "🎵", "game" to "🎮", "palette" to "🎨", "pen" to "✏️", "heart" to "❤️", "star" to "⭐",
        "trophy" to "🏆", "flag" to "🚩", "gift" to "🎁", "cart" to "🛒", "coffee" to "☕", "food" to "🍜",
        "leaf" to "🌿", "dumbbell" to "🏋️", "bike" to "🚲", "graduation" to "🎓", "lightbulb" to "💡",
        "home" to "🏠", "car" to "🚗", "ticket" to "🎟️", "list" to "📋",
    )

    /** The emoji to show for what a list note stores. */
    fun of(icon: String): String {
        val v = icon.trim()
        return OLD_NAMES[v] ?: v.takeIf { it.isNotEmpty() && it.any { c -> c.code > 0x7F } } ?: DEFAULT
    }
}
