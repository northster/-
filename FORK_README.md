# DT Keyboard (HeliBoard 포크): 동적 툴바 UX 테스트용

동적 툴바 UX를 테스트하려고 [HeliBoard](https://github.com/Helium314/HeliBoard)를 포크한 키보드.
upstream 커밋 `dc28c24d`(2026-09-09)가 기준이고, git 히스토리를 그대로 가져왔기 때문에 upstream 변경을 병합할 수 있다.

```
git fetch upstream && git merge upstream/main
```

## 1. 베이스 선정: HeliBoard를 고른 이유

| 기준 | HeliBoard | FlorisBoard |
|---|---|---|
| 한글(자모 조합) | ✅ `HangulCombiner.kt`(338줄, OpenBoard에서 가져온 검증된 조합기). 두벌식, 세벌식 390, 세벌식 최종, 음성식 레이아웃, 하드웨어 키보드 조합까지 지원 | △ `HangulUnicode.kt`(126줄) 컴포저. 두벌식과 음성식만 있고 구현이 단순함 |
| Jetpack Compose | 설정 화면만 Compose. 키보드 뷰는 기존 View(`MainKeyboardView`/`PointerTracker`) | 키보드 UI 전체가 Compose |
| 코드 규모와 구조 | Kotlin 약 3.6만 줄 + Java 약 3.3만 줄 + C++ JNI. 단일 모듈이고, 터치 처리가 `PointerTracker.java` 한 곳에 모여 있음 | Kotlin 약 6.1만 줄, 다중 모듈. 터치 처리가 Compose 포인터 입력에 흩어져 있음 |
| 유지보수 | 활발함(2026-09 커밋) | 활발함(2026-09 커밋, 3월 이후 커밋 53개) |
| 라이선스 | GPL-3.0 (배포하려면 소스 공개 필요) | Apache-2.0 |
| 이후 계획과의 궁합 | 레이아웃 JSON 커스터마이징(삼성 레이아웃), 스플릿 키보드, 폴더블 처리(`FoldableUtils`), 사용자 색상이 이미 있음 | 테마(Snygg)는 강력하지만 스플릿 키보드와 한글 레이아웃은 보강이 필요함 |

**결론: HeliBoard.** 가장 중요한 한글 조합이 성숙해 있다. 또 이번 과제의 핵심인 "탭과 스와이프 구분"은 `PointerTracker`에 모인 단일 터치 상태 머신에 끼워 넣는 것이 가장 확실하다.
단점은 GPL-3.0이다. 외부에 배포하면 소스를 공개해야 한다.

## 2. 구현 내용

이번 포크에서 추가한 코드는 모두 `app/src/main/java/helium314/keyboard/fork/`에 있다. upstream 파일은 호출 지점만 최소한으로 고쳤고, 수정한 곳에는 `fork:` 주석을 달았다.

```
fork/
  ForkSettings.kt              제스처 정책(컴파일 타임) + 환경설정 키/기본값 + 임계값 캐시
  gesture/VerticalSwipeDetector.kt   탭 vs 세로 스와이프 판정 (순수 로직, 단위 테스트 있음)
  toolbar/DynamicToolbarController.kt 펼침/접힘 상태, 애니메이션, 인셋, 상태 저장
  toolbar/DynamicToolbarView.kt       버튼만 그리는 뷰
  toolbar/ToolbarItems.kt             버튼 목록(클립보드, AI, 번역, 더보기: 자리표시)
  settings/DynamicToolbarScreen.kt    설정 > 동적 툴바 (임계값 슬라이더)
```

### 탭과 스와이프 구분 (`VerticalSwipeDetector` + `PointerTracker`)
- 손가락이 **키 위에 하나만** 닿았을 때만 추적한다. 두 번째 손가락이 닿으면 빠른 타이핑으로 보고 중단한다.
- 판정 조건. 기본값은 `설정 > 동적 툴바`에서 바꿀 수 있다.
  - 세로 이동 ≥ **40dp**
  - 그 시점까지의 평균 속도 ≥ **300dp/s**
  - 수직축 기준 각도 ≤ **30°**
  - 가로 이동이 **24dp** 이상이고 세로보다 크면 가로 동작으로 확정한다(스와이프 아님)
  - **350ms** 안에 거리 조건을 채우지 못하면 스와이프 아님(롱프레스나 느린 드래그)
- 판정은 한 번 나면 바뀌지 않는다. 스와이프로 판정되면 롱프레스와 키 반복 타이머를 취소하고, 누른 키 그래픽을 해제한 뒤 **업 이벤트에서 입력되지 않도록** 트래킹을 끈다.
  Shift나 기호 전환처럼 누를 때 동작하는 키는 "밀어서 벗어난" 것처럼 release와 finishSliding을 호출해 원래 상태로 되돌린다.
- 롱프레스 팝업이 뜨거나, 커서 모드, 키 반복, 다른 제스처가 시작되면 판정을 중단한다. 그래서 짧은 탭, 가로 움직임, 롱프레스는 원래 동작 그대로다.
- 손가락이 윗줄 키로 넘어가 키 입력이 먼저 취소된 경우에도 스와이프 판정은 계속된다. 키 윗부분에서 시작한 스와이프도 인식된다.

### 제스처 정리
- 글라이드 타이핑, 백스페이스 스와이프, 스페이스 세로 스와이프(언어 전환/터치패드/숨기기)는 `ForkSettings`에서 코드로 강제로 끈다.
- 스페이스 가로 스와이프는 기본이 "없음"이다. 설정에서 "커서 이동"만 다시 켤 수 있다.
- **스페이스 길게 누른 뒤 드래그 = 커서 이동**이다. 커서 모드에 들어가면 키보드가 반투명해진다. 이 모드에서는 가로 이동만 처리하고 세로 이동은 무시하므로 툴바가 열리지 않는다.
- 자동 수정과 제안 표시는 기본으로 끈다. HeliBoard 기본 툴바/제안 줄도 기본으로 숨긴다(설정에서 다시 켤 수 있음).

### 애니메이션과 뒤쪽 앱이 튀는 문제
키보드 높이가 바뀌면 앱은 `contentTopInsets`에 맞춰 레이아웃을 다시 잡는다. 흔한 방식(툴바 높이를 매 프레임 바꾸기)을 쓰면 **앱이 매 프레임 relayout** 되어 버벅이고 튄다. 그래서 다음처럼 처리했다.

1. 툴바는 `InputView`(IME 창 전체)의 자식이고 **키보드 프레임 뒤**에 있다. 애니메이션은 `translationY`만 바꾸기 때문에 키보드 레이아웃 패스가 한 번도 일어나지 않는다. 키보드 뒤에서 툴바가 올라와 키보드가 자라는 것처럼 보인다(키보드와 같은 배경).
2. 앱 인셋은 **전환마다 딱 한 번**만 바꾼다. 펼칠 때는 시작 시점에, 접을 때는 애니메이션이 끝난 시점에 바꾼다.
3. 그래도 한 번의 점프가 거슬리면 `설정 > 동적 툴바 > 툴바가 앱 위에 겹쳐 표시`를 켠다. 이 모드는 인셋을 아예 바꾸지 않으므로 앱이 **전혀 움직이지 않고**, 툴바가 앱 하단을 가린다.
4. 추가 방안(아직 구현 안 함): 툴바 높이만큼을 항상 앱 인셋으로 잡아 두는 방식이 있다. 접혀 있을 때 앱 하단에 빈 공간이 생기는 대신 앱이 절대 움직이지 않는다.
   Android 11+ `WindowInsetsAnimation`은 IME show/hide에만 동기화되고, 이미 떠 있는 IME의 높이 변화에는 적용되지 않는다. 그래서 앱 쪽 애니메이션 동기화는 기대할 수 없다.

> ⚠️ 이 판단은 코드 설계를 근거로 한 것이다. 실기기(폴드8)에서 버벅임을 직접 확인하지는 못했다. 아래 테스트 스크립트와 두 모드(리사이즈/오버레이)를 비교해 보고, 개발자 옵션의 "GPU 렌더링 프로파일"로 확인하는 것을 권장한다.

### 폴드8 커버 화면 ↔ 내부 화면
화면이 바뀌면 HeliBoard가 입력 뷰를 새로 만든다(`onCreateInputView` → `setInputView`). 툴바 상태는 컨트롤러(서비스 수명)와 SharedPreferences(`fork_toolbar_expanded`)에 저장된다.
새 뷰에 붙을 때 애니메이션 없이 복원되므로 프로세스가 재시작되어도 상태가 유지된다.

## 3. 빌드와 설치
- 패키지명: `io.github.northster.dtkeyboard`(디버그 빌드는 `.debug`). 앱 이름은 "DT Keyboard".
  콘텐츠 프로바이더 authority도 바꿨기 때문에 HeliBoard와 함께 설치할 수 있다.
- ABI는 `arm64-v8a`(폴드8)와 `x86_64`(에뮬레이터)만 넣었다.
- 서명은 저장소의 `keystore/dt-test.jks`(테스트 전용 고정 키)를 쓴다. 그래서 CI 빌드끼리 덮어쓰기 설치가 된다. **공개 배포에는 쓰지 말 것.**
- GitHub Actions(`.github/workflows/dt-build.yml`)가 push마다 단위 테스트를 돌리고 APK를 빌드한다. 결과물은 저장소 Releases의 **`dev-latest`** 프리릴리스에 올라간다. 폰 브라우저에서 바로 받으면 된다.
- 로컬 빌드: Android SDK와 NDK 28이 있는 환경에서 `./gradlew assembleDebug`

## 4. 무선 디버깅 테스트 (`scripts/adb-wireless-test.sh`)
폰과 PC가 같은 Wi-Fi에 있어야 하고, 폰에서 개발자 옵션 > 무선 디버깅을 켠다.
```
scripts/adb-wireless-test.sh --pair 192.168.0.12:37123 123456   # 최초 1회 페어링
scripts/adb-wireless-test.sh 192.168.0.12:41234 DTKeyboard_4.1-debug.apk
```
Windows는 `scripts/adb-wireless-test.ps1 -Target 192.168.0.12:41234 -Apk .\DTKeyboard_4.1-debug.apk`를 쓴다(페어링은 `-Pair ... -Code ...`).
스크립트는 다음 순서로 동작한다.
1. 폰 알림창과 PC에 **"테스트 시작"** 알림을 띄운다.
2. 앱을 설치하고, IME를 활성화하고 기본 키보드로 지정한다.
3. 폰에서 텍스트 칸을 누르고 Enter를 치면 자동 제스처를 실행한다: 탭 → 빠른 위 스와이프 → 빠른 아래 스와이프 → 느린 위 드래그(열리면 안 됨) → 탭.
4. logcat에서 펼침/접힘 횟수와 크래시를 집계해 **"테스트 완료 ✅/⚠️"** 알림을 띄운다. 로그는 `test-logs/`에 저장한다.

## 5. 삼성 키보드 스타일 기본 레이아웃
- 한글은 두벌식(`korean.json`, 삼성과 같은 자모 배치), 영문은 QWERTY이고, 윗줄에 숫자 힌트가 표시된다.
- 맨 아래 줄은 `layouts/functional/functional_keys.json`에 있다.
  - 문자 화면: `!#1 | 한/영 | 스페이스 | . | 엔터`
  - 기호 화면: `ABC | 한/영 | 이모지 | 스페이스 | . | 엔터`
- 기호 1/2 페이지(`symbols/symbols.txt`)는 숫자 줄, `! @ # $ % ^ & * ( )`, `- ' " : ; , ?`로 구성된다. 첫 줄은 숫자 줄로 바뀌고 그 문자들은 길게 누르기 팝업이 된다.
- 기호 2/2 페이지(`more_symbols/symbols_shifted.txt`)는 `+ × ÷ = < > { } [ ]`, `€ £ ¥ ₩ / ~ ` ¤ ° ♡`, `_ \ | 《 》 ¡ ¿`로 구성된다.
- 한국어와 영어가 기본으로 켜져 있고 `한/영` 키로 전환한다(`SubtypeSettings.getDefaultEnabledSubtypes`).
- 폴드 내부 화면(태블릿 크기)에서도 같은 레이아웃을 쓰도록 태블릿용 기능키와 추가 키를 껐다.

## 6. 이후 기능을 붙일 자리 (이번 단계에서는 구현하지 않음)
- **폴드8 내부 화면 스플릿 키보드**: HeliBoard에 이미 접힘/펼침 상태별 스플릿 설정(`Settings.PREF_ENABLE_SPLIT_KEYBOARD`, `..._FOLDED`, `..._LANDSCAPE`)과 `FoldableUtils`가 있다. 이걸 출발점으로 삼는다. 조건 판단은 `fork/`에 새 클래스로 둔다.
- **색상 커스터마이징**: HeliBoard `Colors`/`ColorType`와 사용자 색상 화면을 활용한다. 툴바는 이미 `ColorType`으로 칠해진다.
- **WM Keyboard 기능 / 툴바 버튼 실제 동작**: `ToolbarItems`에 항목을 추가하고 `DynamicToolbarController.onItemClicked`에서 id별로 처리한다.
- 제스처 정책 변경은 `ForkSettings` 한 곳에서 한다.
