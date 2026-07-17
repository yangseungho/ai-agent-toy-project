# AI Agent Reference Project

AI Agent 아키텍처(의도분류 → 라우팅 → 계획 → Tool 호출 → RAG → 답변생성 → 검증 → Reflection)를
**실제 외부 연동 위에서** 구현한 Spring Boot 레퍼런스 프로젝트입니다.

Mock 구현체는 없습니다. 모든 I/O 는 진짜입니다.
도메인만 교체하면 실전에 그대로 컨버팅할 수 있는 구조를 목표로 합니다.

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| Framework | Java 21, Spring Boot 3.5.16, Gradle |
| LLM | **Anthropic Claude** (`claude-opus-4-8`) — 공식 Java SDK `com.anthropic:anthropic-java` |
| RDB | PostgreSQL + Spring Data JPA (주문 / 쿠폰 / 대화이력) |
| Vector DB | **pgvector** (Spring AI `PgVectorStore`) |
| Embedding | 로컬 ONNX `all-MiniLM-L6-v2` (384차원, Spring AI transformers) |
| 외부 API | Spring `RestClient` (배송사 API) |
| Test | JUnit5 + Mockito (외부 인프라만 mocking) |

> **왜 Claude + Spring AI 를 섞어 쓰나요?**
> Claude(Anthropic)는 **임베딩 모델을 제공하지 않습니다.** 그래서 역할을 나눴습니다.
> - **채팅/추론(의도분류·답변생성·Reflection)** → Anthropic 공식 Java SDK 로 직접 호출
> - **임베딩 + Vector Store(pgvector)** → Spring AI
>
> 임베딩은 별도 API Key 가 필요 없도록 로컬 ONNX 모델을 씁니다.
> 관리형 임베딩 API로 바꾸려면 `application.yaml` 의 embedding 설정과 `dimensions` 만 교체하면 됩니다.

---

## 실행에 필요한 외부 인프라 (3가지)

### 1. PostgreSQL + pgvector

RDB(주문/쿠폰/대화이력)와 Vector DB(정책 문서 임베딩)를 **한 인스턴스로** 사용합니다.

```bash
docker run -d --name agent-pg -p 5432:5432 \
  -e POSTGRES_DB=agent -e POSTGRES_USER=agent -e POSTGRES_PASSWORD=agent \
  pgvector/pgvector:pg16
```

> ⚠️ 반드시 `pgvector/pgvector` 이미지를 쓰세요. 일반 `postgres` 이미지는 `CREATE EXTENSION vector` 가 실패합니다.
> 테이블(`orders`, `coupons`, `conversation_messages`)과 `vector_store` 테이블은 기동 시 자동 생성되고 데모 데이터도 자동 주입됩니다.

### 2. Anthropic API Key

```bash
export ANTHROPIC_API_KEY=sk-ant-...
```

### 3. 외부 배송사 API (선택)

`ShippingTool` 이 호출하는 외부 REST API입니다. 없어도 서버는 뜹니다 —
호출 실패는 "배송 정보 확인 불가"라는 **사실**로 처리되고 대화는 계속됩니다.

```
GET {base-url}/shipments?orderId=ORD-1001
→ { "orderId":"ORD-1001", "status":"NOT_SHIPPED",
    "trackingNumber":null, "carrier":"한진택배", "estimatedDelivery":null }
```

연결 정보는 전부 `application.yaml` 의 `agent.*` 에 있습니다.

---

## 실행

```bash
./gradlew bootRun     # 서버 기동 (8080)
./gradlew build       # 빌드 + 전체 테스트 (외부 인프라 불필요)
```

### API 호출

```bash
curl -s http://localhost:8080/api/agent/chat -X POST \
  -H "Content-Type: application/json" \
  -d '{
        "conversationId": "conv-1",
        "customerId": "CUST-1",
        "question": "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
      }'
```

`conversationId` 를 같게 유지하면 멀티턴 대화가 이어집니다.

