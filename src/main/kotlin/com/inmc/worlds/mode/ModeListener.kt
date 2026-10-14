package com.inmc.worlds.mode

import com.inmc.worlds.Worlds
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent
import kr.inmc.core.integration.PlaceNames
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.world.WorldLoadEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 모드의 길목 — 몸 맞추기의 때, 모드를 넘는 이동 막기, 부활 자리, 엔티티 포탈, 복사 막기, 새 월드의 모드.
 *
 * | 때 | 우선순위 | 하는 일 |
 * |---|---|---|
 * | 접속 | LOWEST | 몸 맞추기(essentials 접속 처리·대기 편집(MONITOR+2틱)보다 앞) |
 * | 월드가 바뀜 | LOWEST | 모드가 바뀌었으면 몸 맞추기 |
 * | 순간이동 | HIGHEST | 다른 모드로 가면 취소 — 통과는 `Realms.travel`·관리자(`inmcworlds.bypass.travel`). 빌려 쓰는 월드로는 막지 않는다(그 월드 주인이 판단) |
 * | 포탈 | (취소 안 함) | 모드를 넘어도 된다 — 몸이 바뀐다(사용자 결정 2026-10-15) |
 * | 부활 | HIGHEST | 다른 모드 월드로 부활하면 그 사람 모드의 스폰으로 |
 * | 엔티티 포탈 · 엔티티 순간이동 | HIGHEST | 플레이어가 아닌 것은 모드를 못 넘는다(아이템·짐 든 것·탈것) |
 */
class ModeListener(private val w: Worlds) : Listener {

    /** 접속할 때 서버가 내려놓으려던 자리(= 나간 자리) — 다른 모드로 접속되면 그 모드의 마지막 자리로 적는다. */
    private val joinFrom = ConcurrentHashMap<UUID, Location>()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onSpawnLocation(event: AsyncPlayerSpawnLocationEvent) {
        joinFrom[event.connection.profile.id ?: return] = event.spawnLocation.clone()
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        joinFrom.remove(player.uniqueId)?.let { from ->
            val fromMode = w.realm.modeOf(from)
            if (fromMode != null && fromMode != w.realm.modeAt(player)) w.realm.departing(player, from)
        }
        w.realm.reconcile(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoined(event: PlayerJoinEvent) {
        event.player.scheduler.run(w.plugin, { w.tabs.refresh() }, null)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        w.realm.forget(event.player.uniqueId)
        joinFrom.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onWorldChanged(event: PlayerChangedWorldEvent) {
        val player = event.player
        if (w.realm.bodyMode(player) != w.realm.modeAt(player)) w.realm.reconcile(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (!crosses(event.player, event.to)) return
        if (w.realm.hasPass(event.player.uniqueId) || event.player.hasPermission(RealmsProvider.BYPASS)) return
        event.isCancelled = true
        w.messages.send(event.player, "travel-blocked")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleported(event: PlayerTeleportEvent) {
        if (crosses(event.player, event.to)) w.realm.departing(event.player, event.from)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPortal(event: PlayerPortalEvent) {
        if (crosses(event.player, event.to)) w.realm.departing(event.player, event.from)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val mode = w.realm.bodyMode(player) ?: return
        val target = w.realm.modeOf(event.respawnLocation) ?: return
        if (target != mode) event.respawnLocation = w.realm.spawn(mode)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityPortal(event: EntityPortalEvent) {
        if (event.entity is Player) return
        val to = event.to ?: return
        val from = w.realm.modeOf(event.from) ?: return
        val target = w.realm.modeOf(to) ?: return
        if (from != target) event.isCancelled = true
    }

    /** 탈것·끈에 묶인 동물·짐 든 동물이 사람을 따라 순간이동해도 모드는 못 넘는다(essentials 포탈이 탈것을 같이 옮긴다). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityTeleport(event: EntityTeleportEvent) {
        if (event.entity is Player) return
        val to = event.to ?: return
        val from = w.realm.modeOf(event.from) ?: return
        val target = w.realm.modeOf(to) ?: return
        if (from != target) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onCreative(event: InventoryCreativeEvent) {
        if (w.realm.creativeBlocked(event.whoClicked.uniqueId)) event.isCancelled = true
    }

    /** 처음 보는 월드(다른 플러그인이 만든 것 포함)는 자기 모드를 가진다 — 빌려 쓰는 월드 빼고. */
    @EventHandler(priority = EventPriority.LOW)
    fun onWorldLoad(event: WorldLoadEvent) {
        val name = event.world.name
        w.modes.ensureWorld(name, PlaceNames.world(name) ?: "<white>$name</white>")
    }

    /** 이 이동이 다른 모드로 가는가 — 빌려 쓰는 월드로는 아니다. */
    private fun crosses(player: Player, to: Location?): Boolean {
        val target = to?.let(w.realm::modeOf) ?: return false
        return target != w.realm.modeAt(player)
    }
}
