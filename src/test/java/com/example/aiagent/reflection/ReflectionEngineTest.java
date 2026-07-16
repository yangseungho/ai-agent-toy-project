package com.example.aiagent.reflection;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.llm.LLMClient;
import com.example.aiagent.prompt.Prompt;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReflectionEngine 단위 테스트. (Mockito 로 LLMClient 를 가짜로 만든다)
 */
class ReflectionEngineTest {

    @Test
    @DisplayName("Reflection 은 교정 지시를 담은 Prompt 로 LLM 을 정확히 1회 재호출한다")
    void reflectAndRetryCallsLlmOnceWithReflectionNote() {
        LLMClient llmClient = mock(LLMClient.class);
        when(llmClient.complete(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn("교정된 답변");

        ReflectionEngine engine = new ReflectionEngine(llmClient);

        AgentContext context = new AgentContext("취소 문의", Intent.REFUND);
        context.addToolResult(new ToolResult(ToolNames.SHIPPING, "배송", Map.of("status", "NOT_SHIPPED")));

        Prompt original = new Prompt("system", "질문", "context", null);

        String result = engine.reflectAndRetry(context, original, "배송 상태 모순");

        assertEquals("교정된 답변", result);

        // LLM 이 정확히 1회 호출되었고, 그 Prompt 에 Reflection 지시가 들어있어야 한다
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(llmClient, times(1)).complete(captor.capture());

        Prompt used = captor.getValue();
        assertTrue(used.hasReflectionNote());
        assertTrue(used.getReflectionNote().contains("NOT_SHIPPED"));
    }
}
