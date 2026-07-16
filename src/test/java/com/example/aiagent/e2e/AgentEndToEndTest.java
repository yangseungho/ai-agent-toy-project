package com.example.aiagent.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-End 테스트 (가장 중요).
 *
 * <p>실제 Spring Boot 컨텍스트를 띄우고 REST API 를 호출하여 아래 전체 파이프라인이
 * 한 번의 요청으로 모두 실행되는지 검증한다.</p>
 *
 * <pre>
 * User → Gateway → Orchestrator → IntentClassifier → Router → Workflow
 *      → Planner Loop → Tool 들 → PromptBuilder → FakeLLM → Validator → Reflection → Response
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("대표 질문 하나로 Agent 파이프라인 전체가 실행되어 응답을 반환한다")
    void fullPipelineRunsEndToEnd() throws Exception {
        String requestBody = """
                {
                  "question": "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn();

        // MockMvc 응답을 UTF-8 로 읽어야 한글(쿠폰 등)이 깨지지 않는다.
        String json = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(json);

        // 1) 의도 분류: REFUND
        assertEquals("REFUND", node.get("intent").asText());
        // 2) 라우팅: RefundWorkflow
        assertEquals("RefundWorkflow", node.get("workflow").asText());

        // 3) Agent Loop: 4개 Tool 이 모두 실행됨
        JsonNode executedTools = node.get("executedTools");
        assertEquals(4, executedTools.size());

        // 4) Validation 실패 → Reflection 발동
        assertTrue(node.get("reflectionTriggered").asBoolean());
        // 5) Reflection 이후 재검증 통과
        assertTrue(node.get("validationPassed").asBoolean());

        // 6) 최종 답변이 비어있지 않고 쿠폰 안내를 포함
        String answer = node.get("answer").asText();
        assertTrue(answer.contains("쿠폰"));

        // 7) 교육용 추적 로그(trace)가 남아 파이프라인 단계를 확인할 수 있다
        assertTrue(node.get("trace").size() > 0);
    }

    @Test
    @DisplayName("빈 질문은 400 을 반환한다")
    void blankQuestionReturns400() throws Exception {
        String requestBody = """
                { "question": "" }
                """;

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
