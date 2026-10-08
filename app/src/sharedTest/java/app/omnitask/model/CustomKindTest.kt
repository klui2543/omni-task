package app.omnitask.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomKindTest {

    @Test
    fun kindsRoundTripAndMatchTasks() {
        val read = CustomKind("อ่าน", "📖", "อ่าน")
        val home = CustomKind("งาน บ้าน", "", CustomKind.tagFor("งาน บ้าน"))
        assertEquals("งานบ้าน", home.tag)
        assertEquals(listOf(read, home).sortedBy { it.name }, CustomKind.parse(CustomKind.encode(listOf(read, home))))
        assertEquals("📖 อ่าน", read.label)
        assertEquals("งาน บ้าน", home.label)

        val t = app.omnitask.data.TaskLine.parse("- [ ] Atomic Habits #อ่าน")!!
        assertEquals(read, CustomKind.of(t, listOf(read, home)))
        assertNull(CustomKind.of(app.omnitask.data.TaskLine.parse("- [ ] ล้างจาน")!!, listOf(read)))
        // A custom kind's tag is not a project.
        Projects.ignoredTags = setOf("อ่าน")
        try { assertNull(Projects.projectOf(t)) } finally { Projects.ignoredTags = emptySet() }
    }
}
