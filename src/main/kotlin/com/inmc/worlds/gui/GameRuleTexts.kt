package com.inmc.worlds.gui

/**
 * 게임 규칙의 한국어 설명 — 26.2 클라이언트 번역에는 59개 중 31개만 설명이 있고, 있는 것도 값의 뜻(0·-1·단위)이 빠진 것이 있어
 * 마인크래프트 위키(영어·한국어)와 서버 코드로 확인해 다시 썼다(2026-10-15 사용자 요청 — "인터넷을 확인해서 다시 검토").
 * 여기 없는 규칙(서버를 올려 새로 생긴 것)은 클라이언트 번역 `<규칙>.description` 을 그대로, 그것도 없으면 `-`.
 * `/월드 검증` 이 표에 없는 규칙을 알린다.
 *
 * 이름은 클라이언트 번역이 기본(바닐라 게임 규칙 화면과 같은 말) — 뜻이 틀린 것만 [names] 로 바꾼다.
 * 글에 `<` `>` 를 쓰지 않는다(MiniMessage 로 그린다 — `GameRuleTextsTest`).
 */
internal object GameRuleTexts {

    /** 바닐라 이름이 뜻과 다른 것만. */
    val names: Map<String, String> = mapOf(
        // 바닐라 "관리자 명령 알림" — 다른 관리자 채팅의 알림은 send_command_feedback 이 정한다(CommandSourceStack.broadcastToAdmins). 이것은 콘솔 기록만.
        "minecraft:log_admin_commands" to "관리자 명령 기록",
        // 바닐라 "개체 장비 떨굼" — 몹의 장비가 아니라 몹이 아닌 개체(광산 수레·보트·액자 …)의 떨굼이다.
        "minecraft:entity_drops" to "몹이 아닌 개체의 떨굼",
    )

