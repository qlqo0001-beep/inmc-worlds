package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import com.inmc.worlds.mode.Mode
import com.inmc.worlds.mode.ModeSpawn
import com.inmc.worlds.util.Ph
import kr.inmc.core.gui.ConfirmMenu
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.ItemInputMenu
import kr.inmc.core.gui.Paging
import kr.inmc.core.gui.PickMenu
import kr.inmc.core.integration.PlaceNames
import kr.inmc.core.integration.Realms
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player

/** `/모드` — 갈 수 있는 모드 고르기(권한 `inmcworlds.mode.<id>`, 선언하지 않은 노드라 기본 허용 — 막으려면 LuckPerms 에서 false). */
class ModePickMenu(w: Worlds, viewer: Player) : Menu(w, viewer, 54, "<dark_green>모드</dark_green>") {

    override fun draw() {
        clear()
        val here = w.realm.modeAt(viewer)
        val modes = w.modes.all().filter { canEnter(viewer, it.id) }
        for ((slot, mode) in modes.take(Paging.PER_PAGE).withIndex()) {
            val players = Bukkit.getOnlinePlayers().count { w.realm.of(it.uniqueId) == mode.id }
            set(slot, Icon.of(mode.icon, mode.name, listOfNotNull(
                "<gray>접속 <white>$players</white>명</gray>",
                if (mode.id == here) "<green>▶ 지금 이 모드</green>" else "<gray>클릭: 이 모드로</gray>",
            ))) {
                if (mode.id == here) return@set
                viewer.closeInventory()
                w.realm.go(viewer, mode.id)
            }
        }
        fillEmpty(Icon.FILLER)
        if (viewer.hasPermission(Worlds.ADMIN)) set(49, Icon.of(Material.COMPARATOR, "<gold>모드 관리</gold>", listOf("<gray>모드 만들기·월드·스폰·채팅/탭 묶음·기능 토글</gray>"))) {
            ModeAdminMenu(w, viewer).show()
        }
        navigation()
    }

    companion object {
        fun modePermission(id: String) = "inmcworlds.mode.$id"

        /** 그 모드로 갈 수 있는가 — 관리자는 늘, 그 밖은 권한을 따로 끄지 않았으면. */
        fun canEnter(player: Player, id: String): Boolean {
            val node = modePermission(id)
            return player.hasPermission(Worlds.ADMIN) || !player.isPermissionSet(node) || player.hasPermission(node)
        }
    }
}

/** `/모드 관리` — 모드 목록 · [모드 만들기] · 빌려 쓰는 월드 · 기능 토글. */
class ModeAdminMenu(w: Worlds, viewer: Player) : Menu(w, viewer, 54, "<dark_green>모드 관리</dark_green>") {

    override fun draw() {
        clear()
        for ((slot, mode) in w.modes.all().take(Paging.PER_PAGE - 9).withIndex()) {
            set(slot, Icon.of(mode.icon, mode.name, listOfNotNull(
                "<dark_gray>${mode.id}</dark_gray>" + if (mode.id == w.modes.defaultId) " <gray>· 기본 모드</gray>" else "",
                "<gray>월드: <white>${mode.worlds.joinToString(", ").ifEmpty { "-" }}</white></gray>",
                "<gray>채팅 묶음 <white>${mode.chatGroup}</white> · 탭 묶음 <white>${mode.tabGroup}</white></gray>",
                "", "<gray>클릭: 고치기</gray>",
            ))) { ModeEditMenu(w, viewer, mode.id).show() }
        }
        fillEmpty(Icon.FILLER)
        set(47, Icon.of(Material.LIME_DYE, "<green>＋ 모드 만들기</green>", listOf("<gray>id(영어)와 이름을 정합니다. 월드는 만든 뒤 넣습니다.</gray>"))) {
            ask(DialogForm("모드 만들기").line("id: 영어 소문자·숫자·_·- (예: build)").text("id", "id", "", 32).text("name", "이름", "", 64)) { v ->
                val id = v.text("id").trim().lowercase()
                if (!Realms.MODE_ID.matches(id) || w.modes.get(id) != null) return@ask w.messages.send(viewer, "mode-bad-id")
                w.modes.put(Mode(id = id, name = v.text("name").trim().ifEmpty { id }))
                w.messages.send(viewer, "mode-created", Ph.of().value(id))
            }
        }
        set(49, Icon.of(Material.LEAD, "<yellow>빌려 쓰는 월드</yellow>", listOf(
            "<gray>여러 모드가 같이 들어가는 월드(던전 같은 것).</gray>",
            "<gray>안에서는 들어온 모드를 그대로 씁니다.</gray>",
            "<gray>지금: <white>${w.modes.borrowed().joinToString(", ").ifEmpty { "-" }}</white></gray>",
            "", "<gray>클릭해서 고르기</gray>",
        ))) { borrowed() }
        set(51, Icon.of(Material.REPEATER, "<yellow>기능 토글</yellow>", listOf(
            "<gray>기능마다 모드끼리 같이 쓸지(가방·장비 칸·칭호 …).</gray>",
            "", "<gray>클릭해서 열기</gray>",
        ))) { FeaturesMenu(w, viewer).show() }
        navigation()
    }

