package com.inmc.worlds.gui

import com.inmc.worlds.Worlds
import com.inmc.worlds.util.Ph
import com.inmc.worlds.world.Flat
import com.inmc.worlds.world.TerrainKind
import com.inmc.worlds.world.WorldEntry
import com.inmc.worlds.world.WorldService
import kr.inmc.core.gui.DialogForm
import kr.inmc.core.gui.Icon
import kr.inmc.core.gui.PickMenu
import kr.inmc.core.integration.PlaceNames
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player

/** 마법사가 고르는 중인 것 — 화면을 오갈 때 들고 다닌다(입력창·고르기 화면에서 돌아와도 남게). */
class Draft(
    var entry: WorldEntry = WorldEntry(name = "", flat = Flat()),
    /** 보이는 이름(MiniMessage) — 만든 뒤 타이틀포지 별칭으로. */
    var display: String = "",
    /** 넣을 모드. null = 이 월드만의 새 모드(사용자 결정 2026-10-15 — 기본은 자기 모드). */
    var mode: String? = null,
)

/**
 * 새 월드 만들기 — 이름·보이는 이름·차원·유형·시드·구조물·보너스 상자, 유형마다(평지 층·생물군계), 생성기 플러그인.
 * 전부 클릭과 입력창으로 고른다(사용자 결정 2026-10-15 "모든 항목을 GUI 에서").
 */
class CreateWizard(w: Worlds, viewer: Player, private val draft: Draft = Draft()) : Menu(w, viewer, 54, "<dark_green>새 월드 만들기</dark_green>") {

    override val back: (() -> Unit) = { WorldListMenu(w, viewer).show() }

    private val e get() = draft.entry

