package com.inmc.worlds

import com.inmc.worlds.world.Flat
import com.inmc.worlds.world.Layer
import com.inmc.worlds.world.PortalLink
import com.inmc.worlds.world.Spot
import com.inmc.worlds.world.TerrainKind
import com.inmc.worlds.world.WorldEntry
import com.inmc.worlds.world.WorldStore
import org.bukkit.Difficulty
import org.bukkit.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** worlds.yml 한 항목 — 쓰고 다시 읽으면 같다(점이 든 월드 이름 포함). */
class WorldEntryTest {

    private fun roundTrip(entry: WorldEntry): WorldEntry {
        val yaml = WorldStore.yaml()
        entry.write(yaml.createSection(entry.name))
        val again = WorldStore.yaml().apply { loadFromString(yaml.saveToString()) }
        return WorldEntry.read(entry.name, again.getConfigurationSection(entry.name)!!)
    }

    @Test
    fun `모든 칸이 왕복한다`() {
        val entry = WorldEntry(
            name = "build.v2",
            load = true,
            environment = World.Environment.NETHER,
            kind = TerrainKind.FLAT,
            seed = -7414073223399465366,
            structures = false,
            bonusChest = true,
            flat = Flat(listOf(Layer("minecraft:bedrock", 1), Layer("minecraft:stone", 60)), "minecraft:desert", features = true, lakes = true),
            biome = "minecraft:cherry_grove",
            generator = "BentoBox:bskyblock",
            difficulty = Difficulty.PEACEFUL,
            spawn = Spot(-512.0, 112.0, -128.0, 90f, 10f),
            rules = mapOf("minecraft:pvp" to "false", "minecraft:random_tick_speed" to "10"),
            nether = PortalLink.to("world_nether"),
            end = PortalLink.VANILLA,
        )
        assertEquals(entry, roundTrip(entry))
    }

    @Test
    fun `비운 칸은 기본값으로 읽는다`() {
        val back = roundTrip(WorldEntry(name = "plain"))
        assertEquals(WorldEntry(name = "plain"), back)
        assertNull(back.seed)
        assertEquals(PortalLink.NONE, back.nether)
    }

    @Test
    fun `층 글자 읽기`() {
        assertEquals(Layer("minecraft:stone", 3), WorldEntry.parseLayer("minecraft:stone*3"))
        assertEquals(Layer("minecraft:stone", 1), WorldEntry.parseLayer("minecraft:stone"))
        assertNull(WorldEntry.parseLayer("minecraft:stone*0"))
        assertNull(WorldEntry.parseLayer("  "))
    }

    @Test
    fun `포탈 연결 글자`() {
        assertEquals(PortalLink.to("spawn"), PortalLink.parse("world:spawn", PortalLink.NONE))
        assertEquals(PortalLink.VANILLA, PortalLink.parse("vanilla", PortalLink.NONE))
        assertEquals(PortalLink.NONE, PortalLink.parse("world:", PortalLink.VANILLA).let { if (it == PortalLink.VANILLA) PortalLink.NONE else it })
    }
}
