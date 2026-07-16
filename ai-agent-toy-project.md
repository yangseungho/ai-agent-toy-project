# AI Agent Toy Project

## 목표

이 프로젝트의 목적은 실제 서비스를 만드는 것이 아니라 **AI Agent의 전체 동작 과정을 학습하기 위한 교육용(Spring Boot) 레퍼런스 프로젝트**이다.

따라서 코드의 완성도보다 **구조와 흐름을 이해하기 쉽게 작성하는 것**을 가장 중요한 목표로 한다.

---

# 기술 스택

반드시 아래 기술을 사용한다.

- Java 21
- Spring Boot 3.x
- Gradle
- JUnit5
- Mockito
- Spring AI (OpenAI Client는 Mock 처리)
- Lombok
- Jackson

외부 인프라는 사용하지 않는다.

절대 사용하지 말 것

- Redis
- Kafka
- RabbitMQ
- Docker
- Kubernetes
- PostgreSQL
- MySQL
- ElasticSearch
- LangGraph
- LangChain
- MCP
- Temporal

---

# 프로젝트 목적

AI Agent가

질문을 받아

어떻게

생각(Planning)

하고

Tool을 호출하고

RAG를 수행하고

Validation과 Reflection을 수행하는지

전체 Architecture를 코드로 이해하는 것이 목적이다.

실제 OpenAI API는 호출하지 않는다.

LLM 역시 Mock 객체를 이용한다.

---

# 요구사항

아래 Architecture를 모두 구현한다.

```

                   User

                     │

                     ▼

              AI Gateway

                     │

                     ▼

           AI Orchestrator

                     │

                     ▼

        Intent Classification

                     │

                     ▼

            Rule-based Router

                     │

                     ▼

                Workflow

                     │

                     ▼

                 Planner

                     │

        ┌───────────┼─────────────┐

        ▼           ▼             ▼

     Order Tool  Shipping Tool    RAG

        │           │             │

        └───────────┼─────────────┘

                    ▼

              Planner 판단

          추가 Tool 필요한가?

          YES                NO

           │                  │

           ▼                  ▼

       다음 Tool         Prompt Builder

                             │

                             ▼

                           LLM

                             │

                             ▼

                        Validator

                             │

                       이상 있는가?

                  YES                  NO

                    │                   │

                    ▼                   ▼

               Reflection          Response

```

모든 계층을 코드로 구현한다.

생략하지 않는다.

---

# 구현 원칙

실제 AI API는 사용하지 않는다.

모든 LLM 호출은 FakeLLMClient를 구현한다.

예를 들어

```java
interface LLMClient {

    String complete(Prompt prompt);

}
```

Mock 결과를 반환하도록 한다.

예를 들면

```
배송이 아직 시작되지 않았습니다.
```

같은 문자열을 반환하면 된다.

---

# Domain

도메인은 최대한 단순하게 한다.

쇼핑몰 주문 시스템으로 한다.

사용자는 다음과 같은 질문을 한다.

```
지난주 주문한 상품이 아직 안왔는데
취소하면 쿠폰도 돌려받을 수 있나요?
```

이 질문을 처리하기 위해

다음 Tool을 구현한다.

- OrderTool
- ShippingTool
- CouponTool
- PolicyRagTool

모두 Mock 데이터를 반환한다.

예를 들어

OrderTool

```
Order
- OrderId
- Status
- OrderedAt
```

ShippingTool

```
Shipping
- Status
- TrackingNumber
```

CouponTool

```
Coupon
- Used
- Recoverable
```

PolicyRagTool

```
문서를 검색하여
환불 정책을 반환
```

실제 문서는 필요 없다.

Map<String,String> 정도로 구현한다.

---

# Planner

Planner는

질문을 분석하여

실행 계획을 만든다.

예를 들어

```
Step1
OrderTool

Step2
ShippingTool

Step3
PolicyRagTool

Step4
CouponTool

Step5
Generate Answer
```

ExecutionPlan 클래스를 만든다.

Planner는

ExecutionPlan을 반환한다.

---

# Agent Loop

Planner는

한 번만 호출되지 않는다.

Tool 결과를 받은 뒤

Planner가

다음 Tool을 결정한다.

예를 들어

```
Planner

↓

OrderTool

↓

Planner

↓

ShippingTool

↓

Planner

↓

CouponTool

↓

Planner

↓

Finish
```

Loop 형태로 구현한다.

---

# Intent Classification

