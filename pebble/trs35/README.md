# TR-S 35 — Pebble Time 워치페이스

Pebble Time(basalt, 144×168, 64색)용 워치페이스. 게임 Marathon(Bungie)의 "그래픽 리얼리즘"(색 블록 콜라주, 기하학 도형, 산업용 라벨)을 참고하되 Bungie의 로고·문구·폰트·그래픽은 쓰지 않았다. 도형은 모두 코드로 그리고, 숫자 폰트도 직접 만들었다.

![시안과 구현](docs/screenshots/10-mockup-vs-build.png)

| 영역 | 내용 |
|---|---|
| 헤더 | 워치 이름 `PEBBLE TIME_`(커서), 배터리 % + 5칸. 폰 연결이 끊기면 이름 자리가 반전 태그 `NO LINK` |
| 시계 | 코너 마크 4개 안에 37px 숫자. 아래 눈금자 마커가 지금이 그 시간의 몇 분째인지 가리킴. 날씨를 받은 위치 `37.57N 126.98E`(없으면 펌웨어 버전), `AM`/`PM`/`24H` |
| 날짜 | `WED 07.10` + 직각삼각형 마크, 오른쪽 체커(테마의 유일한 포인트 색) |
| 바코드 | 실제로 스캔되는 Code 128. 내용은 현재 시각 `HHMM`(10:08이면 `1008`) |
| 날씨 | 블록 안 아이콘 + `23°C`. Open-Meteo, 폰 위치 기준, API 키 없음 |
| 걸음 | 흰 패널, 지시선 `↘ ····──── × +`, 5자리 걸음수, 목표 대비 %, 오늘 걸은 거리 |
| 흔들기 | 약 1초: 원·십자·방사선이 시계 위로 열렸다 닫히고, 두 칸이 자리·색을 바꾸고, 눈금자 마커가 훑고, 바코드가 섞이고, 체커가 반전, 커서 깜빡임 |

## 1. 디자인

### 시안 반영 원칙

- **비율**: 시계 칸이 화면의 절반(85px). 날짜 18px, 아래 두 칸 48px.
- **색**: 바탕 1색 + 검정/흰 블록 + 진회색(보조 글자·눈금) + 포인트 1색(체커·흔들기 원)만 쓴다. `src/c/theme.c`의 역할도 이 8개로 줄였다.
- **텍스트**: 의미 있는 정보만. `WEATHER`, `STEPS`, `UPD`, `GOAL` 같은 라벨은 지웠다.
- **안티앨리어싱 없음**: 모든 그리기 함수가 `graphics_context_set_antialiased(ctx, false)`로 시작하고, 폰트는 1비트로 변환된다. 화면에는 테마 색만 나온다(중간색 픽셀 없음).
- **정렬**: 좌우 여백 6px(내용은 x=6..137, 72px 칸 안에서는 6..65). 시계 숫자는 글자 폭이 아니라 실제 잉크 기준으로 가운데. 시계 칸 내용은 위아래 코너 마크 사이 정중앙.

시안과 다르게 고친 점 (정렬·대칭):
- 헤더 글자와 배터리를 x=6, x=137에 맞춤 (시안은 4, 140)
- 오른쪽 위 코너 마크가 왼쪽과 비대칭이던 것을 거울 대칭으로
- 아래쪽 코너 마크를 추가해 시계 칸을 위아래로 닫음 (빈 공간 정리, 원하지 않으면 2줄 삭제)
- 체커를 오른쪽 여백 6px에 맞춤 (시안은 2px)
- 바코드를 칸 가운데로, 걸음 숫자도 패널 가운데로
- `°`를 작은 4px 링으로 직접 그려 `23°C`가 72px 칸 안에 들어가게 함

### 폰트

