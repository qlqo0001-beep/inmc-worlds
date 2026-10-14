package com.inmc.worlds.mode

import com.inmc.worlds.Worlds
import kr.inmc.core.integration.Realms
import kr.inmc.core.util.AtomicFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * `modes.yml` — 모드들 · 빌려 쓰는 월드 · 기능 토글("같이" 인 것). **화면이 써 넣는 파일**.
 *
 * - 모든 월드는 모드 하나에 들거나 **빌려 쓰는 월드**(던전처럼 여러 모드가 들어가는 곳 — 안에서는 들어온 모드를 유지)다.
 *   처음 보는 월드는 자기 모드를 만들어 준다([ensureWorld], 사용자 결정 2026-10-15 "월드가 생성되면 개별적인 모드").
 * - 읽기는 아무 스레드에서나(불변 스냅샷을 바꿔 끼운다 — core `Realms` 가 비동기에서도 묻는다). 고치기는 메인.
 */
class ModeStore(private val w: Worlds) {

    private data class Snapshot(
        val defaultId: String,
        val modes: Map<String, Mode>,
        val borrowed: Set<String>,
        val features: Map<String, Boolean>,
    ) {
        /** 월드 이름(소문자) → 모드 id. */
        val byWorld: Map<String, String> = modes.values.flatMap { m -> m.worlds.map { it.lowercase() to m.id } }.toMap()
    }

    @Volatile
    private var s = Snapshot(DEFAULT_ID, emptyMap(), emptySet(), emptyMap())

    private val file: File get() = w.io.file(FILE)

    @Volatile
    private var quiet = false

    val defaultId: String get() = s.defaultId

    fun all(): List<Mode> = s.modes.values.toList()

    fun get(id: String): Mode? = s.modes[id]

    /** 이 월드의 모드 id. 빌려 쓰는 월드면 null. 아직 모르는 월드는 만들어질 모드의 id(이름에서 — [ensureWorld] 와 같은 규칙). */
    fun modeOfWorld(world: String): String? {
        val lower = world.lowercase()
        if (lower in s.borrowed) return null
        return s.byWorld[lower] ?: Mode.idFor(world)
    }

    fun isBorrowed(world: String): Boolean = world.lowercase() in s.borrowed

    fun borrowed(): Set<String> = s.borrowed

    /** 관리자가 "같이" 로 두었는가 — 고르지 않았으면 등록한 기본값. */
    fun isShared(key: String): Boolean = s.features[key] ?: (Realms.feature(key)?.defaultShared ?: false)

    // --- 고치기(메인) ---------------------------------------------------------------------------

    /**
     * 모르는 월드면 자기 모드를 만든다(이름 = [display]). 빌려 쓰는 월드·이미 든 월드는 그대로. 만든 모드의 id(또는 지금 모드)를 돌려준다.
     */
    fun ensureWorld(world: String, display: String): String? {
        val lower = world.lowercase()
        if (lower in s.borrowed) return null
        s.byWorld[lower]?.let { return it }
        var id = Mode.idFor(world)
        var n = 2
        while (s.modes.containsKey(id)) id = Mode.idFor(world).take(29) + "_" + n++
        put(Mode(id = id, name = display, worlds = listOf(world)))
        return id
    }

    fun put(mode: Mode) {
        // 한 월드는 한 모드에만 — 이 모드가 가져간 월드는 다른 모드에서 뺀다.
        val taken = mode.worlds.map { it.lowercase() }.toSet()
        val others = s.modes.filterKeys { it != mode.id }.mapValues { (_, m) -> m.copy(worlds = m.worlds.filter { it.lowercase() !in taken }) }
        s = s.copy(modes = others + (mode.id to mode), borrowed = s.borrowed - taken)
        save()
    }

    /** 월드를 이 모드로 옮긴다. 떠난 모드가 비면(기본 모드 빼고) 지운다 — 월드마다 저절로 생긴 모드가 쌓이지 않게. */
    fun assign(world: String, modeId: String) {
        val target = get(modeId) ?: return
        if (target.worlds.any { it.equals(world, true) }) return
        val previous = s.byWorld[world.lowercase()]?.let(::get)
        put(target.copy(worlds = target.worlds + world))
        if (previous != null && previous.id != modeId && previous.id != s.defaultId && get(previous.id)?.worlds?.isEmpty() == true) remove(previous.id)
    }