    private fun borrowed() {
        val worlds = Bukkit.getWorlds().map { it.name }
        PickMenu(w, viewer, "<dark_green>빌려 쓰는 월드</dark_green>", worlds,
            icon = { name -> Icon.of(Material.LEAD, Menu.displayName(name), listOf("<dark_gray>$name</dark_gray>")) },
            multi = true,
            selected = { worlds.filter { w.modes.isBorrowed(it) }.toSet() },
            back = { show() },
        ) { name ->
            val now = w.modes.isBorrowed(name)
            w.modes.setBorrowed(name, !now)
            if (now) w.modes.ensureWorld(name, PlaceNames.world(name) ?: "<white>$name</white>")
            w.realm.reconcileAll()
        }.show()
    }
}

/** 모드 하나 — 이름 · 아이콘 · 월드 · 스폰 · 들어올 때 게임 모드 · 채팅/탭 묶음 · 지우기. */
class ModeEditMenu(w: Worlds, viewer: Player, private val id: String) : Menu(w, viewer, 54, "<dark_green>모드 고치기</dark_green>") {

    override val back: (() -> Unit) = { ModeAdminMenu(w, viewer).show() }

    private fun mode(): Mode? = w.modes.get(id)

    private fun update(change: (Mode) -> Mode) {
        val mode = mode() ?: return
        w.modes.put(change(mode))
    }

