package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.integration.PlaceNames
import kr.inmc.core.util.Text
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * core [kr.inmc.core.gui.Menu] 에 이 플러그인의 로케이터와 보는 사람을 붙인 얇은 층(드랍 `gui/Menu.kt` 와 같은 모양).
 * 리로드가 열린 화면을 닫을 때 [owner] 로 우리 것을 가려낸다. 값 입력은 채팅이 아니라 입력창(Dialog) — [ask].
 */
abstract class Menu(
    protected val w: Worlds,
    protected val viewer: Player,
    size: Int,
    title: String,
) : kr.inmc.core.gui.Menu(size, Text.renderFlat(title)) {

    override val owner: Any get() = w

    /** 뒤로 버튼이 여는 화면. null 이면 뒤로 버튼이 없다. */
    protected open val back: (() -> Unit)? = null

    protected fun navigation(backSlot: Int = Paging.SLOT_BACK, closeSlot: Int = Paging.SLOT_CLOSE) {
        back?.let { go -> set(backSlot, Icon.back()) { go() } }
        set(closeSlot, Icon.close()) { viewer.closeInventory() }
    }

    fun show() = open(viewer)

    /** 입력창을 띄운다. 확인하면 [onSubmit] 뒤에 이 화면을 다시 연다(그 안에서 다른 화면을 열면 그쪽이 이긴다). */
    protected fun ask(form: DialogForm, reopen: () -> Unit = { show() }, onSubmit: (DialogForm.Values) -> Unit) {
        form.show(w.plugin, viewer, onCancel = { reopen() }) { _, values ->
            onSubmit(values)
            if (viewer.openInventory.topInventory.holder !is kr.inmc.core.gui.Menu) reopen()
        }
    }

    protected fun toggleIcon(name: String, value: Boolean, vararg lore: String): ItemStack =
        Icon.of(Icon.toggleMaterial(value), "<yellow>$name: </yellow>" + Icon.toggle(value), lore.toList() + listOf("", "<gray>클릭해서 바꾸기</gray>"))

    protected fun valueIcon(material: Material, name: String, value: String, vararg lore: String): ItemStack =
        Icon.of(material, "<yellow>$name: </yellow><white>$value</white>", lore.toList() + listOf("", "<gray>클릭해서 바꾸기</gray>"))

    companion object {

        /** 월드를 보여 줄 이름 — 타이틀포지 이름(MiniMessage), 없으면 id. */
        fun displayName(name: String): String = PlaceNames.world(name) ?: "<white>$name</white>"

        fun environmentLabel(environment: World.Environment): String = when (environment) {
            World.Environment.NORMAL -> "일반"
            World.Environment.NETHER -> "네더"
            World.Environment.THE_END -> "엔드"
            else -> environment.name.lowercase()
        }

        fun environmentIcon(environment: World.Environment): Material = when (environment) {
            World.Environment.NETHER -> Material.NETHERRACK
            World.Environment.THE_END -> Material.END_STONE
            else -> Material.GRASS_BLOCK
        }

        fun difficultyLabel(difficulty: org.bukkit.Difficulty): String = when (difficulty) {
            org.bukkit.Difficulty.PEACEFUL -> "평화로움"
            org.bukkit.Difficulty.EASY -> "쉬움"
            org.bukkit.Difficulty.NORMAL -> "보통"
            org.bukkit.Difficulty.HARD -> "어려움"
        }

        /** 블록 id 를 화면 글자로 — 클라이언트 번역(`<lang:block.minecraft.stone>`). */
        fun blockLabel(id: String): String {
            val key = id.removePrefix("minecraft:")
            return if (id.contains(':') && !id.startsWith("minecraft:")) id else "<lang:block.minecraft.$key>"
        }

        fun blockMaterial(id: String): Material = Material.matchMaterial(id)?.takeIf { it.isItem } ?: Material.STONE
    }
}