### RAG 디버깅용 엔드포인트

```bash
curl -X POST localhost:8080/api/admin/rag/ingest                      # 정책 문서 재색인
curl -X POST "localhost:8080/api/admin/rag/search?query=쿠폰 복구"     # 벡터 검색 결과 확인
```

---

## 아키텍처

```
User
 └─▶ AgentController → AiGateway (단일 진입점, 인증/검증 자리)
       └─▶ AiOrchestrator ─ 공통 절차 지휘
             ├─ 1. ConversationMemory.load()      → PostgreSQL (이전 대화 맥락)
             ├─ 2. IntentClassifier               → Claude 구조화 출력 (복합 의도 다중 라벨)
             ├─ 3. RuleBasedRouter                → primaryIntent 로 Workflow 선택 (규칙)
             ├─ 4. Workflow.execute()
             │     │
             │     ├── Planner ─ 감지된 모든 의도의 Tool 을 합집합으로 계산
             │     │
             │     ├── ┌─ Agent Loop ──────────────────────────────┐
             │     │   │  Planner.decideNextStep()  ← 결과 보고 재결정 │
             │     │   │    ├─ OrderTool      → PostgreSQL (JPA)   │
             │     │   │    ├─ ShippingTool   → 외부 REST API      │
             │     │   │    ├─ CouponTool     → PostgreSQL (JPA)   │
             │     │   │    └─ PolicyRagTool  → pgvector 유사도 검색 │
             │     │   │  (FINISH 나올 때까지 반복)                  │
             │     │   └────────────────────────────────────────────┘
             │     │
             │     ├── PromptBuilder  → 사실(DB/API) + 규칙(RAG) + 이력 조립
             │     ├── ClaudeLlmClient → Claude 호출 (adaptive thinking)
             │     ├── Validator      → 답변 ↔ 실제 데이터 모순 검사 (결정론적)
             │     └── ReflectionEngine → 모순 시 교정 지시 후 재생성 (횟수 제한)
             │
             └─ 5. ConversationMemory.save()      → PostgreSQL
```

---

## 핵심 설계 포인트

### 1. Intent 분류는 LLM, 라우팅은 규칙

키워드 규칙은 **복합 질의를 못 다룹니다.** "취소하면 쿠폰 돌려받나요?" 에는 '취소'와 '쿠폰'이
모두 있어 enum 하나를 고를 수 없습니다. 그래서 분류는 모델이 하고 **여러 의도를 동시에** 반환합니다.

```
"지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
 → intents: [ORDER_STATUS, SHIPPING, REFUND, COUPON]   ← 다중 라벨
 → primaryIntent: REFUND                                ← 라우팅 기준
```

구조화 출력(Structured Output)을 쓰므로 응답은 항상 스키마를 만족합니다 — 자유 텍스트 파싱 실패가 없습니다.

반면 **라우팅은 규칙**입니다. 어떤 코드 경로를 탈지는 예측·테스트 가능해야 하고
모델이 매번 다르게 정하면 안 됩니다.

### 2. Planner 는 한 번만 호출되지 않는다

Tool 결과를 보고 다음 행동을 다시 정합니다. 이게 Agent 와 단순 파이프라인의 차이입니다.

- 여러 의도의 Tool 을 **합집합**으로 모읍니다.
- Tool 간 **데이터 의존성**을 지킵니다 (OrderTool → orderId → ShippingTool/CouponTool).
- 주문을 못 찾으면 배송/쿠폰 조회를 **건너뜁니다** (불필요한 외부 호출 제거).

### 3. Validator + Reflection = 환각 방지

LLM 은 근거를 줘도 지어냅니다. 특히 사용자가 원하는 답이 뻔한 질문에서요.

- **Validator**: 답변이 실제 배송 상태/주문번호와 모순되는지 **결정론적으로** 검사합니다.
  여기서 LLM 을 또 쓰지 않는 이유는 비용도 있지만, **검증기 자체가 환각하면 안 되기** 때문입니다.
