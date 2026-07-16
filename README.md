# AI Agent Toy Project

AI Agent 의 전체 동작 과정(생각 → Tool 호출 → RAG → Validation → Reflection)을
**코드로 이해하기 위한 교육용 Spring Boot 레퍼런스 프로젝트**입니다.

실제 서비스가 아니며, **모든 LLM 호출과 데이터는 Mock** 으로 동작합니다.
외부 인프라(Redis/DB/Kafka/Docker 등)는 전혀 사용하지 않습니다.

## 기술 스택

- Java 21 / Spring Boot 3.3.5 / Gradle
- JUnit5 / Mockito
- Lombok / Jackson

> **Spring AI 관련 안내**
> 요구사항상 "OpenAI 클라이언트는 Mock 처리"이고 실제 API 는 호출하지 않습니다.
> 이 프로젝트는 Spring AI 가 제공하는 것과 동일한 **LLM 추상화(`LLMClient` 인터페이스)** 를
> 직접 정의하고, 그 구현체를 `FakeLLMClient`(고정 응답)로 두었습니다.
> 실제 `spring-ai-*` 아티팩트는 오프라인 빌드 안정성을 위해 의존성에 포함하지 않았으며,
> 학습 관점에서 핵심인 "LLM 을 인터페이스 뒤로 숨기고 Mock 으로 대체" 하는 구조는 그대로 구현되어 있습니다.

## 실행 방법

```bash
# 서버 기동 (기본 포트 8080)
./gradlew bootRun

# 빌드 + 전체 테스트
./gradlew build
```

### REST API 호출

```bash
curl -s http://localhost:8080/api/agent/chat \
  -X POST -H "Content-Type: application/json" \
  -d '{"question":"지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"}'
```

응답에는 최종 답변뿐 아니라 **파이프라인 전체 추적 정보**(intent, workflow,
실행된 tool 목록, validation 통과 여부, reflection 발동 여부, 단계별 trace)가 함께 담깁니다.

## 아키텍처 흐름

```
User
  └─▶ AiGateway (단일 진입점)
        └─▶ AiOrchestrator (전체 조율)
              ├─ IntentClassifier   : Rule 기반 의도 분류 (LLM 미사용)
              ├─ RuleBasedRouter    : Intent → Workflow 선택
              └─▶ Workflow (RefundWorkflow 등)
                    │  ── Agent Loop ──────────────────────────────
                    │   Planner.decideNextStep() 반복
                    │     ├─ OrderTool
                    │     ├─ ShippingTool
                    │     ├─ PolicyRagTool (RAG, Map 기반 Mock)
                    │     └─ CouponTool
                    │   (필요한 Tool 이 모두 실행되면 FINISH)
                    │  ─────────────────────────────────────────────
                    ├─ PromptBuilder  : 수집 정보 → Prompt 조립
                    ├─ FakeLLMClient  : 고정 응답 반환 (Mock)
                    ├─ Validator      : 응답이 실제 배송 상태와 모순되는지 검사
                    └─ ReflectionEngine : 모순 시 Prompt 교정 후 1회 재호출
                          └─▶ AgentResponse
```

## 대표 시나리오로 보는 Validation → Reflection

1. ShippingTool 의 실제 배송 상태 = `NOT_SHIPPED` (아직 출발 전)
2. `FakeLLMClient` 의 **최초 응답**은 일부러 "이미 배송이 완료되어 도착했습니다"라고 답함 → LLM 환각 재현
3. `Validator` 가 실제 상태(`NOT_SHIPPED`)와 모순됨을 감지 → **검증 실패**
4. `ReflectionEngine` 이 실제 상태를 알려주는 교정 지시를 붙여 LLM 을 **1회 재호출**
5. 교정된 응답은 배송 상태와 모순되지 않으므로 **재검증 통과** → 최종 응답 반환

## 패키지 구조

```
com.example.aiagent
 ├── gateway        AiGateway, AgentController (REST)
 ├── orchestrator   AiOrchestrator
 ├── intent         Intent, IntentClassifier
 ├── router         RuleBasedRouter
 ├── workflow       Workflow, AbstractWorkflow, Refund/Shipping/Default
 ├── planner        Planner, ExecutionPlan, PlanStep
 ├── tool           Tool, ToolRegistry, ToolResult + order/shipping/coupon/rag
 ├── prompt         Prompt, PromptBuilder
 ├── llm            LLMClient, FakeLLMClient
 ├── validator      Validator, ValidationResult
 ├── reflection     ReflectionEngine
 ├── domain         Order, Shipping, Coupon, 상태 enum
 ├── dto            ChatRequest/Response, AgentResponse, AgentContext
 └── config         GlobalExceptionHandler
```

## 테스트

```bash
./gradlew test
```

- 컴포넌트별 단위 테스트: IntentClassifier / Planner / Router / 각 Tool / Validator / Reflection
- 흐름 테스트: RefundWorkflow / Orchestrator
- **End-to-End 테스트**(`AgentEndToEndTest`): 실제 Spring 컨텍스트를 띄워
  `POST /api/agent/chat` 한 번으로 전체 파이프라인이 실행되는지 검증
