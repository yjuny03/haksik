# 학식목장 — 대학생 위치 기반 식사 매칭 서비스

> 혼밥이 흔해진 대학생들이 근처에서 함께 밥 먹을 사람을 찾고,
> **매칭 → 채팅 → 약속 → 식당 예약 → 리뷰**까지 한 흐름으로 이어 가는 캠퍼스 서비스

| | |
|---|---|
| 기간 | 2026.05.07 ~ 06.28 (국비 교육 팀 프로젝트) · 이후 개인 개선 |
| 인원 | 4명 — 유준영(팀장), 최진우, 전정수, 원태규 |
| 원본 저장소 | [project-mokjang/haksik](https://github.com/project-mokjang/haksik) |
| 이 저장소 | 원본을 포크해 개인 개선 작업을 이어 간 저장소입니다 |

## 목차
1. [기획 배경](#기획-배경)
2. [주요 기능](#주요-기능)
3. [기술 스택](#기술-스택)
4. [팀 구성과 역할](#팀-구성과-역할)
5. [내가 맡은 기능](#내가-맡은-기능)
6. [패키지 구조](#패키지-구조)
7. [프로젝트 이후 개선: 단체 매칭 동시 요청 문제](#프로젝트-이후-개선-단체-매칭-동시-요청-문제)
8. [실행 방법](#실행-방법)

---

## 기획 배경
혼자 하는 문화가 늘면서 대학생들도 혼자 밥을 먹는 일이 많아졌습니다.
같은 학교 학생끼리 가까운 곳에서 함께 밥을 먹으며 어울릴 수 있도록 **위치 기반 식사 매칭**에서 시작해, 채팅·약속 조율·식당 예약·커뮤니티로 범위를 넓혔습니다.
학교 웹메일 인증으로 가입한 학생만 이용하는 폐쇄형 서비스입니다.

## 주요 기능

**사용자**
- **위치 기반 매칭** — 지도에서 내 위치 기준 반경 안의 상대를 찾고, 식사(1:1·단체)·소개팅·과팅 목적별로 매칭
- **채팅·약속** — 매칭이 성사되면 채팅방 자동 생성, 채팅방 안에서 시간·장소 확정 폼과 투표
- **식당 예약·리뷰** — 학교 주변 식당 예약 신청, 방문 후 리뷰
- **신뢰 시스템** — 상호 평가 기반 매너 점수, 노쇼 누적 시 이용 제한, 활동 배지
- **커뮤니티** — 학교 전용 게시판과 전체 대학생 게시판, 댓글·이미지 첨부
- **회원** — 학교 웹메일 인증 가입, 로그인 5회 실패 시 30분 잠금

**점주** — 가게·메뉴 등록, 영업 상태·품절 관리, 예약 수락·거절, 리뷰 관리

**관리자** — 회원 관리, 점주 가입 승인·반려, 채팅·게시글·리뷰 신고 처리

**이후 추가** — AI 신고 판정·식당 추천 (Claude API)

## 기술 스택

| 구분 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, QueryDSL |
| 실시간 | WebSocket(채팅), SSE(알림) |
| Database | MariaDB |
| Frontend | Thymeleaf, HTML, CSS, JavaScript |
| 외부 API | Naver Maps API, Google SMTP(메일 인증), Claude API |
| Infra | AWS EC2, RDS |
| Tools | Git, GitHub, IntelliJ, Gradle |

## 팀 구성과 역할

| 이름 | 담당 |
|---|---|
| **유준영 (팀장)** | 로그인(Spring Security), 네이버 지도 연결, 위치 기반 매칭, 관리자 페이지(회원·점주·신고), 신뢰 시스템(매너 점수 차감·잠금), 공통 UI와 전체 프레임 설계 |
| 최진우 | 커뮤니티(게시글·댓글), 점주 페이지(가게·메뉴·예약), 식당 리뷰, 알림, 이메일 인증, 약관 동의 |
| 전정수 | 실시간 채팅, 지도 기반 장소 약속 폼, 사용자 리뷰, 배지, 사용자 회원가입, 상대 평가(신뢰 시스템) |
| 원태규 | 프론트 디자인, 아이디·비밀번호 찾기, 점주 회원가입, 마이페이지, AI 챗봇, 학교 목록 저장 |

## 내가 맡은 기능

### 위치 기반 매칭
```
대기 등록 ─▶ 매칭 신청 ─▶ 수락 / 거절 / 취소 ─▶ 성사 시 채팅방 생성
    └──────────── 시간이 지나면 대기 자동 만료 (1분 주기 스케줄러) ──┘
```
- 목적별 매칭: 식사 1:1, 학식 단체, 소개팅, 과팅
- 흐름이 어긋나는 상황을 서버에서 검사해 이유와 함께 막음
  - 자기 자신에게 신청 / 같은 모집방 중복 신청 / 같은 사용자끼리 진행 중인 요청 중복
  - 단체방 정원 초과 / 모집자의 다른 매칭 신청 / 이미 처리된 요청 / 확정된 매칭이 있는데 새 대기 시작

### 관리자
회원 목록·상세, 점주 가입 승인·반려, 신고 처리 화면과 API

### 로그인·신뢰 시스템
Spring Security 로그인, 로그인 실패 누적 시 계정 잠금, 매너 점수 차감

### 팀장으로서
공통 UI와 전체 프레임 설계, Git 브랜치 관리(엉킨 브랜치 이력 복구), 계층별 패키지를 **기능별 패키지**로 재구성

## 패키지 구조
기능별로 패키지를 나누고, 그 안에 controller · service · repository · domain · dto를 두었습니다.
(처음에는 계층별로 나눈 뒤 그 안을 기능별로 나눴는데, 기능 하나를 고칠 때 여러 폴더를 오가야 해서 바꿨습니다.)

```
com.example.haksikmokjang
├── matching        # 위치 기반 매칭 (matchingwaiting, matchingrequest)
├── member          # 회원·가입·약관·배지·신뢰 시스템
├── admin           # 관리자 (회원, 신고, 대시보드)
├── chat            # 채팅방·메시지·약속 폼
├── community       # 게시글·댓글
├── ownerpage       # 점주 가게·메뉴·예약
├── notification    # 알림 (SSE)
├── report          # 신고
├── ai              # AI 신고 판정·식당 추천
├── chatbot, school, fileattachment, view
└── global          # 공통 응답·예외·보안 설정
```

---

## 프로젝트 이후 개선: 단체 매칭 동시 요청 문제

> 브랜치 [`fix/matching-concurrency`](https://github.com/yjuny03/haksik/tree/fix/matching-concurrency) · 커밋 `9d7927d`

### 문제
단체 학식 모집방은 정원을 확인한 뒤 인원을 1 늘리는 구조였지만, **두 과정 사이에 잠금이 없었습니다.**
같은 모집방에 수락·신청이 동시에 들어오면 서로의 변경을 보지 못한 채 처리될 수 있었습니다.

### 재현
동시 요청 테스트로 직접 확인했습니다. (정원 3명 = 남은 자리 2개, **동시 10건 × 20라운드**)

| 시나리오 | 결과 (수정 전) |
|---|---|
| 동시 수락 — MariaDB 12 기본 설정 | 매 라운드 **10건 중 9건이 DB 오류**로 실패, 자리가 남았는데 1명만 수락 |
| 동시 수락 — MySQL·이전 MariaDB와 같은 동작<br>(`innodb_snapshot_isolation=OFF`) | 매 라운드 **자리 2개에 10명 수락**, 인원 수는 2로 고정(갱신 손실), **채팅방 10개로 분리** |
| 같은 사람의 동시 중복 신청 | 매 라운드 **중복 신청 저장** (10번 신청에 9~10건) |

DB 설정에 따라 증상은 달랐지만 원인은 같았습니다: **서버 코드가 동시 요청을 고려하지 않음.**

### 해결 — 모집방 단위 비관적 락
- 모집방 행을 `SELECT ... FOR UPDATE`로 잠그는 조회를 추가하고, 신청·수락·거절·신청 취소·확정 취소가 **시작하자마자** 모집방을 잠그도록 변경
- 같은 방에 요청이 몰리는 구조라, 충돌 시 재시도가 필요한 낙관적 락보다 줄 세워 처리하는 비관적 락을 선택

**잠금을 "맨 처음"에 거는 이유**
잠금 전에 매칭을 먼저 읽으면 JPA 영속성 컨텍스트에 그 시점 상태가 남고, 기본 격리 수준(REPEATABLE READ)에서는 첫 조회 시점의 스냅샷을 계속 봅니다. 그러면 잠금을 얻은 뒤에도 앞 요청이 커밋한 인원 증가·채팅방 생성·신청 저장이 보이지 않습니다.
그래서 매칭이 속한 모집방 ID만 먼저 조회해 잠그고, 해당 메서드는 READ COMMITTED로 직전 트랜잭션의 커밋 결과를 읽도록 했습니다.

함께 고친 것: 정원이 찬 방에 늦게 온 요청이 "상대방이 대기 상태가 아닙니다"로 안내되던 것을 **"정원이 가득 찼습니다"**로 바로잡음 (정원 확인을 상태 확인보다 먼저)

### 결과 (같은 테스트, 두 DB 설정 모두 동일)

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| 동시 수락 | 9건 서버 오류 / 또는 10명 수락·채팅방 10개 | **정확히 2명 수락, 인원 3/3, 채팅방 1개**, 나머지 8건은 "정원 마감" 안내 |
| 동시 중복 신청 | 9~10건 저장 | **1건만 저장**, 나머지 9건은 "이미 신청함" 안내 |

| 테스트 | 내용 |
|---|---|
| `GroupMealMatchingConcurrencyTest` | 동시 수락·동시 중복 신청 재현 (20라운드 × 동시 10건) |
| `MatchingRequestFlowTest` | 잠금을 넣은 메서드의 단일 요청 동작 (거절, 신청 취소, 확정 취소, 정원 마감 등 6개) |

바뀐 코드: [`MatchingWaitingRepository`](src/main/java/com/example/haksikmokjang/matching/matchingwaiting/repository/MatchingWaitingRepository.java) · [`MatchingRepository`](src/main/java/com/example/haksikmokjang/matching/matchingrequest/repository/MatchingRepository.java) · [`MatchingRequestService`](src/main/java/com/example/haksikmokjang/matching/matchingrequest/service/MatchingRequestService.java) · [테스트](src/test/java/com/example/haksikmokjang/matching/concurrency/)

---

## 실행 방법

**필요 환경**: Java 21, MariaDB

1. MariaDB에 `project_mokjang` 데이터베이스를 만들고, `application.properties`의 datasource 계정을 본인 환경에 맞게 수정합니다.
2. `src/main/resources/application-local.properties`를 만들고 아래 값을 채웁니다. (Git에 올라가지 않는 파일)
   ```properties
   naver.map.client-id=네이버_지도_클라이언트_ID
   spring.mail.username=메일_계정
   spring.mail.password=메일_앱_비밀번호
   ai.provider=mock          # 실제 AI 대신 고정 응답 사용
   ```
3. 실행
   ```bash
   ./gradlew bootRun
   ```

**동시성 테스트**
```bash
./gradlew test --tests 'com.example.haksikmokjang.matching.concurrency.*'

# MySQL·이전 MariaDB와 같은 동작으로 확인할 때
CONCURRENCY_SESSION_VARS=innodb_snapshot_isolation=OFF ./gradlew cleanTest test --tests 'com.example.haksikmokjang.matching.concurrency.*'
```
테스트는 개발 DB와 분리된 `haksik_concurrency_test` 스키마를 자동으로 만들어 쓰며, 실제 트랜잭션·잠금을 확인하기 위해 롤백하지 않고 커밋합니다.
