package com.example.aiagent.planner;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Planner 단위 테스트 — 복합 의도 합집합과 결과 기반 적응이 핵심.
 */
class PlannerTest {

    private final Planner planner = new Planner();

    private AgentContext contextWith(List<Intent> intents, Intent primary) {
        return new AgentContext(
                "conv-1", "CUST-1", "질문",
                new IntentClassification(intents, primary, 0.9, "테스트"),
                List.of());
    }

    @Test
    @DisplayName("복합 의도의 필요 Tool 을 합집합으로 모은다")
    void unionsToolsAcrossIntents() {
        // REFUND → Order, Shipping, Rag / COUPON → Order, Coupon, Rag
        List<String> tools = planner.requiredTools(List.of(Intent.REFUND, Intent.COUPON));

        assertEquals(
                List.of(ToolNames.ORDER, ToolNames.SHIPPING, ToolNames.COUPON, ToolNames.POLICY_RAG),
                tools);
    }

    @Test
    @DisplayName("중복 의도가 있어도 Tool 은 한 번만 포함된다")
    void deduplicatesTools() {
        List<String> tools = planner.requiredTools(
                List.of(Intent.ORDER_STATUS, Intent.SHIPPING, Intent.REFUND));

        assertEquals(tools.size(), tools.stream().distinct().count(), "중복이 없어야 한다");
        assertEquals(ToolNames.ORDER, tools.get(0), "OrderTool 이 항상 먼저여야 한다");
    }

    @Test
    @DisplayName("POLICY 단독 의도는 RAG 만 사용하고 DB/외부API 를 건드리지 않는다")
    void policyOnlyUsesRag() {
        List<String> tools = planner.requiredTools(List.of(Intent.POLICY));

        assertEquals(List.of(ToolNames.POLICY_RAG), tools);
    }

    @Test
    @DisplayName("UNKNOWN 은 Tool 을 호출하지 않는다")
    void unknownRequiresNoTools() {
        assertTrue(planner.requiredTools(List.of(Intent.UNKNOWN)).isEmpty());
    }

    @Test
    @DisplayName("decideNextStep 은 아직 실행하지 않은 Tool 을 순서대로 반환한다")
    void decidesNextToolInOrder() {
        AgentContext context = contextWith(List.of(Intent.REFUND), Intent.REFUND);

        assertEquals(ToolNames.ORDER, planner.decideNextStep(context).getToolName());

        // OrderTool 이 주문을 찾아냈다고 가정
        context.addToolResult(ToolResult.success(
                ToolNames.ORDER, "주문 있음", Map.of("orderId", "ORD-1001", "status", "ORDERED")));

        assertEquals(ToolNames.SHIPPING, planner.decideNextStep(context).getToolName());
    }

    @Test
    @DisplayName("주문을 찾지 못하면 주문에 종속된 Tool(배송/쿠폰)을 건너뛴다")
    void skipsOrderDependentToolsWhenNoOrderFound() {
        AgentContext context = contextWith(List.of(Intent.REFUND, Intent.COUPON), Intent.REFUND);

        // OrderTool 은 실행했지만 주문 내역이 없음 (orderId 없음)
        context.addToolResult(ToolResult.empty(ToolNames.ORDER, "주문 내역 없음"));

        // 배송/쿠폰은 orderId 가 필요하므로 건너뛰고 정책 검색으로 넘어가야 한다.
        PlanStep next = planner.decideNextStep(context);
        assertEquals(ToolNames.POLICY_RAG, next.getToolName());
    }

    @Test
    @DisplayName("필요한 Tool 을 모두 실행하면 FINISH 를 반환한다")
    void finishesWhenAllToolsDone() {
        AgentContext context = contextWith(List.of(Intent.ORDER_STATUS), Intent.ORDER_STATUS);
        context.addToolResult(ToolResult.success(ToolNames.ORDER, "주문", Map.of("orderId", "ORD-1001")));

        PlanStep step = planner.decideNextStep(context);

        assertTrue(step.isFinish());
        assertFalse(step.getReason().isBlank());
    }
}
