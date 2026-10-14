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
        Check("게임 규칙 화면의 규칙을 월드마다 다 읽을 수 있다(실험 기능 규칙 빼기)") { _ ->
            val broken = Bukkit.getWorlds().flatMap { world ->
                w.service.rules(world).filter { runCatching { world.getGameRuleValue(it) }.isFailure }.map { "${world.name}:${w.service.key(it)}" }
            }
            if (broken.isEmpty()) null else "못 읽음: ${broken.take(5).joinToString()}"
        },
        Check("게임 규칙마다 한국어 설명이 있다(서버를 올려 새로 생긴 규칙 알림)") { _ ->
            val missing = w.service.rules().map { w.service.key(it) }.filter { it !in com.inmc.worlds.gui.GameRuleTexts.descriptions }
            if (missing.isEmpty()) null else "GameRuleTexts 에 없음(바닐라 설명이 쓰임): ${missing.take(5).joinToString()}"
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
        Check("모드 공급처가 꽂혀 있고 몸 부분이 기능 목록에 있다(core Realms)") { _ ->
            val keys = kr.inmc.core.integration.Realms.features().map { it.key }.toSet()
            val missing = com.inmc.worlds.body.Part.entries.filter { it.key !in keys }
            when {
                !kr.inmc.core.integration.Realms.available -> "Realms 공급처 없음"
                missing.isNotEmpty() -> "기능 목록에 없음: ${missing.joinToString { it.key }}"
                else -> null
            }
        },
        Check("불러온 월드는 모두 모드에 들거나 빌려 쓰는 월드") { _ ->
            val loose = Bukkit.getWorlds().map { it.name }.filter { name ->
                !w.modes.isBorrowed(name) && w.modes.all().none { m -> m.worlds.any { it.equals(name, true) } }
            }
            if (loose.isEmpty()) null else "모드 없음: ${loose.joinToString()}"
        },
        Check("내 모드 = 서 있는 월드의 모드, 몸 표식 = 원하는 칸") { p ->
            val at = w.realm.modeAt(p)
            val held = com.inmc.worlds.body.BukkitBody.parseHeldOf(p)
            val want = w.realm.desired(at)
            when {
                w.realm.of(p.uniqueId) != at -> "Realms.of = ${w.realm.of(p.uniqueId)} ≠ $at"
                w.realm.bodyMode(p) != at -> "몸의 모드 = ${w.realm.bodyMode(p)} ≠ $at"
                held == null -> "몸 표식 없음"
                want.any { (part, scope) -> held[part] != scope } -> "몸 표식 $held ≠ 원하는 $want"
                else -> null
            }
        },
        Check("권한 없는 사람의 다른 모드로 가는 순간이동은 막힌다(진짜 사건)") { p ->
            val here = w.realm.modeAt(p)
            val other = Bukkit.getWorlds().firstOrNull { world -> w.modes.modeOfWorld(world.name)?.let { it != here } == true }
                ?: return@Check null // 다른 모드의 월드가 불러와져 있지 않으면 볼 것이 없다
            val attachment = p.addAttachment(w.plugin, com.inmc.worlds.mode.RealmsProvider.BYPASS, false)
            try {
                val event = org.bukkit.event.player.PlayerTeleportEvent(p, p.location.clone(), other.spawnLocation, TeleportCause.PLUGIN)
                event.callEvent()
                if (!event.isCancelled) "다른 모드(${other.name})로 가는 순간이동이 통과함" else null
            } finally {
                p.removeAttachment(attachment)
            }
        },
        Check("탭 목록이 탭 묶음대로(투명이 아닌 사람끼리)") { _ ->
            val online = Bukkit.getOnlinePlayers().filter { !kr.inmc.core.integration.Vanish.isVanished(it) }
            val wrong = online.flatMap { a -> online.filter { b -> a != b && a.canSee(b) && a.isListed(b) != kr.inmc.core.integration.Realms.sameTab(a, b) }.map { b -> "${a.name}→${b.name}" } }
            if (wrong.isEmpty()) null else "어긋남: ${wrong.take(5).joinToString()}"
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
