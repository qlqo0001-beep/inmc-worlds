package com.inmc.worlds.world

import com.inmc.worlds.Worlds
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.block.Biome
import java.io.File

/**
 * 월드 다루기 — 불러오기·만들기·가져오기·내리기·지우기, 그리고 **플러그인 값이 바닐라를 이기게** 하기.
 *
 * ## 플러그인 값이 이긴다
 * [WorldEntry] 의 난이도·게임 규칙·스폰을 켤 때와 월드가 읽힐 때 덮어쓴다([apply]). 다른 길로 바뀐 것(`/gamerule`·essentials `/게임규칙`·
 * `/setworldspawn`)은 사건([WorldListener])과 저장 때의 훑기([snapshot])로 받아 적는다 — 그래서 덮어쓰는 값은 늘 마지막으로 돌던 값이다.
 * **다른 플러그인이 만든 월드**(던전 등)는 그 플러그인이 규칙을 정하므로, 관리자가 우리 화면에서 무엇을 바꾸기 전에는 받아 적지도 덮지도 않는다(항목이 없다).
 *
 * 모든 메서드는 메인 스레드.
 */
class WorldService(private val w: Worlds) {

    /** [apply] 하는 동안 — 우리가 쏜 규칙 바뀜을 다시 받아 적지 않게. */
    var applying = false
        private set

    val main: World get() = Bukkit.getWorlds().first()

    /** 서버가 직접 불러오는 바닐라 셋의 이름. */
    fun vanillaNames(): Set<String> = main.name.let { setOf(it, it + "_nether", it + "_the_end") }

    fun isVanilla(name: String): Boolean = vanillaNames().any { it.equals(name, true) }

    /** 이 월드의 항목 — 없으면 기본 모양(저장하지 않는다). */
    fun entryOf(world: World): WorldEntry = w.store.get(world.name) ?: defaultEntry(world.name, world.environment)

    fun defaultEntry(name: String, environment: World.Environment): WorldEntry =
        if (isVanilla(name)) WorldEntry.vanilla(name, environment) else WorldEntry(name = name, environment = environment)

    /** 켤 때 — 바닐라 셋의 항목이 없으면 만든다(포탈은 바닐라대로). */
    fun ensureVanillaEntries() {
        for (world in Bukkit.getWorlds()) {
            if (!isVanilla(world.name) || w.store.get(world.name) != null) continue
            w.store.put(WorldEntry.vanilla(world.name, world.environment).copy(difficulty = world.difficulty, spawn = spotOf(world.spawnLocation)))
        }
    }

    /** 켤 때 — 우리가 불러오는 월드 전부. 실패한 것은 로그만 남기고 넘어간다. */
    fun loadAll() {
        for (entry in w.store.all()) {
            if (!entry.load || Bukkit.getWorld(entry.name) != null) continue
            val world = runCatching { creator(entry).createWorld() }
                .onFailure { w.logger.severe("월드 '${entry.name}' 을(를) 불러오지 못했습니다: ${it.message}") }
                .getOrNull()
            if (world == null) continue
            w.logger.info("월드 '${entry.name}' 을(를) 불러왔습니다")
        }
        for (world in Bukkit.getWorlds()) apply(world)
    }

    // --- 만들기·불러오기 ------------------------------------------------------------------------

    /** [entry] 대로 만드는 법. 다시 불러올 때도 같은 것이 필요하다(생성기·생물군계는 저장되지 않는다). */
    fun creator(entry: WorldEntry): WorldCreator {
        val c = WorldCreator(entry.name).environment(entry.environment)
        entry.seed?.let { c.seed(it) }
        c.generateStructures(entry.structures)
        c.bonusChest(entry.bonusChest)
        when (entry.kind) {
            TerrainKind.DEFAULT -> c.type(WorldType.NORMAL)
            TerrainKind.FLAT -> {
                c.type(WorldType.FLAT)
                c.generatorSettings(FlatJson.of(entry.flat ?: Flat(), entry.structures))
            }
            TerrainKind.LARGE_BIOMES -> c.type(WorldType.LARGE_BIOMES)
            TerrainKind.AMPLIFIED -> c.type(WorldType.AMPLIFIED)
            TerrainKind.SINGLE_BIOME -> entry.biome?.let(::biome)?.let { c.biomeProvider(SingleBiome(it)) }
            TerrainKind.VOID -> c.generator(VoidGenerator)
        }
        entry.generator?.let { c.generator(it) }
        return c
    }

