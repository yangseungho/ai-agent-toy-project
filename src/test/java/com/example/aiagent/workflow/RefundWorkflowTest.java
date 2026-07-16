package com.example.aiagent.workflow;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.llm.FakeLLMClient;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.prompt.PromptBuilder;
import com.example.aiagent.reflection.ReflectionEngine;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.tool.coupon.CouponTool;
import com.example.aiagent.tool.order.OrderTool;
import com.example.aiagent.tool.rag.PolicyRagTool;
import com.example.aiagent.tool.shipping.ShippingTool;
import com.example.aiagent.validator.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RefundWorkflow 통합(로직) 테스트.
 *
 * <p>실제 컴포넌트들을 직접 조립하여 Agent Loop → Prompt → LLM → Validator → Reflection
 * 전체 흐름이 한 Workflow 안에서 동작하는지 검증한다.</p>
 */
class RefundWorkflowTest {

    private RefundWorkflow buildWorkflow() {
        List<Tool> tools = List.of(new OrderTool(), new ShippingTool(), new PolicyRagTool(), new CouponTool());
        ToolRegistry registry = new ToolRegistry(tools);
        Planner planner = new Planner();
        PromptBuilder promptBuilder = new PromptBuilder();
        FakeLLMClient llm = new FakeLLMClient();
        Validator validator = new Validator();
        ReflectionEngine reflection = new ReflectionEngine(llm);

        return new RefundWorkflow(planner, registry, promptBuilder, llm, validator, reflection);
    }

    @Test
    @DisplayName("REFUND Workflow 는 4개 Tool 을 순서대로 실행한다")
    void executesAllToolsInOrder() {
        AgentContext context = new AgentContext("취소하면 쿠폰 돌려받나요?", Intent.REFUND);

        AgentResponse response = buildWorkflow().execute(context);

        assertEquals(
                List.of(ToolNames.ORDER, ToolNames.SHIPPING, ToolNames.POLICY_RAG, ToolNames.COUPON),
                response.getExecutedTools());
    }

    @Test
    @DisplayName("최초 LLM 응답이 배송 상태와 모순되어 Reflection 이 발동하고, 재검증을 통과한다")
    void reflectionRecoversFromContradiction() {
        AgentContext context = new AgentContext("취소하면 쿠폰 돌려받나요?", Intent.REFUND);

        AgentResponse response = buildWorkflow().execute(context);

        // FakeLLM 최초 응답은 '배송 완료'를 주장 → Validator 실패 → Reflection 발동
        assertTrue(response.isReflectionTriggered());
        // 교정된 재응답은 배송 상태(NOT_SHIPPED)와 모순되지 않으므로 최종 검증 통과
        assertTrue(response.isValidationPassed());
        // 최종 답변에는 쿠폰 복구 안내가 담겨 있다
        assertTrue(response.getAnswer().contains("쿠폰"));
    }
}
