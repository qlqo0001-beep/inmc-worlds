package com.inmc.worlds.body

/**
 * **몸 맞추기** — 부분마다 "원하는 scope ≠ 몸이 쥔 scope" 이면 바꾼다. 모드 이동·접속·토글 변경(재시작 없이)이 모두 이 함수 하나.
 * Bukkit 을 모른다 — 시험(`SwapperTest`)이 꺼지는 자리마다 복사가 없는지 본다.
 *
 * ## 순서와 불변식 (왜 이 순서인가)
 * 1. 몸의 그 부분 → **쥔 scope 의 파일**(즉시·원자적).
 * 2. 원하는 scope 의 파일 → 몸, 그리고 몸의 표식(쥔 scope)을 바꾼다.
 * 3. 몸을 저장(`player.saveData()` — 표식은 플레이어 PDC 라 가방과 **같은 .dat** 에 함께 들어간다).
 *
 * **scope 파일은 그 scope 로 들어갈 때만 읽는다. 몸이 쥔 scope 의 파일은 읽지 않는다**(그 파일은 낡아도 된다 — 몸이 진짜다).
 * 그래서 어느 틈에 서버가 꺼져도: 다시 켜면 몸(.dat)과 표식이 같은 시점의 것이고, 표식이 가리키지 않는 scope 는 파일이 진짜다 — 한 물건이 두 곳의 진짜가 되지 않는다.
 * 잃는 것은 바닐라 크러시와 같은 범위(마지막 .dat 저장 뒤의 것)뿐이다.
 */
class Swapper<D>(private val body: Body<D>, private val profiles: Profiles<D>) {

    /** 몸 — 진짜는 플레이어. */
    interface Body<D> {
        /** 몸이 부분마다 쥔 scope. 표식이 없으면(처음 보는 사람) null. */
        fun held(): Map<Part, String>?
        fun capture(part: Part): D
        /** [data] 가 null 이면 빈 것(처음 들어가는 scope). */
        fun apply(part: Part, data: D?)
        fun setHeld(held: Map<Part, String>)
        /** 몸과 표식을 디스크에(.dat). */
        fun save()
    }

    /** scope 파일 — 진짜는 디스크. */
    interface Profiles<D> {
        fun read(scope: String, part: Part): D?
        /** 한 scope 의 여러 부분을 한 번에, 원자적으로. */
        fun write(scope: String, parts: Map<Part, D>)
    }

    data class Change(val part: Part, val from: String, val to: String)

    /**
     * 맞춘다. 바꾼 것을 돌려준다(없으면 빈 목록).
     * 표식이 없는 사람 = **지금 몸이 원하는 scope 의 것**(처음 설치한 서버 · 처음 온 사람) — 바꾸지 않고 표식만 남긴다.
     * 새로 생긴 부분(업데이트)도 같다 — 표식에 없는 부분은 이미 원하는 scope 를 쥔 것으로 본다.
     */
    fun reconcile(desired: Map<Part, String>): List<Change> {
        val held = body.held()
        if (held == null) {
            body.setHeld(desired)
            body.save()
            return emptyList()
        }
        val changes = plan(held, desired)
        if (changes.isEmpty()) {
            if (desired.keys.any { it !in held }) {
                body.setHeld(held + desired.filterKeys { it !in held })
                body.save()
            }
            return changes
        }
        // 1. 내보내기 — 쥔 scope 의 파일로.
        for ((scope, group) in changes.groupBy { it.from }) profiles.write(scope, group.associate { it.part to body.capture(it.part) })
        // 2. 들이기 — 원하는 scope 의 파일에서. 그리고 표식.
        for (change in changes) body.apply(change.part, profiles.read(change.to, change.part))
        body.setHeld(held + desired.filterKeys { it !in held } + changes.associate { it.part to it.to })
        // 3. 몸을 저장.
        body.save()
        return changes
    }

    companion object {
        /** 바꿀 부분 — 표식에 없는 부분은 바꾸지 않는다(위 KDoc). */
        fun plan(held: Map<Part, String>, desired: Map<Part, String>): List<Change> =
            desired.mapNotNull { (part, want) ->
                val have = held[part] ?: return@mapNotNull null
                if (have == want) null else Change(part, have, want)
            }.sortedBy { it.part.ordinal }
    }
}
