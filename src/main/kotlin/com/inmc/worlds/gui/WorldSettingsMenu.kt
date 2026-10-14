package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import com.inmc.worlds.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.integration.PlaceNames
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameRules
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 월드 하나의 설정 — 보이는 이름(타이틀포지) · 난이도 · PVP · 스폰 · 게임 규칙 · 포탈 연결 · 이동 · 내리기 · 지우기.
 * 여기서 바꾼 값은 **플러그인 값**이 되어 켤 때마다 바닐라를 이긴다([com.inmc.worlds.world.WorldService.apply]).
 */
class WorldSettingsMenu(w: Worlds, viewer: Player, private val name: String) : Menu(w, viewer, 54, "<dark_green>월드 설정</dark_green>") {

    override val back: (() -> Unit) = { WorldListMenu(w, viewer).show() }

    override fun draw() {
        clear()
        val world = Bukkit.getWorld(name) ?: return WorldListMenu(w, viewer).show()
        val entry = w.service.entryOf(world)
        set(4, Icon.of(Menu.environmentIcon(world.environment), Menu.displayName(world.name), listOf(
            "<dark_gray>${world.name}</dark_gray>",
            "<gray>${Menu.environmentLabel(world.environment)} · ${entry.kind.label} · 시드 <white>${world.seed}</white></gray>",
            "<gray>접속 <white>${world.players.size}</white>명 · 읽힌 청크 <white>${world.loadedChunks.size}</white>개</gray>",
        )))

        set(10, valueIcon(Material.NAME_TAG, "보이는 이름", PlaceNames.world(world.name) ?: "<gray>(없음)</gray>",
            "<gray>타이틀포지 장소 이름(places.yml)과 같은 것입니다.</gray>", "<gray>색 태그를 쓸 수 있습니다. 비우면 지웁니다.</gray>")) {
            if (!PlaceNames.available) return@set w.messages.send(viewer, "names-unavailable")
            ask(DialogForm("보이는 이름").line("비우면 지웁니다 — 폴더 이름이 보입니다.").text("display", "이름", PlaceNames.world(world.name).orEmpty(), 128)) { v ->
                PlaceNames.setWorld(world.name, v.text("display").trim().ifEmpty { null })
            }
        }
        set(11, Icon.of(Material.SKELETON_SKULL, "<yellow>난이도: </yellow><white>${Menu.difficultyLabel(world.difficulty)}</white>",
            listOf("<gray>평화로움 → 쉬움 → 보통 → 어려움</gray>", "", "<gray>좌클릭: 다음 · 우클릭: 앞</gray>"))) { event ->
            val all = Difficulty.entries
            val step = if (event.isRightClick) all.size - 1 else 1
            w.service.setDifficulty(world, all[(all.indexOf(world.difficulty) + step) % all.size])
            refresh()
        }
        val pvp = world.getGameRuleValue(GameRules.PVP) ?: true
        set(12, toggleIcon("PVP", pvp, "<gray>사람끼리 때리기(게임 규칙 pvp).</gray>")) {
            w.service.setRule(world, GameRules.PVP, (!pvp).toString())
            refresh()
        }
        set(13, Icon.of(Material.RED_BED, "<yellow>스폰 자리</yellow>", listOf(
            world.spawnLocation.let { "<gray>지금: <white>${it.blockX}, ${it.blockY}, ${it.blockZ}</white></gray>" },
            "",
            "<gray>좌클릭: <white>내가 선 자리로 정하기</white>${if (viewer.world != world) " <red>(이 월드에 있을 때만)</red>" else ""}</gray>",
            "<gray>우클릭: <white>스폰으로 이동</white></gray>",
        ))) { event ->
            if (event.isRightClick) {
                viewer.closeInventory()
                w.service.travel(viewer, world.spawnLocation)
            } else if (viewer.world == world) {
                w.service.setSpawn(world, viewer.location)
                w.messages.send(viewer, "spawn-set", Ph.of().world(world.name))
                refresh()
            } else {
                w.messages.send(viewer, "spawn-not-here")
            }
        }
        set(14, Icon.of(Material.KNOWLEDGE_BOOK, "<yellow>게임 규칙</yellow>", listOf(
            "<gray>바닐라 게임 규칙 전부 — 설명과 함께.</gray>",
            "<gray>기본값과 다른 규칙 <white>${w.service.entryOf(world).rules.size}</white>개</gray>",
            "", "<gray>클릭해서 열기</gray>",
        ))) { GameRulesMenu(w, viewer, world.name).show() }
        set(15, Icon.of(Material.OBSIDIAN, "<yellow>포탈 연결</yellow>", listOf(
            "<gray>네더 포탈 → <white>${linkLabel(entry.nether)}</white></gray>",
            "<gray>엔드 포탈 → <white>${linkLabel(entry.end)}</white></gray>",
            "", "<gray>클릭해서 열기</gray>",
        ))) { PortalLinkMenu(w, viewer, world.name).show() }
        set(16, Icon.of(Material.ENDER_PEARL, "<yellow>이 월드로 이동</yellow>", listOf("<gray>스폰 자리로 갑니다.</gray>"))) {
            viewer.closeInventory()
            w.service.travel(viewer, world.spawnLocation)
        }

        if (!w.service.isVanilla(world.name)) {
            set(38, Icon.of(Material.GRAY_DYE, "<gold>내리기</gold>", listOf(
                "<gray>월드를 메모리에서 내리고 다음 켤 때도 불러오지 않습니다.</gray>",
                "<gray>안의 사람은 본 월드 스폰으로 갑니다. 목록에서 다시 불러올 수 있습니다.</gray>",
            ))) {
                ConfirmMenu(w, "<gold>'${world.name}' 을(를) 내립니다</gold>", onConfirm = {
                    val ok = w.service.unload(world)
                    w.messages.send(viewer, if (ok) "world-unloaded" else "world-unload-failed", Ph.of().world(name))
                    WorldListMenu(w, viewer).show()
                }, onCancel = { show() }).open(viewer)
            }
            set(42, Icon.of(Material.LAVA_BUCKET, "<red>지우기</red>", listOf("<gray>폴더까지 지웁니다. 되돌릴 수 없습니다.</gray>"))) {
                ConfirmMenu(w, "<red>'${world.name}' 월드를 지웁니다</red>", listOf("<gray>폴더까지 지웁니다. 되돌릴 수 없습니다.</gray>"), onConfirm = {
                    val ok = w.service.delete(name)
                    w.messages.send(viewer, if (ok) "world-deleted" else "world-delete-refused", Ph.of().world(name))
                    WorldListMenu(w, viewer).show()
                }, onCancel = { show() }).open(viewer)
            }
        }
        fillEmpty(Icon.FILLER)
        navigation()
    }

    private fun linkLabel(link: com.inmc.worlds.world.PortalLink): String = PortalLinkMenu.label(link)
}
