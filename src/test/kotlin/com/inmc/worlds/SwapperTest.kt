package com.inmc.worlds

import com.inmc.worlds.body.Part
import com.inmc.worlds.body.Swapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 몸 맞추기 — 바꾸는 부분 고르기, 그리고 **꺼지는 자리마다 모드 사이 복사가 없는지**.
 *
 * 모형: 몸은 "지금"(live)과 "디스크"(.dat — [Body.save] 때만 맞춰진다)가 따로다. 크러시 = 디스크의 몸으로 되돌아가기(파일은 쓴 데까지 남는다).
 * 물건 하나의 **진짜 자리** = 그 부분을 몸이 쥔 scope 면 몸, 아니면 그 scope 의 파일. 같은 물건이 두 진짜 자리에 있으면 복사다.
 */
class SwapperTest {

    private class Crash : RuntimeException()

    /** 몸 모형. 부분마다 값은 물건 이름 목록. */
    private class FakeBody(initial: Map<Part, List<String>>, held: Map<Part, String>?) : Swapper.Body<List<String>> {
        var live = initial.toMutableMap()
        var liveHeld: Map<Part, String>? = held
        var disk = initial.toMap()
        var diskHeld: Map<Part, String>? = held
        var crashAt: String? = null
        var steps = 0

        private fun step(name: String) {
            steps++
            if (crashAt == name) throw Crash()
        }

        override fun held(): Map<Part, String>? = liveHeld
        override fun capture(part: Part): List<String> = live[part].orEmpty()
        override fun apply(part: Part, data: List<String>?) {
            step("apply:$part")
            live[part] = data.orEmpty()
        }
        override fun setHeld(held: Map<Part, String>) {
            step("held")
            liveHeld = held
        }
        override fun save() {
            step("save")
            disk = live.toMap()
            diskHeld = liveHeld
        }

        fun crash() {
            live = disk.toMutableMap()
            liveHeld = diskHeld
            crashAt = null
        }
    }

    private class FakeFiles(initial: Map<String, Map<Part, List<String>>>) : Swapper.Profiles<List<String>> {
        val files = initial.mapValues { it.value.toMutableMap() }.toMutableMap()
        var crashAt: String? = null

        override fun read(scope: String, part: Part): List<String>? = files[scope]?.get(part)
        override fun write(scope: String, parts: Map<Part, List<String>>) {
            if (crashAt == "write:$scope") throw Crash()
            files.getOrPut(scope) { mutableMapOf() }.putAll(parts) // 한 파일은 원자적 — 다 쓰거나 안 쓰거나
        }
    }

    /** 진짜 자리마다의 물건 — (scope, 부분) → 물건. 몸이 쥔 scope 는 몸, 나머지는 파일. */
    private fun truth(body: FakeBody, files: FakeFiles, scopes: Set<String>): Map<Pair<String, Part>, List<String>> {
        val out = HashMap<Pair<String, Part>, List<String>>()
        for (scope in scopes) for (part in Part.entries) {
            val held = body.liveHeld?.get(part)
            val items = if (held == scope) body.live[part].orEmpty() else files.files[scope]?.get(part).orEmpty()
            if (items.isNotEmpty()) out[scope to part] = items
        }
        return out
    }

    private fun all(scope: String) = Part.entries.associateWith { scope }

    @Test
    fun `바꿀 부분만 고른다 — 같이 쓰는 부분은 그대로`() {
        val held = all("main")
        val desired = all("build") + (Part.EXP to "main")
        val plan = Swapper.plan(held, desired)
        assertEquals(Part.entries - Part.EXP, plan.map { it.part })
        assertTrue(plan.all { it.from == "main" && it.to == "build" })
    }

