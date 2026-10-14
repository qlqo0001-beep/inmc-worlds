package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import com.inmc.worlds.util.Ph
import com.inmc.worlds.world.Flat
import com.inmc.worlds.world.FlatJson
import com.inmc.worlds.world.Layer
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.ItemInputMenu
import kr.inmc.core.gui.Paging
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

/** 아이템 → 블록 id. 놓을 수 있는 블록이 아니면 null. */
internal fun blockIdOf(stack: ItemStack): String? = stack.type.takeIf { it.isBlock && !it.isAir }?.key?.asString()

/**
 * 평지 층 편집 — 맨 위 층이 맨 앞. 층을 누르면 그 층의 화면(블록·높이·순서·지우기), 아래 줄 [층 더하기] 는 블록을 올려 맨 위에 1칸.
 * 블록은 **가방의 블록을 올려서** 고른다(core `ItemInputMenu` — 손에 든 것을 읽지 않는다).
 */
class FlatLayersMenu(w: Worlds, viewer: Player, private val draft: Draft, private val done: () -> Unit) :
    Menu(w, viewer, 54, "<dark_green>평지 층</dark_green>") {

    override val back: (() -> Unit) = done

    private val flat: Flat get() = draft.entry.flat ?: Flat()

    private fun update(layers: List<Layer>) {
        draft.entry = draft.entry.copy(flat = flat.copy(layers = layers))
    }

    override fun draw() {
        clear()
        val layers = flat.layers
        // 맨 위 층이 첫 칸 — 눈으로 보는 순서(위에서 아래로).
        for ((slot, index) in layers.indices.reversed().take(Paging.PER_PAGE).withIndex()) {
            val layer = layers[index]
            set(slot, Icon.of(Menu.blockMaterial(layer.block), "${Menu.blockLabel(layer.block)} <gray>× ${layer.height}</gray>", listOf(
                "<gray>아래에서 ${index + 1}번째 층</gray>",
                "",
                "<gray>클릭: <white>이 층 고치기</white></gray>",
            ))) { LayerMenu(index).show() }
        }
        fillEmpty(Icon.FILLER)
        val thickness = flat.thickness
        set(48, Icon.of(Material.SPYGLASS, "<yellow>두께: </yellow><white>$thickness</white> <gray>/ ${FlatJson.MAX_THICKNESS}</gray>", listOf(
            if (thickness > FlatJson.MAX_THICKNESS) "<red>너무 두껍습니다 — 만들 수 없습니다.</red>" else "<gray>바닥(높이 -64)부터 쌓입니다.</gray>",
        )))
        set(49, Icon.of(Material.LIME_DYE, "<green>＋ 층 더하기</green>", listOf("<gray>블록을 올려 맨 위에 한 칸.</gray>"))) {
            pickBlock("새 층의 블록") { id -> update(flat.layers + Layer(id, 1)) }
        }
        navigation()
    }

    /** 블록 올리기 → [then], 그리고 이 화면으로. */
    private fun pickBlock(title: String, then: (String) -> Unit) {
        ItemInputMenu(w, viewer, title, hint = listOf("<gray>가방의 블록을 누르세요.</gray>"), submitLabel = "고르기", back = { show() }) { stack ->
            val id = blockIdOf(stack)
            if (id == null) w.messages.send(viewer, "not-a-block") else then(id)
            show()
        }.show()
    }

    /** 층 하나 — 블록 · 높이 · 위/아래로 · 지우기. */
    private inner class LayerMenu(private val index: Int) : Menu(w, viewer, 27, "<dark_green>층 고치기</dark_green>") {

        override val back: (() -> Unit) = { this@FlatLayersMenu.show() }

        override fun draw() {
            clear()
            val layers = flat.layers
            val layer = layers.getOrNull(index) ?: return this@FlatLayersMenu.show()
            set(10, Icon.of(Menu.blockMaterial(layer.block), "<yellow>블록: </yellow>${Menu.blockLabel(layer.block)}", listOf("", "<gray>클릭: 블록 바꾸기(올리기)</gray>"))) {
                ItemInputMenu(w, viewer, "층의 블록", hint = listOf("<gray>가방의 블록을 누르세요.</gray>"), submitLabel = "고르기", back = { show() }) { stack ->
                    val id = blockIdOf(stack)
                    if (id == null) w.messages.send(viewer, "not-a-block") else update(flat.layers.toMutableList().also { it[index] = layer.copy(block = id) })
                    show()
                }.show()
            }
            set(13, Icon.of(Material.LADDER, "<yellow>높이: </yellow><white>${layer.height}</white>", listOf(
                "<gray>좌클릭 +1 · 우클릭 −1 · Shift 와 함께 ±10</gray>",
                "<gray>숫자키(1~9)·Q: 직접 입력</gray>",
            ))) { event ->
                if (Editors.isPrompt(event) || event.click == ClickType.CONTROL_DROP) {
                    ask(DialogForm("층 높이").long("height", "높이", layer.height.toLong(), 1, FlatJson.MAX_THICKNESS.toLong())) { v ->
                        v.long("height")?.let { setHeight(it.toInt()) }
                    }
                    return@set
                }
                val step = if (event.isShiftClick) 10 else 1
                setHeight(layer.height + if (event.isRightClick) -step else step)
                refresh()
            }
            set(15, Icon.of(Material.ARROW, "<yellow>▲ 위로</yellow>", listOf("<gray>한 층 위와 자리를 바꿉니다.</gray>"))) { move(+1) }
            set(16, Icon.of(Material.ARROW, "<yellow>▼ 아래로</yellow>", listOf("<gray>한 층 아래와 자리를 바꿉니다.</gray>"))) { move(-1) }
            set(22, Icon.of(Material.RED_DYE, "<red>✖ 이 층 지우기</red>", emptyList())) {
                if (flat.layers.size <= 1) w.messages.send(viewer, "flat-last-layer")
                else {
                    update(flat.layers.toMutableList().also { it.removeAt(index) })
                    this@FlatLayersMenu.show()
                }
            }
            fillEmpty(Icon.FILLER)
            navigation(backSlot = 18, closeSlot = 26)
        }

        private fun setHeight(height: Int) {
            val layer = flat.layers.getOrNull(index) ?: return
            update(flat.layers.toMutableList().also { it[index] = layer.copy(height = height.coerceIn(1, FlatJson.MAX_THICKNESS)) })
        }

        private fun move(delta: Int) {
            val target = index + delta
            val layers = flat.layers.toMutableList()
            if (target !in layers.indices) return
            layers[index] = layers[target].also { layers[target] = layers[index] }
            update(layers)
            LayerMenu(target).show()
        }
    }
}

