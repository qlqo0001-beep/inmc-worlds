package com.inmc.worlds.world

import org.bukkit.Difficulty
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection

/** 지형 유형 — 만들기 마법사의 "유형". 이름은 클라이언트 번역(`generator.minecraft.*`)과 같은 뜻. */
enum class TerrainKind(val label: String) {
    DEFAULT("기본"),
    FLAT("완전한 평지"),
    LARGE_BIOMES("넓은 생물 군계"),
    AMPLIFIED("증폭"),
    SINGLE_BIOME("단일 생물 군계"),
    VOID("공허"),
    ;

    companion object {
        fun of(raw: String?): TerrainKind = entries.firstOrNull { it.name.equals(raw, true) } ?: DEFAULT
    }
}

/** 평지의 층 하나 — 아래에서 위로. [block] 은 `minecraft:stone` 같은 블록 id. */
data class Layer(val block: String, val height: Int)

/** 평지 설정. [features] = 나무·꽃 같은 지형지물, [lakes] = 호수. */
data class Flat(
    val layers: List<Layer> = DEFAULT_LAYERS,
    val biome: String = "minecraft:plains",
    val features: Boolean = false,
    val lakes: Boolean = false,
) {
    /** 바닥에서 맨 위 층 꼭대기까지의 두께. */
    val thickness: Int get() = layers.sumOf { it.height }

    companion object {
        /** 바닐라 "고전 평지" 와 같다 — 기반암 1 · 흙 2 · 잔디 1. */
        val DEFAULT_LAYERS = listOf(Layer("minecraft:bedrock", 1), Layer("minecraft:dirt", 2), Layer("minecraft:grass_block", 1))

        /** 빠른 설정 — 바닥 1칸 · 속 블록 · 겉 블록 1칸으로 두께 [height]. */
        fun quick(height: Int, top: String, filler: String, bottom: String): List<Layer> {
            val h = height.coerceAtLeast(1)
            return buildList {
                add(Layer(bottom, 1))
                if (h >= 3) add(Layer(filler, h - 2))
                if (h >= 2) add(Layer(top, 1))
            }
        }
    }
}

/** 포탈 연결 — 바닐라에 맡김 · 막음 · 이 월드로. */
data class PortalLink(val kind: Kind, val world: String? = null) {
    enum class Kind { VANILLA, NONE, WORLD }

    fun serialize(): String = when (kind) {
        Kind.VANILLA -> "vanilla"
        Kind.NONE -> "none"
        Kind.WORLD -> "world:" + world.orEmpty()
    }

    companion object {
        val VANILLA = PortalLink(Kind.VANILLA)
        val NONE = PortalLink(Kind.NONE)

        fun to(world: String) = PortalLink(Kind.WORLD, world)

        fun parse(raw: String?, fallback: PortalLink): PortalLink {
            val text = raw?.trim().orEmpty()
            return when {
                text.equals("vanilla", true) -> VANILLA
                text.equals("none", true) -> NONE
                text.startsWith("world:", true) && text.length > 6 -> to(text.substring(6))
                else -> fallback
            }
        }
    }
}

/** 스폰 자리(월드 안). */
data class Spot(val x: Double, val y: Double, val z: Double, val yaw: Float = 0f, val pitch: Float = 0f)

/**
 * 월드 하나 — 만드는 법(다시 불러올 때도 같은 생성기가 필요하다)과 **플러그인이 이기는** 설정(난이도·게임 규칙·스폰).
 *
 * - [load] = 켤 때 우리가 불러온다. 바닐라 셋(world·world_nether·world_the_end)은 서버가 불러오므로 false.
 * - [rules] 는 기본값과 다른 것만(열쇠 = 게임 규칙 id `minecraft:pvp`, 값 = 글자). 켤 때·월드가 읽힐 때 덮어쓴다.
 *   다른 길로 바뀐 것(`/gamerule`·essentials `/게임규칙`)도 받아 적는다([WorldListener]).
 */
