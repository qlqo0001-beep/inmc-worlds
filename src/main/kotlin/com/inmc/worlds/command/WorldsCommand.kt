package com.inmc.worlds.command

import com.inmc.worlds.Worlds
import com.inmc.worlds.WorldsPlugin
import com.inmc.worlds.gui.CreateWizard
import com.inmc.worlds.gui.WorldListMenu
import com.inmc.worlds.gui.WorldSettingsMenu
import com.inmc.worlds.util.Ph
import com.inmc.worlds.verify.Verifier
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/월드` 한 트리(관리자). 인자 없이 치면 월드 관리 화면 — 설정은 전부 화면에서 한다(사용자 결정 2026-10-15).
 */
class WorldsCommand(private val w: Worlds, private val plugin: WorldsPlugin) {

    fun register(owner: JavaPlugin) {
        owner.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "INMC 월드 관리", listOf("inmcworlds", "worlds"))
        }
    }

    private fun sender(ctx: CommandContext<CommandSourceStack>): CommandSender = ctx.source.sender

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? =
        (ctx.source.executor as? Player ?: ctx.source.sender as? Player) ?: null.also { w.messages.send(sender(ctx), "player-only") }

    private fun isAdmin(source: CommandSourceStack): Boolean = source.sender.hasPermission(Worlds.ADMIN)

    private fun worldArg() = Commands.argument("월드", StringArgumentType.word()).suggests { _, builder ->
        for (world in Bukkit.getWorlds()) if (world.name.startsWith(builder.remainingLowerCase, true)) builder.suggest(world.name)
        builder.buildFuture()
    }

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("월드").requires(::isAdmin)
            .executes { ctx -> player(ctx)?.let { WorldListMenu(w, it).show() }; 1 }
            .then(Commands.literal("만들기").executes { ctx -> player(ctx)?.let { CreateWizard(w, it).show() }; 1 })
            .then(
                Commands.literal("설정")
                    .executes { ctx -> player(ctx)?.let { WorldSettingsMenu(w, it, it.world.name).show() }; 1 }
                    .then(worldArg().executes { ctx ->
                        val player = player(ctx) ?: return@executes 0
                        val name = StringArgumentType.getString(ctx, "월드")
                        if (Bukkit.getWorld(name) == null) w.messages.send(player, "world-unknown", Ph.of().world(name))
                        else WorldSettingsMenu(w, player, name).show()
                        1
                    }),
            )
            .then(
                Commands.literal("이동").then(worldArg().executes { ctx ->
                    val player = player(ctx) ?: return@executes 0
                    val name = StringArgumentType.getString(ctx, "월드")
                    val world = Bukkit.getWorld(name) ?: return@executes 0.also { w.messages.send(player, "world-unknown", Ph.of().world(name)) }
                    w.service.travel(player, world.spawnLocation)
                    1
                }),
            )
            .then(Commands.literal("리로드").executes { ctx ->
                plugin.reload()
                w.messages.send(sender(ctx), "reloaded")
                1
            })
            .then(Commands.literal("검증").executes { ctx -> player(ctx)?.let { Verifier(w).run(it) }; 1 })
            .then(Commands.literal("도움말").executes { ctx -> w.messages.send(sender(ctx), "help"); 1 })
}
