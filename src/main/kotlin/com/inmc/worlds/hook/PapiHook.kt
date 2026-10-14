package com.inmc.worlds.hook

import com.inmc.worlds.Worlds
import kr.inmc.core.util.Text
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

/**
 * PlaceholderAPI — `%inmcworlds_mode%`(모드 id) · `%inmcworlds_mode_name%`(모드 이름, § 색). PlaceholderAPI 클래스는 compileOnly 라 **플러그인이 있을 때만**
 * 건드린다 — [setup] 이 유일한 문이고, 모든 플러그인이 켜진 뒤(`ServerLoadEvent`) 부른다(우리가 PlaceholderAPI 보다 먼저 켜진다).
 */
class PapiHook(private val w: Worlds) {

    private var expansion: PlaceholderExpansion? = null

    fun setup() {
        teardown()
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return
        try {
            expansion = WorldsExpansion(w).also { it.register() }
        } catch (t: Throwable) {
            w.logger.warning("PlaceholderAPI 연동 실패: ${t.message}")
            teardown()
        }
    }

    fun teardown() {
        expansion?.let { runCatching { it.unregister() } }
        expansion = null
    }
}

/** 따로 둔 것은 PlaceholderAPI 가 있을 때만 이 클래스가 적재되게 하려는 것이다. */
private class WorldsExpansion(private val w: Worlds) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "inmcworlds"

    override fun getAuthor(): String = "INMC"

    override fun getVersion(): String = w.plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun getPlaceholders(): List<String> = listOf("%inmcworlds_mode%", "%inmcworlds_mode_name%")

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val id = player?.uniqueId ?: return ""
        val mode = w.realm.of(id)
        return when (params.lowercase()) {
            "mode" -> mode
            "mode_name" -> LegacyComponentSerializer.legacySection().serialize(Text.renderFlat(w.modes.get(mode)?.name ?: mode))
            else -> null
        }
    }
}