data class WorldEntry(
    val name: String,
    val load: Boolean = false,
    val environment: World.Environment = World.Environment.NORMAL,
    val kind: TerrainKind = TerrainKind.DEFAULT,
    val seed: Long? = null,
    val structures: Boolean = true,
    val bonusChest: Boolean = false,
    val flat: Flat? = null,
    /** 단일 생물 군계의 생물군계 id. */
    val biome: String? = null,
    /** 생성기 플러그인(`플러그인:id`) — 스카이블록 플러그인 같은 것. */
    val generator: String? = null,
    val difficulty: Difficulty? = null,
    val spawn: Spot? = null,
    val rules: Map<String, String> = emptyMap(),
    val nether: PortalLink = PortalLink.NONE,
    val end: PortalLink = PortalLink.NONE,
) {

    fun write(section: ConfigurationSection) {
        section.set("load", load)
        section.set("environment", environment.name.lowercase())
        section.set("type", kind.name.lowercase())
        section.set("seed", seed)
        section.set("structures", structures)
        section.set("bonus-chest", bonusChest)
        flat?.let { f ->
            val s = section.createSection("flat")
            s.set("layers", f.layers.map { "${it.block}*${it.height}" })
            s.set("biome", f.biome)
            s.set("features", f.features)
            s.set("lakes", f.lakes)
        }
        section.set("biome", biome)
        section.set("generator", generator)
        section.set("difficulty", difficulty?.name?.lowercase())
        spawn?.let { s ->
            val sp = section.createSection("spawn")
            sp.set("x", s.x); sp.set("y", s.y); sp.set("z", s.z); sp.set("yaw", s.yaw.toDouble()); sp.set("pitch", s.pitch.toDouble())
        }
        if (rules.isNotEmpty()) {
            val r = section.createSection("rules")
            for ((key, value) in rules.toSortedMap()) r.set(key, value)
        }
        // 경로 구분자에 기대지 않는다 — 저장소가 월드 이름의 점 때문에 구분자를 바꿔 쓴다([WorldStore]).
        val portals = section.createSection("portals")
        portals.set("nether", nether.serialize())
        portals.set("end", end.serialize())
    }

    companion object {

        /** 세계 이름 규칙(새로 만드는 월드) — 폴더 이름·열쇠에 그대로 들어간다. */
        val NAME = Regex("[a-z0-9_-]{1,32}")

        /** 바닐라 셋의 기본 — 서버가 불러오고 포탈은 바닐라대로. */
        fun vanilla(name: String, environment: World.Environment) =
            WorldEntry(name = name, environment = environment, nether = PortalLink.VANILLA, end = PortalLink.VANILLA)

        fun read(name: String, section: ConfigurationSection): WorldEntry {
            val flat = section.getConfigurationSection("flat")?.let { s ->
                Flat(
                    layers = s.getStringList("layers").mapNotNull(::parseLayer).ifEmpty { Flat.DEFAULT_LAYERS },
                    biome = s.getString("biome") ?: "minecraft:plains",
                    features = s.getBoolean("features", false),
                    lakes = s.getBoolean("lakes", false),
                )
            }
            val spawn = section.getConfigurationSection("spawn")?.let { s ->
                Spot(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), s.getDouble("yaw").toFloat(), s.getDouble("pitch").toFloat())
            }
            val rules = section.getConfigurationSection("rules")?.let { r -> r.getKeys(false).associateWith { r.getString(it).orEmpty() } }.orEmpty()
            return WorldEntry(
                name = name,
                load = section.getBoolean("load", false),
                environment = World.Environment.entries.firstOrNull { it.name.equals(section.getString("environment"), true) } ?: World.Environment.NORMAL,
                kind = TerrainKind.of(section.getString("type")),
                seed = if (section.isSet("seed")) section.getLong("seed") else null,
                structures = section.getBoolean("structures", true),
                bonusChest = section.getBoolean("bonus-chest", false),
                flat = flat,
                biome = section.getString("biome")?.takeIf { it.isNotBlank() },
                generator = section.getString("generator")?.takeIf { it.isNotBlank() },
                difficulty = Difficulty.entries.firstOrNull { it.name.equals(section.getString("difficulty"), true) },
                spawn = spawn,
                rules = rules.filterValues { it.isNotBlank() },
                nether = PortalLink.parse(section.getConfigurationSection("portals")?.getString("nether"), PortalLink.NONE),
                end = PortalLink.parse(section.getConfigurationSection("portals")?.getString("end"), PortalLink.NONE),
            )
        }

        /** `minecraft:stone*3` → 층. 높이가 없으면 1. */
        fun parseLayer(raw: String): Layer? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            val star = text.lastIndexOf('*')
            if (star < 0) return Layer(text, 1)
            val height = text.substring(star + 1).trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
            return Layer(text.substring(0, star).trim(), height)
        }
    }
}
