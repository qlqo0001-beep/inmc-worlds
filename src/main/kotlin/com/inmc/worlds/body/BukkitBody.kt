package com.inmc.worlds.body

import com.inmc.worlds.Worlds
import kr.inmc.core.item.ItemCodec
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.Base64

/**
 * 진짜 몸 — 플레이어. 표식(부분 → 쥔 scope)은 플레이어 PDC `inmcworlds:held` — **.dat 에 가방과 같이** 저장된다([Swapper] 의 불변식이 이것에 기댄다).
 *
 * 부분마다 담는 것:
 * - 가방 41칸(0~35 · 갑옷 36~39 · 왼손 40) / 엔더 27칸 — core [ItemCodec] 바이트(Base64).
 * - 경험치 = 레벨 · 진행 · 총량. 체력·배고픔 = 체력 · 배고픔 · 포화 · 소모 · 숨 · 불 · 낙하.
 * - 물약 효과 = **시간이 있는 것만**(무한 효과는 플러그인이 거는 능력치 효과 — 그 플러그인이 모드 바뀜 사건에서 다시 건다).
 * - 부활 자리 = 침대·정박기 자리(`respawnLocation`).
 *
 * 체력은 [apply] 에서 지금 최대 체력으로 자르고, 사건 뒤에 [settleHealth] 로 한 번 더 맞춘다(능력치 플러그인이 최대 체력을 다시 건 뒤).
 */
class BukkitBody(private val player: Player) : Swapper.Body<ConfigurationSection> {

    /** 사건 뒤에 넣을 체력(능력치가 다시 걸린 뒤). */
    var pendingHealth: Double? = null
        private set

    override fun held(): Map<Part, String>? {
        val raw = player.persistentDataContainer.get(HELD, PersistentDataType.STRING) ?: return null
        return parseHeld(raw)
    }

    override fun setHeld(held: Map<Part, String>) {
        player.persistentDataContainer.set(HELD, PersistentDataType.STRING, formatHeld(held))
    }

    override fun save() {
        player.saveData()
    }

    override fun capture(part: Part): ConfigurationSection {
        val s = ProfileFiles.section()
        when (part) {
            Part.INVENTORY -> s.set("items", b64(ItemCodec.encode(Array(INVENTORY_SIZE) { player.inventory.getItem(it)?.takeIf { s2 -> !s2.type.isAir } })))
            Part.ENDER -> s.set("items", b64(ItemCodec.encode(Array(ENDER_SIZE) { player.enderChest.getItem(it)?.takeIf { s2 -> !s2.type.isAir } })))
            Part.EXP -> {
                s.set("level", player.level)
                s.set("progress", player.exp.toDouble())
                s.set("total", player.totalExperience)
            }
            Part.VITALS -> {
                s.set("health", player.health)
                s.set("food", player.foodLevel)
                s.set("saturation", player.saturation.toDouble())
                s.set("exhaustion", player.exhaustion.toDouble())
                s.set("air", player.remainingAir)
                s.set("fire", player.fireTicks)
                s.set("fall", player.fallDistance.toDouble())
            }
            Part.EFFECTS -> s.set("effects", ItemCodec.encodeEffects(player.activePotionEffects.filter { !it.isInfinite }))
            Part.RESPAWN -> player.respawnLocation?.let { at ->
                s.set("world", at.world?.name)
                s.set("x", at.x); s.set("y", at.y); s.set("z", at.z); s.set("yaw", at.yaw.toDouble()); s.set("pitch", at.pitch.toDouble())
            }
        }
        return s
    }

    override fun apply(part: Part, data: ConfigurationSection?) {
        when (part) {
            Part.INVENTORY -> {
                val items = ItemCodec.decode(data?.getString("items")?.let(::unb64), INVENTORY_SIZE)
                for (i in 0 until INVENTORY_SIZE) player.inventory.setItem(i, items[i])
            }
            Part.ENDER -> {
                val items = ItemCodec.decode(data?.getString("items")?.let(::unb64), ENDER_SIZE)
                for (i in 0 until ENDER_SIZE) player.enderChest.setItem(i, items[i])
            }
            Part.EXP -> {
                player.level = data?.getInt("level") ?: 0
                player.exp = (data?.getDouble("progress") ?: 0.0).toFloat().coerceIn(0f, 0.9999f)
                player.totalExperience = data?.getInt("total") ?: 0
            }
            Part.VITALS -> {
                val max = maxHealth()
                val health = data?.getDouble("health", max) ?: max
                player.health = health.coerceIn(0.5, max)
                pendingHealth = health
                player.foodLevel = (data?.getInt("food", 20) ?: 20).coerceIn(0, 20)
                player.saturation = (data?.getDouble("saturation", 5.0) ?: 5.0).toFloat().coerceIn(0f, 20f)
                player.exhaustion = (data?.getDouble("exhaustion", 0.0) ?: 0.0).toFloat().coerceAtLeast(0f)
                player.remainingAir = (data?.getInt("air", player.maximumAir) ?: player.maximumAir).coerceIn(0, player.maximumAir)
                player.fireTicks = data?.getInt("fire", 0) ?: 0
                player.fallDistance = (data?.getDouble("fall", 0.0) ?: 0.0).toFloat()
            }
            Part.EFFECTS -> {
                for (effect in player.activePotionEffects) if (!effect.isInfinite) player.removePotionEffect(effect.type)
                for (effect in ItemCodec.decodeEffects(data?.getString("effects"))) player.addPotionEffect(effect)
            }
            Part.RESPAWN -> {
                val world = data?.getString("world")?.let(Bukkit::getWorld)
                val at = if (data != null && world != null) {
                    Location(world, data.getDouble("x"), data.getDouble("y"), data.getDouble("z"), data.getDouble("yaw").toFloat(), data.getDouble("pitch").toFloat())
                } else null
                player.setRespawnLocation(at, true)
            }
        }
    }

    /** 사건 뒤 — 능력치 플러그인이 최대 체력을 다시 건 뒤의 최대에 맞춰 체력을 넣는다. */
    fun settleHealth() {
        val wanted = pendingHealth ?: return
        pendingHealth = null
        if (!player.isOnline || player.isDead) return
        player.health = wanted.coerceIn(0.5, maxHealth())
    }

    private fun maxHealth(): Double = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0

    companion object {
        const val INVENTORY_SIZE = 41
        const val ENDER_SIZE = 27

        val HELD = NamespacedKey(Worlds.NAMESPACE, "held")

        /** 이 몸의 표식(없으면 null). */
        fun parseHeldOf(player: Player): Map<Part, String>? =
            player.persistentDataContainer.get(HELD, PersistentDataType.STRING)?.let(::parseHeld)

        /** `inventory=main;ender=main;…` — 모르는 부분 이름은 버린다. */
        fun parseHeld(raw: String): Map<Part, String> = raw.split(';').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val part = Part.ofId(pair.substring(0, eq)) ?: return@mapNotNull null
            part to pair.substring(eq + 1)
        }.toMap()

        fun formatHeld(held: Map<Part, String>): String = held.entries.sortedBy { it.key.ordinal }.joinToString(";") { "${it.key.id}=${it.value}" }

        private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        private fun unb64(text: String): ByteArray? = runCatching { Base64.getDecoder().decode(text) }.getOrNull()
    }
}
