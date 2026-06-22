# Architecture

## Goal

문제는 단순합니다.

- 아이폰 사진은 `HEIC`가 기본 포맷인 경우가 많음
- 일부 서비스는 `HEIC` 업로드를 처리하지 못함
- 업로드 전에 빠르게 `JPEG`로 바꾸는 로컬 도구가 필요함

이 저장소의 1차 목표는 macOS에서 바로 쓸 수 있는 Java CLI를 만드는 것입니다.

## Why Java First

- 현재 포트폴리오의 중심 기술 스택은 Java 백엔드임
- 가장 익숙한 언어로 먼저 문제를 작게 닫는 편이 구현 속도와 품질 측면에서 유리함
- 이후 같은 문제를 Swift 앱으로 확장하면 “문제 해결의 단계적 확장”이 포트폴리오 포인트가 됨

즉, 이 프로젝트는 “Java로 빠르게 해결하고, Swift로 사용자 범위를 넓힌다”는 전략을 따른다.

## Why `sips`

Java 표준 라이브러리만으로는 `HEIC` 디코딩을 안정적으로 처리하기 어렵습니다.  
반면 macOS에는 `sips`가 기본 포함되어 있고, Apple 생태계의 이미지 포맷 처리를 바로 활용할 수 있습니다.

따라서 현재 버전은 다음 구조를 사용합니다.

- Java: CLI, 경로 탐색, 출력 경로 계산, 옵션 처리, 오류 보고
- `sips`: 실제 이미지 포맷 변환과 리사이즈

이 선택 덕분에 외부 라이브러리 의존성 없이 바로 실행 가능한 도구를 만들 수 있습니다.

## Main Components

- `CliOptionsParser`
  - 명령행 옵션 파싱
- `ConversionPlanner`
  - 입력 파일/디렉터리 해석
  - 재귀 탐색
  - 출력 경로 계산
  - 출력 충돌 검증
- `HeicJpgCli`
  - 실행 흐름 제어
  - 요약 출력
  - 종료 코드 관리
- `SipsImageConverter`
  - `sips` 명령 실행
  - 변환 실패 메시지 정리

## Output Rules

- 파일 입력 + `--output-dir` 없음
  - 원본 파일 옆에 같은 이름의 `.jpg` 생성
- 파일 입력 + `--output-dir` 있음
  - 지정한 디렉터리 바로 아래에 `.jpg` 생성
- 디렉터리 입력 + `--output-dir` 없음
  - 원본 폴더 구조 안에서 각 파일 옆에 `.jpg` 생성
- 디렉터리 입력 + `--output-dir` 있음
  - `output-dir/<입력디렉터리이름>/...` 형태로 구조를 유지

## Exit Codes

- `0`: 성공
- `1`: 잘못된 인자 또는 입력 검증 실패
- `2`: 변환 중 하나 이상 실패

## Swift Expansion (`swift-app/`)

CLI 다음 단계로 SwiftUI 앱을 추가했습니다. 코드는 `swift-app/`에 있습니다.

### 변환 엔진: `sips` → ImageIO

CLI는 macOS 내장 `sips`에 의존하지만, iOS에는 `sips`가 없습니다.
따라서 앱의 공유 코어(`HeicJpgKit`)는 **ImageIO / Core Graphics**로 직접 디코딩·인코딩합니다.

- `CGImageSourceCreateWithURL`로 HEIC/HEIF 읽기
- `CGImageSourceCreateThumbnailAtIndex`(+transform)로 EXIF 방향을 픽셀에 항상 반영
- 리사이즈가 없을 때도 원본 해상도를 유지하면서 orientation을 1로 정규화
- `CGImageDestination`으로 JPEG 인코딩(`kCGImageDestinationLossyCompressionQuality`)

덕분에 macOS와 iOS가 **완전히 같은 변환 코드**를 공유합니다.

### 구성

- `HeicJpgKit` (SwiftPM 라이브러리): `HeicConverter`, `ImageFileScanner`,
  `ConversionOptions`, `ConversionResult` — 플랫폼 비의존, `swift test`로 검증
- `Apps/Shared`: SwiftUI `ContentView` + `ConversionViewModel`
- `Apps/macOS`, `Apps/iOS`: 각 플랫폼 진입점
- `project.yml`(XcodeGen): macOS/iOS 두 앱 타겟 생성

### 재사용한 CLI 규칙

CLI에서 정리한 입력/출력 규칙과 실패 처리 기준을 그대로 옮겼습니다.

- 출력 경로 계산(`targetURL`): `--output-dir` 유무에 따른 규칙 동일
- 디렉터리 입력은 출력 폴더 아래에 입력 루트 이름과 상대 경로를 보존
- 기존 `.jpg`가 있으면 건너뜀, `overwrite`로 덮어쓰기
- 디렉터리 입력 시 하위까지 재귀 탐색
- 둘 이상의 입력이 같은 출력으로 매핑되면 변환을 차단

### 앱 전용 흐름

- 변환 결과를 iOS 사진 앱에 추가하거나 Files로 복사하고 시스템 공유 시트로 전달
- 원본/결과 ImageIO 썸네일을 나란히 표시
- 원본 정리는 예상 위치에 비어 있지 않은 JPEG가 있는 HEIC/HEIF만 대상으로 계산
- 삭제 직전에 JPEG 조건을 다시 검사하고 사용자 확인 뒤 영구 삭제
- 변환과 삭제 파일 작업은 actor에서 실행해 MainActor UI를 막지 않음
