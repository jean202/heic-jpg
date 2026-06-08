# Design: `--delete-converted` (검토 후 원본 HEIC 정리)

날짜: 2026-06-08

## 목적

변환은 HEIC/HEIF → JPG를 새로 만들 뿐 원본을 건드리지 않는다. 사용자가
변환 결과를 직접 검토한 뒤, 짝 JPG가 확실히 존재하는 원본 HEIC만 골라
영구 삭제하는 별도 정리 단계를 제공한다.

## 핵심 결정 사항

- **삭제 판단 기준**: 각 HEIC에 대응하는 짝 JPG가 실제로 존재하고
  비어있지 않을(크기 > 0) 때만 그 HEIC를 삭제 대상으로 본다.
- **삭제 방식**: 영구 삭제(`Files.delete`). 휴지통 이동 아님.
- **확인 절차**: 삭제 대상 목록을 먼저 출력하고, 몇 개를 지울지 보여준 뒤
  사용자가 `y`를 입력해야만 실제로 삭제한다. (기본은 취소)
- **구현 방식**: 기존 명령에 `--delete-converted` 플래그를 추가한다.
  별도 서브커맨드나 셸 스크립트가 아니라, 변환 때 쓰던 타깃 경로 계산
  로직(`ConversionPlanner`)을 그대로 재사용해 "짝 찾기" 규칙을 변환과
  100% 일치시킨다.

## 사용법

```bash
# 1단계: 변환 (기존 그대로)
./heic-jpg ~/Pictures/iPhone --output-dir ~/Pictures/converted

# 2단계: 사람이 직접 결과 검토

# 3단계: 검토 끝나면 원본 HEIC 정리
#   변환 때와 같은 입력 경로 + --output-dir 를 줘야 짝 JPG를 같은 규칙으로 찾는다
./heic-jpg ~/Pictures/iPhone --output-dir ~/Pictures/converted --delete-converted
```

예상 출력:

```text
DELETE  ~/Pictures/iPhone/IMG_0001.HEIC  (jpg: ~/Pictures/converted/iPhone/IMG_0001.jpg)
DELETE  ~/Pictures/iPhone/IMG_0002.HEIC  (jpg: ~/Pictures/converted/iPhone/IMG_0002.jpg)
KEEP    ~/Pictures/iPhone/IMG_0003.HEIC  (no matching .jpg)

3 HEIC file(s) scanned: 2 to delete, 1 kept (no jpg).
Permanently delete 2 file(s)? This cannot be undone. [y/N]:
```

`y` 또는 `yes` 입력 시에만 삭제. 그 외(엔터, `n` 등)는 취소.

## 아키텍처 / 컴포넌트

기존 구조 재사용을 원칙으로 한다.

- `CliOptions`: `boolean deleteConverted` 필드 추가.
- `CliOptionsParser`: `--delete-converted` 파싱 추가.
- `ConversionPlanner`: **변경 없음.** 이미 각 HEIC→JPG 매핑을 담은
  `ConversionPlan`을 생성하므로 그대로 재사용한다.
- `HeicJpgCli`: `deleteConverted`가 true면 `executePlan` 대신
  새 메서드 `executeCleanup`로 분기한다.
- **새 seam `UserConfirmation`** (인터페이스): `boolean confirm(String message)`.
  - 기본 구현은 `System.in`에서 한 줄을 읽어 `y`/`yes`(대소문자 무관) 판정.
  - 테스트에서는 stub을 주입해 외부 라이브러리 없이 검증한다
    (기존 테스트 방식 유지).

### `executeCleanup` 로직

1. `plan.tasks()`를 순회한다. 각 task의 `target()`(JPG)이 존재하고
   크기 > 0 이면 삭제 후보(DELETE), 아니면 유지(KEEP)로 분류한다.
2. DELETE/KEEP 목록을 출력한다.
3. 삭제 후보가 0개면 메시지를 출력하고 종료한다.
4. `UserConfirmation.confirm(...)`을 호출한다. true면 각 후보의
   `source()`(HEIC)를 `Files.delete()`한다.
5. 삭제 성공/실패 개수를 요약 출력한다.

## 데이터 흐름 핵심

- 짝 JPG 판정 시 **빈(0바이트) 파일은 변환되지 않은 것으로 간주**하여
  삭제 대상에서 제외한다. 변환 실패로 깨진/빈 JPG가 남아 있을 때 원본까지
  삭제되는 사고를 막는다.
- 짝 JPG 경로는 변환과 동일하게 `ConversionPlanner`가 계산한 `target()`을
  사용하므로, `--output-dir` 구조 보존 동작과 자동으로 일치한다.

## 에러 처리

- 짝 JPG 없음 → KEEP (정상, 에러 아님).
- 개별 HEIC 삭제 실패(권한 등) → `FAIL` 출력 후 다음 파일 계속 진행,
  종료코드 `EXIT_CONVERSION_FAILURE`(2).
- 프롬프트에서 취소 → 아무것도 삭제하지 않고 `EXIT_SUCCESS`(0).
- 스캔 결과 HEIC 0개 → 기존처럼 "No HEIC/HEIF files found." 출력.

## 테스트

기존 `HeicJpgCliTest` 패턴(임시 디렉터리 + stub)을 그대로 따른다.

- 짝 JPG 있는 HEIC → 확인 `y` → 삭제됨.
- 짝 JPG 없는 HEIC → KEEP, 삭제 안 됨.
- 0바이트 JPG → KEEP.
- 프롬프트 `n`/취소 → 삭제 안 됨.
- `--output-dir` 구조 보존 경로에서 짝 매칭 정상 동작.
- 삭제 후보 0개 처리.

## 비범위 (Out of scope / YAGNI)

- 휴지통 이동, `--permanent` 분기: 영구 삭제 단일 동작만 구현.
- `--yes`(프롬프트 생략) 플래그: 항상 프롬프트한다.
- `--dry-run`과의 조합: 별도 동작을 정의하지 않는다. 프롬프트 자체가
  목록 미리보기 역할을 하므로 추가 미리보기 모드는 두지 않는다.
- 문서(README) 갱신: 구현 단계 계획에서 별도로 다룬다.