    @Test
    fun `표식이 없는 사람은 지금 몸이 원하는 scope 의 것 — 바꾸지 않는다`() {
        val body = FakeBody(mapOf(Part.INVENTORY to listOf("a1")), held = null)
        val files = FakeFiles(emptyMap())
        val changes = Swapper(body, files).reconcile(all("main"))
        assertTrue(changes.isEmpty())
        assertEquals(listOf("a1"), body.live[Part.INVENTORY])
        assertEquals(all("main"), body.diskHeld)
    }

    @Test
    fun `모드를 넘으면 몸과 파일이 자리를 바꾼다`() {
        val body = FakeBody(mapOf(Part.INVENTORY to listOf("a1"), Part.ENDER to listOf("ae")), held = all("main"))
        val files = FakeFiles(mapOf("build" to mutableMapOf(Part.INVENTORY to listOf("b1"))))
        Swapper(body, files).reconcile(all("build"))
        assertEquals(listOf("b1"), body.live[Part.INVENTORY])
        assertEquals(emptyList(), body.live[Part.ENDER].orEmpty())
        assertEquals(listOf("a1"), files.files["main"]?.get(Part.INVENTORY))
        assertEquals(listOf("ae"), files.files["main"]?.get(Part.ENDER))
        // 되돌아오면 처음 그대로.
        Swapper(body, files).reconcile(all("main"))
        assertEquals(listOf("a1"), body.live[Part.INVENTORY])
        assertEquals(listOf("ae"), body.live[Part.ENDER])
    }

    @Test
    fun `어느 자리에서 꺼져도 복사가 없고, 다시 맞추면 끝까지 간다`() {
        val points = listOf("write:main") + Part.entries.map { "apply:$it" } + listOf("held", "save")
        for (point in points) {
            val body = FakeBody(mapOf(Part.INVENTORY to listOf("a1"), Part.ENDER to listOf("ae")), held = all("main"))
            val files = FakeFiles(mapOf("build" to mutableMapOf(Part.INVENTORY to listOf("b1"), Part.ENDER to listOf("be"))))
            val before = truth(body, files, setOf("main", "build"))
            if (point.startsWith("write:")) files.crashAt = point else body.crashAt = point
            runCatching { Swapper(body, files).reconcile(all("build")) }
            files.crashAt = null
            body.crash()

            val after = truth(body, files, setOf("main", "build"))
            val everything = after.values.flatten()
            assertEquals(everything.size, everything.toSet().size, "$point 에서 꺼진 뒤 복사: $after")
            // 크러시 직후는 바뀌기 전 그대로여야 한다(몸이 디스크에 닿기 전에 꺼졌으므로).
            assertEquals(before, after, "$point 에서 꺼진 뒤 진짜 자리가 바뀜")

            // 다시 켜고 다시 넘으면 끝까지.
            Swapper(body, files).reconcile(all("build"))
            assertEquals(listOf("b1"), body.live[Part.INVENTORY], point)
            assertEquals(listOf("a1"), files.files["main"]?.get(Part.INVENTORY), point)
            val done = truth(body, files, setOf("main", "build")).values.flatten()
            assertEquals(done.size, done.toSet().size, "$point 뒤 다시 맞춘 뒤 복사")
            assertEquals(setOf("a1", "ae", "b1", "be"), done.toSet(), "$point 뒤 잃음")
        }
    }

    @Test
    fun `토글을 켠 채 바꿔도 같은 함수 — 같이로 바꾸면 그 부분만 같이 칸으로`() {
        val body = FakeBody(mapOf(Part.INVENTORY to listOf("a1"), Part.EXP to listOf("x-main")), held = all("main"))
        val files = FakeFiles(mapOf("" to mutableMapOf(Part.EXP to listOf("x-shared"))))
        Swapper(body, files).reconcile(all("main") + (Part.EXP to ""))
        assertEquals(listOf("a1"), body.live[Part.INVENTORY])
        assertEquals(listOf("x-shared"), body.live[Part.EXP])
        assertEquals(listOf("x-main"), files.files["main"]?.get(Part.EXP))
    }
}