- **Reflection**: 실패 시 "실제 배송 상태는 NOT_SHIPPED 다"처럼 **구체적 사실**을 못 박아 재생성합니다.
- 재시도해도 실패하면 잘못된 정보를 내보내는 대신 **상담원 연결 안내**로 대체합니다.

### 4. 외부 I/O 실패가 대화를 죽이지 않는다

Tool 은 예외를 던지지 않고 `ToolResult.failure` 로 감쌉니다. 그리고 **"조회 실패"를 프롬프트에
사실로 전달**합니다. 이걸 숨기면 모델이 빈칸을 그럴듯하게 채웁니다.

---

## 테스트 전략

```bash
./gradlew test     # 60개 테스트, 외부 인프라 불필요
```

**Mock 은 오직 외부 인프라 경계에만** 둡니다 (PostgreSQL / 배송 API / pgvector / Claude API).
그 사이의 Agent 로직은 전부 실제 코드가 실행됩니다.

| 테스트 | 검증 대상 |
|---|---|
| `IntentClassifierTest` | 복합 의도 반환, 이력 주입, LLM 장애 시 graceful degradation |
| `PlannerTest` | 의도별 Tool 합집합, 결과 기반 적응(주문 없으면 스킵) |
| `OrderTool/CouponTool/ShippingToolTest` | DB·외부 API 정상/없음/장애 3가지 경로 |
| `PolicyRagToolTest` | 벡터 검색 근거 구성, topK/임계값, Vector DB 장애 |
| `ValidatorTest` | 환각(배송상태·주문번호 위조) 차단 |
| `ReflectionEngineTest` | 교정 지시에 실제 사실 주입 |
| `CustomerSupportWorkflowTest` | Agent Loop 전체 |
| **`AgentPipelineEndToEndTest`** | **Gateway→응답 전 구간 (가장 중요)** |
| `AgentControllerTest` | HTTP 계약 |

---

## 패키지 구조

```
com.example.aiagent
 ├── gateway        AiGateway, AgentController
 ├── orchestrator   AiOrchestrator          — 공통 절차 지휘
 ├── intent         IntentClassifier        — LLM 다중 라벨 분류
 ├── router         RuleBasedRouter         — 규칙 라우팅
 ├── workflow       AbstractAgentWorkflow   — Agent Loop 엔진
 ├── planner        Planner, ExecutionPlan  — 계획/적응
 ├── tool           Order/Shipping/Coupon/PolicyRag
 ├── rag            Ingestion / Retriever   — pgvector
 ├── memory         ConversationMemory      — 멀티턴
 ├── prompt         PromptBuilder           — 근거 조립
 ├── llm            LlmClient / ClaudeLlmClient
 ├── validator      Validator               — 가드레일
 ├── reflection     ReflectionEngine
 ├── domain         JPA 엔티티
 ├── infra          persistence / shipping  — 외부 연동
 └── config         AgentProperties 등
```

---

## 실전 전환 시 반드시 바꿔야 할 것

이 프로젝트는 학습용이라 아래를 의도적으로 단순화했습니다.

1. **`customerId` 를 request body 로 받습니다.** 실전에서는 반드시 인증 토큰(JWT subject)에서
   꺼내야 합니다. 지금 구조는 남의 주문을 조회할 수 있습니다.
2. **`ddl-auto: update`** — Flyway/Liquibase + `validate` 로 교체하세요.
3. **기동 시 RAG 자동 색인** — 인스턴스가 여러 대면 중복 색인됩니다. 별도 배치로 분리하세요.
4. **`/api/admin/rag/**` 인증 없음** — 인가를 붙이고 외부 노출을 막으세요.
5. **trace 를 응답에 노출** — 로그/APM 으로만 보내고 고객 응답에는 `answer` 만 남기세요.
6. **재시도/서킷브레이커 없음** — Resilience4j 등을 붙이면 좋습니다.