    val descriptions: Map<String, List<String>> = mapOf(
        // --- 채팅 ---
        "minecraft:command_block_output" to listOf("명령 블록이 명령을 실행할 때", "그 결과를 관리자 채팅에 알리는지."),
        "minecraft:log_admin_commands" to listOf(
            "관리자가 친 명령을 서버 콘솔 기록에 남기는지.",
            "다른 관리자 채팅의 [이름: …] 알림은",
            "'명령 피드백 전송' 규칙을 따릅니다.",
        ),
        "minecraft:send_command_feedback" to listOf(
            "플레이어가 친 명령의 결과 문구가 채팅에 나오는지.",
            "다른 관리자에게 가는 [이름: …] 알림과",
            "명령 블록이 결과를 저장하는지도 이것을 따릅니다.",
        ),
        "minecraft:show_advancement_messages" to listOf("발전 과제를 이루면 채팅에 알리는지."),
        "minecraft:show_death_messages" to listOf(
            "플레이어가 죽으면 채팅에 사망 문구를 띄우는지.",
            "길들인 동물이 죽었을 때 주인에게 가는 문구도 포함.",
        ),

        // --- 떨굼 ---
        "minecraft:block_drops" to listOf("블록을 부수면 아이템(경험 구슬 포함)이 나오는지."),
        "minecraft:block_explosion_drop_decay" to listOf(
            "블록이 일으킨 폭발(오버월드 밖에서 쓴 침대·",
            "리스폰 정박기 등)에 부서진 블록의 아이템 중",
            "일부가 사라지는지(켬), 다 떨어지는지(끔).",
        ),
        "minecraft:entity_drops" to listOf(
            "몹이 아닌 개체(광산 수레와 그 안의 물건·보트·",
            "아이템 액자·갑옷 거치대 등)를 부수면 아이템이 나오는지.",
        ),
        "minecraft:mob_drops" to listOf("몹이 죽으면 아이템과 경험 구슬을 떨구는지."),
        "minecraft:mob_explosion_drop_decay" to listOf(
            "몹의 폭발(크리퍼 등)에 부서진 블록의 아이템 중",
            "일부가 사라지는지(켬), 다 떨어지는지(끔).",
        ),
        "minecraft:projectiles_can_break_blocks" to listOf(
            "화살 같은 투사체가 맞혀서 부술 수 있는 블록",
            "(후렴화·뾰족한 점적석·장식된 항아리)을 부수는지.",
        ),
        "minecraft:tnt_explosion_drop_decay" to listOf(
            "TNT 폭발에 부서진 블록의 아이템 중",
            "일부가 사라지는지(켬), 다 떨어지는지(끔).",
        ),

        // --- 기타 ---
        "minecraft:allow_entering_nether_using_portals" to listOf(
            "네더 차원문으로 네더에 들어갈 수 있는지.",
            "끄면 차원문에 서 있어도 넘어가지 않습니다.",
        ),
        "minecraft:command_blocks_work" to listOf("명령 블록이 작동하는지.", "끄면 모든 명령 블록이 명령을 실행하지 않습니다."),
        "minecraft:global_sound_events" to listOf(
            "위더 생성·엔더 드래곤 죽음·엔드 차원문 열림 소리가",
            "거리와 상관없이 모든 플레이어에게 들리는지.",
        ),
        "minecraft:max_block_modifications" to listOf("/fill·/clone·/fillbiome 명령 한 번에", "바꿀 수 있는 블록 수의 최대."),
        "minecraft:max_command_forks" to listOf(
            "한 틱에 명령이 만들 수 있는 맥락의 최대 수",
            "(execute as @a 처럼 대상마다 갈라지는 수).",
            "명령 블록과 함수에 적용됩니다.",
        ),
        "minecraft:max_command_sequence_length" to listOf(
            "한 틱에 한 곳에서 이어서 실행할 수 있는",
            "명령 수의 최대. 연쇄 명령 블록과 함수에 적용됩니다.",
        ),
        "minecraft:max_minecart_speed" to listOf(
            "광산 수레가 땅 위에서 내는 최고 속도(초당 블록).",
            "'광산 수레 개선' 실험 기능을 켠 월드에만 있습니다.",
        ),
        "minecraft:reduced_debug_info" to listOf(
            "F3 디버그 화면의 정보를 줄이고, 히트박스(F3+B)와",
            "청크 경계(F3+G) 표시를 막는지.",
        ),
        "minecraft:spawner_blocks_work" to listOf("몬스터 생성기 블록이 몹을 만들어 내는지."),
        "minecraft:tnt_explodes" to listOf("TNT에 불을 붙일 수 있고 터지는지."),

        // --- 몹 ---
        "minecraft:forgive_dead_players" to listOf(
            "화난 중립 몹(늑대·좀비 피글린 등)이 쫓던 플레이어가",
            "근처에서 죽으면 화를 푸는지.",
        ),
        "minecraft:max_entity_cramming" to listOf(
            "한 자리에 이 수보다 많은 개체가 몰리면 질식 피해를",
            "입습니다(0.5초마다 하트 3칸). 0 = 끔.",
            "서바이벌·모험 플레이어와 박쥐 말고 모든 몹에 적용.",
        ),
        "minecraft:mob_griefing" to listOf(
            "크리퍼·엔더맨·가스트·위더 등 몹이 블록을 부수거나",
            "바꾸는지, 몹이 아이템을 줍는지. 끄면 주민이 농사를",
            "못 짓고 피글린이 금을 줍지 않습니다",
            "(손으로 건네면 물물교환은 됩니다).",
        ),
        "minecraft:raids" to listOf("흉조 효과를 가진 플레이어가 마을에 들어가면", "습격이 일어나는지."),
        "minecraft:universal_anger" to listOf(
            "화난 중립 몹이 화나게 한 사람뿐 아니라",
            "근처의 모든 플레이어를 공격하는지.",
            "'죽은 플레이어 용서'를 끄면 잘 맞습니다.",
        ),

        // --- 플레이어 ---
        "minecraft:drowning_damage" to listOf("물속에서 숨이 다하면 플레이어가 피해를 입는지."),
        "minecraft:elytra_movement_check" to listOf(
            "겉날개로 날 때 서버가 플레이어 속도를 검사하는지.",
            "끄면 렉으로 되돌려지는 일이 줄지만,",
            "빠른 이동 핵도 잡지 않습니다.",
        ),
        "minecraft:ender_pearls_vanish_on_death" to listOf("플레이어가 죽으면 그 사람이 던져", "날아가고 있는 엔더 진주가 사라지는지."),
        "minecraft:fall_damage" to listOf("플레이어가 낙하 피해를 입는지."),
        "minecraft:fire_damage" to listOf("플레이어가 불·용암·모닥불·마그마 블록에서", "피해를 입는지."),
        "minecraft:freeze_damage" to listOf("플레이어가 가루눈 속에서 얼어 피해를 입는지."),
        "minecraft:immediate_respawn" to listOf("죽으면 사망 화면 없이 바로 리스폰하는지."),
        "minecraft:keep_inventory" to listOf("죽어도 가방의 아이템과 경험치를 그대로 갖는지."),
        "minecraft:limited_crafting" to listOf("제작법 책에서 잠금 해제한 제작법만", "만들 수 있는지."),
        "minecraft:locator_bar" to listOf("다른 플레이어가 있는 방향을 보여 주는", "위치 표시 바를 화면에 띄우는지."),
        "minecraft:natural_health_regeneration" to listOf(
            "배고픔이 충분하면 체력이 저절로 차는지.",
            "황금 사과·재생 효과 같은 회복은 상관없습니다.",
        ),
        "minecraft:player_movement_check" to listOf(
            "서버가 플레이어 이동 속도를 검사해",
            "너무 빠르면 되돌리는지. 끄면 렉으로 되돌려지는",
            "일이 줄지만, 빠른 이동 핵도 잡지 않습니다.",
        ),
        "minecraft:players_nether_portal_creative_delay" to listOf(
            "크리에이티브 모드 플레이어가 네더 차원문에",
            "몇 틱 서 있어야 넘어가는지(20틱 = 1초).",
        ),
        "minecraft:players_nether_portal_default_delay" to listOf(
            "크리에이티브가 아닌 플레이어가 네더 차원문에",
            "몇 틱 서 있어야 넘어가는지(20틱 = 1초).",
        ),
        "minecraft:players_sleeping_percentage" to listOf(
            "오버월드의 플레이어 중 몇 %가 자야 밤을 건너뛰는지.",
            "0 이하 = 한 명만 자도 됨 · 100 초과 = 못 건너뜀.",
        ),
        "minecraft:pvp" to listOf("플레이어가 다른 플레이어에게 피해를 줄 수 있는지."),
        "minecraft:respawn_radius" to listOf(
            "리스폰 지점이 없는 플레이어가 처음 들어오거나",
            "죽었을 때 월드 스폰에서 최대 몇 블록 떨어진 곳에",
            "나타나는지. 기본 게임 모드가 모험이면 효과가 없습니다.",
        ),
        "minecraft:spectators_generate_chunks" to listOf(
            "관전 모드 플레이어가 아직 없는 지형을 새로 만드는지.",
            "끄면 관전자는 이미 만들어진 곳만 볼 수 있습니다.",
        ),

        // --- 생성 ---
        "minecraft:spawn_mobs" to listOf(
            "몹이 저절로 생기는지(고양이·팬텀·정찰대·",
            "떠돌이 상인·좀비 포위 포함). 생성기·습격·",
            "생성 알·명령어로 나오는 것은 상관없습니다.",
        ),
        "minecraft:spawn_monsters" to listOf("적대적 몬스터가 저절로 생기는지."),
        "minecraft:spawn_patrols" to listOf("약탈자 정찰대가 생기는지."),
        "minecraft:spawn_phantoms" to listOf("오래 잠을 자지 않은 플레이어에게", "밤에 팬텀이 나타나는지."),
        "minecraft:spawn_wandering_traders" to listOf("떠돌이 상인이 찾아오는지."),
        "minecraft:spawn_wardens" to listOf("스컬크 비명체가 워든을 불러내는지."),

        // --- 갱신 ---
        "minecraft:advance_time" to listOf("낮과 밤, 달 모양이 바뀌는지.", "끄면 지금 시각에서 멈춥니다(/time 으로는 바뀜)."),
        "minecraft:advance_weather" to listOf("날씨가 저절로 바뀌는지.", "끄면 지금 날씨에서 멈춥니다(/weather 로는 바뀜)."),
        "minecraft:fire_spread_radius_around_player" to listOf(
            "플레이어 주변 몇 블록 안에서만 불이 번지는지.",
            "0 = 불이 번지지 않음 · -1 = 거리 제한 없음.",
        ),
        "minecraft:lava_source_conversion" to listOf("흐르는 용암이 두 면 이상 용암 원천에 닿으면", "새 원천이 되는지(무한 용암)."),
        "minecraft:max_snow_accumulation_height" to listOf("눈이 올 때 한 칸에 쌓일 수 있는", "눈 층의 최대 수."),
        "minecraft:random_tick_speed" to listOf(
            "청크 한 구역(16×16×16)마다 한 틱에 무작위로",
            "고르는 블록 수 — 클수록 작물 성장·잎 썩음 등이",
            "빨라집니다. 0 = 멈춤.",
        ),
        "minecraft:spread_vines" to listOf("덩굴이 옆 블록으로 퍼지는지.", "동굴 덩굴·늘어진 덩굴·휘어진 덩굴은 상관없습니다."),
        "minecraft:water_source_conversion" to listOf("흐르는 물이 두 면 이상 물 원천에 닿으면", "새 원천이 되는지(무한 물)."),
    )
}
