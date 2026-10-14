# inmc-worlds — 월드·모드

Multiverse-Core · Multiverse-Inventories · Multiverse-NetherPortals 를 대신한다(2026-10-15 사용자 결정 — 계획 `~/.claude/plans/validated-questing-sparrow.md`).

1. **월드 관리**(2단계 — 이 문서의 지금 범위): 월드 만들기·불러오기·가져오기·내리기·지우기, 난이도·게임 규칙·스폰·포탈 연결을 **인게임 화면(한글)** 으로.
   설정값은 화면이 `worlds.yml` 에 써 넣는다 — 사람이 yml 을 고칠 일이 없다.
2. **모드**(3단계): 서버를 모드(월드 묶음)로 나눠 모드마다 다른 서버처럼 — core `Realms` 공급처.

## 플러그인 값이 바닐라를 이긴다

월드마다 난이도·게임 규칙(기본값과 다른 것)·스폰을 `worlds.yml` 에 쥐고, **켤 때와 월드가 읽힐 때** 덮어쓴다(`WorldService.apply`).
다른 길로 바뀐 것도 받아 적는다 — 그래서 덮어쓰는 값은 늘 마지막으로 돌던 값이다:

| 길 | 받아 적는 곳 |
|---|---|
| `/gamerule` · essentials `/게임규칙` · 다른 플러그인 | Paper `WorldGameRuleChangeEvent` MONITOR (`WorldListener.onGameRule`) |
| `/difficulty` | Paper `WorldDifficultyChangeEvent` |
| 사건이 오지 않는 길(API 호출) · `/setworldspawn` | 자동 저장 때 훑기(`WorldSaveEvent` → `WorldService.snapshot`) · 끌 때 · 내릴 때 |

**다른 플러그인이 만든 월드**(던전 `inmc_dungeon` 등)는 그 플러그인이 규칙을 정하므로 항목이 없고, 받아 적지도 덮지도 않는다.
관리자가 우리 화면에서 그 월드의 무엇을 바꾸면 그때 항목이 생긴다.

## 월드 항목 (`worlds.yml`)

`WorldEntry` — 만드는 법(차원·유형·시드·구조물·보너스 상자·평지 층·생물군계·생성기 플러그인 — **다시 불러올 때도 같은 생성기가 필요**하다)과
플러그인 값(난이도·스폰·규칙)·포탈 연결(네더/엔드 — 바닐라·막기·월드). `load: true` = 켤 때 우리가 불러온다(바닐라 셋은 서버가 불러오므로 false).
경로 구분자는 `|`(월드 이름의 점).

| 유형 | 만드는 법 |
|---|---|
| 기본 · 넓은 생물 군계 · 증폭 | `WorldType.NORMAL/LARGE_BIOMES/AMPLIFIED` |
| 완전한 평지 | `WorldType.FLAT` + `generatorSettings` = 바닐라 평지 JSON(`FlatJson` — 층·생물군계·지형지물·호수·구조물이면 마을·요새) |
| 단일 생물 군계 | `biomeProvider(SingleBiome)` — 지형은 기본, 생물군계 하나 |
| 공허 | `VoidGenerator` + 만들 때 스폰에 5×5 돌 발판 |
| 생성기 플러그인 | `generator("플러그인:id")` — 위 유형보다 우선 |

Paper 26 은 새 월드를 본 월드 안 `dimensions/minecraft/<이름>` 에 둔다. 가져오기 후보 = 거기와 서버 폴더 바로 아래의 `level.dat` 있는 폴더 중 모르는 것.

## 포탈 연결 (`PortalListener`, HIGH)

- 네더 포탈 → 월드: 차원 비율(오버월드 1 · 네더 8)대로 옮긴 자리에서 Paper 가 포탈을 찾거나 만든다.
- 엔드 포탈 → 엔드 월드: (100, 48, 0) 둘레에 흑요석 발판을 깔고 그 위로. → 그 밖의 월드: 그 월드의 스폰.
- 막기: 플레이어에게 알리고 취소. 엔티티는 같은 연결을 따르고 막히면 조용히.
- 기본: 바닐라 셋은 바닐라, 그 밖(항목 없는 월드 포함)은 막기 — Multiverse-NetherPortals 가 짝 없는 월드의 포탈을 막던 것과 같다.

## 월드·생물군계 이름

core `PlaceNames`(공급처 = 타이틀포지 `places.yml`). 화면의 "보이는 이름"을 정하면 타이틀포지 파일에 들어간다(`%titleforge_world%` 와 같은 것).
없으면 월드는 폴더 이름, 생물군계는 클라이언트 번역(`<lang:biome.minecraft.…>`). 게임 규칙 이름·설명도 클라이언트 번역(`GameRule.translationKey()`·`.description`).

## 처음 켤 때

Multiverse-Core `worlds.yml` 이 있으면 한 번 가져온다(`MultiverseImport` — 불러오기·시드·생성기·난이도·스폰, `==: MVSpawnLocation` 줄은 빼고 읽는다).
사람 자료(가방)는 옮기지 않는다(본섭이 없다). Multiverse 가 같이 켜져 있으면 콘솔에 경고한다.

## 켜는 순서

월드를 불러오는 플러그인이라 월드를 읽는 플러그인보다 먼저 켜진다 — `paper-plugin.yml` 의 `load: AFTER`(essentials·던전·메뉴·커스텀아이템·Citizens·Shopkeepers·Images).
