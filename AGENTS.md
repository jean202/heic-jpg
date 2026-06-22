<!-- AGENTS.md 와 CLAUDE.md 는 동일하게 유지됩니다. 한쪽을 수정하면 다른 쪽도 같이 수정하세요. -->
# heic-jpg — Project Guide (CLAUDE.md = AGENTS.md)

## 개요
macOS에서 HEIC/HEIF를 JPEG로 일괄 변환하는 Java 17 CLI + SwiftUI macOS/iOS 앱. (기능 구현 완료)

## 스택 & 실행
- Java 17 CLI (`src/`), 빌드 산출물 `build/`, 스크립트 `scripts/`
- 설치: `scripts/install.sh` (로컬 링크), `Formula/heic-jpg.rb` (Homebrew tap 초안)
- SwiftUI 앱 (`swift-app/`): ImageIO 기반 공유 코어 `HeicJpgKit` + macOS/iOS 앱
  - 코어: `cd swift-app && swift build && swift test`
  - 앱: `xcodegen generate` 후 `HeicJpgApp.xcodeproj` 열기
- 빌드 도구 확인 후 표준 빌드/테스트 실행.

## 다음 작업 시작 시
- 배포 시 첫 릴리스 태그와 sha256으로 `Formula/heic-jpg.rb`를 확정하세요.
