package com.example.aiagent.planner;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassification;
import com.example.aiagent.tool.CouponTool;
import com.example.aiagent.tool.OrderTool;
import com.example.aiagent.tool.PolicyRagTool;
import com.example.aiagent.tool.ShippingTool;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolRegistry;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Planner 단위 테스트.
 *
 * <p>핵심은 세 가지다 — 복합 의도 합집합, 의존성 기반 병렬 묶음(wave), 결과 기반 계획 수정.</p>
 *
 * <p>Tool 은 의존성 선언({@code requiredInputs})만 보므로 실제 인프라가 필요 없다.
 * 생성자 인자는 mock 으로 채우고 호출하지 않는다.</p>
 */
class PlannerTest {

    private final List<Tool> tools = List.of(
            new OrderTool(mock(com.example.aiagent.infra.persistence.OrderRepository.class)),
            new ShippingTool(mock(com.example.aiagent.infra.shipping.ShippingApiClient.class)),
            new CouponTool(mock(com.example.aiagent.infra.persistence.CouponRepository.class)),
            new PolicyRagTool(mock(com.example.aiagent.rag.PolicyRetriever.class)));

    private final Planner planner = new Planner(new ToolRegistry(tools));

    private AgentContext contextWith(List<Intent> intents, Intent primary) {
        AgentContext context = new AgentContext(
                "conv-1", "CUST-1", "질문",
                new IntentClassification(intents, primary, 0.9, "테스트"),
                List.of());
        context.setPlan(planner.plan(context));
        return context;
    }

    /** wave 하나에 담긴 Tool 이름. */
    private List<String> toolNames(List<PlanStep> batch) {
        return batch.stream().map(PlanStep::getToolName).toList();
    }

    @Test
    @DisplayName("복합 의도의 필요 Tool 을 합집합으로 모은다")
    void unionsToolsAcrossIntents() {
        // REFUND → Order, Shipping, Rag / COUPON → Order, Coupon, Rag
        List<String> required = planner.requiredTools(List.of(Intent.REFUND, Intent.COUPON));

        assertEquals(4, required.size());
        assertTrue(required.containsAll(List.of(
                ToolNames.ORDER, ToolNames.SHIPPING, ToolNames.COUPON, ToolNames.POLICY_RAG)));
    }

    @Test
    @DisplayName("중복 의도가 있어도 Tool 은 한 번만 포함된다")
    void deduplicatesTools() {
        List<String> required = planner.requiredTools(
                List.of(Intent.ORDER_STATUS, Intent.SHIPPING, Intent.REFUND));

        assertEquals(required.size(), required.stream().distinct().count(), "중복이 없어야 한다");
    }

    @Test
    @DisplayName("POLICY 단독 의도는 RAG 만 사용하고 DB/외부API 를 건드리지 않는다")
    void policyOnlyUsesRag() {
        assertEquals(List.of(ToolNames.POLICY_RAG), planner.requiredTools(List.of(Intent.POLICY)));
    }

    @Test
    @DisplayName("UNKNOWN 은 Tool 을 호출하지 않는다")
    void unknownRequiresNoTools() {
        assertTrue(planner.requiredTools(List.of(Intent.UNKNOWN)).isEmpty());
    }

    @Test
    @DisplayName("입력이 필요 없는 Tool 들은 첫 wave 에 함께 묶여 병렬 실행된다")
    void groupsIndependentToolsIntoFirstWave() {
        AgentContext context = contextWith(List.of(Intent.REFUND), Intent.REFUND);

        // OrderTool 은 아무 입력도 필요 없고, PolicyRagTool 도 질문만 있으면 된다.
        // 둘은 서로를 기다릴 이유가 없다.
        List<String> firstWave = toolNames(planner.nextBatch(context));

        assertEquals(2, firstWave.size());
        assertTrue(firstWave.containsAll(List.of(ToolNames.ORDER, ToolNames.POLICY_RAG)));
        assertFalse(firstWave.contains(ToolNames.SHIPPING), "orderId 가 아직 없으므로 배송은 대기해야 한다");
    }

