package com.inmc.worlds.util

import kr.inmc.core.util.TokenBag

/** 메시지 한 번 렌더링에 쓰이는 토큰 주머니. 한글/영문 둘 다 받는다 — 다른 INMC 플러그인들과 같은 관례. */
class Ph : TokenBag<Ph>() {

    override val aliases: Map<String, List<String>> get() = ALIASES

    fun world(name: String): Ph = put(WORLD, name)

    fun value(text: String): Ph = put(VALUE, text)

    fun count(value: Int): Ph = put(COUNT, value.toString())

    fun player(name: String): Ph = put(PLAYER, name)

    companion object {

        fun of(): Ph = Ph()

        const val WORLD = "world"
        const val VALUE = "value"
        const val COUNT = "count"
        const val PLAYER = "player"

        private val ALIASES: Map<String, List<String>> = mapOf(
            WORLD to listOf("{월드}", "{world}"),
            VALUE to listOf("{값}", "{value}"),
            COUNT to listOf("{수}", "{count}"),
            PLAYER to listOf("{플레이어}", "{player}"),
        )
    }
}