    sealed interface Created {
        data class Ok(val world: World) : Created
        data class Fail(val key: String) : Created
    }

    /**
     * 새 월드. 이름 규칙·겹침을 보고, 만든 뒤 항목을 남긴다(켤 때 불러오기 켬). **만드는 동안 서버가 잠깐 멈춘다**(스폰 둘레를 만든다).
     * 공허는 스폰 아래에 발판을 한 번 깐다 — 아니면 들어가자마자 떨어진다.
     */
    fun create(draft: WorldEntry): Created {
        if (!WorldEntry.NAME.matches(draft.name)) return Created.Fail("create-bad-name")
        if (Bukkit.getWorld(draft.name) != null || folders(draft.name).any { it.exists() } || w.store.get(draft.name) != null) {
            return Created.Fail("create-exists")
        }
        if (draft.kind == TerrainKind.FLAT && (draft.flat?.thickness ?: 0) > FlatJson.MAX_THICKNESS) return Created.Fail("create-flat-too-thick")
        if (draft.kind == TerrainKind.SINGLE_BIOME && draft.biome?.let(::biome) == null) return Created.Fail("create-no-biome")
        val world = runCatching { creator(draft).createWorld() }
            .onFailure { w.logger.severe("월드 '${draft.name}' 을(를) 만들지 못했습니다: ${it.message}") }
            .getOrNull() ?: return Created.Fail("create-failed")
        if (draft.kind == TerrainKind.VOID) platform(world)
        val stored = draft.copy(load = true, seed = world.seed, spawn = spotOf(world.spawnLocation), difficulty = draft.difficulty ?: world.difficulty)
        w.store.put(stored)
        apply(world)
        return Created.Ok(world)
    }

    /** 공허 월드의 스폰 발판 — 5×5 돌, 그 위에 스폰. */
    private fun platform(world: World) {
        for (x in -2..2) for (z in -2..2) world.getBlockAt(x, 63, z).type = Material.STONE
        world.setSpawnLocation(Location(world, 0.5, 64.0, 0.5))
    }

    /** 항목은 있는데 내려 둔 월드를 다시 불러온다. 켤 때 불러오기도 켠다. */
    fun load(name: String): World? {
        Bukkit.getWorld(name)?.let { return it }
        val entry = w.store.get(name) ?: return null
        val world = runCatching { creator(entry).createWorld() }
            .onFailure { w.logger.severe("월드 '$name' 을(를) 불러오지 못했습니다: ${it.message}") }
            .getOrNull() ?: return null
        w.store.put(entry.copy(load = true))
        apply(world)
        return world
    }

    /** 폴더만 있는 월드를 가져온다 — 차원을 고르게 한다(폴더만 보고는 알 수 없다). */
    fun import(name: String, environment: World.Environment): World? {
        if (Bukkit.getWorld(name) != null || w.store.get(name) != null) return null
        val entry = WorldEntry(name = name, load = true, environment = environment)
        val world = runCatching { creator(entry).createWorld() }
            .onFailure { w.logger.severe("월드 '$name' 을(를) 가져오지 못했습니다: ${it.message}") }
            .getOrNull() ?: return null
        w.store.put(entry.copy(seed = world.seed, difficulty = world.difficulty, spawn = spotOf(world.spawnLocation)))
        return world
    }