    override fun draw() {
        clear()
        set(4, summary())

        set(10, valueIcon(Material.NAME_TAG, "폴더 이름", e.name.ifEmpty { "<red>(정하세요)</red>" },
            "<gray>영어 소문자·숫자·_·- 만, 32자까지.</gray>", "<gray>폴더와 명령에 쓰이는 이름입니다.</gray>")) {
            ask(DialogForm("폴더 이름").line("영어 소문자·숫자·_·- 만 (예: build, mine_farm)").text("name", "이름", e.name, 32)) { v ->
                val name = v.text("name").trim().lowercase()
                if (!WorldEntry.NAME.matches(name)) w.messages.send(viewer, "create-bad-name") else draft.entry = e.copy(name = name)
            }
        }
        set(11, valueIcon(Material.OAK_SIGN, "보이는 이름", draft.display.ifEmpty { "<gray>(없음 — 폴더 이름)</gray>" },
            "<gray>타이틀포지 장소 이름(places.yml)에 들어갑니다.</gray>", "<gray>색 태그를 쓸 수 있습니다: <white>&lt;gold&gt;건축&lt;/gold&gt;</white></gray>")) {
            ask(DialogForm("보이는 이름").line("비우면 폴더 이름이 보입니다.").text("display", "이름", draft.display, 128)) { v ->
                draft.display = v.text("display").trim()
            }
        }
        set(12, Icon.of(Menu.environmentIcon(e.environment), "<yellow>차원: </yellow><white>${Menu.environmentLabel(e.environment)}</white>",
            listOf("<gray>일반 · 네더 · 엔드 — 하늘·빛·물 증발 같은 차원의 성질.</gray>", "", "<gray>클릭해서 바꾸기</gray>"))) {
            val all = listOf(World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END)
            draft.entry = e.copy(environment = all[(all.indexOf(e.environment) + 1) % all.size])
            refresh()
        }
        set(13, Icon.of(kindIcon(e.kind), "<yellow>유형: </yellow><white>${e.kind.label}</white>", listOf("", "<gray>클릭해서 고르기</gray>"))) {
            PickMenu(w, viewer, "<dark_green>지형 유형</dark_green>", TerrainKind.entries,
                icon = { k -> Icon.of(kindIcon(k), "<white>${k.label}</white>", kindLore(k)) },
                back = { show() },
            ) { k -> draft.entry = e.copy(kind = k); show() }.show()
        }
        set(14, valueIcon(Material.WHEAT_SEEDS, "시드", e.seed?.toString() ?: "무작위", "<gray>숫자나 글자. 비우면 무작위.</gray>")) {
            ask(DialogForm("시드").line("숫자나 글자(글자는 바닐라처럼 숫자로 바뀝니다). 비우면 무작위.").text("seed", "시드", e.seed?.toString().orEmpty(), 64)) { v ->
                draft.entry = e.copy(seed = seedOf(v.text("seed")))
            }
        }
        set(15, toggleIcon("구조물", e.structures, "<gray>마을·요새·폐광 같은 구조물.</gray>")) { draft.entry = e.copy(structures = !e.structures); refresh() }
        set(16, toggleIcon("보너스 상자", e.bonusChest, "<gray>스폰 둘레에 시작 상자 하나.</gray>")) { draft.entry = e.copy(bonusChest = !e.bonusChest); refresh() }

        when (e.kind) {
            TerrainKind.FLAT -> flatButtons()
            TerrainKind.SINGLE_BIOME -> set(29, Icon.of(Material.OAK_SAPLING, "<yellow>생물군계: </yellow>" + (e.biome?.let(::biomeLabel) ?: "<red>(고르세요)</red>"),
                listOf("", "<gray>클릭해서 고르기</gray>"))) {
                BiomePick.open(w, viewer, back = { show() }) { key -> draft.entry = e.copy(biome = key); show() }
            }
            else -> Unit
        }
        set(34, valueIcon(Material.COMMAND_BLOCK, "생성기 플러그인", e.generator ?: "없음",
            "<gray>다른 플러그인의 생성기(예: 스카이블록) — <white>플러그인이름:id</white></gray>", "<gray>비우면 위 유형대로.</gray>")) {
            ask(DialogForm("생성기 플러그인").line("예: BentoBox:bskyblock — 비우면 없음.").text("generator", "생성기", e.generator.orEmpty(), 128)) { v ->
                draft.entry = e.copy(generator = v.text("generator").trim().ifEmpty { null })
            }
        }

        set(33, Icon.of(Material.COMPASS, "<yellow>모드: </yellow>" + (draft.mode?.let { w.modes.get(it)?.name } ?: "<white>새 모드(이 월드만)</white>"), listOf(
            "<gray>기본은 이 월드만의 모드 — 가방·장비가 다른 모드와 따로입니다.</gray>", "", "<gray>클릭해서 고르기</gray>",
        ))) {
            val options = listOf<String?>(null) + w.modes.all().map { it.id }
            PickMenu(w, viewer, "<dark_green>넣을 모드</dark_green>", options,
                icon = { id -> if (id == null) Icon.of(Material.LIME_DYE, "<white>새 모드(이 월드만)</white>", emptyList())
                    else w.modes.get(id)!!.let { m -> Icon.of(m.icon, m.name, listOf("<dark_gray>${m.id}</dark_gray>")) } },
                back = { show() },
            ) { id -> draft.mode = id; show() }.show()
        }

        fillEmpty(Icon.FILLER)
        set(SLOT_CREATE, Icon.of(Material.EMERALD_BLOCK, "<green>✔ 만들기</green>", listOf(
            "<gray>만드는 동안 서버가 잠깐 멈춥니다(스폰 둘레를 만듭니다).</gray>",
        ))) { create() }
        navigation()
    }

    private fun flatButtons() {
        val flat = e.flat ?: Flat()
        set(28, Icon.of(Material.SMOOTH_STONE, "<yellow>평지 층</yellow> <gray>(${flat.layers.size}층 · 두께 ${flat.thickness})</gray>",
            flat.layers.reversed().map { "<gray>${Menu.blockLabel(it.block)} × ${it.height}</gray>" } + listOf("", "<gray>클릭해서 편집</gray>"))) {
            FlatLayersMenu(w, viewer, draft) { show() }.show()
        }
        set(29, Icon.of(Material.COMPARATOR, "<yellow>빠른 설정</yellow>", listOf(
            "<gray>높이 · 겉 블록 · 속 블록 · 바닥 블록으로 층을 한 번에.</gray>", "", "<gray>클릭해서 열기</gray>",
        ))) { QuickFlatMenu(w, viewer, draft) { show() }.show() }
        set(30, Icon.of(Material.OAK_SAPLING, "<yellow>평지 생물군계: </yellow>" + biomeLabel(flat.biome), listOf("", "<gray>클릭해서 고르기</gray>"))) {
            BiomePick.open(w, viewer, back = { show() }) { key -> draft.entry = e.copy(flat = flat.copy(biome = key)); show() }
        }
        set(31, toggleIcon("지형지물", flat.features, "<gray>나무·꽃·풀 같은 장식.</gray>")) { draft.entry = e.copy(flat = flat.copy(features = !flat.features)); refresh() }
        set(32, toggleIcon("호수", flat.lakes, "<gray>물·용암 호수.</gray>")) { draft.entry = e.copy(flat = flat.copy(lakes = !flat.lakes)); refresh() }
    }

