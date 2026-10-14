package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import com.inmc.worlds.util.Ph
import com.inmc.worlds.world.WorldEntry
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.Paging
import kr.inmc.core.gui.PickMenu
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

/**
 * `/월드` 첫 화면 — 불러온 월드 · 내려 둔 월드 · 가져올 수 있는 폴더. 아래 줄에 [새 월드 만들기].
 */
class WorldListMenu(w: Worlds, viewer: Player, private var page: Int = 0) : Menu(w, viewer, 54, "<dark_green>월드 관리</dark_green>") {

    private sealed interface Row {
        data class Loaded(val world: World) : Row
        data class Unloaded(val entry: WorldEntry) : Row
        data class Folder(val name: String) : Row
    }

    private fun rows(): List<Row> {
        val loaded = Bukkit.getWorlds().map { Row.Loaded(it) }
        val unloaded = w.store.all().filter { Bukkit.getWorld(it.name) == null }.map { Row.Unloaded(it) }
        val folders = w.service.candidates().map { Row.Folder(it) }
        return loaded + unloaded + folders
    }

    override fun draw() {
        clear()
        val rows = rows()
        page = Paging.clamp(page, rows.size)
        for ((slot, row) in Paging.slice(rows, page).withIndex()) when (row) {
            is Row.Loaded -> set(slot, loadedIcon(row.world)) { event ->
                if (event.click.isRightClick) {
                    viewer.closeInventory()
                    w.service.travel(viewer, row.world.spawnLocation)
                } else {
                    WorldSettingsMenu(w, viewer, row.world.name).show()
                }
            }
            is Row.Unloaded -> set(slot, Icon.of(Material.GRAY_DYE, Menu.displayName(row.entry.name), listOf(
                "<dark_gray>${row.entry.name}</dark_gray>",
                "<gray>내려 둠 · ${Menu.environmentLabel(row.entry.environment)} · ${row.entry.kind.label}</gray>",
                "",
                "<gray>좌클릭: <white>불러오기</white></gray>",
                "<gray>Shift+우클릭: <red>지우기</red></gray>",
            ))) { event ->
                if (event.click == ClickType.SHIFT_RIGHT) confirmDelete(row.entry.name) else load(row.entry.name)
            }
            is Row.Folder -> set(slot, Icon.of(Material.PAPER, "<white>${row.name}</white>", listOf(
                "<gray>폴더만 있는 월드 — 아직 이 플러그인이 모릅니다.</gray>",
                "",
                "<gray>클릭: <white>가져오기</white> (차원을 고릅니다)</gray>",
            ))) { importFolder(row.name) }
        }
        fillEmpty(Icon.FILLER)
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(rows.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        set(SLOT_CREATE, Icon.of(Material.WRITABLE_BOOK, "<green>＋ 새 월드 만들기</green>", listOf(
            "<gray>이름·차원·유형(평지·증폭·단일 생물 군계·공허 …)·시드·구조물을</gray>",
            "<gray>고른 뒤 만듭니다.</gray>",
        ))) { CreateWizard(w, viewer).show() }
        navigation()
    }

    private fun loadedIcon(world: World) = Icon.of(Menu.environmentIcon(world.environment), Menu.displayName(world.name), listOfNotNull(
        "<dark_gray>${world.name}</dark_gray>",
        "<gray>${Menu.environmentLabel(world.environment)} · ${w.service.entryOf(world).kind.label} · 난이도 ${Menu.difficultyLabel(world.difficulty)}</gray>",
        "<gray>접속 <white>${world.players.size}</white>명</gray>",
        if (w.service.isVanilla(world.name)) "<dark_gray>바닐라 월드 — 서버가 불러옵니다</dark_gray>" else null,
        "",
        "<gray>좌클릭: <white>설정</white> · 우클릭: <white>스폰으로 이동</white></gray>",
    ))

    private fun load(name: String) {
        viewer.closeInventory()
        w.messages.send(viewer, "world-loading", Ph.of().world(name))
        val world = w.service.load(name)
        w.messages.send(viewer, if (world != null) "world-loaded" else "world-load-failed", Ph.of().world(name))
        WorldListMenu(w, viewer, page).show()
    }

    private fun confirmDelete(name: String) {
        ConfirmMenu(w, "<red>'$name' 월드를 지웁니다</red>", listOf("<gray>폴더까지 지웁니다. 되돌릴 수 없습니다.</gray>"), onConfirm = {
            val ok = w.service.delete(name)
            w.messages.send(viewer, if (ok) "world-deleted" else "world-delete-refused", Ph.of().world(name))
            WorldListMenu(w, viewer, page).show()
        }, onCancel = { show() }).open(viewer)
    }

    private fun importFolder(name: String) {
        val options = listOf(World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END)
        PickMenu(w, viewer, "<dark_green>'$name' 의 차원</dark_green>", options,
            icon = { env -> Icon.of(Menu.environmentIcon(env), "<white>${Menu.environmentLabel(env)}</white>", listOf("<gray>이 차원으로 가져옵니다.</gray>")) },
            back = { show() },
        ) { env ->
            viewer.closeInventory()
            w.messages.send(viewer, "world-loading", Ph.of().world(name))
            val world = w.service.import(name, env)
            w.messages.send(viewer, if (world != null) "world-imported" else "world-load-failed", Ph.of().world(name))
            WorldListMenu(w, viewer, page).show()
        }.show()
    }

    private companion object {
        const val SLOT_CREATE = 49
    }
}