- **TRS Grid** (직접 제작, `tools/make_font.py`): 시안의 숫자처럼 45° 모따기, 획 굵기 ≈ 높이의 0.19. 획 뼈대를 굵게 만들어 TrueType으로 저장한다. 시계 53px(숫자 37px), 날짜 21px(15px), 기온 24px(17px).
- **TRS Grid Narrow**: 걸음수용 좁은 버전(숫자 21px, 폭 10px).
- **Silkscreen 8px** (OFL): 작은 라벨.
- **KH Interference로 바꾸기**: KH Type의 상용 폰트(무료는 체험판)라 저장소에는 넣지 않는다. 파일이 있으면

  ```sh
  make font FONT=~/Downloads/KHInterference-Bold.otf     # 걸음수용을 따로: NARROW=...
  make demo
  ```

  `resources/fonts/display.ttf`, `narrow.ttf`는 git이 무시한다. 빌드할 때 FreeType으로 숫자 높이·위치를 다시 재서 정렬이 자동으로 맞는다(`wscript`의 `FM_*`). 되돌리기: `make font-reset`.

### 테마 (모두 Pebble 64색, `tools/palette_map.py`로 레퍼런스 색에서 매핑)

![테마와 배치](docs/screenshots/11-themes-layouts.png)

| # | 이름 | 바탕 | 블록 / 패널 | 포인트 |
|---|---|---|---|---|
| 0 | LIME (기본, 시안) | SpringBud `#AAFF00` | 검정 / 흰 | BlueMoon `#0055FF` |
| 1 | MINT | Celeste `#AAFFFF` | 검정 / 흰 | BlueMoon |
| 2 | ACID | 검정 | 라임 / 흰 | ElectricBlue |
| 3 | VIOLET | 검정 | LavenderIndigo / 흰 | SpringBud |
| 4 | SIGNAL | 흰 | 검정 / 라임 | BlueMoon |
| 5 | NULL | Folly `#FF0055` | 검정 / 흰 | 검정 |
| 6 | FLARE | ElectricUltramarine | 검정 / 라임 | Folly |

레퍼런스 색 매핑 예: 라임 `#C6FF33`→SpringBud(ΔE 12.5), 민트 `#CAEADA`→Celeste, 파랑 `#1D6FF9`→BlueMoon, 핫핑크 `#FF0961`→Folly(ΔE 6.7), 바이올렛 `#7D39EB`→LavenderIndigo.

배치 프리셋 3개는 모듈 크기가 같고 위치만 다르다: STACK(기본) / SWAP(두 칸 좌우 교체) / INVERT(칸 위, 시계 아래).

### 바코드

`src/c/barcode.c`가 Code 128 (Set C)을 직접 그린다: 시작 C, 숫자 2개씩, mod-103 체크, 정지. 1px 모듈로 숫자 4자리가 57px라 72px 칸에 맞는다. 에뮬레이터 화면을 `zbarimg`로 읽으면 `CODE-128:1008`이 나온다. 다른 내용으로 바꾸려면 `wx_update`의 `code` 한 줄(예: 날짜 `MMDD`). 걸음수 6자리는 68px이라 여백 없이 꽉 차서 권하지 않는다. 실제 휴대폰 스캔은 모듈이 약 0.22mm로 작아서 실기기에서 확인이 필요하다.

## 2. 개발 기반 선정

GitHub에서 시간·날씨·걸음수·설정 화면이 이미 있는 오픈소스 C 워치페이스를 찾아 비교했다.

