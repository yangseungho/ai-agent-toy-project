package com.example.aiagent.planner;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Planner 단위 테스트.
 */
class PlannerTest {

    private final Planner planner = new Planner();

    @Test
    @DisplayName("REFUND 초기 계획은 주문→배송→정책→쿠폰→FINISH 순서다")
    void planForRefund() {
        AgentContext context = new AgentContext("취소 문의", Intent.REFUND);

        ExecutionPlan plan = planner.plan(context);

        assertEquals(5, plan.getSteps().size());
        assertEquals(ToolNames.ORDER, plan.getSteps().get(0).getToolName());
        assertEquals(ToolNames.SHIPPING, plan.getSteps().get(1).getToolName());
        assertEquals(ToolNames.POLICY_RAG, plan.getSteps().get(2).getToolName());
        assertEquals(ToolNames.COUPON, plan.getSteps().get(3).getToolName());
        assertTrue(plan.getSteps().get(4).isFinish());
    }

    @Test
    @DisplayName("decideNextStep 은 아직 실행되지 않은 Tool 을 차례로 반환한다")
    void decideNextStepProgresses() {
        AgentContext context = new AgentContext("취소 문의", Intent.REFUND);

        // 처음에는 OrderTool
        PlanStep first = planner.decideNextStep(context);
        assertEquals(ToolNames.ORDER, first.getToolName());

        // OrderTool 실행되었다고 표시하면 → 다음은 ShippingTool
        context.addToolResult(new ToolResult(ToolNames.ORDER, "order", Map.of("status", "ORDERED")));
        assertEquals(ToolNames.SHIPPING, planner.decideNextStep(context).getToolName());
    }

    @Test
    @DisplayName("필요한 Tool 이 모두 실행되면 FINISH 를 반환한다")
    void decideFinishWhenAllDone() {
        AgentContext context = new AgentContext("취소 문의", Intent.REFUND);
        context.addToolResult(new ToolResult(ToolNames.ORDER, "s", Map.of()));
        context.addToolResult(new ToolResult(ToolNames.SHIPPING, "s", Map.of()));
        context.addToolResult(new ToolResult(ToolNames.POLICY_RAG, "s", Map.of()));
        context.addToolResult(new ToolResult(ToolNames.COUPON, "s", Map.of()));

        PlanStep step = planner.decideNextStep(context);

        assertTrue(step.isFinish());
    }

    @Test
    @DisplayName("SHIPPING 은 주문/배송 Tool 만 계획한다")
    void planForShipping() {
        AgentContext context = new AgentContext("배송 문의", Intent.SHIPPING);

        ExecutionPlan plan = planner.plan(context);

        // 주문, 배송, FINISH = 3개
        assertEquals(3, plan.getSteps().size());
        assertFalse(plan.describe().contains(ToolNames.COUPON));
    }
}
