package com.inmc.worlds

import com.inmc.worlds.gui.GameRuleTexts
import kotlin.test.Test
import kotlin.test.assertTrue

/** 게임 규칙 설명 표 — 화면이 MiniMessage 로 그리므로 태그 글자가 섞이면 줄이 깨진다. */
class GameRuleTextsTest {

    private val all = GameRuleTexts.names.values + GameRuleTexts.descriptions.values.flatten()

    @Test
    fun `글에 꺾쇠가 없다`() {
        assertTrue(all.none { '<' in it || '>' in it }, all.filter { '<' in it || '>' in it }.toString())
    }

    @Test
    fun `열쇠는 규칙 id 모양`() {
        val keys = GameRuleTexts.names.keys + GameRuleTexts.descriptions.keys
        assertTrue(keys.all { Regex("minecraft:[a-z_]+").matches(it) }, keys.toString())
    }

    @Test
    fun `이름을 바꾼 규칙은 설명도 있다`() {
        assertTrue(GameRuleTexts.descriptions.keys.containsAll(GameRuleTexts.names.keys))
    }

    @Test
    fun `설명 한 줄이 툴팁에 들어간다`() {
        val long = GameRuleTexts.descriptions.values.flatten().filter { it.length > 34 }
        assertTrue(long.isEmpty(), long.toString())
    }
}
