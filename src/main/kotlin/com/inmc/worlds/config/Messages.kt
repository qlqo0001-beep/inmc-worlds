package com.inmc.worlds.config

import com.inmc.worlds.util.Ph
import kr.inmc.core.config.MessageCatalog
import org.bukkit.configuration.file.YamlConfiguration

/**
 * `messages.yml` 한 벌. 읽고 보내는 부분은 core 의 [MessageCatalog] 가 갖고 있고 여기는 기본값 표뿐이다.
 * `ResourceTest` 가 배포 파일과 이 표의 키가 정확히 같은지 지킨다.
 */
class Messages(values: Map<String, String>) : MessageCatalog<Ph>(values, DEFAULTS) {

    companion object {

        fun from(config: YamlConfiguration): Messages = Messages(merge(DEFAULTS, config))

        val DEFAULTS: Map<String, String> = linkedMapOf(
            PREFIX to "<gradient:#7fd3ff:#3a8dde>[ 월드 ]</gradient> ",

            // --- 공통 ---------------------------------------------------------------
            "player-only" to "<red>플레이어만 쓸 수 있습니다.</red>",
            "reloaded" to "<green>메시지를 다시 불러왔습니다.</green>",
            "world-unknown" to "<red>'{world}' 월드가 불러와져 있지 않습니다.</red>",
            "names-unavailable" to "<red>타이틀포지가 꺼져 있어 보이는 이름을 정할 수 없습니다.</red>",

            // --- 만들기·불러오기 ------------------------------------------------------------
            "world-creating" to "<yellow>'{world}' 월드를 만드는 중… 잠깐 멈출 수 있습니다.</yellow>",
            "world-created" to "<green>'{world}' 월드를 만들었습니다.</green>",
            "create-no-name" to "<red>폴더 이름을 먼저 정하세요.</red>",
            "create-bad-name" to "<red>폴더 이름은 영어 소문자·숫자·_·- 만, 32자까지입니다.</red>",
            "create-exists" to "<red>'{world}' 은(는) 이미 있습니다(불러온 월드·폴더·목록).</red>",
            "create-flat-too-thick" to "<red>평지 층이 너무 두껍습니다(384 칸까지).</red>",
            "create-no-biome" to "<red>생물군계를 먼저 고르세요.</red>",
            "create-failed" to "<red>'{world}' 월드를 만들지 못했습니다 — 콘솔을 보세요.</red>",
            "world-loading" to "<yellow>'{world}' 월드를 불러오는 중…</yellow>",
            "world-loaded" to "<green>'{world}' 월드를 불러왔습니다.</green>",
            "world-imported" to "<green>'{world}' 월드를 가져왔습니다.</green>",
            "world-load-failed" to "<red>'{world}' 월드를 불러오지 못했습니다 — 콘솔을 보세요.</red>",
            "world-unloaded" to "<gold>'{world}' 월드를 내렸습니다.</gold>",
            "world-unload-failed" to "<red>'{world}' 월드를 내리지 못했습니다.</red>",
            "world-deleted" to "<gold>'{world}' 월드를 지웠습니다.</gold>",
            "world-delete-refused" to "<red>'{world}' 월드는 지울 수 없습니다(바닐라 월드이거나 내리지 못했습니다).</red>",

            // --- 평지 -------------------------------------------------------------------
            "not-a-block" to "<red>놓을 수 있는 블록을 올려 주세요.</red>",
            "flat-last-layer" to "<red>층이 하나뿐이라 지울 수 없습니다.</red>",
            "flat-quick-applied" to "<green>평지 층을 두께 {count} 로 바꿨습니다.</green>",

            // --- 설정 -------------------------------------------------------------------
            "spawn-set" to "<green>'{world}' 의 스폰을 여기로 정했습니다.</green>",
            "spawn-not-here" to "<red>스폰을 정하려면 그 월드에 서 있어야 합니다.</red>",

            // --- 포탈 -------------------------------------------------------------------
            "portal-blocked" to "<red>이 월드의 포탈은 막혀 있습니다.</red>",
            "portal-missing" to "<red>이 포탈이 가는 월드가 불러와져 있지 않습니다.</red>",

            // --- 검증 -------------------------------------------------------------------
            "verify-done" to "<gold>월드 검증</gold> <gray>— 통과 <green>{value}</green> · 실패 <red>{count}</red></gray>",
            "verify-failure" to "<red> ✘ {world}</red> <gray>— {value}</gray>",
            "verify-report" to "<gray>결과 파일: <white>{value}</white></gray>",

            // --- 도움말 -------------------------------------------------------------------
            "help" to listOf(
                "<gold>/월드</gold> <gray>- 월드 관리 화면(목록·만들기·설정)</gray>",
                "<gold>/월드 만들기</gold> <gray>- 새 월드 마법사</gray>",
                "<gold>/월드 설정 [월드]</gold> <gray>- 월드 설정(이름·난이도·PVP·스폰·게임 규칙·포탈)</gray>",
                "<gold>/월드 이동 (월드)</gold> <gray>- 그 월드의 스폰으로</gray>",
                "<gold>/월드 검증</gold> <gray>- 서버 안 자동 검사</gray>",
            ).joinToString("\n"),
        )
    }
}
