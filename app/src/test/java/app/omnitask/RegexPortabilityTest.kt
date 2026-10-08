package app.omnitask

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Android compiles regexes with ICU, which reads a set opening with `[:` as a POSIX class like `[:alpha:]`
 * and throws, while the JVM these tests run on accepts it. So the JVM cannot catch it by compiling the
 * pattern; this looks for the form in the sources instead.
 */
class RegexPortabilityTest {

    @Test
    fun noSetStartsWithAColon() {
        val src = File("src/main/java").takeIf { it.isDirectory } ?: File("app/src/main/java")
        val bad = src.walk().filter { it.extension == "kt" }.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> "Regex(" in line && Regex("""(?<!\\)\[\^?:""").containsMatchIn(line) }
                .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        }.toList()
        assertEquals("Sets starting with ':' crash on Android; put another character first", emptyList<String>(), bad)
    }
}
