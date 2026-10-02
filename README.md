# DT Keyboard

HeliBoard를 바탕으로 만든 안드로이드 키보드. 접고 펴는 폰(갤럭시 Z 폴드)에 맞춘 레이아웃과, 키보드 위의 동적 툴바(클립보드, 스마트 칩, AI 명령, 번역, GIF, 위젯)가 특징이다.

An Android keyboard based on [HeliBoard](https://github.com/HeliBorg/HeliBoard), with a dynamic toolbar (clipboard, smart chips, AI commands, translation, GIFs, widgets) and a layout made for foldable phones.

## 설치
- 정식 버전: [Releases](https://github.com/northster/-/releases/latest)에서 `DTKeyboard-release.apk`
- 개발 버전(push마다 자동 빌드, "DT Keyboard Dev"로 따로 설치됨): Releases의 맨 위 Pre-release에서 `DTKeyboard-debug.apk`
- [Obtainium](https://github.com/ImranR98/Obtainium)에 `https://github.com/northster/-`를 추가하면 자동 업데이트

## 주요 기능
- 동적 툴바: 위아래 스와이프로 열고 닫기, 클립보드 / 이모지 / GIF 패널을 위로 밀어 크게
- 클립보드: 고정, 여러 줄 보기, 길게 눌러 링크 열기 / 이미지 미리보기, 붙여넣기 칩과 링크 미리보기
- 스마트 칩: 계산, 환율, 단위 변환, 인증번호
- AI 명령과 번역(Google Gemini, 본인 API 키)
- GIF 검색(KLIPY / GIPHY), 즐겨찾기, 최근 사용
- 툴바 위젯: Claude 사용량, 잡학 상식, 이모지 추천(앱에 들어 있는 사전, 오프라인)
- 분할 키보드 자동 전환(폴드 펼침), 한손 모드, 화면별로 따로 배우는 오타 보정
- 테마 편집, 글로우 / 웨이브 효과, 쉬운 설정 모드(고급 > 설정 모드)

자세한 설명: [FORK_README.md](FORK_README.md)

## 개인정보
HeliBoard와 달리 인터넷 권한이 있다. 아래 기능을 켜고 키를 넣었을 때만 쓴다.
- Google Gemini: AI 명령, 번역, 잡학 상식, AI 이모지 추천. 선택한 글이 Google로 간다.
- KLIPY / GIPHY: GIF 검색어
- claude.ai: 사용량 위젯(본인 세션 키)
- 환율, 링크 미리보기(붙여넣은 링크의 페이지 제목과 아이콘)

키보드로 입력한 글을 따로 수집하거나 보내지 않는다. API 키는 기기 안에 암호화해서 저장한다.

## 라이선스와 출처
- DT Keyboard: [GNU GPL v3.0](LICENSE). HeliBoard(GPL-3.0)를 고친 버전이고, HeliBoard는 OpenBoard와 AOSP LatinIME([Apache 2.0](LICENSE-Apache-2.0))을 바탕으로 한다. 자세한 출처는 [HeliBoard README](https://github.com/HeliBorg/HeliBoard#credits) 참고.
- 가져온 코드(MIT): [WM Keyboard](https://github.com/wasi-master/wmkeyboard) (Wasi Master), [SwiftSlate](https://github.com/Musheer360/SwiftSlate) (Musheer Alam)
- 아이콘: [pixelarticons](https://github.com/halfmage/pixelarticons) (MIT, Gerrit Halfmann)을 도트로 다시 그림
- 이모지 키워드: [Unicode CLDR](https://cldr.unicode.org) (Unicode License V3)
- 전체 고지문: 앱의 설정 > 정보 > 오픈소스 고지, 또는 [open_source_licenses.txt](app/src/main/assets/open_source_licenses.txt)
- 제스처 타이핑 라이브러리는 HeliBoard처럼 앱에 들어 있지 않고 사용자가 직접 불러온다(비공개 라이브러리).
