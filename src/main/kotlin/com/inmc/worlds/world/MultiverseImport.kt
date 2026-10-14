package com.inmc.worlds.world

import com.inmc.worlds.Worlds
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 처음 켤 때 한 번 — Multiverse-Core `worlds.yml` 의 월드를 우리 항목으로(불러오기·시드·생성기·난이도·스폰).
 * 사람 자료(가방)는 옮기지 않는다(본섭이 없다 — 사용자 결정 2026-10-15).
 *
 * MV 의 스폰 자리에는 `==: MVSpawnLocation` 직렬화 표시가 붙어 있다 — MV 가 없으면 그 줄 하나 때문에 파일 전체를 못 읽으므로 그 줄을 빼고 읽는다.
 */
class MultiverseImport(private val w: Worlds) {

    /** 가져온 월드 수. 가져올 파일이 없거나 이미 가져왔으면 0. */
    fun runOnce(): Int {
        if (w.store.multiverseImported) return 0
        val file = File(Bukkit.getPluginsFolder(), "Multiverse-Core/worlds.yml")
        if (!file.isFile) {
            w.store.markMultiverseImported()
            return 0
        }
        val text = file.readLines(Charsets.UTF_8).filterNot { it.trimStart().startsWith("==:") }.joinToString("\n")
        val yaml = runCatching { YamlConfiguration().apply { loadFromString(text) } }
            .onFailure { w.logger.warning("Multiverse worlds.yml 을 읽지 못했습니다: ${it.message}") }
            .getOrNull() ?: return 0
        var count = 0
        for (name in yaml.getKeys(false)) {
            val s = yaml.getConfigurationSection(name) ?: continue
            if (w.store.get(name) != null) continue
            val vanilla = w.service.isVanilla(name)
            val environment = World.Environment.entries.firstOrNull { it.name.equals(s.getString("environment"), true) } ?: World.Environment.NORMAL
            val spawn = s.getConfigurationSection("spawn-location")?.let {
                Spot(it.getDouble("x"), it.getDouble("y"), it.getDouble("z"), it.getDouble("yaw").toFloat(), it.getDouble("pitch").toFloat())
            }
            val base = w.service.defaultEntry(name, environment)
            w.store.put(base.copy(
                load = !vanilla,
                seed = if (s.isSet("seed")) s.getLong("seed") else null,
                generator = s.getString("generator")?.takeIf { it.isNotBlank() },
                difficulty = Difficulty.entries.firstOrNull { it.name.equals(s.getString("difficulty"), true) },
                spawn = spawn,
            ))
            count++
        }
        w.store.markMultiverseImported()
        if (count > 0) w.logger.info("Multiverse worlds.yml 에서 월드 ${count}개를 가져왔습니다")
        return count
    }
}
