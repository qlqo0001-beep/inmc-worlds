package com.inmc.worlds.mode

import com.inmc.worlds.Worlds
import com.inmc.worlds.body.BukkitBody
import com.inmc.worlds.body.Part
import com.inmc.worlds.body.PlayerIndex
import com.inmc.worlds.body.ProfileFiles
import com.inmc.worlds.body.Swapper
import kr.inmc.core.event.RealmChangeEvent
import kr.inmc.core.event.RealmFeatureChangeEvent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 모드 — 사람의 지금 모드, 몸 맞추기([Swapper]), 모드를 넘는 이동, 마지막 자리, 들어올 때 게임 모드, 모드 바뀜 사건.
 *
 * **사람의 모드** = 서 있는 월드의 모드. 빌려 쓰는 월드(던전)에서는 **몸에 적힌 모드**(플레이어 PDC `inmcworlds:mode` — .dat 에 같이 저장)를 그대로 쓴다.
 * 오프라인·다른 스레드는 [index] 의 마지막 모드(메모리).
 *
 * 모든 메서드는 메인 스레드(따로 적은 것 빼고).
 */
class ModeService(private val w: Worlds) {

    val files = ProfileFiles(w)

    val index = PlayerIndex(w)

    /** 접속 중인 사람의 지금 모드 — core `Realms.of` 가 아무 스레드에서나 읽는다. */
    private val current = ConcurrentHashMap<UUID, String>()

    /** `Realms.travel` 로 가는 중인 사람 — 모드를 넘어도 막지 않는다. */
    private val passes = ConcurrentHashMap.newKeySet<UUID>()

    /** 모드를 넘는 이동의 출발 자리 — 바뀐 뒤 떠난 모드의 마지막 자리로 적는다. */
    private val departures = HashMap<UUID, Location>()

    /** 몸을 바꾼 직후 크리에이티브 칸 바꾸기를 막는 끝 시각(밀리초) — MV-Inv 의 복사 막기와 같은 것. */
    private val creativeGuard = HashMap<UUID, Long>()

    /** `/모드 가방` 으로 열어 둔 화면 — (사람, scope) → 닫기(쓰기). 몸이 그 scope 를 드나들기 전에 먼저 닫는다. */
    val editors = HashMap<Pair<UUID, String>, () -> Unit>()

    // --- 읽기 -------------------------------------------------------------------------------------

    /** 아무 스레드. 접속 중이면 지금 모드, 아니면 마지막 모드, 모르면 기본 모드. */
    fun of(player: UUID): String = current[player] ?: index.mode(player) ?: w.modes.defaultId

    /** 몸에 적힌 모드. 없으면(처음 보는 몸) null. */
    fun bodyMode(player: Player): String? = player.persistentDataContainer.get(MODE, PersistentDataType.STRING)

    /** 이 사람이 있어야 할 모드 — 서 있는 월드의 모드, 빌려 쓰는 월드면 몸의 모드. */
    fun modeAt(player: Player): String =
        w.modes.modeOfWorld(player.world.name) ?: bodyMode(player) ?: index.mode(player.uniqueId) ?: w.modes.defaultId

    /** 그 자리의 모드 — 빌려 쓰는 월드면 null. */
    fun modeOf(at: Location): String? = at.world?.let { w.modes.modeOfWorld(it.name) }

    fun desired(mode: String): Map<Part, String> = Part.entries.associateWith { if (w.modes.isShared(it.key)) "" else mode }

    /** 모드의 스폰 — 정한 자리, 없으면 모드 첫 월드의 스폰, 그것도 없으면 본 월드 스폰. */
    fun spawn(mode: String): Location {
        val m = w.modes.get(mode)
        m?.spawn?.let { s -> Bukkit.getWorld(s.world)?.let { return Location(it, s.x, s.y, s.z, s.yaw, s.pitch) } }
        m?.worlds?.firstNotNullOfOrNull { Bukkit.getWorld(it) }?.let { return it.spawnLocation }
        return w.service.main.spawnLocation
    }

