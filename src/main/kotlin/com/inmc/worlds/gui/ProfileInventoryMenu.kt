package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import com.inmc.worlds.body.BukkitBody
import com.inmc.worlds.body.Part
import com.inmc.worlds.body.ProfileFiles
import com.inmc.worlds.util.Ph
import kr.inmc.core.gui.Icon
import kr.inmc.core.item.ItemCodec
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import java.util.Base64
import java.util.UUID

/**
 * `/모드 가방 (사람) (모드)` — 다른 모드의 가방(파일)을 보고 고친다. 0~35 가방 · 36~39 갑옷(발→머리) · 40 왼손, 닫으면 파일에 쓴다.
 *
 * **몸이 그 칸을 쥐고 있으면 열지 않는다**(그때는 essentials `/인벤`). 열어 둔 사이 그 사람이 그 칸으로 드나들면 모드 서비스가
 * 이 화면을 먼저 닫는다(닫기가 쓴다 — `ModeService.editors`) — 낡은 사본이 몸이 쓴 것을 덮지 않게.
 */
class ProfileInventoryMenu(
    w: Worlds,
    viewer: Player,
    private val target: UUID,
    private val targetName: String,
    private val scope: String,
) : Menu(w, viewer, 54, "<dark_green>$targetName · ${if (scope.isEmpty()) "같이 쓰는 칸" else scope} 가방</dark_green>") {

    private var drawn = false
    private var closed = false

    override fun isSlotEditable(slot: Int): Boolean = slot in 0 until BukkitBody.INVENTORY_SIZE

    override fun acceptsShiftInsert(): Boolean = true

    override fun draw() {
        if (drawn) return // 고친 칸을 다시 그려 덮지 않는다
        drawn = true
        val raw = w.realm.files.read(target, scope, Part.INVENTORY)?.getString("items")
        val items = ItemCodec.decode(raw?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }, BukkitBody.INVENTORY_SIZE)
        for (i in 0 until BukkitBody.INVENTORY_SIZE) inventory.setItem(i, items[i])
        for (slot in BukkitBody.INVENTORY_SIZE until 54) set(slot, Icon.EDGE)
        set(49, Icon.of(Material.PAPER, "<yellow>${if (scope.isEmpty()) "같이 쓰는 칸" else scope} 의 가방</yellow>", listOf(
            "<gray>0~35 가방 · 36~39 갑옷 · 40 왼손</gray>",
            "<gray>닫으면 저장합니다.</gray>",
        )))
        w.realm.editors[target to scope] = { if (!closed) viewer.closeInventory() }
    }

    override fun onClose(event: InventoryCloseEvent) {
        if (closed) return
        closed = true
        w.realm.editors.remove(target to scope)
        val items = Array(BukkitBody.INVENTORY_SIZE) { inventory.getItem(it)?.takeIf { s -> !s.type.isAir } }
        val section = ProfileFiles.section()
        section.set("items", Base64.getEncoder().encodeToString(ItemCodec.encode(items)))
        runCatching { w.realm.files.write(target, scope, mapOf(Part.INVENTORY.id to section)) }
            .onSuccess { w.messages.send(viewer, "profile-saved", Ph.of().player(targetName).value(scope.ifEmpty { "같이 쓰는 칸" })) }
            .onFailure { w.messages.send(viewer, "profile-save-failed", Ph.of().value(it.message.orEmpty())) }
    }
}
