package com.example.aiagent.workflow;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.domain.CouponEntity;
import com.example.aiagent.domain.OrderEntity;
import com.example.aiagent.domain.OrderStatus;
import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.infra.persistence.CouponRepository;
import com.example.aiagent.infra.persistence.OrderRepository;
import com.example.aiagent.infra.shipping.ShippingApiClient;
import com.example.aiagent.infra.shipping.ShippingApiResponse;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.prompt.Prompt;
import com.example.aiagent.prompt.PromptBuilder;
import com.example.aiagent.rag.PolicyRetriever;
import com.example.aiagent.reflection.ReflectionEngine;
import com.example.aiagent.tool.CouponTool;
import com.example.aiagent.tool.OrderTool;
import com.example.aiagent.tool.PolicyRagTool;
import com.example.aiagent.tool.ShippingTool;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.validator.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerSupportWorkflow 통합 테스트.
 *
 * <p>Agent 코어(Planner Loop → Tool → Prompt → LLM → Validator → Reflection)는
 * <b>실제 코드</b>를 그대로 쓰고, 외부 인프라(DB / 배송 API / Vector DB / Claude)만
 * Mockito 로 대체한다. 이것이 이 프로젝트의 테스트 전략이다.</p>
 */
class CustomerSupportWorkflowTest {

    // --- 외부 인프라 (모두 mock) ---
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final ShippingApiClient shippingApiClient = mock(ShippingApiClient.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final LlmClient llmClient = mock(LlmClient.class);

    private CustomerSupportWorkflow workflow;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.getLoop().setMaxSteps(10);
        properties.getLoop().setMaxReflectionRetries(1);
        properties.getRag().setTopK(3);
        properties.getRag().setSimilarityThreshold(0.5);

        List<Tool> tools = List.of(
                new OrderTool(orderRepository),
                new ShippingTool(shippingApiClient),
                new CouponTool(couponRepository),
                new PolicyRagTool(new PolicyRetriever(vectorStore, properties)));

        workflow = new CustomerSupportWorkflow(
                new Planner(),
                new ToolRegistry(tools),
                new PromptBuilder(),
                llmClient,
                new Validator(),
                new ReflectionEngine(llmClient),
                properties);

        stubHealthyInfrastructure();
    }