| 후보 | 플랫폼 | 날씨 | 걸음 | 설정 | 라이선스 | 판단 |
|---|---|---|---|---|---|---|
| **[peerdavid/pebble-mesh](https://github.com/peerdavid/pebble-mesh)** | basalt·diorite·emery | Open-Meteo + 폰 GPS, 키 없음 | Health | Clay | Apache-2.0 | **선택** |
| [gerbert/grid](https://github.com/gerbert/grid) (//GRID) | emery·flint만 | Open-Meteo 등 여러 제공자 | Health | Clay | GPL-2.0 | basalt 미지원, 기능(알람·도플러 등)이 많아 덜어낼 게 많음 |
| [clach04/watchface_framework](https://github.com/clach04/watchface_framework) | 전 플랫폼 | 없음 | Health | Clay | Apache-2.0 | 깔끔한 뼈대지만 날씨를 새로 짜야 함 |
| (제외) TranquilMarmot/StyleTime | Fitbit | | | | | Pebble 아님 |
| (제외) astosia Elizabeth | | | | | | 소스 저장소를 찾지 못함 |

pebble-mesh를 고른 이유: 요구사항(basalt, Open-Meteo·폰 위치, Health 걸음수, 배터리, 블루투스 끊김, Clay)이 전부 이미 있고, 라이선스가 GPL 저장소에 넣기 쉬운 Apache-2.0이다.

가져온 것(구조와 방식) — 코드는 이 디자인에 맞게 다시 썼다:
- 워치 → 폰 `WEATHER_REQ` 요청, 폰 JS가 GPS → Open-Meteo → AppMessage로 응답하는 흐름
- AppMessage를 한 번에 하나만 보내는 JS 큐(`send`/`pump`) — 설정 저장과 날씨 전송이 부딪히지 않게
- Clay select 값(문자열)과 toggle(정수)을 워치에서 모두 받는 처리, 날씨 영구 저장 후 재시작 시 복원
- WMO 날씨 코드 → 아이콘 구간 나누기

바꾼 것: PDC 아이콘 대신 기본 도형으로 그린 날씨 아이콘, TextLayer 대신 모듈별 Layer 직접 그리기(레이아웃 프리셋·애니메이션 때문에), 역지오코딩(bigdatacloud) 제거, 기온을 섭씨×10 정수로 보내 단위 전환을 워치에서 처리(재요청 불필요).

## 3. 구조

```
src/c/main.c      서비스 구독(시간·배터리·연결·Health·탭), AppMessage, 흔들기 타이머
src/c/face.c      모듈별 그리기 (헤더/시계/날짜/날씨 칸/걸음 패널), 흔들기 오버레이
src/c/shapes.c    코너 마크, 눈금자, 체커, 직각삼각형, + ×, 날씨 아이콘
src/c/barcode.c   Code 128 Set C
src/c/layout.c    배치 프리셋 3개
src/c/theme.c     테마 7개 (색 역할 8개)
src/c/state.c     설정·날씨 저장, 메시지 해석, 걸음수·거리, 기온·위치 포맷
src/pkjs/         날씨(Open-Meteo)·메시지 큐·Clay 설정 화면
tools/            make_font.py, palette_map.py, emu_health_on.py, clay_drive.js, test_weather.js
```

## 4. 빠른 확인 환경

### PC 설치 (macOS / Linux, 한 번만)

```sh
uv tool install pebble-tool          # 또는: pipx install pebble-tool
pebble sdk install latest            # SDK + 에뮬레이터
# Linux는 에뮬레이터용 라이브러리:  sudo apt install libsdl2-2.0-0 libpixman-1-0 libglib2.0-0
cd pebble/trs35
npm install                          # Clay
```

### 에뮬레이터 미리보기

```sh
make emu        # = pebble build && pebble install --emulator basalt
make demo       # 시간 10:08 고정 + 샘플 날씨/걸음 (스크린샷용)
make shot       # docs/screenshots/latest.png
make config     # 에뮬레이터용 설정 화면을 브라우저로
pebble emu-tap --emulator basalt                    # 흔들기
pebble emu-battery --percent 15 --emulator basalt   # 배터리
pebble emu-bt-connection --connected no --emulator basalt
```

데모 빌드에서 테마·배치: `TRS_DEMO=1 TRS_THEME=1 TRS_LAYOUT=2 pebble build`.
에뮬레이터에서 "This app requires Pebble Health" 창이 뜨면 `make health` 후 다시 설치.

### 실제 워치에 명령 한 줄로 설치

1. 폰과 PC를 같은 Wi-Fi에 연결
2. 폰의 Pebble 앱 → 설정 → **개발자 연결(Developer Connection)** 켜기 → 표시되는 IP 확인
3. PC에서:

```sh
make phone PHONE=192.168.0.12        # 빌드 + 설치 + 로그
# 또는 IP를 환경변수로 두면 그냥 `pebble install`이 워치로 감
export PEBBLE_PHONE=192.168.0.12
pebble build && pebble install
```

## 5. 결과와 제안

- 흔들기 애니메이션: ![흔들기](docs/screenshots/05-shake.gif)
  16프레임 × 66ms(약 1.1초), 끝나면 3초 쿨다운(타이머라 시계가 바뀌어도 막히지 않음), 배터리 10% 이하면 재생 안 함, 설정에서 끄면 가속도 탭 구독도 해제. 에뮬레이터는 그리기가 느려 약 2초로 보인다.
- 설정 화면(Clay): <img src="docs/screenshots/13-settings-page.png" width="240">
- 추가·수정 제안 샘플 (반영 안 됨, 고르면 넣음):

  ![제안](docs/screenshots/12-proposals.png)

  1. 아래 코너 마크 없이 (시안 그대로)
  2. 헤더 가운데 하루 진행 점 (2시간당 1점)
  3. 목표 달성 시 걸음 패널 반전 + `GOAL`
  4. 비·밤·영하 표시 확인용 (아이콘·`-4°C`)
  5. 폰 연결 끊김 + 배터리 15% (이 상태 표시는 반영됨)
  6. 일몰 후 자동으로 어두운 테마(ACID) 전환

## 6. 실기기 테스트 체크리스트

- [ ] 걸음수·거리가 실제 값으로 오르는지 (basalt 에뮬레이터는 걸음 주입을 지원하지 않아 데모 값으로만 확인)
- [ ] 폰 GPS 권한 허용 후 실제 Open-Meteo 날씨가 오는지 (`make phone` 로그에 `open-meteo: 18.5C code 3`)
- [ ] 흔들기 감도와 애니메이션 체감 속도
- [ ] 폰 블루투스 끄기 → `NO LINK` + 진동, 다시 켜면 복귀
- [ ] 바코드가 휴대폰 스캐너로 읽히는지
- [ ] 실제 화면에서 라임/민트 바탕이 너무 바래 보이지 않는지

## 부록: 이 저장소를 만든 클라우드 환경에서의 SDK 구성

작업한 클라우드 환경은 `sdk.repebble.com`과 `api.open-meteo.com`이 네트워크 정책으로 막혀 있어서 공식 SDK 대신 다음처럼 구성했다. 일반 PC에서는 위의 `pebble sdk install latest`면 된다.

- 툴체인·QEMU: GitHub [coredevices/PebbleOS-SDK](https://github.com/coredevices/PebbleOS-SDK) 릴리스 번들
- 앱 SDK + basalt 에뮬레이터 펌웨어: [coredevices/PebbleOS](https://github.com/coredevices/PebbleOS) `v4.9.60`(snowy 보드가 남아 있는 버전)을 `./waf configure --board=snowy_bb2 --qemu --nojs --release && ./waf build qemu_image_micro qemu_image_spi`로 빌드한 뒤 `pebble sdk install --tintin <경로>`
- 날씨 경로는 JS의 API 주소만 로컬 목 서버로 바꾼 임시 사본으로 끝까지 확인, 설정 화면은 `tools/clay_drive.js`(헤드리스 Chromium)로 `pebble emu-app-config`를 구동

## 라이선스와 출처

- 이 워치페이스 코드와 TRS Grid 폰트: 저장소 라이선스(GPL-3.0)
- 구조 참고: [pebble-mesh](https://github.com/peerdavid/pebble-mesh) (David Peer, Apache-2.0)
- 날씨: [Open-Meteo](https://open-meteo.com) (CC BY 4.0, 비상업 무료)
- Silkscreen (Jason Kottke) — SIL OFL 1.1, `resources/fonts/OFL-silkscreen.txt`
- KH Interference는 포함하지 않는다(KH Type 라이선스). Marathon / Bungie의 상표·로고·폰트·그래픽도 포함하지 않으며 레퍼런스 이미지는 저장소에 넣지 않았다.
