package com.inmc.worlds.mode

import com.inmc.worlds.Worlds
import kr.inmc.core.integration.Realms
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** core [Realms] 의 공급처 — 이 플러그인의 모드. 아무 스레드 메서드는 메모리 스냅샷만 본다. */
class RealmsProvider(private val w: Worlds) : Realms.Provider {

    override fun of(playerId: UUID): String = w.realm.of(playerId)

    override fun ofWorld(world: String): String? = w.modes.modeOfWorld(world)

    override fun name(mode: String): String = w.modes.get(mode)?.name ?: mode

    override fun all(): List<String> = w.modes.all().map { it.id }

    override fun isShared(key: String): Boolean = w.modes.isShared(key)

    override fun spawn(mode: String): Location? = if (w.modes.get(mode) != null) w.realm.spawn(mode) else null

    override fun setSpawn(mode: String, location: Location): Boolean {
        val m = w.modes.get(mode) ?: return false
        val world = location.world ?: return false
        w.modes.put(m.copy(spawn = ModeSpawn(world.name, location.x, location.y, location.z, location.yaw, location.pitch)))
        return true
    }

    override fun sameChat(a: UUID, b: UUID): Boolean = group(of(a)) { it.chatGroup } == group(of(b)) { it.chatGroup }

    override fun sameTab(a: UUID, b: UUID): Boolean = group(of(a)) { it.tabGroup } == group(of(b)) { it.tabGroup }

    private fun group(mode: String, pick: (Mode) -> String): String = w.modes.get(mode)?.let(pick) ?: mode

    override fun canTravel(player: Player, to: Location): Boolean {
        if (player.hasPermission(BYPASS)) return true
        val target = w.realm.modeOf(to) ?: return true
        return target == w.realm.modeAt(player)
    }

    override fun travel(player: Player, to: Location): CompletableFuture<Boolean> = w.realm.travel(player, to)

    companion object {
        const val BYPASS = "inmcworlds.bypass.travel"
    }
}
