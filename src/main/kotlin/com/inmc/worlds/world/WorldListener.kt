package com.inmc.worlds.world

import com.inmc.worlds.Worlds
import io.papermc.paper.event.world.WorldDifficultyChangeEvent
import io.papermc.paper.event.world.WorldGameRuleChangeEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldSaveEvent

/**
 * 플러그인 값이 이기게 — 월드가 읽히면 덮어쓰고([WorldService.apply]), 다른 길로 바뀐 값은 받아 적는다.
 * 받아 적기는 MONITOR — 앞에서 취소한 것은 안 바뀐 것이다.
 */
class WorldListener(private val w: Worlds) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onLoad(event: WorldLoadEvent) {
        w.service.apply(event.world)
    }

    /** `/gamerule`·essentials `/게임규칙`·다른 플러그인. 우리가 덮어쓰는 중의 것은 다시 적지 않는다. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGameRule(event: WorldGameRuleChangeEvent) {
        if (w.service.applying) return
        w.service.capture(event.world, event.gameRule, event.value)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDifficulty(event: WorldDifficultyChangeEvent) {
        if (w.service.applying) return
        w.service.captureDifficulty(event.world, event.difficulty)
    }

    /** 자동 저장 때 지금 값을 훑어 적는다 — 사건이 오지 않는 길(API 호출)로 바뀐 것까지. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onSave(event: WorldSaveEvent) {
        w.service.snapshot(event.world)
    }
}
