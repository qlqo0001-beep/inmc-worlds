package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import com.inmc.worlds.world.PortalLink
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.PickMenu
import kr.inmc.core.integration.PlaceNames
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * 포탈 연결 — 이 월드의 네더 포탈·엔드 포탈이 어디로 가는지(Multiverse-NetherPortals 대신). 바닐라대로 · 막기 · 월드 고르기.
 * 네더 포탈은 차원 비율대로 좌표를 옮기고(오버월드 1 · 네더 8), 엔드 포탈은 엔드 월드면 발판 위로, 아니면 그 월드의 스폰으로.
 */
class PortalLinkMenu(w: Worlds, viewer: Player, private val name: String) : Menu(w, viewer, 27, "<dark_green>포탈 연결</dark_green>") {

    override val back: (() -> Unit) = { WorldSettingsMenu(w, viewer, name).show() }

    override fun draw() {
        clear()
        val world = Bukkit.getWorld(name) ?: return WorldListMenu(w, viewer).show()
        val entry = w.service.entryOf(world)
        set(11, Icon.of(Material.OBSIDIAN, "<yellow>네더 포탈 → </yellow>${label(entry.nether)}", listOf(
            "<gray>이 월드에서 네더 포탈을 타면 가는 곳.</gray>", "", "<gray>클릭해서 고르기</gray>",
        ))) { pick(nether = true) }
        set(15, Icon.of(Material.END_PORTAL_FRAME, "<yellow>엔드 포탈 → </yellow>${label(entry.end)}", listOf(
            "<gray>이 월드에서 엔드 포탈을 타면 가는 곳.</gray>", "", "<gray>클릭해서 고르기</gray>",
        ))) { pick(nether = false) }
        fillEmpty(Icon.FILLER)
        navigation(backSlot = 18, closeSlot = 26)
    }

    private fun pick(nether: Boolean) {
        val world = Bukkit.getWorld(name) ?: return
        val options = listOf(PortalLink.VANILLA, PortalLink.NONE) + Bukkit.getWorlds().filter { it != world }.map { PortalLink.to(it.name) }
        PickMenu(w, viewer, if (nether) "<dark_green>네더 포탈이 갈 곳</dark_green>" else "<dark_green>엔드 포탈이 갈 곳</dark_green>", options,
            icon = { link ->
                when (link.kind) {
                    PortalLink.Kind.VANILLA -> Icon.of(Material.GRASS_BLOCK, "<white>바닐라대로</white>", listOf("<gray>본 월드의 네더·엔드처럼 서버에 맡깁니다.</gray>"))
                    PortalLink.Kind.NONE -> Icon.of(Material.BARRIER, "<red>막기</red>", listOf("<gray>포탈을 타도 아무 데도 안 갑니다.</gray>"))
                    PortalLink.Kind.WORLD -> {
                        val target = Bukkit.getWorld(link.world!!)
                        Icon.of(target?.let { Menu.environmentIcon(it.environment) } ?: Material.MAP, label(link),
                            listOf("<dark_gray>${link.world}</dark_gray>"))
                    }
                }
            },
            back = { show() },
        ) { link ->
            w.service.setPortal(world, nether, link)
            show()
        }.show()
    }

    companion object {
        fun label(link: PortalLink): String = when (link.kind) {
            PortalLink.Kind.VANILLA -> "<white>바닐라대로</white>"
            PortalLink.Kind.NONE -> "<red>막음</red>"
            PortalLink.Kind.WORLD -> link.world?.let { PlaceNames.world(it) ?: "<white>$it</white>" } ?: "<red>?</red>"
        }
    }
}