    /** 지운다 — 그 모드의 월드는 다음 [ensureWorld] 에서 각자 자기 모드가 된다. 기본 모드는 못 지운다. */
    fun remove(id: String): Mode? {
        if (id == s.defaultId) return null
        val gone = s.modes[id] ?: return null
        s = s.copy(modes = s.modes - id)
        save()
        return gone
    }

    fun setBorrowed(world: String, borrowed: Boolean) {
        val lower = world.lowercase()
        if (borrowed) {
            val modes = s.modes.mapValues { (_, m) -> m.copy(worlds = m.worlds.filter { it.lowercase() != lower }) }
            s = s.copy(modes = modes, borrowed = s.borrowed + lower)
        } else {
            s = s.copy(borrowed = s.borrowed - lower)
        }
        save()
    }

    fun setShared(key: String, shared: Boolean) {
        s = s.copy(features = s.features + (key to shared))
        save()
    }

    /** 검증기 — 이 안의 바뀜은 메모리에서만, 끝나면 되돌린다. */
    fun <T> quietly(block: () -> T): T {
        val before = s
        quiet = true
        try {
            return block()
        } finally {
            s = before
            quiet = false
        }
    }

    // --- 파일 ---------------------------------------------------------------------------------

    /** 켤 때 그 자리에서. 파일이 없으면 기본 모드(바닐라 셋) + 빌려 쓰는 월드(던전)로 시작한다. */
    fun loadNow(vanilla: List<String>) {
        if (!file.isFile) {
            s = Snapshot(DEFAULT_ID, mapOf(DEFAULT_ID to Mode(DEFAULT_ID, DEFAULT_NAME, worlds = vanilla)), setOf(DUNGEON_WORLD), emptyMap())
            save()
            return
        }
        val yaml = yaml().apply { load(file) }
        val modes = yaml.getConfigurationSection("modes")?.let { sec ->
            sec.getKeys(false).filter { Realms.MODE_ID.matches(it) }.mapNotNull { id -> sec.getConfigurationSection(id)?.let { Mode.read(id, it) } }
        }.orEmpty().associateBy { it.id }
        val defaultId = yaml.getString("default")?.takeIf { it in modes } ?: modes.keys.firstOrNull() ?: DEFAULT_ID
        val features = yaml.getConfigurationSection("features")?.let { f -> f.getKeys(false).associateWith { f.getBoolean(it) } }.orEmpty()
        s = Snapshot(
            defaultId,
            modes.ifEmpty { mapOf(DEFAULT_ID to Mode(DEFAULT_ID, DEFAULT_NAME, worlds = vanilla)) },
            yaml.getStringList("borrowed").map { it.lowercase() }.toSet(),
            features,
        )
    }

    private fun render(): String {
        val snap = s
        val yaml = yaml()
        yaml.options().setHeader(HEADER)
        yaml.set("default", snap.defaultId)
        yaml.set("borrowed", snap.borrowed.sorted())
        val f = yaml.createSection("features")
        for ((key, value) in snap.features.toSortedMap()) f.set(key, value)
        val sec = yaml.createSection("modes")
        for (mode in snap.modes.values) mode.write(sec.createSection(mode.id))
        return yaml.saveToString()
    }

    private fun save() {
        if (quiet) return
        val text = render()
        w.io.asyncRun {
            runCatching { AtomicFiles.write(file, text) }.onFailure { w.logger.severe("modes.yml 저장 실패: ${it.message}") }
        }
    }

    fun saveBlocking() {
        runCatching { AtomicFiles.write(file, render()) }.onFailure { w.logger.severe("modes.yml 저장 실패: ${it.message}") }
    }

    companion object {
        const val FILE = "modes.yml"
        const val DEFAULT_ID = "main"
        const val DEFAULT_NAME = "<green>기본 모드</green>"
        const val DUNGEON_WORLD = "inmc_dungeon"

        private val HEADER = listOf(
            "모드 — 월드 묶음. 모드마다 다른 서버처럼(가방·장비·홈 … 이 따로). 빌려 쓰는 월드는 들어온 모드를 그대로 쓴다.",
            "features: 기능마다 모드끼리 같이 쓰는가(true = 같이). /모드 관리 화면이 써 넣습니다 — 손으로 고치지 마세요.",
        )

        fun yaml(): YamlConfiguration = YamlConfiguration().apply { options().pathSeparator('|') }
    }
}
