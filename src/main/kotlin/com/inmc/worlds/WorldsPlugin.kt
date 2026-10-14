package com.inmc.worlds

import com.inmc.worlds.command.WorldsCommand
import com.inmc.worlds.config.Messages
import com.inmc.worlds.portal.PortalListener
import com.inmc.worlds.world.MultiverseImport
import com.inmc.worlds.world.WorldListener
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/**
 * 켤 때 월드를 **그 자리에서** 불러온다 — 다른 플러그인(스폰·NPC·상점)이 켜질 때 월드가 있어야 한다(`paper-plugin.yml` 의 `load: AFTER`).
 */
class WorldsPlugin : JavaPlugin() {

    private lateinit var worlds: Worlds

    override fun onEnable() {
        worlds = Worlds(this)
        worlds.io.copyDefault(MESSAGES, worlds.io.file(MESSAGES))
        worlds.messages = Messages.from(worlds.io.load(worlds.io.file(MESSAGES)))

        if (server.pluginManager.getPlugin("Multiverse-Core")?.isEnabled == true) {
            logger.severe("Multiverse-Core 가 켜져 있습니다 — 둘이 같은 월드를 다룹니다. Multiverse 3종(Core·Inventories·NetherPortals)을 빼세요.")
        }
        worlds.store.loadNow()
        MultiverseImport(worlds).runOnce()
        worlds.service.ensureVanillaEntries()
        worlds.service.loadAll()

        val manager = server.pluginManager
        manager.registerEvents(WorldListener(worlds), this)
        manager.registerEvents(PortalListener(worlds), this)
        manager.registerEvents(kr.inmc.core.listener.MenuListener(worlds), this)
        WorldsCommand(worlds, this).register(this)

        logger.info("inmc-worlds 활성화 완료 - 월드 ${Bukkit.getWorlds().size}개")
    }

    override fun onDisable() {
        if (!::worlds.isInitialized) return
        for (world in Bukkit.getWorlds()) runCatching { worlds.service.snapshot(world) }
        worlds.store.saveBlocking()
        worlds.io.shutdown()
    }

    /** `/월드 리로드` — 메시지만(월드 항목은 화면이 쓰는 파일이라 다시 읽지 않는다). */
    fun reload() {
        worlds.closeMenus()
        worlds.messages = Messages.from(worlds.io.load(worlds.io.file(MESSAGES)))
    }

    private companion object {
        const val MESSAGES = "messages.yml"
    }
}
