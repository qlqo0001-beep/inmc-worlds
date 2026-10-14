package com.inmc.worlds.body

import com.inmc.worlds.Worlds
import kr.inmc.core.util.AtomicFiles
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * `players.yml` — 사람마다 마지막 모드와 가 본 모드들. 마지막 모드는 **오프라인인 사람의 모드**(core `Realms.of(uuid)` — 우편·보상이 어느 모드로 갈지),
 * 가 본 모드는 "이 모드에 처음 들어왔다"(`RealmChangeEvent.firstEntry` — 첫 킷·첫 스폰).
 *
 * 읽기는 아무 스레드에서나(동시성 맵). 켤 때 그 자리에서 읽고, 바뀌면 워커에서 원자적으로 쓴다(잃어도 다음 바뀜에 다시 적힌다 — 진짜는 몸의 PDC).
 */
class PlayerIndex(private val w: Worlds) {

    private class Row(val mode: String, val visited: Set<String>)

    private val rows = ConcurrentHashMap<UUID, Row>()

    private val file: File get() = w.io.file(FILE)

    fun mode(player: UUID): String? = rows[player]?.mode

    fun visited(player: UUID, mode: String): Boolean = rows[player]?.visited?.contains(mode) == true

    /** 이 사람의 모드를 [mode] 로, 가 본 모드에 더한다. 처음 가 본 것이면 true. */
    fun enter(player: UUID, mode: String): Boolean {
        val before = rows[player]
        val first = before?.visited?.contains(mode) != true
        if (before?.mode == mode && !first) return false
        rows[player] = Row(mode, before?.visited.orEmpty() + mode)
        save()
        return first
    }

    fun loadNow() {
        if (!file.isFile) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (key in yaml.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val s = yaml.getConfigurationSection(key) ?: continue
            val mode = s.getString("mode") ?: continue
            rows[uuid] = Row(mode, s.getStringList("visited").toSet())
        }
    }

    private fun render(): String {
        val yaml = YamlConfiguration()
        for ((uuid, row) in rows) {
            val s = yaml.createSection(uuid.toString())
            s.set("mode", row.mode)
            s.set("visited", row.visited.sorted())
        }
        return yaml.saveToString()
    }

    private fun save() {
        val text = render()
        w.io.asyncRun { runCatching { AtomicFiles.write(file, text) }.onFailure { w.logger.warning("players.yml 저장 실패: ${it.message}") } }
    }

    fun saveBlocking() {
        runCatching { AtomicFiles.write(file, render()) }.onFailure { w.logger.warning("players.yml 저장 실패: ${it.message}") }
    }

    companion object {
        const val FILE = "players.yml"
    }
}
