# ACT Workflow for Codex Usage

## 개요

이 문서는 Claude/OpenCode용 Agentic Coding Toolkit(ACT)의 핵심 워크플로를 Codex에서 비슷하게 사용하는 방법을 정리한다.

중요한 점:

- 기존 Claude용 ACT는 그대로 유지된다.
- Codex용 변환본은 별도 스킬로 추가되었다.
- Claude의 slash command/hook을 그대로 복제한 것은 아니고, Codex에서 같은 흐름으로 작업하도록 맞춘 것이다.

## 설치 위치

Codex 스킬 설치 경로:

```bash
~/.codex/skills/act-workflow-codex
```

주요 파일:

```bash
~/.codex/skills/act-workflow-codex/SKILL.md
~/.codex/skills/act-workflow-codex/scripts/artifact.py
~/.codex/skills/act-workflow-codex/scripts/plan_status.py
```

Codex가 새 스킬을 인식하려면 보통 재시작이 필요하다.

## 무엇을 할 수 있나

이 스킬은 다음 ACT 흐름을 Codex에서 재현한다.

- `act:workflow:spec`
- `act:workflow:refine-spec`
- `act:workflow:plan`
- `act:workflow:work`
- `act:workflow:compound`

Codex에서는 실제 slash command가 실행되는 것이 아니라, 위 표현을 "원하는 워크플로 모드"로 해석해 해당 작업을 수행한다.

## 기본 사용법

### 1. spec 만들기

작업 요구사항을 기반으로 spec 문서를 만든다.

예시:

```text
act:workflow:spec 버튼 생성
```

또는

```text
이 작업을 ACT 방식으로 spec부터 작성해줘: 버튼 생성
```

생성 대상:

```text
ai_specs/<slug>-spec.md
```

예:

```text
ai_specs/button-creation-spec.md
```

### 2. spec 리뷰하기

작성된 spec의 누락, 잘못된 가정, 코드베이스 불일치, UX 허점 등을 점검한다.

예시:

```text
act:workflow:refine-spec ai_specs/button-creation-spec.md
```

생성 대상:

```text
ai_specs/<slug>-review.md
```

예:

```text
ai_specs/button-creation-review.md
```

### 3. plan 만들기

spec를 바탕으로 phase 단위 구현 계획을 만든다.

예시:

```text
act:workflow:plan ai_specs/button-creation-spec.md
```

생성 대상:

```text
ai_specs/<slug>-plan.md
```

예:

```text
ai_specs/button-creation-plan.md
```

### 4. work 실행하기

plan을 읽고 실제 구현을 진행한다.

예시:

```text
act:workflow:work ai_specs/button-creation-plan.md
```

한 phase만 실행하려면:

```text
act:workflow:work ai_specs/button-creation-plan.md --single-phase
```

동작 방식:

- plan 전체를 읽는다.
- plan 안에 spec 경로가 있으면 spec도 읽는다.
- 체크박스를 갱신한다.
- 검증 명령을 실행한다.
- git 저장소이고 안전한 상태라면 phase 경계에서 커밋한다.

### 5. compound 문서 만들기

이번 세션에서 얻은 재사용 가능한 지식과 의사결정을 문서로 남긴다.

예시:

```text
act:workflow:compound 버튼 생성 작업 정리
```

생성 대상:

```text
ai_docs/solutions/<category>/<slug>.md
```

예:

```text
ai_docs/solutions/feature-delivery/button-creation.md
```

## 산출물 규칙

기본 규칙:

- spec: `ai_specs/<slug>-spec.md`
- review: `ai_specs/<slug>-review.md`
- plan: `ai_specs/<slug>-plan.md`
- solution: `ai_docs/solutions/<category>/<slug>.md`

숫자 prefix가 있는 경우 유지한다.

예:

```text
ai_specs/001-auth-spec.md
ai_specs/001-auth-review.md
ai_specs/001-auth-plan.md
ai_docs/solutions/feature-delivery/001-auth.md
```

## helper script 사용법

Codex가 자동으로 문서를 만들게 두는 것이 기본이지만, 필요하면 스캐폴드 스크립트를 직접 쓸 수 있다.

환경 변수:

```bash
export CODEX_HOME="${CODEX_HOME:-$HOME/.codex}"
export ACT_CODEX_SKILL="$CODEX_HOME/skills/act-workflow-codex"
export ACT_CODEX_ARTIFACT="$ACT_CODEX_SKILL/scripts/artifact.py"
export ACT_CODEX_PLAN_STATUS="$ACT_CODEX_SKILL/scripts/plan_status.py"
```

### artifact.py

spec 스캐폴드:

```bash
python3 "$ACT_CODEX_ARTIFACT" spec --title "Button creation"
```

review 스캐폴드:

```bash
python3 "$ACT_CODEX_ARTIFACT" review --source ai_specs/button-creation-spec.md
```

plan 스캐폴드:

```bash
python3 "$ACT_CODEX_ARTIFACT" plan --source ai_specs/button-creation-spec.md
```

solution 스캐폴드:

```bash
python3 "$ACT_CODEX_ARTIFACT" solution \
  --category feature-delivery \
  --title "Button creation rollout" \
  --source ai_specs/button-creation-plan.md
```

### plan_status.py

다음 미완료 phase를 확인한다.

```bash
python3 "$ACT_CODEX_PLAN_STATUS" ai_specs/button-creation-plan.md
```

JSON으로 보고 싶으면:

```bash
python3 "$ACT_CODEX_PLAN_STATUS" ai_specs/button-creation-plan.md --json
```

## 권장 사용 흐름

가장 무난한 흐름은 아래와 같다.

1. `act:workflow:spec`
2. `act:workflow:refine-spec`
3. `act:workflow:plan`
4. `act:workflow:work --single-phase`
5. 남은 phase에 대해 `work` 반복
6. 마지막에 `act:workflow:compound`

복잡한 작업일수록 `--single-phase`로 끊어서 진행하는 편이 안정적이다.

## 주의사항

### 1. Claude와 완전히 동일하지는 않다

Codex 버전은 workflow behavior를 맞춘 것이다. Claude의 다음 요소까지 복제하는 것은 아니다.

- slash command 런타임
- Claude hook
- `~/.claude/settings.json` 기반 자동화

### 2. git이 없으면 phase commit은 생략된다

현재 디렉터리가 git 저장소가 아니면 `work` 단계에서 자동 커밋은 하지 않는다.

### 3. main/master에서는 바로 자동 커밋하지 않는 것이 안전하다

ACT 스타일 phase commit을 기대한다면 feature branch에서 작업하는 것이 좋다.

### 4. 검증 명령은 프로젝트마다 다르다

plan에 verify 명령이 있으면 그것을 우선한다. 없으면 프로젝트의 일반적인 테스트/린트/빌드 명령을 사용한다.

## 예시 프롬프트

아래처럼 바로 요청하면 된다.

```text
act:workflow:spec 로그인 기능 추가
```

```text
act:workflow:refine-spec ai_specs/login-spec.md
```

```text
act:workflow:plan ai_specs/login-spec.md
```

```text
act:workflow:work ai_specs/login-plan.md --single-phase
```

```text
act:workflow:compound 로그인 기능 구현 과정 정리
```

## 요약

- Claude용 ACT는 계속 사용 가능하다.
- Codex용은 별도 스킬이다.
- 사용자는 예전 ACT 표현을 그대로 써도 된다.
- Codex는 그 표현을 해당 workflow 모드로 해석해 `ai_specs/`와 `ai_docs/solutions/` 규약에 맞춰 작업한다.
