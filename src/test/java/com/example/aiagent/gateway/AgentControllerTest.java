package com.example.aiagent.gateway;

import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.dto.ChatRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 계층 테스트 — HTTP 계약(JSON 스펙, 상태코드)만 검증한다.
 *
 * <p>{@code @WebMvcTest} 는 웹 계층만 로드하므로 DB / Claude / pgvector 연결이 필요 없다.
 * AI 로직은 {@link AiGateway} 를 mocking 하여 분리한다.</p>
 */
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiGateway gateway;

    @Test
    @DisplayName("정상 요청은 200 과 파이프라인 추적 정보를 반환한다")
    void returnsAnswerAndTrace() throws Exception {
        when(gateway.handle(any(ChatRequest.class))).thenReturn(AgentResponse.builder()
                .answer("아직 배송이 시작되지 않아 취소 가능하며 쿠폰도 복구됩니다.")
                .conversationId("conv-1")
                .intents(List.of("ORDER_STATUS", "SHIPPING", "REFUND", "COUPON"))
                .primaryIntent("REFUND")
                .intentConfidence(0.95)
                .intentReasoning("복합 질의")
                .workflow("CustomerSupportWorkflow")
                .executedTools(List.of("OrderTool", "ShippingTool", "CouponTool", "PolicyRagTool"))
                .validationPassed(true)
                .reflectionTriggered(false)
                .trace(List.of("[Planner] ...", "[Validator] 통과"))
                .build());

        String body = """
                {
                  "conversationId": "conv-1",
                  "customerId": "CUST-1",
                  "question": "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryIntent").value("REFUND"))
                .andExpect(jsonPath("$.intents.length()").value(4))
                .andExpect(jsonPath("$.workflow").value("CustomerSupportWorkflow"))
                .andExpect(jsonPath("$.executedTools.length()").value(4))
                .andExpect(jsonPath("$.validationPassed").value(true))
                .andReturn();

        // MockMvc 응답은 UTF-8 로 읽어야 한글이 깨지지 않는다.
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(json.contains("쿠폰"));
    }

    @Test
    @DisplayName("question 이 비어 있으면 400 을 반환한다")
    void rejectsBlankQuestion() throws Exception {
        String body = """
                { "conversationId": "conv-1", "customerId": "CUST-1", "question": "" }
                """;

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
    }
}
