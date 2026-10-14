package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.PickMenu
import kr.inmc.core.integration.PlaceNames
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.entity.Player

/**
 * 생물군계 고르기 — 이름은 **타이틀포지 별칭**(places.yml), 없으면 클라이언트 번역(`<lang:biome.minecraft.…>` — 한국어 클라이언트면 한국어).
 * 데이터팩 생물군계도 서버 레지스트리에 함께 있어 그대로 나온다(바닐라 먼저).
 */
object BiomePick {

    fun open(w: Worlds, viewer: Player, back: () -> Unit, onPick: (String) -> Unit) {
        val biomes = w.service.biomes().map { keyOf(it) }.sortedWith(compareBy({ !it.startsWith("minecraft:") }, { it }))
        PickMenu(w, viewer, "<dark_green>생물군계</dark_green>", biomes,
            icon = { key -> Icon.of(icon(key), label(key), listOf("<dark_gray>$key</dark_gray>")) },
            back = back,
        ) { key -> onPick(key) }.show()
    }

    /** 보여 줄 이름(MiniMessage). */
    fun label(key: String): String {
        PlaceNames.biome(key)?.let { return it }
        return if (key.startsWith("minecraft:") || !key.contains(':')) "<white><lang:biome.minecraft.${key.substringAfter(':')}></white>" else "<white>$key</white>"
    }

    @Suppress("DEPRECATION") // Biome.getKey — 레지스트리에서 꺼낸 것은 늘 열쇠가 있다(essentials BiomeNames 와 같다)
    fun keyOf(biome: Biome): String = biome.key.asString()

    /** 이름의 낱말로 고른 그림 — 꼭 맞지 않아도 된다(목록에서 눈에 띄게). */
    private fun icon(key: String): Material {
        val k = key.substringAfter(':')
        return when {
            "nether" in k || "crimson" in k || "warped" in k || "basalt" in k || "soul" in k -> Material.NETHERRACK
            "end" in k && ("the_end" in k || "end_" in k || k.startsWith("small_end") || "_end" in k) -> Material.END_STONE
            "ocean" in k || "river" in k -> Material.WATER_BUCKET
            "desert" in k || "beach" in k -> Material.SAND
            "badlands" in k -> Material.TERRACOTTA
            "snow" in k || "ice" in k || "frozen" in k || "grove" in k -> Material.SNOW_BLOCK
            "jungle" in k || "bamboo" in k -> Material.JUNGLE_LOG
            "taiga" in k -> Material.SPRUCE_LOG
            "birch" in k -> Material.BIRCH_LOG
            "dark_forest" in k -> Material.DARK_OAK_LOG
            "cherry" in k -> Material.CHERRY_LOG
            "forest" in k -> Material.OAK_LOG
            "savanna" in k -> Material.ACACIA_LOG
            "swamp" in k || "mangrove" in k -> Material.LILY_PAD
            "mushroom" in k -> Material.RED_MUSHROOM_BLOCK
            "deep_dark" in k -> Material.SCULK
            "cave" in k -> Material.DRIPSTONE_BLOCK
            "peak" in k || "slope" in k || "hill" in k || "stony" in k || "windswept" in k -> Material.STONE
            "meadow" in k || "flower" in k || "sunflower" in k -> Material.POPPY
            else -> Material.GRASS_BLOCK
        }
    }
}
