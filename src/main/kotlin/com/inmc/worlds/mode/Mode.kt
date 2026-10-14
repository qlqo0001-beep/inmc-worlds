package com.inmc.worlds.mode

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection

/** 모드 스폰 자리(월드 이름 포함). */
data class ModeSpawn(val world: String, val x: Double, val y: Double, val z: Double, val yaw: Float = 0f, val pitch: Float = 0f)

/**
 * 모드 하나 — 월드 묶음. 모드마다 다른 서버처럼 돈다(가방·장비·홈 … 이 모드마다 따로).
 *
 * @param chatGroup 같은 묶음끼리 채팅이 보인다. 기본은 자기 id(따로) — 다른 모드의 묶음으로 바꾸면 묶인다.
 * @param tabGroup 같은 묶음끼리 탭 목록에 보인다.
 * @param gameMode 들어올 때 바꿀 게임 모드. null = 그대로.
 */
data class Mode(
    val id: String,
    val name: String,
    val icon: Material = Material.GRASS_BLOCK,
    val worlds: List<String> = emptyList(),
    val spawn: ModeSpawn? = null,
    val gameMode: GameMode? = null,
    val chatGroup: String = id,
    val tabGroup: String = id,
) {
    fun write(section: ConfigurationSection) {
        section.set("name", name)
        section.set("icon", icon.name.lowercase())
        section.set("worlds", worlds)
        spawn?.let { s ->
            val sp = section.createSection("spawn")
            sp.set("world", s.world); sp.set("x", s.x); sp.set("y", s.y); sp.set("z", s.z); sp.set("yaw", s.yaw.toDouble()); sp.set("pitch", s.pitch.toDouble())
        }
        section.set("gamemode", gameMode?.name?.lowercase())
        section.set("chat-group", chatGroup)
        section.set("tab-group", tabGroup)
    }

    companion object {
        fun read(id: String, section: ConfigurationSection): Mode = Mode(
            id = id,
            name = section.getString("name")?.takeIf { it.isNotBlank() } ?: id,
            icon = section.getString("icon")?.let { Material.matchMaterial(it) }?.takeIf { it.isItem } ?: Material.GRASS_BLOCK,
            worlds = section.getStringList("worlds"),
            spawn = section.getConfigurationSection("spawn")?.let { s ->
                val world = s.getString("world") ?: return@let null
                ModeSpawn(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), s.getDouble("yaw").toFloat(), s.getDouble("pitch").toFloat())
            },
            gameMode = GameMode.entries.firstOrNull { it.name.equals(section.getString("gamemode"), true) },
            chatGroup = section.getString("chat-group")?.takeIf { it.isNotBlank() } ?: id,
            tabGroup = section.getString("tab-group")?.takeIf { it.isNotBlank() } ?: id,
        )

        /** 월드 이름 → 모드 id 후보(규칙 `[a-z0-9_-]{1,32}` 에 맞게). 겹치면 부르는 쪽이 뒤에 숫자를 붙인다. */
        fun idFor(world: String): String {
            val base = world.lowercase().map { if (it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-') it else '_' }.joinToString("")
            return base.take(32).ifEmpty { "mode" }
        }
    }
}
