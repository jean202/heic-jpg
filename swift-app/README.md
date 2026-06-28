# heic-jpg — SwiftUI App

Java CLI에서 정리한 입력/출력 규칙과 실패 처리 기준을 SwiftUI macOS/iOS 앱으로
확장한 것입니다. CLI가 macOS 전용 `sips`에 의존하는 것과 달리, 이 앱의 변환 코어
(`HeicJpgKit`)는 **ImageIO/Core Graphics**를 사용해 macOS와 iOS에서 동일하게 동작합니다.

## 구조

```
swift-app/
├── Package.swift              # HeicJpgKit: 크로스플랫폼 변환 코어 (swift build 가능)
├── project.yml                # XcodeGen 스펙 → macOS/iOS 앱 타겟 생성
├── Sources/HeicJpgKit/        # 공유 코어
│   ├── ConversionOptions.swift
│   ├── ConversionResult.swift
│   ├── HeicConverter.swift    # ImageIO 기반 HEIC→JPEG
│   └── ImageFileScanner.swift # 디렉터리 재귀 탐색
├── Tests/HeicJpgKitTests/     # XCTest 단위 테스트
└── Apps/
    ├── Shared/                # 공유 SwiftUI (ContentView, ViewModel)
    ├── macOS/                 # macOS 앱 진입점
    └── iOS/                   # iOS 앱 진입점
```

## 빌드 방법

### 1) 코어만 빌드/테스트 (Xcode 불필요)

```bash
cd swift-app
swift build
swift test
```

### 2) macOS/iOS 앱 빌드

앱 타겟은 Xcode 프로젝트가 필요합니다. 프로젝트 파일은 `project.yml`에서 생성합니다.

```bash
brew install xcodegen      # 최초 1회
cd swift-app
xcodegen generate          # HeicJpgApp.xcodeproj 생성
open HeicJpgApp.xcodeproj
```

Xcode에서 `HeicJpgMac`(macOS) 또는 `HeicJpgiOS`(iOS) 스킴을 선택해 실행하세요.

> 서명: 개인 개발용이면 각 타겟의 Signing & Capabilities에서 본인 Team을 선택하면
> 됩니다. `project.yml`은 `CODE_SIGN_STYLE: Automatic`으로 설정되어 있습니다.

## 동작

- 파일/폴더 추가 (폴더는 하위까지 재귀 탐색해 HEIC/HEIF만 수집)
- macOS는 드래그 앤 드롭도 지원
- 옵션: 최대 변(max dimension), JPEG 품질, 덮어쓰기
- 출력 폴더 미지정 시 원본 옆에 `.jpg` 저장
- 출력 폴더 지정 시 디렉터리 입력의 루트 이름과 하위 구조를 보존
- 변환 결과(성공/건너뜀/실패)를 목록으로 표시
- 원본/결과 썸네일 비교
- 짝 JPEG가 존재하고 비어 있지 않은 원본만 확인 후 정리
- iOS 사진 앱 저장, Files 내보내기, 시스템 공유 시트

## CLI와의 매핑

| CLI 플래그 | 앱 옵션 |
|---|---|
| `--max-dimension N` | Max dimension |
| `--overwrite` | Overwrite existing .jpg |
| `--output-dir` | Choose output folder |
| `--delete-converted` | Delete converted originals (confirmation required) |
| (없음) | JPEG quality 슬라이더 |

## 검증

```bash
swift build && swift test
xcodegen generate
xcodebuild -project HeicJpgApp.xcodeproj -scheme HeicJpgMac \
  -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project HeicJpgApp.xcodeproj -scheme HeicJpgiOS \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```
