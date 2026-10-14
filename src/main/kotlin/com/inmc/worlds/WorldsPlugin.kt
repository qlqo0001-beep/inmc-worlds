package com.inmc.worlds

import com.inmc.worlds.body.Part
import com.inmc.worlds.command.ModesCommand
import com.inmc.worlds.command.WorldsCommand
import com.inmc.worlds.config.Messages
import com.inmc.worlds.mode.ModeListener
import com.inmc.worlds.mode.RealmsProvider
import com.inmc.worlds.portal.PortalListener
import com.inmc.worlds.world.MultiverseImport
import com.inmc.worlds.world.WorldListener
import kr.inmc.core.integration.PlaceNames
import kr.inmc.core.integration.Realms
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/**
 * 켤 때 월드를 **그 자리에서** 불러온다 — 다른 플러그인(스폰·NPC·상점)이 켜질 때 월드가 있어야 한다(`paper-plugin.yml` 의 `load: AFTER`).
 * 그다음 모드를 읽고 core `Realms` 공급처를 꽂는다 — 그 뒤로 켜지는 플러그인은 처음부터 모드를 본다.
 */
class WorldsPlugin : JavaPlugin() {

    private lateinit var worlds: Worlds

    private var provider: RealmsProvider? = null

    private var papi: com.inmc.worlds.hook.PapiHook? = null

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

        // 모드 — 월드를 불러오기 전에(불러오는 월드마다 모드를 정한다).
        worlds.modes.loadNow(worlds.service.vanillaNames().filter { Bukkit.getWorld(it) != null })
        worlds.realm.index.loadNow()
        val manager = server.pluginManager
        manager.registerEvents(ModeListener(worlds), this)
        worlds.service.loadAll()
        for (world in Bukkit.getWorlds()) worlds.modes.ensureWorld(world.name, PlaceNames.world(world.name) ?: "<white>${world.name}</white>")

        for (part in Part.entries) {
            Realms.register(Realms.Feature(part.key, OWNER, part.label, part.icon, part.description))
        }
        provider = RealmsProvider(worlds).also(Realms::register)

        manager.registerEvents(WorldListener(worlds), this)
        manager.registerEvents(PortalListener(worlds), this)
        manager.registerEvents(kr.inmc.core.listener.MenuListener(worlds), this)
        WorldsCommand(worlds, this).register(this)
        ModesCommand(worlds).register(this)

        // PlaceholderAPI 는 우리 뒤에 켜진다 — 다 켜진 뒤에 건다.
        manager.registerEvents(object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onServerLoad(event: org.bukkit.event.server.ServerLoadEvent) {
                papi = com.inmc.worlds.hook.PapiHook(worlds).also { it.setup() }
            }
        }, this)

        // 리로드로 켜졌으면 접속자의 몸을 맞춘다.
        worlds.realm.reconcileAll()
        worlds.tabs.refresh()
        // 투명·숨김이 풀리면 서버가 목록에 다시 올린다 — 짧은 주기로 탭 묶음을 다시 맞춘다.
        server.globalRegionScheduler.runAtFixedRate(this, { worlds.tabs.refresh() }, TAB_PERIOD, TAB_PERIOD)
        logger.info("inmc-worlds 활성화 완료 - 월드 ${Bukkit.getWorlds().size}개 · 모드 ${worlds.modes.all().size}개")
    }

    override fun onDisable() {
        if (!::worlds.isInitialized) return
        papi?.teardown()
        provider?.let(Realms::unregister)
        Realms.unregisterAll(OWNER)
        for (world in Bukkit.getWorlds()) runCatching { worlds.service.snapshot(world) }
        worlds.store.saveBlocking()
        worlds.modes.saveBlocking()
        worlds.realm.index.saveBlocking()
        worlds.io.shutdown()
    }

    /** `/월드 리로드` — 메시지만(월드·모드 항목은 화면이 쓰는 파일이라 다시 읽지 않는다). */
    fun reload() {
        worlds.closeMenus()
        worlds.messages = Messages.from(worlds.io.load(worlds.io.file(MESSAGES)))
    }

    private companion object {
        const val MESSAGES = "messages.yml"

        /** core `Realms` 기능 목록의 묶음 이름. */
        const val OWNER = "월드·모드"

        /** 탭 묶음을 다시 맞추는 주기(틱). */
        const val TAB_PERIOD = 100L
    }
}
