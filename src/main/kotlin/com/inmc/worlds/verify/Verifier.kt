package com.inmc.worlds.verify

import com.inmc.worlds.Worlds
import com.inmc.worlds.util.Ph
import com.inmc.worlds.world.PortalLink
import io.papermc.paper.event.world.WorldGameRuleChangeEvent
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameRules
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `/월드 검증` — 서버 안에서 판정을 확인한다(드랍·커스텀아이템 검증기와 같은 방식). 사건은 진짜로 쏘고, 바꾼 항목은
 * [com.inmc.worlds.world.WorldStore.quietly] 안에서만 — 파일에 쓰지 않고 끝나면 되돌린다. 바꾼 월드 값(난이도)도 되돌린다.
 *
 * `WorldLoadEvent` 는 쏘지 않는다 — 다른 플러그인(땅 보호·NPC)이 월드를 새로 읽은 것으로 알고 다시 준비한다. 덮어쓰기는 [com.inmc.worlds.world.WorldService.apply] 를 직접 부른다.
 */
class Verifier(private val w: Worlds) {

    data class Result(val name: String, val failure: String?)

    private class Check(val name: String, val run: (Player) -> String?)

    private val checks = listOf(
        Check("Multiverse 가 꺼져 있다(같이 쓰면 둘이 같은 월드를 다룬다)") { _ ->
            val on = listOf("Multiverse-Core", "Multiverse-Inventories", "Multiverse-NetherPortals").filter { Bukkit.getPluginManager().getPlugin(it)?.isEnabled == true }
            if (on.isEmpty()) null else "켜져 있음: ${on.joinToString()}"
        },
        Check("켤 때 불러오는 월드가 다 열렸다") { _ ->
            val missing = w.store.all().filter { it.load && Bukkit.getWorld(it.name) == null }.map { it.name }
            if (missing.isEmpty()) null else "안 열림: ${missing.joinToString()}"
        },
        Check("플러그인 값이 이긴다 — 저장된 난이도로 덮어쓴다") { p ->
            val world = p.world
            val original = world.difficulty
            val wanted = if (original == Difficulty.HARD) Difficulty.EASY else Difficulty.HARD
            try {
                w.store.quietly {
                    val entry = w.service.entryOf(world)
                    w.store.put(entry.copy(difficulty = wanted))
                    w.service.apply(world)
                    if (world.difficulty != wanted) "덮어쓰지 않음: ${world.difficulty}" else null
                }
            } finally {
                world.difficulty = original
            }
        },
        Check("다른 길로 바뀐 게임 규칙을 받아 적는다(WorldGameRuleChangeEvent)") { p ->
            val world = p.world
            w.store.quietly {
                if (w.store.get(world.name) == null) w.store.put(w.service.defaultEntry(world.name, world.environment))
                val current = world.getGameRuleValue(GameRules.SPAWN_PHANTOMS) ?: true
                val event = WorldGameRuleChangeEvent(world, p, GameRules.SPAWN_PHANTOMS, (!current).toString())
                event.callEvent()
                val stored = w.store.get(world.name)?.rules?.get(w.service.key(GameRules.SPAWN_PHANTOMS))
                val default = world.getGameRuleDefault(GameRules.SPAWN_PHANTOMS)
                val expected = if (!current == default) null else (!current).toString()
                if (stored != expected) "적힌 값 $stored ≠ $expected" else null
            }
        },
        Check("포탈 연결 — 막기면 취소, 월드면 그 월드로(네더 비율)") { p ->
            val world = p.world
            val target = Bukkit.getWorlds().firstOrNull { it != world } ?: return@Check null
            w.store.quietly {
                val entry = w.service.entryOf(world)
                val from = p.location.clone()
                w.store.put(entry.copy(nether = PortalLink.NONE))
                val blocked = PlayerPortalEvent(p, from, from.clone(), TeleportCause.NETHER_PORTAL, 128, true, 16)
                blocked.callEvent()
                if (!blocked.isCancelled) return@quietly "막기인데 취소되지 않음"

                w.store.put(entry.copy(nether = PortalLink.to(target.name)))
                val linked = PlayerPortalEvent(p, from, from.clone(), TeleportCause.NETHER_PORTAL, 128, true, 16)
                linked.callEvent()
                val to: Location = linked.to
                val ratio = (if (world.environment == org.bukkit.World.Environment.NETHER) 8.0 else 1.0) /
                    (if (target.environment == org.bukkit.World.Environment.NETHER) 8.0 else 1.0)
                when {
                    linked.isCancelled -> "연결인데 취소됨"
                    to.world != target -> "다른 월드로 감: ${to.world?.name}"
                    kotlin.math.abs(to.x - from.x * ratio) > 0.01 -> "비율이 틀림: ${to.x} (기대 ${from.x * ratio})"
                    else -> null
                }
            }
        },
    )

    fun run(player: Player) {
        val results = checks.map { check ->
            val failure = try {
                check.run(player)
            } catch (t: Throwable) {
                "검증기 오류: " + t.javaClass.simpleName + (t.message?.let { ": $it" } ?: "")
            }
            Result(check.name, failure)
        }
        val failures = results.filter { it.failure != null }
        w.messages.send(player, "verify-done", Ph.of().value((results.size - failures.size).toString()).count(failures.size))
        for (failure in failures) w.messages.send(player, "verify-failure", Ph.of().world(failure.name).value(failure.failure.orEmpty()))

        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = w.io.file("verify", "worlds-$stamp.txt")
        val text = buildString {
            appendLine("# inmc-worlds 검증 - ${LocalDateTime.now()}")
            for (row in results) appendLine((if (row.failure == null) "- 통과 " else "- 실패 ") + row.name + (row.failure?.let { " — $it" } ?: ""))
        }
        w.io.asyncRun {
            file.parentFile.mkdirs()
            file.writeText(text)
        }
        w.messages.send(player, "verify-report", Ph.of().value("plugins/${w.plugin.name}/verify/${file.name}"))
    }
}