    override fun draw() {
        clear()
        val mode = mode() ?: return ModeAdminMenu(w, viewer).show()
        set(4, Icon.of(mode.icon, mode.name, listOf("<dark_gray>${mode.id}</dark_gray>")))
        set(10, valueIcon(Material.NAME_TAG, "이름", mode.name, "<gray>색 태그를 쓸 수 있습니다.</gray>")) {
            ask(DialogForm("모드 이름").text("name", "이름", mode.name, 64)) { v -> v.text("name").trim().takeIf { it.isNotEmpty() }?.let { n -> update { it.copy(name = n) } } }
        }
        set(11, Icon.of(mode.icon, "<yellow>아이콘</yellow>", listOf("", "<gray>클릭: 아이템 올려 바꾸기</gray>"))) {
            ItemInputMenu(w, viewer, "모드 아이콘", submitLabel = "고르기", back = { show() }) { stack ->
                update { it.copy(icon = stack.type) }
                show()
            }.show()
        }
        set(12, Icon.of(Material.GRASS_BLOCK, "<yellow>월드</yellow>", mode.worlds.map { "<gray>· </gray>" + Menu.displayName(it) } + listOf(
            "", "<gray>클릭: 넣기·빼기(다른 모드의 월드를 넣으면 그 모드에서 빠집니다)</gray>",
        ))) { worlds() }
        set(13, Icon.of(Material.RED_BED, "<yellow>모드 스폰</yellow>", listOf(
            mode.spawn?.let { "<gray>지금: <white>${it.world} ${it.x.toInt()}, ${it.y.toInt()}, ${it.z.toInt()}</white></gray>" } ?: "<gray>지금: 모드 첫 월드의 스폰</gray>",
            "", "<gray>좌클릭: 내가 선 자리로 · 우클릭: 비우기</gray>",
        ))) { event ->
            if (event.isRightClick) update { it.copy(spawn = null) }
            else {
                val at = viewer.location
                if (w.modes.modeOfWorld(at.world.name) != id) return@set w.messages.send(viewer, "mode-spawn-not-here")
                update { it.copy(spawn = ModeSpawn(at.world.name, at.x, at.y, at.z, at.yaw, at.pitch)) }
            }
            refresh()
        }
        set(14, Icon.of(Material.DIAMOND_SWORD, "<yellow>들어올 때 게임 모드: </yellow><white>${gameModeLabel(mode.gameMode)}</white>", listOf(
            "<gray>그대로 → 서바이벌 → 크리에이티브 → 모험 → 관전</gray>", "", "<gray>클릭해서 바꾸기</gray>",
        ))) {
            val options = listOf<GameMode?>(null) + GameMode.entries
            update { it.copy(gameMode = options[(options.indexOf(it.gameMode) + 1) % options.size]) }
            refresh()
        }
        set(15, Icon.of(Material.WRITABLE_BOOK, "<yellow>채팅 묶음: </yellow><white>${groupLabel(mode.chatGroup)}</white>", listOf(
            "<gray>같은 묶음의 모드끼리 채팅이 보입니다.</gray>", "", "<gray>클릭: 묶을 모드 고르기</gray>",
        ))) { pickGroup(chat = true) }
        set(16, Icon.of(Material.PLAYER_HEAD, "<yellow>탭 묶음: </yellow><white>${groupLabel(mode.tabGroup)}</white>", listOf(
            "<gray>같은 묶음의 모드끼리 탭 목록에 보입니다.</gray>", "", "<gray>클릭: 묶을 모드 고르기</gray>",
        ))) { pickGroup(chat = false) }
        set(22, Icon.of(Material.ENDER_PEARL, "<yellow>이 모드로 이동</yellow>", emptyList())) {
            viewer.closeInventory()
            w.realm.go(viewer, id)
        }
        if (id != w.modes.defaultId) set(40, Icon.of(Material.LAVA_BUCKET, "<red>모드 지우기</red>", listOf(
            "<gray>이 모드의 월드는 각자 자기 모드가 됩니다.</gray>", "<gray>사람들의 이 모드 자료(가방 파일)는 남습니다.</gray>",
        ))) {
            ConfirmMenu(w, "<red>'${mode.id}' 모드를 지웁니다</red>", onConfirm = {
                w.modes.remove(id)?.worlds?.forEach { world -> w.modes.ensureWorld(world, PlaceNames.world(world) ?: "<white>$world</white>") }
                w.realm.reconcileAll()
                w.messages.send(viewer, "mode-deleted", Ph.of().value(id))
                ModeAdminMenu(w, viewer).show()
            }, onCancel = { show() }).open(viewer)
        }
        fillEmpty(Icon.FILLER)
        navigation()
    }

    private fun worlds() {
        val all = Bukkit.getWorlds().map { it.name }.filter { !w.modes.isBorrowed(it) }
        PickMenu(w, viewer, "<dark_green>이 모드의 월드</dark_green>", all,
            icon = { name -> Icon.of(Bukkit.getWorld(name)?.let { Menu.environmentIcon(it.environment) } ?: Material.MAP, Menu.displayName(name),
                listOf("<dark_gray>$name</dark_gray>", "<gray>지금 모드: <white>${w.modes.modeOfWorld(name)}</white></gray>")) },
            multi = true,
            selected = { mode()?.worlds?.toSet().orEmpty() },
            back = { show() },
        ) { name ->
            val mode = mode() ?: return@PickMenu
            if (mode.worlds.any { it.equals(name, true) }) {
                update { it.copy(worlds = it.worlds.filterNot { n -> n.equals(name, true) }) }
                w.modes.ensureWorld(name, PlaceNames.world(name) ?: "<white>$name</white>")
            } else {
                // 다른 모드에서 옮겨 온다 — 비게 된 자동 모드는 지운다.
                w.modes.assign(name, id)
            }
            w.realm.reconcileAll()
        }.show()
    }

