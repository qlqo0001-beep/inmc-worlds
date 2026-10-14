package com.inmc.worlds.command

import com.inmc.worlds.Worlds
import com.inmc.worlds.body.BukkitBody
import com.inmc.worlds.body.Part
import com.inmc.worlds.gui.ModeAdminMenu
import com.inmc.worlds.gui.ModePickMenu
import com.inmc.worlds.gui.ProfileInventoryMenu
import com.inmc.worlds.util.Ph
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** `/모드` — 누구나: 고르기 화면 · `/모드 <id>`. 관리자: `/모드 관리` · `/모드 가방 <사람> <모드>`. */
class ModesCommand(private val w: Worlds) {

    fun register(owner: JavaPlugin) {
        owner.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "INMC 모드 고르기", listOf("inmcmode", "mode"))
        }
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? =
        (ctx.source.executor as? Player ?: ctx.source.sender as? Player) ?: null.also { w.messages.send(ctx.source.sender, "player-only") }

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("모드")
            .executes { ctx -> player(ctx)?.let { ModePickMenu(w, it).show() }; 1 }
            .then(Commands.literal("관리").requires { it.sender.hasPermission(Worlds.ADMIN) }.executes { ctx ->
                player(ctx)?.let { ModeAdminMenu(w, it).show() }
                1
            })
            .then(Commands.literal("가방").requires { it.sender.hasPermission(Worlds.ADMIN) }
                .then(Commands.argument("사람", StringArgumentType.word()).suggests { _, builder ->
                    for (p in Bukkit.getOnlinePlayers()) if (p.name.startsWith(builder.remaining, true)) builder.suggest(p.name)
                    builder.buildFuture()
                }.then(Commands.argument("모드", StringArgumentType.word()).suggests { _, builder ->
                    for (mode in w.modes.all()) if (mode.id.startsWith(builder.remainingLowerCase)) builder.suggest(mode.id)
                    builder.buildFuture()
                }.executes { ctx ->
                    val viewer = player(ctx) ?: return@executes 0
                    profile(viewer, StringArgumentType.getString(ctx, "사람"), StringArgumentType.getString(ctx, "모드").lowercase())
                    1
                })))
            .then(Commands.argument("모드", StringArgumentType.word()).suggests { ctx, builder ->
                val sender = ctx.source.sender as? Player
                for (mode in w.modes.all()) {
                    if (sender != null && !ModePickMenu.canEnter(sender, mode.id)) continue
                    if (mode.id.startsWith(builder.remainingLowerCase)) builder.suggest(mode.id)
                }
                builder.buildFuture()
            }.executes { ctx ->
                val player = player(ctx) ?: return@executes 0
                val id = StringArgumentType.getString(ctx, "모드").lowercase()
                when {
                    w.modes.get(id) == null -> w.messages.send(player, "mode-unknown", Ph.of().value(id))
                    !ModePickMenu.canEnter(player, id) -> w.messages.send(player, "mode-denied", Ph.of().value(id))
                    w.realm.modeAt(player) == id -> w.messages.send(player, "mode-here")
                    else -> w.realm.go(player, id)
                }
                1
            })

    /** `/모드 가방` — 그 모드(같이 쓰면 같이 쓰는 칸)의 가방 파일. 몸이 그 칸을 쥐고 있으면 열지 않는다. */
    private fun profile(viewer: Player, name: String, mode: String) {
        if (w.modes.get(mode) == null) return w.messages.send(viewer, "mode-unknown", Ph.of().value(mode))
        val online = Bukkit.getPlayerExact(name)
        val target = online ?: Bukkit.getOfflinePlayerIfCached(name) ?: return w.messages.send(viewer, "player-unknown", Ph.of().value(name))
        val scope = if (w.modes.isShared(Part.INVENTORY.key)) "" else mode
        if (online != null && BukkitBody.parseHeldOf(online)?.get(Part.INVENTORY) == scope) {
            return w.messages.send(viewer, "profile-held", Ph.of().player(online.name))
        }
        ProfileInventoryMenu(w, viewer, target.uniqueId, target.name ?: name, scope).show()
    }
}
