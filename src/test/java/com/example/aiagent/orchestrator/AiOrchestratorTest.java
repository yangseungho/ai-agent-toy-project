package com.example.aiagent.orchestrator;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.intent.IntentClassifier;
import com.example.aiagent.memory.ChatMessage;
import com.example.aiagent.memory.ConversationMemory;
import com.example.aiagent.router.RuleBasedRouter;
import com.example.aiagent.workflow.CustomerSupportWorkflow;
import com.example.aiagent.workflow.PolicyQnaWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiOrchestrator 단위 테스트 — 공통 절차(Memory 로드/저장, 분류, 라우팅)를 검증한다.
 *
 * <p>Workflow 는 여기서 관심사가 아니므로 mock 으로 대체한다.</p>
 */
class AiOrchestratorTest {

    private final ConversationMemory memory = mock(ConversationMemory.class);
    private final IntentClassifier classifier = mock(IntentClassifier.class);
    private final CustomerSupportWorkflow supportWorkflow = mock(CustomerSupportWorkflow.class);
    private final PolicyQnaWorkflow policyWorkflow = mock(PolicyQnaWorkflow.class);

    private AiOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        RuleBasedRouter router = new RuleBasedRouter(supportWorkflow, policyWorkflow);
        orchestrator = new AiOrchestrator(memory, classifier, router);

        when(supportWorkflow.name()).thenReturn("CustomerSupportWorkflow");
        when(policyWorkflow.name()).thenReturn("PolicyQnaWorkflow");
        when(memory.load(anyString())).thenReturn(List.of());
    }

    private AgentResponse stubResponse(String answer) {
        return AgentResponse.builder()
                .answer(answer)
                .conversationId("conv-1")
                .intents(List.of("REFUND"))
                .primaryIntent("REFUND")
                .executedTools(List.of())
                .trace(List.of())
                .build();
    }

    @Test
    @DisplayName("REFUND 문의는 CustomerSupportWorkflow 로 라우팅된다")
    void routesRefundToSupportWorkflow() {
        when(classifier.classify(anyString(), anyList())).thenReturn(new IntentClassification(
                List.of(Intent.REFUND, Intent.COUPON), Intent.REFUND, 0.95, "취소 문의"));
        when(supportWorkflow.execute(any(AgentContext.class))).thenReturn(stubResponse("답변"));

        orchestrator.process("conv-1", "CUST-1", "취소하면 쿠폰 돌려받나요?");

        verify(supportWorkflow).execute(any(AgentContext.class));
        verify(policyWorkflow, org.mockito.Mockito.never()).execute(any());
    }

    @Test
    @DisplayName("POLICY 문의는 PolicyQnaWorkflow 로 라우팅된다")
    void routesPolicyToPolicyWorkflow() {
        when(classifier.classify(anyString(), anyList())).thenReturn(new IntentClassification(
                List.of(Intent.POLICY), Intent.POLICY, 0.9, "정책 문의"));
        when(policyWorkflow.execute(any(AgentContext.class))).thenReturn(stubResponse("정책 답변"));

        orchestrator.process("conv-1", "CUST-1", "환불 정책이 어떻게 되나요?");

        verify(policyWorkflow).execute(any(AgentContext.class));
    }

    @Test
    @DisplayName("전용 Workflow 가 없는 의도(UNKNOWN)는 fallback 으로 라우팅된다")
    void routesUnknownToFallback() {
        when(classifier.classify(anyString(), anyList())).thenReturn(
                IntentClassification.unknown("분류 실패"));
        when(policyWorkflow.execute(any(AgentContext.class))).thenReturn(stubResponse("죄송합니다"));

        orchestrator.process("conv-1", "CUST-1", "안녕하세요");

        verify(policyWorkflow).execute(any(AgentContext.class));
    }

    @Test
    @DisplayName("이전 대화 이력을 로드해 분류기와 컨텍스트에 전달한다")
    void loadsAndPassesHistory() {
        List<ChatMessage> history = List.of(
                ChatMessage.user("배송 언제 오나요?"),
                ChatMessage.assistant("아직 출고 전입니다."));
        when(memory.load("conv-1")).thenReturn(history);

        when(classifier.classify(anyString(), anyList())).thenReturn(new IntentClassification(
                List.of(Intent.REFUND), Intent.REFUND, 0.9, "맥락상 취소"));
        when(supportWorkflow.execute(any(AgentContext.class))).thenReturn(stubResponse("취소 안내"));

        orchestrator.process("conv-1", "CUST-1", "그럼 취소해주세요");

        // 분류기에 이력이 전달되어야 후속 질문을 이해할 수 있다.
        verify(classifier).classify(eq("그럼 취소해주세요"), eq(history));

        // 컨텍스트에도 이력이 실려야 프롬프트에 들어간다.
        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(supportWorkflow).execute(captor.capture());
        assertEquals(2, captor.getValue().getHistory().size());
    }

    @Test
    @DisplayName("이번 턴의 질문과 답변을 Memory 에 저장한다")
    void savesTurnToMemory() {
        when(classifier.classify(anyString(), anyList())).thenReturn(new IntentClassification(
                List.of(Intent.REFUND), Intent.REFUND, 0.9, "취소"));
        when(supportWorkflow.execute(any(AgentContext.class))).thenReturn(stubResponse("취소 가능합니다"));

        orchestrator.process("conv-1", "CUST-1", "취소되나요?");

        verify(memory).save("conv-1", "취소되나요?", "취소 가능합니다");
    }

    @Test
    @DisplayName("복합 의도 정보가 컨텍스트에 그대로 전달된다")
    void passesCompositeIntentsToWorkflow() {
        when(classifier.classify(anyString(), anyList())).thenReturn(new IntentClassification(
                List.of(Intent.ORDER_STATUS, Intent.SHIPPING, Intent.REFUND, Intent.COUPON),
                Intent.REFUND, 0.95, "복합"));
        when(supportWorkflow.execute(any(AgentContext.class))).thenReturn(stubResponse("답변"));

        orchestrator.process("conv-1", "CUST-1", "안왔는데 취소하면 쿠폰 돌려받나요?");

        ArgumentCaptor<AgentContext> captor = ArgumentCaptor.forClass(AgentContext.class);
        verify(supportWorkflow).execute(captor.capture());

        AgentContext context = captor.getValue();
        assertEquals(4, context.intents().size());
        assertEquals(Intent.REFUND, context.primaryIntent());
        assertTrue(context.getIntentClassification().isComposite());
    }
}
