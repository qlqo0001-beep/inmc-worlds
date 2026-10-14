# inmc-worlds — 월드·모드

Multiverse-Core · Multiverse-Inventories · Multiverse-NetherPortals 를 대신한다(2026-10-15 사용자 결정 — 계획 `~/.claude/plans/validated-questing-sparrow.md`).

1. **월드 관리**(2단계): 월드 만들기·불러오기·가져오기·내리기·지우기, 난이도·게임 규칙·스폰·포탈 연결을 **인게임 화면(한글)** 으로.
   설정값은 화면이 `worlds.yml` 에 써 넣는다 — 사람이 yml 을 고칠 일이 없다.
2. **모드**(3단계): 서버를 모드(월드 묶음)로 나눠 모드마다 다른 서버처럼 — core `Realms` 공급처. 아래 "모드" 절.

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

## 모드 (3단계)

**모드 = 월드 묶음**(`modes.yml`, 화면이 쓴다). 모든 월드는 모드 하나에 들거나 **빌려 쓰는 월드**(던전처럼 여러 모드가 들어가는 곳 — 안에서는 들어온 모드를 그대로)다.
첫 켜기에 **기본 모드 = 바닐라 셋**, 처음 보는 월드(만들기 마법사·다른 플러그인)는 **자기 모드**를 가진다(사용자 결정 2026-10-15). 관리자가 `/모드 관리` 에서 묶는다.

### 사람의 모드
서 있는 월드의 모드. 빌려 쓰는 월드에서는 **몸에 적힌 모드**(플레이어 PDC `inmcworlds:mode`). 오프라인·다른 스레드는 `players.yml` 의 마지막 모드(메모리).
core `Realms.of(uuid)`·`scope(key, uuid)` 가 이것을 준다 — 다른 플러그인은 사람별 자료의 열쇠에 `@<모드>` 를 붙인다(같이 쓰기면 `""` = 오늘의 열쇠).

### 몸 바꾸기 (`body/`)
부분 6개 — 가방(41칸) · 엔더(27칸) · 경험치 · 체력·배고픔 · 물약 효과(시간 있는 것만) · 부활 자리. 부분마다 core `Realms` 기능 열쇠(`worlds.<부분>`) — 기본 따로, 관리자가 같이로.
**맞추기 하나**(`Swapper.reconcile`)가 모드 이동·접속·토글 변경을 다 한다: 부분마다 "원하는 scope ≠ 몸이 쥔 scope" 면
① 몸 → 쥔 scope 파일(즉시·원자적) ② 원하는 scope 파일 → 몸, 표식 갱신 ③ `player.saveData()`.
**불변식: scope 파일은 그 scope 로 들어갈 때만 읽고, 몸이 쥔 scope 의 파일은 읽지 않는다** — 표식이 PDC 라 .dat 에 몸과 같이 저장되므로 어느 틈에 꺼져도
한 물건이 두 곳의 진짜가 되지 않는다(`SwapperTest` 가 꺼지는 자리마다 본다). 잃는 것은 바닐라 크러시 범위뿐.
- 맞추는 때: 접속 LOWEST · 월드가 바뀜 LOWEST · 토글 변경 즉시(접속자 전부) · 월드를 다른 모드로 옮김.
- 몸을 뜨기 전에 창을 닫는다(2×2 제작 칸·커서의 것이 가방으로 돌아온 뒤에 뜬다). 바꾼 직후 250ms 크리에이티브 칸 바꾸기를 막는다.
- 표식 없는 몸(처음 설치·처음 온 사람) = 지금 몸이 지금 모드의 것. 처음 들어가는 모드 = 빈 몸(체력 가득 · 배고픔 20).
- 모드가 바뀌면 들어올 때 게임 모드를 맞추고 `RealmChangeEvent(from, to, firstEntry)` — 처음 설치한 서버에서 이미 놀던 사람은 첫 입장이 아니다.

### 이동 규칙 (`mode/ModeListener`)
| 길 | 규칙 |
|---|---|
| 순간이동(TPA·워프·홈·`/tp` …) | 다른 모드로 가면 **취소**(HIGHEST). 통과: `Realms.travel`(`/모드`·`/월드 이동`·essentials 포탈) · `inmcworlds.bypass.travel`(관리자 — 몸이 바뀐다) |
| 포탈 | 막지 않는다 — 다른 모드로 가면 몸이 바뀐다(사용자 결정) |
| 빌려 쓰는 월드로 | 막지 않는다 — 그 월드 주인(던전)이 판단 |
| 부활 | 다른 모드 월드로 부활하려 하면 그 사람 모드의 스폰(HIGHEST — 빌려 쓰는 월드는 그대로) |
| 엔티티(포탈·순간이동) | 플레이어가 아닌 것은 모드를 못 넘는다 — 아이템·짐 든 동물·탈것 |

모드를 넘으면 떠난 모드의 마지막 자리를 모드 파일(`location`)에 적는다 — `/모드 <id>` 가 거기로(없거나 그 월드가 없으면 모드 스폰).

### 채팅·탭 묶음
모드마다 채팅 묶음·탭 묶음(id). 같은 묶음끼리 보인다 — 모두 한 묶음 = 전체 공유, 저마다 = 따로. 채팅은 essentials 가 `Realms.sameChat` 으로 `viewers()` 를 거르고,
탭은 우리가 `unlistPlayer/listPlayer`(안 보이는 짝 — 투명·숨김 — 은 건드리지 않음, 100틱마다 다시 맞춤).

### 다른 플러그인과
- essentials: 스폰 = 그 사람 모드의 스폰(`/스폰 설정` = 선 곳의 모드 스폰), 오프라인 가방 편집의 칸 = `Realms.scope("worlds.inventory")`, 채팅 묶음, 포탈 = `Realms.travel`.
- 던전: 빌려 쓰는 월드 · 파티는 같은 모드끼리만 · 돌아갈 월드가 없으면 모드 스폰.
- 나머지 플러그인의 사람별 자료(장비 칸·우편·홈·업적 …)는 4·5단계에서 `Realms.scope` 로 나눈다 — 그 전까지는 모드끼리 같이 쓴다.