/** 평지 빠른 설정 — 높이 · 겉 블록 · 속 블록 · 바닥 블록 → 층을 한 번에(바닥 1 · 속 · 겉 1). */
class QuickFlatMenu(w: Worlds, viewer: Player, private val draft: Draft, private val done: () -> Unit) :
    Menu(w, viewer, 27, "<dark_green>평지 빠른 설정</dark_green>") {

    override val back: (() -> Unit) = done

    private var height = 4
    private var top = "minecraft:grass_block"
    private var filler = "minecraft:dirt"
    private var bottom = "minecraft:bedrock"

    override fun draw() {
        clear()
        set(10, blockIcon("바닥 블록", bottom)) { pick("바닥 블록") { bottom = it } }
        set(12, blockIcon("속 블록", filler)) { pick("속 블록") { filler = it } }
        set(14, blockIcon("겉 블록", top)) { pick("겉 블록") { top = it } }
        set(16, Icon.of(Material.LADDER, "<yellow>두께: </yellow><white>$height</white>", listOf(
            "<gray>바닥부터 겉까지의 칸 수.</gray>",
            "<gray>좌클릭 +1 · 우클릭 −1 · Shift 와 함께 ±10 · 숫자키(1~9)·Q: 직접 입력</gray>",
        ))) { event ->
            if (Editors.isPrompt(event) || event.click == ClickType.CONTROL_DROP) {
                ask(DialogForm("평지 두께").long("height", "두께", height.toLong(), 1, FlatJson.MAX_THICKNESS.toLong())) { v ->
                    v.long("height")?.let { height = it.toInt() }
                }
                return@set
            }
            val step = if (event.isShiftClick) 10 else 1
            height = (height + if (event.isRightClick) -step else step).coerceIn(1, FlatJson.MAX_THICKNESS)
            refresh()
        }
        set(22, Icon.of(Material.EMERALD, "<green>✔ 적용</green>", Flat.quick(height, top, filler, bottom).reversed()
            .map { "<gray>${Menu.blockLabel(it.block)} × ${it.height}</gray>" })) {
            val flat = draft.entry.flat ?: Flat()
            draft.entry = draft.entry.copy(flat = flat.copy(layers = Flat.quick(height, top, filler, bottom)))
            w.messages.send(viewer, "flat-quick-applied", Ph.of().count(height))
            done()
        }
        fillEmpty(Icon.FILLER)
        navigation(backSlot = 18, closeSlot = 26)
    }

    private fun blockIcon(name: String, id: String) =
        Icon.of(Menu.blockMaterial(id), "<yellow>$name: </yellow>${Menu.blockLabel(id)}", listOf("", "<gray>클릭: 블록 올려 바꾸기</gray>"))

    /** 블록 올리기 → [then], 그리고 이 화면으로(고른 값은 이 화면이 쥐고 있다). */
    private fun pick(title: String, then: (String) -> Unit) {
        ItemInputMenu(w, viewer, title, hint = listOf("<gray>가방의 블록을 누르세요.</gray>"), submitLabel = "고르기", back = { show() }) { stack ->
            val id = blockIdOf(stack)
            if (id == null) w.messages.send(viewer, "not-a-block") else then(id)
            show()
        }.show()
    }
}