    private fun summary() = Icon.of(Material.FILLED_MAP, "<gold>만들 월드</gold>", listOfNotNull(
        "<gray>폴더 이름: <white>${e.name.ifEmpty { "-" }}</white></gray>",
        "<gray>보이는 이름: </gray>" + draft.display.ifEmpty { "<gray>-</gray>" },
        "<gray>차원: <white>${Menu.environmentLabel(e.environment)}</white> · 유형: <white>${e.kind.label}</white></gray>",
        "<gray>시드: <white>${e.seed ?: "무작위"}</white> · 구조물 ${if (e.structures) "켬" else "끔"}</gray>",
        e.generator?.let { "<gray>생성기: <white>$it</white></gray>" },
    ))

    private fun create() {
        if (e.name.isEmpty()) return w.messages.send(viewer, "create-no-name")
        val draftNow = e
        val display = draft.display
        val mode = draft.mode
        viewer.closeInventory()
        w.messages.send(viewer, "world-creating", Ph.of().world(draftNow.name))
        // 메시지가 먼저 보이게 한 틱 뒤에.
        viewer.scheduler.run(w.plugin, {
            when (val result = w.service.create(draftNow)) {
                is WorldService.Created.Fail -> {
                    w.messages.send(viewer, result.key, Ph.of().world(draftNow.name))
                    show()
                }
                is WorldService.Created.Ok -> {
                    if (display.isNotEmpty()) PlaceNames.setWorld(result.world.name, display)
                    if (mode != null) w.modes.assign(result.world.name, mode)
                    w.messages.send(viewer, "world-created", Ph.of().world(result.world.name))
                    WorldSettingsMenu(w, viewer, result.world.name).show()
                }
            }
        }, null)
    }

    private fun biomeLabel(key: String): String = BiomePick.label(key)

    private companion object {
        const val SLOT_CREATE = 49

        fun kindIcon(kind: TerrainKind): Material = when (kind) {
            TerrainKind.DEFAULT -> Material.GRASS_BLOCK
            TerrainKind.FLAT -> Material.SMOOTH_STONE_SLAB
            TerrainKind.LARGE_BIOMES -> Material.MAP
            TerrainKind.AMPLIFIED -> Material.STONE
            TerrainKind.SINGLE_BIOME -> Material.OAK_SAPLING
            TerrainKind.VOID -> Material.GLASS
        }

        fun kindLore(kind: TerrainKind): List<String> = when (kind) {
            TerrainKind.DEFAULT -> listOf("<gray>바닐라 기본 지형.</gray>")
            TerrainKind.FLAT -> listOf("<gray>층을 쌓은 평지 — 층·생물군계·장식을 고릅니다.</gray>")
            TerrainKind.LARGE_BIOMES -> listOf("<gray>생물군계가 16배 넓습니다.</gray>")
            TerrainKind.AMPLIFIED -> listOf("<gray>산이 아주 높고 험합니다(무겁습니다).</gray>")
            TerrainKind.SINGLE_BIOME -> listOf("<gray>지형은 기본대로, 생물군계는 하나만.</gray>")
            TerrainKind.VOID -> listOf("<gray>아무것도 없는 월드 — 스폰에 5×5 발판 하나.</gray>")
        }

        /** 비면 무작위(null), 숫자면 그대로, 글자면 바닐라처럼 `hashCode`. */
        fun seedOf(raw: String): Long? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            return text.toLongOrNull() ?: text.hashCode().toLong()
        }
    }
}
