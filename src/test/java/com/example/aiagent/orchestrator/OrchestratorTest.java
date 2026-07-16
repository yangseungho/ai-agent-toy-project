package com.example.aiagent.orchestrator;

import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.intent.IntentClassifier;
import com.example.aiagent.llm.FakeLLMClient;
import com.example.aiagent.planner.Planner;
import com.example.aiagent.prompt.PromptBuilder;
import com.example.aiagent.reflection.ReflectionEngine;
import com.example.aiagent.router.RuleBasedRouter;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.tool.coupon.CouponTool;
import com.example.aiagent.tool.order.OrderTool;
import com.example.aiagent.tool.rag.PolicyRagTool;
import com.example.aiagent.tool.shipping.ShippingTool;
import com.example.aiagent.validator.Validator;
import com.example.aiagent.workflow.DefaultWorkflow;
import com.example.aiagent.workflow.RefundWorkflow;
import com.example.aiagent.workflow.ShippingWorkflow;
import com.example.aiagent.workflow.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiOrchestrator 통합(로직) 테스트.
 *
 * <p>Intent 분류 → 라우팅 → Workflow 실행까지 Orchestrator 가 올바르게 조율하는지 검증한다.</p>
 */
class OrchestratorTest {

    private AiOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        List<Tool> tools = List.of(new OrderTool(), new ShippingTool(), new PolicyRagTool(), new CouponTool());
        ToolRegistry registry = new ToolRegistry(tools);
        Planner planner = new Planner();
        PromptBuilder promptBuilder = new PromptBuilder();
        FakeLLMClient llm = new FakeLLMClient();
        Validator validator = new Validator();
        ReflectionEngine reflection = new ReflectionEngine(llm);

        RefundWorkflow refundWorkflow =
                new RefundWorkflow(planner, registry, promptBuilder, llm, validator, reflection);
        ShippingWorkflow shippingWorkflow =
                new ShippingWorkflow(planner, registry, promptBuilder, llm, validator, reflection);
        DefaultWorkflow defaultWorkflow =
                new DefaultWorkflow(planner, registry, promptBuilder, llm, validator, reflection);

        List<Workflow> workflows = List.of(refundWorkflow, shippingWorkflow, defaultWorkflow);
        RuleBasedRouter router = new RuleBasedRouter(workflows, defaultWorkflow);

        orchestrator = new AiOrchestrator(new IntentClassifier(), router);
    }

    @Test
    @DisplayName("환불 질문은 REFUND 로 분류되어 RefundWorkflow 로 처리된다")
    void processRefundQuestion() {
        AgentResponse response = orchestrator.process(
                "지난주 주문한 상품이 아직 안왔는데 취소하면 쿠폰도 돌려받을 수 있나요?");

        assertEquals("REFUND", response.getIntent());
        assertEquals("RefundWorkflow", response.getWorkflow());
        assertTrue(response.getExecutedTools().size() >= 4);
    }

    @Test
    @DisplayName("배송 질문은 SHIPPING 으로 분류되어 ShippingWorkflow 로 처리된다")
    void processShippingQuestion() {
        AgentResponse response = orchestrator.process("제 택배 배송 언제 오나요?");

        assertEquals("SHIPPING", response.getIntent());
        assertEquals("ShippingWorkflow", response.getWorkflow());
    }
}
