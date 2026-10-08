package app.omnitask.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons a list can wear (Bucket list, Watch list, or any list the owner makes), drawn from 24x24
 * path data like the rest of the app. The key is what the list note stores (`icon: mountain`).
 */
object ListIcons {

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    addPathNodes(it), stroke = SolidColor(Color.White), strokeLineWidth = 1.9f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val all: List<Pair<String, ImageVector>> = listOf(
        "mountain" to icon("mountain", "M3 20l6.5-11 4 6.5 2.5-3.5 5 8Z", "M8 12.5l1.5 1.5 1.5-1.5"),
        "plane" to icon("plane", "M10.5 13.5L4 11l1.5-1.5 7 1 4-4a2 2 0 0 1 3 3l-4 4 1 7L16 22l-2.5-6.5", "M7 17l-3 0.5 0.5-3"),
        "globe" to icon("globe", "M12 3.5a8.5 8.5 0 1 0 0.01 0Z", "M3.5 12h17", "M12 3.5c2.5 2.5 3.5 5.5 3.5 8.5s-1 6-3.5 8.5c-2.5-2.5-3.5-5.5-3.5-8.5s1-6 3.5-8.5Z"),
        "map" to icon("map", "M3.5 6.5l5.5-2.5 6 2.5 5.5-2.5v13.5l-5.5 2.5-6-2.5-5.5 2.5Z", "M9 4v13.5M15 6.5V20"),
        "compass" to icon("compass", "M12 3.5a8.5 8.5 0 1 0 0.01 0Z", "M15.5 8.5l-2 5-5 2 2-5Z"),
        "tent" to icon("tent", "M3 20h18", "M12 4L4 20", "M12 4l8 16", "M12 13l-2.5 7h5Z"),
        "camera" to icon("camera", "M4 8h3l2-2.5h6L17 8h3v11H4Z", "M12 9.5a3.5 3.5 0 1 0 0.01 0Z"),
        "film" to icon("film", "M4 4.5h16v15H4Z", "M8 4.5v15M16 4.5v15M4 9h4M4 15h4M16 9h4M16 15h4"),
        "tv" to icon("tv", "M3.5 7h17v12h-17Z", "M8.5 3l3.5 4 3.5-4"),
        "play" to icon("play", "M12 3.5a8.5 8.5 0 1 0 0.01 0Z", "M10 8.5l5.5 3.5-5.5 3.5Z"),
        "book" to icon("book", "M4 5.5a2 2 0 0 1 2-2h14v15H6a2 2 0 0 0-2 2Z", "M4 20.5a2 2 0 0 1 2-2h14v2H6"),
        "headphones" to icon("headphones", "M4 15v-3a8 8 0 0 1 16 0v3", "M4 15h3v5H4Z", "M17 15h3v5h-3Z"),
        "music" to icon("music", "M9 18V5l11-2v13", "M6.5 15.5a2.5 2.5 0 1 0 0.01 0Z", "M17.5 13.5a2.5 2.5 0 1 0 0.01 0Z"),
        "game" to icon("game", "M6 8h12a4 4 0 0 1 0 8c-1.5 0-2.5-1-3.5-2h-5c-1 1-2 2-3.5 2a4 4 0 0 1 0-8Z", "M7.5 12h3M9 10.5v3", "M15.5 11h0.01M17 13h0.01"),
        "palette" to icon("palette", "M12 3.5a8.5 8.5 0 0 0 0 17c1.5 0 2-1 1.5-2.2-0.6-1.4 0.3-2.8 1.8-2.8H17a3.5 3.5 0 0 0 3.5-3.5A8.5 8.5 0 0 0 12 3.5Z", "M7.5 11h0.01M10 7.5h0.01M14.5 7.5h0.01"),
        "pen" to icon("pen", "M4 20l1-4 11-11 3 3-11 11Z", "M14 7l3 3"),
        "heart" to icon("heart", "M12 20s-7.5-4.5-7.5-10A4.5 4.5 0 0 1 12 7a4.5 4.5 0 0 1 7.5 3c0 5.5-7.5 10-7.5 10Z"),
        "star" to icon("star", "M12 3.5l2.6 5.4 5.9 0.8-4.3 4.1 1 5.8L12 16.8l-5.2 2.8 1-5.8-4.3-4.1 5.9-0.8Z"),
        "trophy" to icon("trophy", "M8 4h8v5a4 4 0 0 1-8 0Z", "M8 6H5v1.5A3 3 0 0 0 8 10.5M16 6h3v1.5a3 3 0 0 1-3 3", "M12 13v4M8.5 20h7M10 17h4v3h-4Z"),
        "flag" to icon("flag", "M5 21V4h11l-2 4 2 4H5"),
        "gift" to icon("gift", "M4 9h16v4H4Z", "M5.5 13h13v7h-13Z", "M12 9v11", "M12 9c-2-3-5-3-5-1s3 1 5 1c2 0 5 1 5-1s-3-2-5 1Z"),
        "cart" to icon("cart", "M3 4h2.5l2 11h11l2-8H6.5", "M9 19.5h0.01M17 19.5h0.01"),
        "coffee" to icon("coffee", "M5 9h11v6a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4Z", "M16 10h1.5a2.5 2.5 0 0 1 0 5H16", "M8 3.5v2.5M11 3.5v2.5"),
        "food" to icon("food", "M6 3.5v7a2 2 0 0 0 4 0v-7M8 10.5V20.5", "M16.5 3.5c-2 1-3 3.5-3 6.5h3v10.5"),
        "leaf" to icon("leaf", "M5 19c0-8 5-13 15-14-1 10-6 15-14 15Z", "M5 19l7-7"),
        "dumbbell" to icon("dumbbell", "M6.5 7v10M17.5 7v10M3.5 9.5v5M20.5 9.5v5M6.5 12h11"),
        "bike" to icon("bike", "M6 17.5a3.5 3.5 0 1 0 0.01 0Z", "M18 17.5a3.5 3.5 0 1 0 0.01 0Z", "M6 17.5l4-7h5l3 7M10 10.5l-1.5-3H7M15 10.5l-1.5-3h2"),
        "graduation" to icon("graduation", "M2.5 9.5L12 5l9.5 4.5L12 14Z", "M6.5 11.5v4.5c3 2.5 8 2.5 11 0v-4.5", "M21.5 9.5v5"),
        "lightbulb" to icon("lightbulb", "M9 17.5h6M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9V16h7v-2.1A6 6 0 0 0 12 3Z"),
        "home" to icon("home", "M3.5 11L12 4l8.5 7", "M6 9.5V20h12V9.5", "M10 20v-5.5h4V20"),
        "car" to icon("car", "M4 16v-4l2-5h12l2 5v4Z", "M4 12h16", "M7.5 19.5v-3.5M16.5 19.5v-3.5"),
        "ticket" to icon("ticket", "M3.5 7.5h17v3a1.5 1.5 0 0 0 0 3v3h-17v-3a1.5 1.5 0 0 0 0-3Z", "M14 7.5v9"),
        "list" to icon("list", "M9 6h11M9 12h11M9 18h11", "M4.5 6h0.01M4.5 12h0.01M4.5 18h0.01"),
    )

    fun of(key: String): ImageVector = all.firstOrNull { it.first == key }?.second ?: all.last().second
}