    /** 이 모드에서 마지막으로 있던 자리(그 월드가 불러와져 있을 때만). */
    fun lastLocation(player: UUID, mode: String): Location? {
        val s = files.load(player, mode).getConfigurationSection(LOCATION) ?: return null
        val world = s.getString("world")?.let(Bukkit::getWorld) ?: return null
        if (w.modes.modeOfWorld(world.name) != mode) return null
        return Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), s.getDouble("yaw").toFloat(), s.getDouble("pitch").toFloat())
    }

    // --- 맞추기 -------------------------------------------------------------------------------------

    /**
     * 몸을 지금 있어야 할 모드에 맞춘다(모드를 넘었다 · 접속 · 토글이 바뀌었다 · 월드가 다른 모드로 옮겨졌다). 바뀐 것이 없으면 아무 일도 없다.
     * 모드가 바뀌었으면 게임 모드를 맞추고 [RealmChangeEvent] 를 쏜다.
     */
    fun reconcile(player: Player) {
        val uuid = player.uniqueId
        val from = bodyMode(player)
        val to = modeAt(player)
        val desired = desired(to)
        val held = BukkitBody.parseHeldOf(player)
        // 몸이 드나들 scope 를 관리자가 열어 두었으면 먼저 닫는다(닫기가 파일에 쓴다) — 낡은 사본이 나중에 덮지 않게.
        if (held != null) for (part in Part.entries) {
            val have = held[part] ?: continue
            val want = desired.getValue(part)
            if (have != want) {
                editors.remove(uuid to have)?.invoke()
                editors.remove(uuid to want)?.invoke()
            }
        }
        // 2×2 제작 칸·커서의 것은 닫으면 가방으로 돌아온다 — 몸을 뜨기 전에(안 그러면 바뀐 뒤 새 가방으로 들어온다).
        player.closeInventory()

        val body = BukkitBody(player)
        val modeChanged = from != to
        if (modeChanged) player.persistentDataContainer.set(MODE, PersistentDataType.STRING, to)
        val changes = Swapper(body, adapter(uuid)).reconcile(desired)
        current[uuid] = to
        if (changes.isNotEmpty()) creativeGuard[uuid] = System.currentTimeMillis() + GUARD_MILLIS
        if (!modeChanged) return
        if (changes.isEmpty() && held != null) player.saveData()

        // 처음 설치한 서버의 이미 놀던 사람은 "처음 들어옴" 이 아니다.
        val first = index.enter(uuid, to) && !(from == null && player.hasPlayedBefore())
        if (from != null) departures.remove(uuid)?.let { at -> saveLocation(uuid, from, at) }
        w.modes.get(to)?.gameMode?.let { if (player.gameMode != it) player.gameMode = it }
        RealmChangeEvent(player, from, to, first).callEvent()
        body.settleHealth()
        w.tabs.refresh()
    }

    /** 접속자 전부 — 토글·모드 구성이 바뀐 뒤. */
    fun reconcileAll() {
        for (player in Bukkit.getOnlinePlayers()) runCatching { reconcile(player) }
            .onFailure { w.logger.severe("${player.name} 의 몸을 맞추지 못했습니다: ${it.message}") }
    }

    /** 관리자가 기능 하나의 "같이" 를 바꿨다 — 재시작 없이 바로(사용자 결정 2026-10-15). */
    fun setShared(key: String, shared: Boolean) {
        w.modes.setShared(key, shared)
        if (Part.entries.any { it.key == key }) reconcileAll()
        RealmFeatureChangeEvent(key, shared).callEvent()
    }

    fun forget(player: UUID) {
        current.remove(player)
        departures.remove(player)
        creativeGuard.remove(player)
        passes.remove(player)
    }

    fun creativeBlocked(player: UUID): Boolean = (creativeGuard[player] ?: 0L) > System.currentTimeMillis()

    // --- 이동 ---------------------------------------------------------------------------------------

    fun hasPass(player: UUID): Boolean = player in passes

    /** 모드를 넘을 수 있는 이동 — `/모드`·`/월드 이동`·포탈류(core `Realms.travel`). 넘으면 바뀐 월드 사건에서 몸이 바뀐다. */
    fun travel(player: Player, to: Location): CompletableFuture<Boolean> {
        passes += player.uniqueId
        return player.teleportAsync(to).whenComplete { _, _ -> passes -= player.uniqueId }
    }

    /** 그 자리에서 바로 옮긴다(월드를 내리기 전에 사람을 빼는 것처럼 기다릴 수 없을 때). 모드를 넘어도 막지 않는다. */
    fun moveNow(player: Player, to: Location) {
        passes += player.uniqueId
        try {
            player.teleport(to)
        } finally {
            passes -= player.uniqueId
        }
    }

    /** `/모드 <id>` — 그 모드의 마지막 자리, 없으면 모드 스폰. */
    fun go(player: Player, mode: String): CompletableFuture<Boolean> =
        travel(player, lastLocation(player.uniqueId, mode) ?: spawn(mode))

    /** 모드를 넘는 이동이 통과하는 순간(MONITOR) — 출발 자리를 기억한다. */
    fun departing(player: Player, from: Location) {
        departures[player.uniqueId] = from.clone()
    }

    private fun saveLocation(player: UUID, mode: String, at: Location) {
        val s = ProfileFiles.section()
        s.set("world", at.world?.name); s.set("x", at.x); s.set("y", at.y); s.set("z", at.z); s.set("yaw", at.yaw.toDouble()); s.set("pitch", at.pitch.toDouble())
        runCatching { files.write(player, mode, mapOf(LOCATION to s)) }.onFailure { w.logger.warning("마지막 자리를 적지 못했습니다: ${it.message}") }
    }

    /** [Swapper.Profiles] — scope 파일의 부분 절. */
    private fun adapter(player: UUID) = object : Swapper.Profiles<ConfigurationSection> {
        override fun read(scope: String, part: Part): ConfigurationSection? = files.read(player, scope, part)
        override fun write(scope: String, parts: Map<Part, ConfigurationSection>) = files.write(player, scope, parts.mapKeys { it.key.id })
    }

    companion object {
        val MODE = NamespacedKey(Worlds.NAMESPACE, "mode")
        const val LOCATION = "location"
        const val GUARD_MILLIS = 250L
    }
}
