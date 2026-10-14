package com.inmc.worlds.world

import com.inmc.worlds.Worlds
import kr.inmc.core.util.AtomicFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * `worlds.yml` — 월드마다 만드는 법과 플러그인이 이기는 설정. **화면이 써 넣는 파일**이라 사람이 고칠 일은 없다.
 *
 * - 켤 때 **그 자리에서** 읽는다 — 월드를 불러오는 것이 켤 때의 일이라 기다릴 수 없다(작은 파일).
 * - 바뀌면 메인에서 글자를 뜨고 워커에서 원자적으로 쓴다(core [AtomicFiles]).
 * - 월드 이름에 점이 들어갈 수 있어 경로 구분자를 `|` 로 쓴다(타이틀포지 `places.yml` 과 같은 까닭).
 */
class WorldStore(private val w: Worlds) {

    @Volatile
    private var entries: Map<String, WorldEntry> = emptyMap()

    /** Multiverse `worlds.yml` 을 한 번 가져왔는가(다시 가져오지 않게). */
    @Volatile
    var multiverseImported: Boolean = false
        private set

    private val file: File get() = w.io.file(FILE)

    /** 대소문자를 가리지 않는다 — Bukkit 월드 이름과 같다. */
    fun get(name: String): WorldEntry? = entries[name.lowercase()]

    fun all(): List<WorldEntry> = entries.values.sortedBy { it.name }

    fun put(entry: WorldEntry) {
        entries = entries + (entry.name.lowercase() to entry)
        save()
    }

    /** 없으면 [create] 로 만들어 고친다. */
    fun update(name: String, create: () -> WorldEntry, change: (WorldEntry) -> WorldEntry): WorldEntry {
        val next = change(get(name) ?: create())
        put(next)
        return next
    }

    fun remove(name: String) {
        entries = entries - name.lowercase()
        save()
    }

    fun markMultiverseImported() {
        multiverseImported = true
        save()
    }

    fun loadNow() {
        if (!file.isFile) return
        val yaml = yaml().apply { load(file) }
        multiverseImported = yaml.getBoolean("multiverse-imported", false)
        val section = yaml.getConfigurationSection("worlds") ?: return
        entries = section.getKeys(false).mapNotNull { name ->
            val s = section.getConfigurationSection(name) ?: return@mapNotNull null
            runCatching { WorldEntry.read(name, s) }
                .onFailure { w.logger.warning("worlds.yml 의 '$name' 을(를) 읽지 못했습니다: ${it.message}") }
                .getOrNull()
        }.associateBy { it.name.lowercase() }
    }

    private fun render(): String {
        val yaml = yaml()
        yaml.options().setHeader(HEADER)
        yaml.set("multiverse-imported", multiverseImported)
        val section = yaml.createSection("worlds")
        for (entry in all()) entry.write(section.createSection(entry.name))
        return yaml.saveToString()
    }

    /** 검증기 — 이 안의 바뀜은 메모리에서만, 끝나면 전으로 되돌린다(파일에 쓰지 않는다). 메인 스레드, 한 틱 안에. */
    fun <T> quietly(block: () -> T): T {
        val before = entries
        val wasImported = multiverseImported
        quiet = true
        try {
            return block()
        } finally {
            entries = before
            multiverseImported = wasImported
            quiet = false
        }
    }

    @Volatile
    private var quiet = false

    private fun save() {
        if (quiet) return
        val text = render()
        w.io.asyncRun {
            runCatching { AtomicFiles.write(file, text) }
                .onFailure { w.logger.severe("worlds.yml 저장 실패: ${it.message}") }
        }
    }

    /** 끌 때 — 워커가 곧 닫히므로 여기서 바로. */
    fun saveBlocking() {
        runCatching { AtomicFiles.write(file, render()) }
            .onFailure { w.logger.severe("worlds.yml 저장 실패: ${it.message}") }
    }

    companion object {
        const val FILE = "worlds.yml"

        private val HEADER = listOf(
            "월드 — 만드는 법과 플러그인이 이기는 설정(난이도·게임 규칙·스폰·포탈 연결).",
            "/월드 화면이 써 넣습니다. 손으로 고치지 마세요 — 서버가 켜져 있으면 다음 저장이 덮습니다.",
        )

        fun yaml(): YamlConfiguration = YamlConfiguration().apply {
            options().pathSeparator('|')
        }
    }
}
