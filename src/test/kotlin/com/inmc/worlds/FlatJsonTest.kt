package com.inmc.worlds

import com.inmc.worlds.world.Flat
import com.inmc.worlds.world.FlatJson
import com.inmc.worlds.world.Layer
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 평지 층 → 생성기 JSON. 바닐라 고전 평지 프리셋과 같은 모양이어야 서버가 읽는다. */
class FlatJsonTest {

    @Test
    fun `층은 아래에서 위로 그대로 나간다`() {
        val json = JsonParser.parseString(FlatJson.of(Flat(), structures = true)).asJsonObject
        val layers = json.getAsJsonArray("layers").map { it.asJsonObject["block"].asString to it.asJsonObject["height"].asInt }
        assertEquals(listOf("minecraft:bedrock" to 1, "minecraft:dirt" to 2, "minecraft:grass_block" to 1), layers)
        assertEquals("minecraft:plains", json["biome"].asString)
    }

    @Test
    fun `구조물을 끄면 빈 목록 — 끈 평지에 마을이 안 생긴다`() {
        val on = JsonParser.parseString(FlatJson.of(Flat(), structures = true)).asJsonObject
        val off = JsonParser.parseString(FlatJson.of(Flat(), structures = false)).asJsonObject
        assertTrue(on.getAsJsonArray("structure_overrides").size() > 0)
        assertEquals(0, off.getAsJsonArray("structure_overrides").size())
    }

    @Test
    fun `빠른 설정 — 바닥 1 · 속 · 겉 1 로 두께를 맞춘다`() {
        assertEquals(listOf(Layer("b", 1), Layer("f", 3), Layer("t", 1)), Flat.quick(5, "t", "f", "b"))
        assertEquals(listOf(Layer("b", 1), Layer("t", 1)), Flat.quick(2, "t", "f", "b"))
        assertEquals(listOf(Layer("b", 1)), Flat.quick(1, "t", "f", "b"))
        assertEquals(5, Flat(Flat.quick(5, "t", "f", "b")).thickness)
    }

    @Test
    fun `따옴표가 든 id 도 JSON 이 깨지지 않는다`() {
        JsonParser.parseString(FlatJson.of(Flat(listOf(Layer("a\"b", 1)), biome = "x\\y"), structures = false))
    }
}
