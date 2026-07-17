package com.example.aiagent.e2e;

import com.example.aiagent.config.AgentProperties;
import com.example.aiagent.domain.CouponEntity;
import com.example.aiagent.domain.OrderEntity;
import com.example.aiagent.domain.OrderStatus;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.dto.ChatRequest;
import com.example.aiagent.gateway.AiGateway;
import com.example.aiagent.infra.persistence.CouponRepository;
import com.example.aiagent.infra.persistence.OrderRepository;
import com.example.aiagent.infra.shipping.ShippingApiClient;
import com.example.aiagent.infra.shipping.ShippingApiResponse;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.intent.IntentClassifier;
import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.memory.ChatMessage;
import com.example.aiagent.memory.ConversationMemory;
import com.example.aiagent.orchestrator.AiOrchestrator;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.prompt.Prompt;
import com.example.aiagent.prompt.PromptBuilder;
import com.example.aiagent.rag.PolicyRetriever;
import com.example.aiagent.reflection.ReflectionEngine;
import com.example.aiagent.router.RuleBasedRouter;
import com.example.aiagent.tool.CouponTool;
import com.example.aiagent.tool.OrderTool;
import com.example.aiagent.tool.PolicyRagTool;
import com.example.aiagent.tool.ShippingTool;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.validator.Validator;
import com.example.aiagent.workflow.CustomerSupportWorkflow;
import com.example.aiagent.workflow.PolicyQnaWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-End 테스트 — 가장 중요한 테스트.
 *
 * <p>Gateway 부터 최종 응답까지 <b>모든 계층의 실제 코드</b>가 한 번의 요청으로
 * 이어져 동작하는지 검증한다.</p>
 *
 * <pre>
 * User → Gateway → Orchestrator → Memory 로드 → IntentClassifier(LLM)
 *      → Router → Workflow → Planner Loop
 *          → OrderTool(DB) → ShippingTool(외부 API) → CouponTool(DB) → PolicyRagTool(Vector DB)
 *      → PromptBuilder → LLM → Validator → Reflection → Memory 저장 → Response
 * </pre>
 *
 * <p><b>Mock 은 오직 외부 인프라 경계에만</b> 있다.
 * (PostgreSQL / 외부 배송 API / pgvector / Claude API)
 * 그 사이의 Agent 로직은 전부 실제 코드가 실행된다.</p>
 */
class AgentPipelineEndToEndTest {

    // --- 외부 인프라 경계 (여기만 mock) ---
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final ShippingApiClient shippingApiClient = mock(ShippingApiClient.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final ConversationMemory memory = mock(ConversationMemory.class);

    private AiGateway gateway;

    private static final String QUESTION =
            "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?";

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.getLoop().setMaxSteps(10);
        properties.getLoop().setMaxReflectionRetries(1);
        properties.getRag().setTopK(3);
        properties.getRag().setSimilarityThreshold(0.5);

        // --- 실제 Agent 코어를 그대로 조립한다 ---
        List<Tool> tools = List.of(
                new OrderTool(orderRepository),
                new ShippingTool(shippingApiClient),
                new CouponTool(couponRepository),
                new PolicyRagTool(new PolicyRetriever(vectorStore, properties)));

        Planner planner = new Planner();
        ToolRegistry registry = new ToolRegistry(tools);
        PromptBuilder promptBuilder = new PromptBuilder();
        Validator validator = new Validator();
        ReflectionEngine reflection = new ReflectionEngine(llmClient);

        CustomerSupportWorkflow supportWorkflow = new CustomerSupportWorkflow(
                planner, registry, promptBuilder, llmClient, validator, reflection, properties);
        PolicyQnaWorkflow policyWorkflow = new PolicyQnaWorkflow(
                planner, registry, promptBuilder, llmClient, validator, reflection, properties);

        RuleBasedRouter router = new RuleBasedRouter(supportWorkflow, policyWorkflow);
        IntentClassifier classifier = new IntentClassifier(llmClient);
        AiOrchestrator orchestrator = new AiOrchestrator(memory, classifier, router);

        gateway = new AiGateway(orchestrator);

        stubExternalInfrastructure();
    }

