package com.inmc.worlds.mode

import kr.inmc.core.integration.Realms
import org.bukkit.Bukkit

/**
 * 탭 목록 묶음 — 탭 묶음이 다른 사람은 서로의 목록에서 뺀다. Paper `unlistPlayer/listPlayer`(목록에서만 — 엔티티는 그대로라 서로 다른 월드에서는 어차피 안 보인다).
 * 투명(essentials 의 `hidePlayer`)·메뉴의 "다른 사람 숨기기" 로 **안 보이는 짝은 건드리지 않는다** — 숨긴 사람을 목록에 올려 투명이 새지 않게.
 * 숨김이 풀리면 서버가 목록에 다시 올리므로, 짧은 주기로 다시 맞춘다([WorldsPlugin] 의 반복 — 사람이 많지 않아 짝마다 본다).
 */
class TabSync {

    /** 접속자 전부를 맞춘다 — 접속 · 모드 바뀜 · 묶음 바뀜. 사람이 많지 않아 짝마다 본다. */
    fun refresh() {
        val online = Bukkit.getOnlinePlayers().toList()
        for (viewer in online) for (other in online) {
            if (viewer == other || !viewer.canSee(other)) continue
            val listed = viewer.isListed(other)
            val want = Realms.sameTab(viewer, other)
            if (want && !listed) viewer.listPlayer(other) else if (!want && listed) viewer.unlistPlayer(other)
        }
    }
}
