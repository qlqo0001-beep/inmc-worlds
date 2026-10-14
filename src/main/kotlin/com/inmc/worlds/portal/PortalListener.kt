package com.inmc.worlds.portal

import com.inmc.worlds.Worlds
import com.inmc.worlds.world.PortalLink
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPortalEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause

/**
 * 포탈 연결(Multiverse-NetherPortals 대신). 월드마다 네더 포탈·엔드 포탈이 어디로 가는지 — 바닐라 · 막기 · 이 월드로.
 *
 * - **네더 포탈 → 월드**: 차원 비율대로(오버월드 1 · 네더 8) 좌표를 옮기고 그 둘레에서 포탈을 찾거나 만든다(Paper 의 포탈 찾기에 맡긴다).
 * - **엔드 포탈 → 엔드 월드**: 바닐라처럼 (100, 49, 0) 흑요석 발판 위로. **엔드 포탈 → 그 밖의 월드**: 그 월드의 스폰(엔드에서 나가는 길).
 * - 막으면 플레이어에게 알린다. 엔티티(아이템·몹)는 같은 연결을 따르고, 막히면 조용히 안 간다.
 *
 * HIGH — 땅 보호 플러그인이 먼저 막을 기회를 준다. 연결이 바닐라면 손대지 않는다.
 */
class PortalListener(private val w: Worlds) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerPortal(event: PlayerPortalEvent) {
        val nether = when (event.cause) {
            TeleportCause.NETHER_PORTAL -> true
            TeleportCause.END_PORTAL -> false
            else -> return
        }
        when (val target = resolve(event.from, nether)) {
            Target.Vanilla -> Unit
            Target.Blocked -> {
                event.isCancelled = true
                w.messages.send(event.player, "portal-blocked")
            }
            Target.Missing -> {
                event.isCancelled = true
                w.messages.send(event.player, "portal-missing")
            }
            is Target.To -> event.to = target.location
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityPortal(event: EntityPortalEvent) {
        val nether = when (event.portalType) {
            org.bukkit.PortalType.NETHER -> true
            org.bukkit.PortalType.ENDER -> false
            else -> return
        }
        when (val target = resolve(event.from, nether)) {
            Target.Vanilla -> Unit
            Target.Blocked, Target.Missing -> event.isCancelled = true
            is Target.To -> event.to = target.location
        }
    }

    sealed interface Target {
        data object Vanilla : Target
        data object Blocked : Target
        data object Missing : Target
        data class To(val location: Location) : Target
    }

    fun resolve(from: Location, nether: Boolean): Target {
        val world = from.world ?: return Target.Vanilla
        val entry = w.service.entryOf(world)
        val link = if (nether) entry.nether else entry.end
        return when (link.kind) {
            PortalLink.Kind.VANILLA -> Target.Vanilla
            PortalLink.Kind.NONE -> Target.Blocked
            PortalLink.Kind.WORLD -> {
                val target = link.world?.let(Bukkit::getWorld) ?: return Target.Missing
                Target.To(if (nether) netherDestination(from, target) else endDestination(target))
            }
        }
    }

    private fun netherDestination(from: Location, target: World): Location {
        val ratio = scale(from.world!!.environment) / scale(target.environment)
        val y = from.y.coerceIn(target.minHeight + 1.0, target.maxHeight - 2.0)
        return Location(target, from.x * ratio, y, from.z * ratio, from.yaw, from.pitch)
    }

    private fun endDestination(target: World): Location {
        if (target.environment != World.Environment.THE_END) return target.spawnLocation
        platform(target)
        return Location(target, 100.5, 49.0, 0.5, 90f, 0f)
    }

    /** 바닐라 엔드 발판 — (100, 48, 0) 둘레 5×5 흑요석, 그 위 세 칸은 비운다. */
    private fun platform(world: World) {
        for (x in 98..102) for (z in -2..2) {
            world.getBlockAt(x, 48, z).type = Material.OBSIDIAN
            for (y in 49..51) world.getBlockAt(x, y, z).type = Material.AIR
        }
    }

    /** 좌표 비율 — 오버월드·엔드 1, 네더 8(바닐라 차원 설정과 같다). */
    private fun scale(environment: World.Environment): Double = if (environment == World.Environment.NETHER) 8.0 else 1.0
}
