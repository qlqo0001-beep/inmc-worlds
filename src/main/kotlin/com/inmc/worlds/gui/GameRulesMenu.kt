package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Editors
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * 게임 규칙 전부 — 이름과 설명은 **클라이언트 번역 열쇠**(`GameRule.translationKey()` · `.description`)라 한국어 클라이언트면 한국어로 보인다
 * (커스텀아이템 규칙 79 와 같은 까닭 — 서버에 한국어 표를 두면 판마다 낡는다). 기본값과 다른 규칙은 노랗게.
 *
 * 참/거짓: 좌클릭 바꾸기 · Q 기본값. 숫자: 좌 +1 · 우 −1 · Shift ±10 · 숫자키(1~9)·Q 직접 입력(core `Editors.isPrompt`) · Ctrl+Q 기본값.
 */
class GameRulesMenu(w: Worlds, viewer: Player, private val name: String, private var page: Int = 0) :
    Menu(w, viewer, 54, "<dark_green>게임 규칙</dark_green>") {

    override val back: (() -> Unit) = { WorldSettingsMenu(w, viewer, name).show() }

    override fun draw() {
        clear()
        val world = Bukkit.getWorld(name) ?: return WorldListMenu(w, viewer).show()
        val rules = w.service.rules(world)
        page = Paging.clamp(page, rules.size)
        for ((slot, rule) in Paging.slice(rules, page).withIndex()) {
            set(slot, icon(world, rule)) { event -> click(world, rule, event) }
        }
        fillEmpty(Icon.FILLER)
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(rules.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        navigation()
    }

    private fun icon(world: World, rule: GameRule<*>) = run {
        val value = world.getGameRuleValue(rule)
        val default = world.getGameRuleDefault(rule)
        val changed = value != default
        val bool = w.service.isBoolean(rule)
        val key = rule.translationKey()
        val material = when {
            bool -> Icon.toggleMaterial(value == true)
            else -> Material.COMPARATOR
        }
        val shown = if (bool) Icon.toggle(value == true) else "<white>$value</white>"
        val color = if (changed) "gold" else "yellow"
        Icon.of(material, "<$color><lang:$key></$color>", listOfNotNull(
            "<gray><lang_or:$key.description:'-'></gray>",
            "<dark_gray>${w.service.key(rule)}</dark_gray>",
            "",
            "<gray>지금: </gray>$shown <gray>· 기본: <white>${if (bool) (if (default == true) "켜짐" else "꺼짐") else default}</white></gray>",
            if (changed) "<gold>기본값과 다릅니다 — 켤 때마다 이 값으로 덮어씁니다.</gold>" else null,
            "",
            if (bool) "<gray>좌클릭: 바꾸기 · Q: 기본값으로</gray>"
            else "<gray>좌클릭 +1 · 우클릭 −1 · Shift ±10 · 숫자키(1~9)·Q: 직접 입력 · Ctrl+Q: 기본값</gray>",
        ))
    }

    private fun click(world: World, rule: GameRule<*>, event: InventoryClickEvent) {
        val click = event.click
        val value = world.getGameRuleValue(rule)
        val default = world.getGameRuleDefault(rule)
        if (w.service.isBoolean(rule)) {
            val next = if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) default else !(value as Boolean)
            w.service.setRule(world, rule, next.toString())
            return refresh()
        }
        val current = (value as? Int) ?: 0
        when {
            click == ClickType.CONTROL_DROP -> w.service.setRule(world, rule, default.toString())
            Editors.isPrompt(event) -> {
                ask(DialogForm("게임 규칙").line(w.service.key(rule)).long("value", "값", current.toLong(), Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())) { v ->
                    v.long("value")?.let { w.service.setRule(world, rule, it.toString()) }
                }
                return
            }
            else -> {
                val step = if (click.isShiftClick) 10 else 1
                w.service.setRule(world, rule, (current + if (click.isRightClick) -step else step).toString())
            }
        }
        refresh()
    }
}
