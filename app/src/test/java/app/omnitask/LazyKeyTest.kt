package app.omnitask

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A lazy list crashes the app when two of its rows share a key. The today page did this on Mondays (2026-11-09:
 * the Monday look-ahead card and the week card were both "ahead"), so the app closed on every launch. Within one
 * file, each fixed key written as `item(key = "...")` must be used once.
 */
class LazyKeyTest {

    private val KEY = Regex("""item\(key = "([^"]+)"\)""")

    @Test
    fun fixedKeysAreUniquePerFile() {
        val root = File("src/main/java").takeIf { it.isDirectory }?.let { File("..") } ?: File(".")
        val files = File(root, "app/src/main/java").walk().filter { it.extension == "kt" }.toList()
        val dupes = files.flatMap { f ->
            KEY.findAll(f.readText()).map { it.groupValues[1] }.groupBy { it }.filterValues { it.size > 1 }.keys.map { "${f.name}: $it" }
        }
        assertEquals("Two rows share a fixed key; the list crashes when both show", emptyList<String>(), dupes)
    }
}