    /** 정상 시나리오: 지난주 주문, 아직 출고 전, 쿠폰 사용됨. */
    private void stubHealthyInfrastructure() {
        when(orderRepository.findFirstByCustomerIdOrderByOrderedAtDesc("CUST-1"))
                .thenReturn(Optional.of(new OrderEntity(
                        "ORD-1001", "CUST-1", OrderStatus.ORDERED,
                        LocalDate.now().minusDays(7), "무선 이어폰", 89000L)));

        when(shippingApiClient.findByOrderId("ORD-1001"))
                .thenReturn(Optional.of(new ShippingApiResponse(
                        "ORD-1001", "NOT_SHIPPED", null, "한진택배", null)));

        when(couponRepository.findByOrderId("ORD-1001"))
                .thenReturn(List.of(new CouponEntity(
                        "CPN-500", "ORD-1001", "10% 신규가입 쿠폰", true, true, 9000L)));

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document(
                        "주문에 사용된 쿠폰은 주문이 정상 취소되면 자동으로 복구됩니다.",
                        Map.of("source", "coupon-policy.md"))));
    }

    /** 대표 복합 질의 컨텍스트. */
    private AgentContext compositeContext() {
        return new AgentContext(
                "conv-1", "CUST-1",
                "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?",
                new IntentClassification(
                        List.of(Intent.ORDER_STATUS, Intent.SHIPPING, Intent.REFUND, Intent.COUPON),
                        Intent.REFUND, 0.95, "복합 질의"),
                List.of());
    }

    @Test
    @DisplayName("복합 질의는 4개 Tool 을 의존성 순서대로 실행한다")
    void executesAllToolsInDependencyOrder() {
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("아직 배송이 시작되지 않아 취소 가능하며, 쿠폰은 복구됩니다.");

        AgentResponse response = workflow.execute(compositeContext());

        assertEquals(
                List.of(ToolNames.ORDER, ToolNames.SHIPPING, ToolNames.COUPON, ToolNames.POLICY_RAG),
                response.getExecutedTools());
    }

    @Test
    @DisplayName("근거에 부합하는 답변은 검증을 통과하고 Reflection 이 발생하지 않는다")
    void groundedAnswerPassesWithoutReflection() {
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("아직 배송이 시작되지 않았습니다. 지금 취소하시면 쿠폰도 복구됩니다.");

        AgentResponse response = workflow.execute(compositeContext());

        assertTrue(response.isValidationPassed());
        assertFalse(response.isReflectionTriggered());
        // 재호출이 없어야 한다 = 불필요한 비용 없음
        verify(llmClient, times(1)).complete(any(Prompt.class));
    }

    @Test
    @DisplayName("환각 답변은 Validator 가 잡아내고 Reflection 이 교정한다")
    void hallucinationIsCaughtAndCorrectedByReflection() {
        // 1번째 호출: 실제 상태(NOT_SHIPPED)와 모순되는 환각 답변
        // 2번째 호출(Reflection): 교정된 답변
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("고객님의 상품은 이미 배송이 완료되어 도착했습니다.")
                .thenReturn("확인 결과 아직 배송이 시작되지 않았습니다. 취소 시 쿠폰은 복구됩니다.");

        AgentResponse response = workflow.execute(compositeContext());

        assertTrue(response.isReflectionTriggered(), "환각이 감지되어 Reflection 이 돌아야 한다");
        assertTrue(response.isValidationPassed(), "교정 후 재검증을 통과해야 한다");
        assertTrue(response.getAnswer().contains("아직 배송이 시작되지"));
        verify(llmClient, times(2)).complete(any(Prompt.class));
    }

    @Test
    @DisplayName("재시도해도 계속 환각이면 안전 응답으로 대체한다")
    void fallsBackToSafeAnswerWhenReflectionFails() {
        // 계속 모순된 답변만 생성하는 상황
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("이미 배송이 완료되어 도착했습니다.");

        AgentResponse response = workflow.execute(compositeContext());

        assertTrue(response.isReflectionTriggered());
        assertFalse(response.isValidationPassed());
        // 잘못된 정보를 고객에게 내보내느니 상담원 연결을 안내해야 한다.
        assertTrue(response.getAnswer().contains("상담원"));
        assertFalse(response.getAnswer().contains("배송이 완료"));
    }

    @Test
    @DisplayName("주문이 없으면 배송/쿠폰 Tool 을 건너뛰고 외부 API 를 호출하지 않는다")
    void skipsDependentToolsWhenNoOrder() {
        when(orderRepository.findFirstByCustomerIdOrderByOrderedAtDesc(anyString()))
                .thenReturn(Optional.empty());
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("주문 내역이 확인되지 않습니다.");

        AgentResponse response = workflow.execute(compositeContext());

        assertEquals(List.of(ToolNames.ORDER, ToolNames.POLICY_RAG), response.getExecutedTools());
        // 주문이 없는데 배송 API 를 부르는 것은 낭비다.
        verify(shippingApiClient, org.mockito.Mockito.never()).findByOrderId(anyString());
    }

    @Test
    @DisplayName("추적 로그에 파이프라인 전 단계가 남는다")
    void producesObservableTrace() {
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("아직 배송이 시작되지 않았습니다.");

        AgentResponse response = workflow.execute(compositeContext());

        String trace = String.join("\n", response.getTrace());
        assertTrue(trace.contains("[Planner]"));
        assertTrue(trace.contains("[Tool]"));
        assertTrue(trace.contains("[PromptBuilder]"));
        assertTrue(trace.contains("[LLM]"));
        assertTrue(trace.contains("[Validator]"));
    }
}