    /** 가져올 수 있는 폴더 — 불러오지 않았고 항목도 없는 월드 폴더(`level.dat` 이 있는 것). */
    fun candidates(): List<String> {
        val known = Bukkit.getWorlds().map { it.name.lowercase() }.toSet() + w.store.all().map { it.name.lowercase() }
        val dims = File(main.worldFolder, "dimensions/minecraft").listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in VANILLA_DIMENSIONS && File(it, "level.dat").isFile }
        val legacy = Bukkit.getWorldContainer().listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "level.dat").isFile && !it.name.equals(main.name, true) }
        return (dims + legacy).map { it.name }.filter { it.lowercase() !in known }.distinct().sorted()
    }

    /** 이 이름의 월드 폴더가 있을 수 있는 곳(Paper 26 은 본 월드 안 `dimensions/minecraft/<이름>`, 옛 방식은 서버 폴더 바로 아래). */
    fun folders(name: String): List<File> =
        listOf(File(main.worldFolder, "dimensions/minecraft/$name"), File(Bukkit.getWorldContainer(), name)).distinct()

    // --- 내리기·지우기 -------------------------------------------------------------------------

    /** 내린다. 안에 있던 사람은 본 월드 스폰으로. 바닐라 셋은 내리지 않는다. 켤 때 불러오기도 끈다. */
    fun unload(world: World): Boolean {
        if (isVanilla(world.name)) return false
        snapshot(world)
        evacuate(world)
        if (!Bukkit.unloadWorld(world, true)) return false
        w.store.get(world.name)?.let { w.store.put(it.copy(load = false)) }
        return true
    }

    /** 지운다 — 내리고 항목을 지우고 폴더를 워커에서 지운다. 바닐라 셋은 지우지 않는다. */
    fun delete(name: String): Boolean {
        if (isVanilla(name)) return false
        Bukkit.getWorld(name)?.let { world ->
            evacuate(world)
            if (!Bukkit.unloadWorld(world, false)) return false
        }
        w.store.remove(name)
        val targets = folders(name).filter { it.exists() }
        w.io.asyncRun {
            for (folder in targets) if (!folder.deleteRecursively()) w.logger.warning("월드 폴더를 다 지우지 못했습니다: ${folder.path}")
        }
        return true
    }

    /** 관리자 이동(목록의 우클릭·`/월드 이동`) — core `Realms.travel`(모드가 있으면 모드 공급처가 몸을 바꾼다). */
    fun travel(player: org.bukkit.entity.Player, to: Location) = kr.inmc.core.integration.Realms.travel(player, to)

    private fun evacuate(world: World) {
        val out = main.spawnLocation
        for (player in world.players.toList()) player.teleport(out)
    }

    // --- 플러그인 값이 이긴다 ----------------------------------------------------------------------

    /** 저장된 난이도·게임 규칙·스폰을 덮어쓴다. 항목이 없으면(다른 플러그인의 월드) 아무것도 안 한다. */
    fun apply(world: World) {
        val entry = w.store.get(world.name) ?: return
        applying = true
        try {
            entry.difficulty?.let { if (world.difficulty != it) world.difficulty = it }
            for ((key, value) in entry.rules) {
                val rule = rule(key) ?: continue
                setRaw(world, rule, value)
            }
            entry.spawn?.let { s -> world.setSpawnLocation(Location(world, s.x, s.y, s.z, s.yaw, s.pitch)) }
        } finally {
            applying = false
        }
    }

    /**
     * 지금 월드의 값을 항목에 받아 적는다(저장 때·끌 때·내릴 때). 규칙은 **기본값과 다른 것만**. 바뀐 것이 있을 때만 쓴다.
     * 항목이 없는 월드는 안 한다(위 KDoc).
     */
    fun snapshot(world: World) {
        val entry = w.store.get(world.name) ?: return
        val rules = LinkedHashMap<String, String>()
        for (rule in rules()) {
            val value = runCatching { world.getGameRuleValue(rule) }.getOrNull() ?: continue
            val default = runCatching { world.getGameRuleDefault(rule) }.getOrNull()
            if (value != default) rules[key(rule)] = value.toString()
        }
        val next = entry.copy(rules = rules, difficulty = world.difficulty, spawn = spotOf(world.spawnLocation))
        if (next != entry) w.store.put(next)
    }

    /** 사건으로 받은 규칙 하나 — 기본값이면 지운다. */
    fun capture(world: World, rule: GameRule<*>, value: String) {
        val entry = w.store.get(world.name) ?: return
        val default = runCatching { world.getGameRuleDefault(rule) }.getOrNull()?.toString()
        val rules = LinkedHashMap(entry.rules)
        if (value == default) rules.remove(key(rule)) else rules[key(rule)] = value
        if (rules != entry.rules) w.store.put(entry.copy(rules = rules))
    }

    fun captureDifficulty(world: World, difficulty: org.bukkit.Difficulty) {
        val entry = w.store.get(world.name) ?: return
        if (entry.difficulty != difficulty) w.store.put(entry.copy(difficulty = difficulty))
    }

    // --- 화면에서 바꾸기 ------------------------------------------------------------------------------

    /** 규칙 하나를 바꾸고 항목에 남긴다(항목이 없으면 만든다 — 이제부터 우리 값이 이긴다). [raw] 를 못 읽으면 false. */
    fun setRule(world: World, rule: GameRule<*>, raw: String): Boolean {
        if (parse(rule, raw) == null) return false
        ensureEntry(world)
        setRaw(world, rule, raw)
        capture(world, rule, parse(rule, raw).toString())
        return true
    }

    fun setDifficulty(world: World, difficulty: org.bukkit.Difficulty) {
        ensureEntry(world)
        world.difficulty = difficulty
        captureDifficulty(world, difficulty)
    }

    fun setSpawn(world: World, at: Location) {
        ensureEntry(world)
        world.setSpawnLocation(at)
        w.store.get(world.name)?.let { w.store.put(it.copy(spawn = spotOf(at))) }
    }

    fun setPortal(world: World, nether: Boolean, link: PortalLink) {
        val entry = ensureEntry(world)
        w.store.put(if (nether) entry.copy(nether = link) else entry.copy(end = link))
    }

    /** 항목이 없는 월드(다른 플러그인이 만든 것)를 화면에서 처음 고칠 때 — 지금 값으로 만든다. */
    private fun ensureEntry(world: World): WorldEntry = w.store.get(world.name) ?: defaultEntry(world.name, world.environment)
        .copy(difficulty = world.difficulty, spawn = spotOf(world.spawnLocation))
        .also { w.store.put(it) }

    // --- 게임 규칙 도구 ----------------------------------------------------------------------------

    fun rules(): List<GameRule<*>> = runCatching { RegistryAccess.registryAccess().getRegistry(RegistryKey.GAME_RULE).toList() }
        .getOrElse { emptyList() }
        .sortedBy { key(it) }

    fun rule(key: String): GameRule<*>? = rules().firstOrNull { key(it) == key }

    fun key(rule: GameRule<*>): String = rule.key.asString()

    /** 규칙의 값 글자를 그 형으로(참/거짓 · 정수). 못 읽으면 null. */
    fun parse(rule: GameRule<*>, raw: String): Any? = when (rule.type) {
        Boolean::class.javaObjectType -> raw.trim().lowercase().let { if (it == "true") true else if (it == "false") false else null }
        Int::class.javaObjectType -> raw.trim().toIntOrNull()
        else -> null
    }

    fun isBoolean(rule: GameRule<*>): Boolean = rule.type == Boolean::class.javaObjectType

    @Suppress("UNCHECKED_CAST")
    private fun setRaw(world: World, rule: GameRule<*>, raw: String) {
        val value = parse(rule, raw) ?: return
        runCatching { world.setGameRule(rule as GameRule<Any>, value) }
            .onFailure { w.logger.warning("월드 '${world.name}' 의 규칙 ${key(rule)} = $raw 을(를) 걸지 못했습니다: ${it.message}") }
    }

    // --- 생물군계 -------------------------------------------------------------------------------------

    fun biome(key: String): Biome? = NamespacedKey.fromString(key)?.let {
        runCatching { RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(it) }.getOrNull()
    }

    fun biomes(): List<Biome> = runCatching { RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).toList() }.getOrElse { emptyList() }

    companion object {
        /** Paper 26 의 바닐라 차원 폴더 — 가져오기 후보에서 뺀다. */
        val VANILLA_DIMENSIONS = setOf("overworld", "the_nether", "the_end")

        fun spotOf(at: Location): Spot = Spot(at.x, at.y, at.z, at.yaw, at.pitch)
    }
}
