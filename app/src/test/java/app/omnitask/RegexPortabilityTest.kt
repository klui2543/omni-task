package app.omnitask

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Regexes run on three engines: the JVM these tests run on, Android's ICU and, for the shared code, JavaScript in
 * the web app. Each reads some patterns differently from the JVM, so the JVM cannot catch it by compiling the
 * pattern; this looks for those forms in the sources instead.
 */
class RegexPortabilityTest {

    private fun sources(): List<File> {
        val root = File("src/main/java").takeIf { it.isDirectory }?.let { File("..") } ?: File(".")
        return listOf("app/src/main/java", "shared/src/commonMain/kotlin").map { File(root, it) }
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }
    }

    private fun regexLines() = sources().flatMap { file ->
        file.readLines().withIndex().filter { (_, line) -> "Regex(" in line }.map { (i, line) -> Triple(file, i, line) }
    }

    /** Android compiles regexes with ICU, which reads a set opening with `[:` as a POSIX class like `[:alpha:]` and throws. */
    @Test
    fun noSetStartsWithAColon() {
        val bad = regexLines()
            .filter { (_, _, line) -> Regex("""(?<!\\)\[\^?:""").containsMatchIn(line) }
            .map { (file, i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        assertEquals("Sets starting with ':' crash on Android; put another character first", emptyList<String>(), bad)
    }

    /** JavaScript reads patterns in Unicode mode, where a `]` outside a set is an error rather than a plain bracket. */
    @Test
    fun noLoneClosingBracket() {
        val bad = regexLines()
            .filter { (file, _, _) -> file.path.contains("shared") }
            .filter { (_, _, line) -> RAW.findAll(line).any { loneBracket(it.groupValues[1]) } }
            .map { (file, i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        assertEquals("A ']' outside a set breaks the web app; write it as '\\]'", emptyList<String>(), bad)
    }

    private val RAW = Regex("\"\"\"(.*?)\"\"\"")

    private fun loneBracket(pattern: String): Boolean {
        var inSet = false
        var i = 0
        while (i < pattern.length) {
            when {
                pattern[i] == '\\' -> i++
                inSet && pattern[i] == ']' -> inSet = false
                !inSet && pattern[i] == '[' -> inSet = true
                !inSet && pattern[i] == ']' -> return true
            }
            i++
        }
        return false
    }
}