    @Test
    @DisplayName("orderId 가 생기면 그에 의존하는 Tool 들이 다음 wave 에 함께 묶인다")
    void groupsOrderDependentToolsIntoSecondWave() {
        AgentContext context = contextWith(
                List.of(Intent.REFUND, Intent.COUPON), Intent.REFUND);

        // wave 1 실행 완료로 가정
        completeWave(context, planner.nextBatch(context));
        context.addToolResult(ToolResult.success(
                ToolNames.ORDER, "주문 있음", Map.of("orderId", "ORD-1001", "status", "ORDERED")));
        context.getPlan().markCompleted(ToolNames.ORDER);

        List<String> secondWave = toolNames(planner.nextBatch(context));

        // 배송과 쿠폰은 둘 다 orderId 만 필요하다 → 서로 독립이므로 동시에 실행 가능하다.
        assertEquals(2, secondWave.size());
        assertTrue(secondWave.containsAll(List.of(ToolNames.SHIPPING, ToolNames.COUPON)));
    }

    @Test
    @DisplayName("주문을 찾지 못하면 주문에 종속된 Tool 을 계획에서 덜어낸다")
    void dropsOrderDependentToolsWhenNoOrderFound() {
        AgentContext context = contextWith(
                List.of(Intent.REFUND, Intent.COUPON), Intent.REFUND);

        // OrderTool / PolicyRagTool 은 실행했지만 주문 내역이 없음 (orderId 없음)
        context.addToolResult(ToolResult.empty(ToolNames.ORDER, "주문 내역 없음"));
        context.addToolResult(ToolResult.success(
                ToolNames.POLICY_RAG, "정책 문서", Map.of("retrievedCount", "1")));
        context.getPlan().markCompleted(ToolNames.ORDER);
        context.getPlan().markCompleted(ToolNames.POLICY_RAG);

        // orderId 는 앞으로도 생기지 않는다 → 배송/쿠폰은 실행해봐야 의미가 없다.
        assertTrue(planner.nextBatch(context).isEmpty());

        Map<String, String> dropped = context.getPlan().getDropped();
        assertTrue(dropped.containsKey(ToolNames.SHIPPING));
        assertTrue(dropped.containsKey(ToolNames.COUPON));
        // 왜 건너뛰었는지가 남아야 나중에 답변에 정보가 빠진 이유를 추적할 수 있다.
        assertTrue(dropped.get(ToolNames.SHIPPING).contains("orderId"));
    }

    @Test
    @DisplayName("등록되지 않은 Tool(MCP 서버 미연결 등)은 계획에서 제외하고 진행한다")
    void dropsUnregisteredTools() {
        // ShippingTool 이 없는 레지스트리 = MCP 배송 서버에 연결하지 못한 상황
        Planner planner = new Planner(new ToolRegistry(List.of(
                new OrderTool(mock(com.example.aiagent.infra.persistence.OrderRepository.class)))));

        AgentContext context = new AgentContext(
                "conv-1", "CUST-1", "배송 어디쯤인가요",
                new IntentClassification(List.of(Intent.SHIPPING), Intent.SHIPPING, 0.9, "테스트"),
                List.of());
        context.setPlan(planner.plan(context));

        assertEquals(List.of(ToolNames.ORDER), toolNames(planner.nextBatch(context)));

        context.addToolResult(ToolResult.success(
                ToolNames.ORDER, "주문", Map.of("orderId", "ORD-1001")));
        context.getPlan().markCompleted(ToolNames.ORDER);

        // 배송 Tool 이 없어도 예외로 죽지 않고 계획에서 빠진다.
        assertTrue(planner.nextBatch(context).isEmpty());
        assertTrue(context.getPlan().getDropped().containsKey(ToolNames.SHIPPING));
    }

    @Test
    @DisplayName("필요한 Tool 을 모두 실행하면 빈 배치를 반환한다")
    void returnsEmptyBatchWhenAllToolsDone() {
        AgentContext context = contextWith(List.of(Intent.ORDER_STATUS), Intent.ORDER_STATUS);

        context.addToolResult(ToolResult.success(
                ToolNames.ORDER, "주문", Map.of("orderId", "ORD-1001")));
        context.getPlan().markCompleted(ToolNames.ORDER);

        assertTrue(planner.nextBatch(context).isEmpty());
        assertTrue(context.getPlan().isComplete());
    }

    private void completeWave(AgentContext context, List<PlanStep> batch) {
        for (PlanStep step : batch) {
            if (!ToolNames.ORDER.equals(step.getToolName())) {
                context.addToolResult(ToolResult.empty(step.getToolName(), "결과 없음"));
            }
            context.getPlan().markCompleted(step.getToolName());
        }
    }
}
