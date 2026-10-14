package com.inmc.worlds.body

import com.inmc.worlds.Worlds
import kr.inmc.core.util.AtomicFiles
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.MemoryConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

/**
 * scope 파일 — `profiles/<uuid>/<scope>.yml`(같이 쓰는 칸 `""` 은 `_shared.yml`). 부분마다 절 하나, 모드 파일에는 마지막 자리(`location`)도.
 *
 * **메인 스레드에서 바로, 원자적으로** 읽고 쓴다(core [AtomicFiles]) — 바꾸는 순간이 몸 바꾸기의 한가운데라 워커로 넘기면 순서가 흐트러진다
 * (커스텀아이템 장착 칸 `EquipmentStore` 와 같은 까닭 — 이 워크스페이스에서 메인 스레드 디스크 쓰기를 일부러 하는 자리). 파일은 몇 KB.
 */
class ProfileFiles(private val w: Worlds) {

    fun folder(player: UUID): File = w.io.file("profiles", player.toString())

    fun file(player: UUID, scope: String): File = File(folder(player), (scope.ifEmpty { SHARED }) + ".yml")

    fun exists(player: UUID, scope: String): Boolean = file(player, scope).isFile

    fun load(player: UUID, scope: String): YamlConfiguration {
        val target = file(player, scope)
        return if (target.isFile) YamlConfiguration.loadConfiguration(target) else YamlConfiguration()
    }

    fun read(player: UUID, scope: String, part: Part): ConfigurationSection? = load(player, scope).getConfigurationSection(part.id)

    /** 한 scope 의 여러 절을 한 번에 — 읽고, 바꾸고, 원자적으로 쓴다. */
    fun write(player: UUID, scope: String, sections: Map<String, ConfigurationSection?>) {
        val yaml = load(player, scope)
        for ((name, section) in sections) {
            yaml.set(name, null)
            if (section != null) copy(section, yaml.createSection(name))
        }
        val target = file(player, scope)
        target.parentFile.mkdirs()
        AtomicFiles.write(target, yaml.saveToString())
    }

    /** 이 사람의 scope 전부(파일 이름에서). */
    fun scopes(player: UUID): List<String> = folder(player).listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".yml") }
        .map { it.name.removeSuffix(".yml").let { n -> if (n == SHARED) "" else n } }

    companion object {
        const val SHARED = "_shared"

        fun copy(from: ConfigurationSection, to: ConfigurationSection) {
            for (key in from.getKeys(false)) {
                val value = from.get(key)
                if (value is ConfigurationSection) copy(value, to.createSection(key)) else to.set(key, value)
            }
        }

        fun section(): ConfigurationSection = MemoryConfiguration()
    }
}