LLM을 사용하지 않는다.

간단한 Rule 기반으로 구현한다.

예를 들면

```
배송

→ SHIPPING
```

```
환불

→ REFUND
```

```
회원

→ ACCOUNT
```

등으로 구현한다.

---

# Rule-based Router

Intent에 따라

Workflow를 선택한다.

예를 들어

```
REFUND

↓

RefundWorkflow
```

---

# Workflow

Workflow 인터페이스를 만든다.

```
interface Workflow {

    AgentResponse execute(...)

}
```

RefundWorkflow

ShippingWorkflow

등으로 구현한다.

---

# Tool Calling

Tool 인터페이스를 만든다.

```
Tool

execute()
```

OrderTool

ShippingTool

CouponTool

PolicyRagTool

모두 구현한다.

---

# Prompt Builder

Planner가 모은 정보를

Prompt 객체로 만든다.

Prompt 클래스도 구현한다.

---

# Fake LLM

FakeLLMClient를 만든다.

Prompt를 입력받아

고정된 응답을 반환한다.

---

# Validator

LLM 응답이

Order 상태와 모순되는지 검사한다.

예를 들어

OrderStatus

```
NOT_SHIPPED
```

인데

LLM이

```
배송기사가 출발했습니다.
```

라고 말하면

Validation 실패.

---

# Reflection

Validation 실패 시

Reflection이 실행된다.

Reflection은

Prompt를 수정하여

LLM을 다시 호출한다.

Retry는 최대 1회.

---

# AI Orchestrator

전체 흐름을 관리한다.

AI Gateway는

Orchestrator만 호출한다.

모든 흐름은

Orchestrator를 중심으로 진행한다.

---

# 테스트

외부 API 테스트는 하지 않는다.

JUnit만 사용한다.

각 컴포넌트마다 테스트를 만든다.

예를 들어

- PlannerTest
- IntentClassifierTest
- RouterTest
- OrderToolTest
- ShippingToolTest
- CouponToolTest
- RagToolTest
- ValidatorTest
- ReflectionTest
- WorkflowTest
- OrchestratorTest

그리고

가장 중요한

End-to-End Test를 만든다.

```
User Question

↓

Gateway

↓

Orchestrator

↓

Planner

↓

Tool

↓

Planner

↓

Tool

↓

Prompt Builder

↓

FakeLLM

↓

Validator

↓

Reflection

↓

Response
```

위 흐름이 모두 실행되는 테스트를 작성한다.

---

# 프로젝트 구조

예시)

```
src

 ├── gateway
 ├── orchestrator
 ├── planner
 ├── workflow
 ├── router
 ├── intent
 ├── tool
 │     ├── order
 │     ├── shipping
 │     ├── coupon
 │     └── rag
 ├── prompt
 ├── llm
 ├── validator
 ├── reflection
 ├── domain
 ├── dto
 ├── config
 └── test
```

---

# 코드 스타일

중요하다.

교육용 프로젝트이므로

코드는 최대한 읽기 쉽게 작성한다.

복잡한 Generic

복잡한 Functional Programming

과도한 Stream 사용

과도한 디자인 패턴은 사용하지 않는다.

주석을 적극적으로 작성한다.

각 클래스의 역할을 JavaDoc으로 설명한다.

---

# 최종 목표

프로젝트를 실행하면

Spring Boot 서버가 정상적으로 기동되어야 한다.

REST API를 하나 제공한다.

```
POST /api/agent/chat
```

Body

```
{
    "question":"지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
}
```

Mock 데이터 기반으로

AI Agent 전체 Pipeline이 실행되어

Response를 반환한다.

이 프로젝트는 실제 AI 서비스가 아니라

AI Agent Architecture를 이해하기 위한 학습용 레퍼런스 프로젝트이다.

모든 계층을 생략 없이 구현한다.


# 구현 순서

한 번에 모든 코드를 생성하지 않는다.

아래 순서대로 진행한다.

1. 프로젝트 생성
2. Domain 및 DTO
3. Intent Classification
4. Rule-based Router
5. Workflow
6. Planner
7. Tool 구현
8. Agent Loop
9. Prompt Builder
10. Fake LLM
11. Validator
12. Reflection
13. AI Orchestrator
14. REST API
15. 테스트 코드
16. 프로젝트 실행 및 빌드 검증

각 단계마다 컴파일이 가능한 상태를 유지하며 진행한다.
빌드가 깨지는 코드를 작성하지 않는다.
