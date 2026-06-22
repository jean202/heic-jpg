# Install Guide

`heic-jpg`를 설치하는 방법은 두 가지입니다. 지금 바로 쓰고 싶으면 **설치 스크립트**,
`brew` 흐름으로 배포/관리하고 싶으면 **Homebrew formula**를 사용하세요.

공통 요구사항: macOS, JDK 17+, 내장 `sips` 명령.

---

## 1. 설치 스크립트 (권장: 즉시 사용)

레포를 클론한 뒤 빌드 + 링크를 한 번에 처리합니다.

```bash
git clone https://github.com/jean202/heic-jpg.git
cd heic-jpg
./scripts/install.sh
```

기본적으로 `~/.local/bin` 아래에 `heic-jpg`, `heic-jpg-ui` 두 명령을 **심볼릭 링크**로
설치합니다. 링크 방식이라 이후 `git pull` 후 다시 빌드하면 변경 사항이 그대로 반영됩니다.

설치 위치 바꾸기:

```bash
PREFIX=/usr/local ./scripts/install.sh   # /usr/local/bin 에 설치
BINDIR=~/bin ./scripts/install.sh        # 특정 디렉터리에 설치
./scripts/install.sh --copy              # 링크 대신 복사
```

설치 후 `~/.local/bin`이 PATH에 없다는 안내가 나오면 셸 프로파일에 추가하세요:

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

확인:

```bash
heic-jpg --help
```

제거:

```bash
./scripts/uninstall.sh
# 설치 때 PREFIX/BINDIR을 줬다면 제거 때도 동일하게 지정
```

`uninstall.sh`는 이 레포를 가리키는 링크/생성된 복사 래퍼만 지우므로, 이름이 같은 무관한 명령은
건드리지 않습니다.

---

## 2. Homebrew formula (tap 배포)

`Formula/heic-jpg.rb`는 tap 배포용 **초안**입니다. 실제로 `brew install`이 되려면
GitHub 릴리스를 먼저 만들어야 합니다.

### 2-1. 릴리스 만들기

```bash
git tag v0.1.0
git push origin v0.1.0
```

GitHub가 자동 생성하는 소스 tarball의 체크섬을 구합니다:

```bash
curl -L https://github.com/jean202/heic-jpg/archive/refs/tags/v0.1.0.tar.gz \
  | shasum -a 256
```

출력된 값을 `Formula/heic-jpg.rb`의 `sha256 "REPLACE_WITH_TARBALL_SHA256"`에 넣습니다.

### 2-2. tap 저장소에 올리기

Homebrew tap은 이름 규칙이 `homebrew-<tap이름>`입니다.

1. GitHub에 `homebrew-heic-jpg` 라는 새 저장소를 만듭니다.
2. 그 안에 `Formula/heic-jpg.rb`를 복사해 커밋/푸시합니다.

### 2-3. 사용자 설치

```bash
brew tap jean202/heic-jpg
brew install heic-jpg
```

이후 업데이트는 `brew upgrade heic-jpg`, 제거는 `brew uninstall heic-jpg`로 통일됩니다.

### formula vs. 스크립트

| | 설치 스크립트 | Homebrew formula |
|---|---|---|
| 사전 준비 | 없음 (클론만) | 릴리스 + tap 저장소 |
| 의존성 처리 | 수동(JDK 직접 설치) | `brew`가 openjdk@17 자동 설치 |
| 업데이트 | `git pull` 후 재빌드 | `brew upgrade` |
| 적합한 경우 | 본인/개발용 즉시 사용 | 외부 사용자 배포 |
