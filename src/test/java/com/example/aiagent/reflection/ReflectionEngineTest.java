package com.example.aiagent.reflection;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.llm.LlmClient;
import com.example.aiagent.prompt.Prompt;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReflectionEngine 단위 테스트 — LLM 은 Mockito 로 mocking 한다.
 */
class ReflectionEngineTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final ReflectionEngine engine = new ReflectionEngine(llmClient);

    private AgentContext context() {
        return new AgentContext("conv-1", "CUST-1", "취소하면 쿠폰 돌려받나요?",
                new IntentClassification(List.of(Intent.REFUND), Intent.REFUND, 0.9, "취소 문의"),
                List.of());
    }

    @Test
    @DisplayName("교정 지시에 실제 배송 상태와 주문번호를 명시해 LLM 을 1회 재호출한다")
    void retriesOnceWithConcreteFacts() {
        when(llmClient.complete(any(Prompt.class))).thenReturn("교정된 답변");

        AgentContext context = context();
        context.addToolResult(ToolResult.success(ToolNames.ORDER, "주문",
                Map.of("orderId", "ORD-1001")));
        context.addToolResult(ToolResult.success(ToolNames.SHIPPING, "배송",
                Map.of("status", "NOT_SHIPPED")));

        Prompt original = new Prompt("system", List.of(), "질문", null);

        String answer = engine.reflectAndRetry(context, original, "배송 상태 모순");

        assertEquals("교정된 답변", answer);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(llmClient, times(1)).complete(captor.capture());

        Prompt corrected = captor.getValue();
        assertTrue(corrected.hasReflectionNote());

        String note = corrected.getReflectionNote();
        // 막연한 "다시 답하세요"가 아니라 구체적 사실을 못 박아야 교정된다.
        assertTrue(note.contains("NOT_SHIPPED"), "실제 배송 상태를 명시해야 한다");
        assertTrue(note.contains("ORD-1001"), "실제 주문번호를 명시해야 한다");
        assertTrue(note.contains("배송 상태 모순"), "검증 실패 사유를 포함해야 한다");
    }

    @Test
    @DisplayName("배송 조회가 실패했으면 '단정하지 말라'고 지시한다")
    void instructsUncertaintyWhenLookupFailed() {
        when(llmClient.complete(any(Prompt.class))).thenReturn("교정된 답변");

        AgentContext context = context();
        context.addToolResult(ToolResult.failure(ToolNames.SHIPPING, "타임아웃"));

        engine.reflectAndRetry(context, new Prompt("system", List.of(), "질문", null), "단정적 주장");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(llmClient).complete(captor.capture());

        assertTrue(captor.getValue().getReflectionNote().contains("조회 실패"));
    }

    @Test
    @DisplayName("교정 지시는 원본 Prompt 를 변경하지 않는다 (불변)")
    void doesNotMutateOriginalPrompt() {
        when(llmClient.complete(any(Prompt.class))).thenReturn("교정된 답변");

        Prompt original = new Prompt("system", List.of(), "질문", null);
        engine.reflectAndRetry(context(), original, "사유");

        assertTrue(!original.hasReflectionNote(), "원본은 그대로여야 한다");
    }
}
