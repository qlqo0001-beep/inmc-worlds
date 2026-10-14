package com.inmc.worlds.body

import org.bukkit.Material

/**
 * 몸에서 모드마다 따로 둘 수 있는 부분 — 부분마다 core `Realms` 기능 열쇠(관리자 토글). 기본은 전부 따로(사용자 결정 2026-10-15).
 * 순서를 바꾸지 않는다 — 저장된 표식([Swapper])은 [key] 로 읽으므로 순서와는 상관없지만, 화면의 순서가 이것이다.
 *
 * 바닐라 발전과제·통계는 넣지 않는다 — 모드끼리 같이 쓴다(사용자 결정). 플레이어 PDC·무한 지속 효과도 넣지 않는다 — 각 플러그인의 몫이다.
 */
enum class Part(val id: String, val label: String, val icon: Material, val description: List<String>) {
    INVENTORY("inventory", "가방", Material.CHEST, listOf("가방 36칸 · 갑옷 · 왼손")),
    ENDER("ender", "엔더 상자", Material.ENDER_CHEST, listOf("엔더 상자 27칸")),
    EXP("exp", "경험치", Material.EXPERIENCE_BOTTLE, listOf("레벨 · 경험치")),
    VITALS("vitals", "체력·배고픔", Material.GOLDEN_APPLE, listOf("체력 · 배고픔 · 포화 · 숨 · 불 · 낙하")),
    EFFECTS("effects", "물약 효과", Material.POTION, listOf("시간이 있는 물약 효과", "(플러그인이 거는 무한 효과는 그 플러그인이 다시 건다)")),
    RESPAWN("respawn-point", "부활 자리", Material.RED_BED, listOf("침대 · 리스폰 정박기")),
    ;

    /** core `Realms` 기능 열쇠. */
    val key: String get() = "worlds.$id"

    companion object {
        fun ofId(id: String): Part? = entries.firstOrNull { it.id == id }
    }
}
