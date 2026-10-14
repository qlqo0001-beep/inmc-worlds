package com.inmc.worlds

import com.inmc.worlds.config.Messages
import com.inmc.worlds.mode.ModeService
import com.inmc.worlds.mode.ModeStore
import com.inmc.worlds.mode.TabSync
import com.inmc.worlds.util.Ph
import com.inmc.worlds.world.WorldService
import com.inmc.worlds.world.WorldStore
import kr.inmc.core.InmcHost
import kr.inmc.core.config.ConfigService
import kr.inmc.core.util.Placeholders
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

/**
 * 플러그인을 엮는 서비스 로케이터.
 *
 * 다른 inmc 플러그인을 모른다 — 월드 이름은 core `PlaceNames`(타이틀포지), 모드는 core `Realms` 로 내놓는다.
 */
class Worlds(override val plugin: JavaPlugin) : InmcHost {

    val logger: java.util.logging.Logger = plugin.logger

    override val io = ConfigService(plugin)

    override fun tell(target: CommandSender, key: String, ph: Placeholders?) = messages.send(target, key, ph as? Ph)

    @Volatile
    var messages: Messages = Messages.from(YamlConfiguration())

    val store = WorldStore(this)

    val service = WorldService(this)

    /** 모드들 · 빌려 쓰는 월드 · 기능 토글(`modes.yml`). */
    val modes = ModeStore(this)

    /** 사람의 모드 · 몸 맞추기 · 모드를 넘는 이동. */
    val realm = ModeService(this)

    val tabs = TabSync()

    /** 리로드 때 — 버려질 상태를 계속 고치지 않게 열린 우리 화면을 닫는다. */
    fun closeMenus() {
        for (player in Bukkit.getOnlinePlayers()) {
            val holder = player.openInventory.topInventory.holder
            if (holder is kr.inmc.core.gui.Menu && holder.owner === this) player.closeInventory()
        }
    }

    companion object {
        /** PDC 네임스페이스 — 플러그인 이름이 바뀌어도 이미 찍힌 표식이 정체를 잃지 않게 상수로 고정한다. */
        const val NAMESPACE = "inmcworlds"

        const val ADMIN = "inmcworlds.admin"
    }
}