    /** 외부 시스템이 정상 동작하는 상황을 재현한다. */
    private void stubExternalInfrastructure() {
        when(memory.load(anyString())).thenReturn(List.of());

        // PostgreSQL: 지난주 주문, 아직 배송 준비 중
        when(orderRepository.findFirstByCustomerIdOrderByOrderedAtDesc("CUST-1"))
                .thenReturn(Optional.of(new OrderEntity(
                        "ORD-1001", "CUST-1", OrderStatus.ORDERED,
                        LocalDate.now().minusDays(7), "무선 이어폰", 89000L)));

        // 외부 배송사 API: 아직 출고 전
        when(shippingApiClient.findByOrderId("ORD-1001"))
                .thenReturn(Optional.of(new ShippingApiResponse(
                        "ORD-1001", "NOT_SHIPPED", null, "한진택배", null)));

        // PostgreSQL: 쿠폰 사용됨 + 복구 가능
        when(couponRepository.findByOrderId("ORD-1001"))
                .thenReturn(List.of(new CouponEntity(
                        "CPN-500", "ORD-1001", "10% 신규가입 쿠폰", true, true, 9000L)));

        // pgvector: 관련 정책 문서 검색됨
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document("주문 취소는 배송 시작 전까지 가능합니다.",
                                Map.of("source", "refund-policy.md")),
                        new Document("주문에 사용된 쿠폰은 주문이 정상 취소되면 자동으로 복구됩니다.",
                                Map.of("source", "coupon-policy.md"))));

        // Claude: 의도 분류 (구조화 출력)
        when(llmClient.completeStructured(anyString(), anyString(), any()))
                .thenReturn(new IntentClassification(
                        List.of(Intent.ORDER_STATUS, Intent.SHIPPING, Intent.REFUND, Intent.COUPON),
                        Intent.REFUND, 0.95,
                        "배송 지연 확인과 취소 가능 여부, 쿠폰 복구를 함께 묻는 복합 질의"));
    }

    @Test
    @DisplayName("복합 질의 하나로 전체 파이프라인이 실행된다")
    void fullPipelineRunsForCompositeQuestion() {
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("아직 배송이 시작되지 않아 지금 취소하실 수 있으며, 사용하신 쿠폰도 복구됩니다.");

        AgentResponse response = gateway.handle(
                new ChatRequest("conv-1", "CUST-1", QUESTION));

        // 1) 의도 분류: 복합 의도 4개 감지
        assertEquals(4, response.getIntents().size());
        assertEquals("REFUND", response.getPrimaryIntent());

        // 2) 라우팅
        assertEquals("CustomerSupportWorkflow", response.getWorkflow());

        // 3) Agent Loop: DB → 외부 API → DB → Vector DB 순서로 실행
        assertEquals(
                List.of(ToolNames.ORDER, ToolNames.SHIPPING, ToolNames.COUPON, ToolNames.POLICY_RAG),
                response.getExecutedTools());

        // 4) 검증 통과
        assertTrue(response.isValidationPassed());
        assertFalse(response.isReflectionTriggered());

        // 5) 답변
        assertTrue(response.getAnswer().contains("쿠폰"));

        // 6) 대화 이력 저장
        verify(memory).save("conv-1", QUESTION, response.getAnswer());
    }

    @Test
    @DisplayName("환각이 발생하면 Validator 가 잡고 Reflection 이 교정한 답변이 나간다")
    void hallucinationIsCaughtAndCorrected() {
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("고객님의 상품은 이미 배송이 완료되어 도착했습니다.")   // 환각
                .thenReturn("확인 결과 아직 배송이 시작되지 않았습니다. 취소하시면 쿠폰도 복구됩니다."); // 교정

        AgentResponse response = gateway.handle(new ChatRequest("conv-1", "CUST-1", QUESTION));

        assertTrue(response.isReflectionTriggered());
        assertTrue(response.isValidationPassed());
        assertFalse(response.getAnswer().contains("배송이 완료"));
        verify(llmClient, times(2)).complete(any(Prompt.class));
    }

    @Test
    @DisplayName("프롬프트에 실제 조회 데이터와 정책 문서가 근거로 주입된다")
    void promptIsGroundedInRealData() {
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("아직 배송이 시작되지 않았습니다.");

        gateway.handle(new ChatRequest("conv-1", "CUST-1", QUESTION));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(llmClient).complete(captor.capture());
        String promptText = captor.getValue().toText();

        // DB 에서 온 사실
        assertTrue(promptText.contains("ORD-1001"), "실제 주문번호가 근거로 들어가야 한다");
        assertTrue(promptText.contains("NOT_SHIPPED"), "실제 배송 상태가 근거로 들어가야 한다");
        // Vector DB 에서 온 규칙
        assertTrue(promptText.contains("쿠폰은 주문이 정상 취소되면 자동으로 복구"),
                "RAG 로 검색한 정책이 근거로 들어가야 한다");
        // 환각 방지 지시
        assertTrue(promptText.contains("추측"), "추측 금지 규칙이 있어야 한다");
    }

    @Test
    @DisplayName("외부 배송 API 가 죽어도 대화는 계속되고 상태를 단정하지 않는다")
    void survivesShippingApiOutage() {
        when(shippingApiClient.findByOrderId(anyString()))
                .thenThrow(new com.example.aiagent.infra.shipping.ShippingApiException("타임아웃", null));
        when(llmClient.complete(any(Prompt.class)))
                .thenReturn("현재 배송 정보를 확인하기 어렵습니다. 잠시 후 다시 확인해 주세요.");

        AgentResponse response = gateway.handle(new ChatRequest("conv-1", "CUST-1", QUESTION));

        // 외부 API 장애가 전체 요청을 실패시키면 안 된다.
        assertTrue(response.isValidationPassed());
        assertTrue(response.getExecutedTools().contains(ToolNames.SHIPPING));

        // 프롬프트에 '조회 실패'가 사실로 전달되어야 모델이 지어내지 않는다.
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(llmClient).complete(captor.capture());
        assertTrue(captor.getValue().toText().contains("조회에 실패"));
    }

    @Test
    @DisplayName("멀티턴: 이전 대화 맥락이 프롬프트에 주입된다")
    void multiTurnConversationIncludesHistory() {
        when(memory.load("conv-1")).thenReturn(List.of(
                ChatMessage.user("제 주문 배송 언제 오나요?"),
                ChatMessage.assistant("아직 출고 전입니다.")));
        when(llmClient.complete(any(Prompt.class))).thenReturn("네, 아직 배송 전이라 취소 가능합니다.");

        gateway.handle(new ChatRequest("conv-1", "CUST-1", "그럼 취소하면 쿠폰은요?"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(llmClient).complete(captor.capture());

        Prompt prompt = captor.getValue();
        assertEquals(2, prompt.getHistory().size());
        assertTrue(prompt.toText().contains("제 주문 배송 언제 오나요?"));
    }

    @Test
    @DisplayName("conversationId 가 없으면 새 대화 ID 를 발급한다")
    void issuesConversationIdWhenAbsent() {
        when(llmClient.complete(any(Prompt.class))).thenReturn("아직 배송이 시작되지 않았습니다.");

        AgentResponse response = gateway.handle(new ChatRequest(null, "CUST-1", QUESTION));

        assertTrue(response.getConversationId().startsWith("conv-"));
    }

    @Test
    @DisplayName("빈 질문은 거부한다")
    void rejectsBlankQuestion() {
        assertThrows(IllegalArgumentException.class,
                () -> gateway.handle(new ChatRequest("conv-1", "CUST-1", "  ")));
    }

    @Test
    @DisplayName("정책 문의는 DB/외부 API 없이 RAG 만 사용한다")
    void policyQuestionUsesRagOnly() {
        when(llmClient.completeStructured(anyString(), anyString(), any()))
                .thenReturn(new IntentClassification(
                        List.of(Intent.POLICY), Intent.POLICY, 0.9, "일반 정책 문의"));
        when(llmClient.complete(any(Prompt.class))).thenReturn("배송 시작 전까지 취소 가능합니다.");

        AgentResponse response = gateway.handle(
                new ChatRequest("conv-2", "CUST-1", "환불 정책이 어떻게 되나요?"));

        assertEquals("PolicyQnaWorkflow", response.getWorkflow());
        assertEquals(List.of(ToolNames.POLICY_RAG), response.getExecutedTools());

        // 특정 고객 데이터가 필요 없는 질문에 DB/외부 API 를 부르면 낭비다.
        verify(orderRepository, org.mockito.Mockito.never())
                .findFirstByCustomerIdOrderByOrderedAtDesc(anyString());
        verify(shippingApiClient, org.mockito.Mockito.never()).findByOrderId(anyString());
    }
}