    private fun pickGroup(chat: Boolean) {
        val others = w.modes.all()
        PickMenu(w, viewer, if (chat) "<dark_green>채팅을 같이 볼 모드</dark_green>" else "<dark_green>탭을 같이 볼 모드</dark_green>", others,
            icon = { m -> Icon.of(m.icon, m.name, listOf(if (m.id == id) "<gray>따로(이 모드만)</gray>" else "<gray>이 모드와 묶기</gray>")) },
            back = { show() },
        ) { m ->
            val group = if (m.id == id) id else (if (chat) m.chatGroup else m.tabGroup)
            update { if (chat) it.copy(chatGroup = group) else it.copy(tabGroup = group) }
            if (!chat) w.tabs.refresh()
            show()
        }.show()
    }

    private fun groupLabel(group: String): String =
        w.modes.all().filter { it.chatGroup == group || it.tabGroup == group || it.id == group }.joinToString(", ") { it.id }.ifEmpty { group }

    private fun gameModeLabel(mode: GameMode?): String = when (mode) {
        null -> "그대로"
        GameMode.SURVIVAL -> "서바이벌"
        GameMode.CREATIVE -> "크리에이티브"
        GameMode.ADVENTURE -> "모험"
        GameMode.SPECTATOR -> "관전"
    }
}

/** 기능 토글 — core `Realms.features()` 전부(플러그인마다). 누르면 확인 뒤 **바로** 적용(재시작 없이). */
class FeaturesMenu(w: Worlds, viewer: Player, private var page: Int = 0) : Menu(w, viewer, 54, "<dark_green>기능 토글</dark_green>") {

    override val back: (() -> Unit) = { ModeAdminMenu(w, viewer).show() }

    override fun draw() {
        clear()
        val features = Realms.features().sortedWith(compareBy({ it.owner }, { it.label }))
        page = Paging.clamp(page, features.size)
        for ((slot, feature) in Paging.slice(features, page).withIndex()) {
            val shared = w.modes.isShared(feature.key)
            set(slot, Icon.of(feature.icon, "<white>${feature.label}</white> <gray>· ${feature.owner}</gray>", feature.description.map { "<gray>$it</gray>" } + listOf(
                "<dark_gray>${feature.key}</dark_gray>",
                "",
                if (shared) "<aqua>모드끼리 같이</aqua>" else "<green>모드마다 따로</green>",
                "<gray>클릭해서 바꾸기(접속한 사람에게 바로)</gray>",
            ))) {
                val online = Bukkit.getOnlinePlayers().size
                ConfirmMenu(w, "<yellow>${feature.label}: ${if (shared) "모드마다 따로" else "모드끼리 같이"}</yellow>", listOf(
                    "<gray>접속한 ${online}명의 ${feature.label} 이(가) 지금 바뀝니다.</gray>",
                    "<gray>옛 칸의 자료는 지우지 않습니다 — 다시 바꾸면 돌아옵니다.</gray>",
                ), onConfirm = {
                    w.realm.setShared(feature.key, !shared)
                    show()
                }, onCancel = { show() }).open(viewer)
            }
        }
        fillEmpty(Icon.FILLER)
        if (page > 0) set(Paging.SLOT_PREV, Icon.prevPage()) { page--; refresh() }
        if (page < Paging.pageCount(features.size) - 1) set(Paging.SLOT_NEXT, Icon.nextPage()) { page++; refresh() }
        navigation()
    }
}
