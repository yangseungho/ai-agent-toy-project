package com.example.aiagent.planner;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Planner — 어떤 Tool 을 어떤 순서로 호출할지 결정한다.
 *
 * <p>핵심 두 가지.</p>
 *
 * <p><b>1) 복합 의도 처리</b><br>
 * 감지된 <b>모든</b> 의도에 필요한 Tool 을 합집합(union)으로 모은다.
 * "취소하면 쿠폰 돌려받나요?" → intents=[ORDER_STATUS, SHIPPING, REFUND, COUPON]
 * → 주문/배송/쿠폰/정책을 모두 확인해야 정확히 답할 수 있다.</p>
 *
 * <p><b>2) 결과에 따른 적응</b><br>
 * Planner 는 한 번만 호출되지 않는다. Tool 결과를 받은 뒤 다시 호출되어
 * 다음 행동을 정한다({@link #decideNextStep}). 예를 들어 OrderTool 이 주문을
 * 찾지 못했다면 orderId 가 없으므로 ShippingTool/CouponTool 은 호출해봐야
 * 의미가 없다 → 건너뛰고 종료한다.</p>
 */
@Component
public class Planner {

    /**
     * Tool 호출 순서(고정).
     *
     * <p>OrderTool 이 반드시 먼저다. ShippingTool/CouponTool 은 OrderTool 이 찾아낸
     * orderId 가 있어야 조회할 수 있기 때문이다 — Tool 간 데이터 의존성이 존재한다.</p>
     */
    private static final List<String> TOOL_ORDER = List.of(
            ToolNames.ORDER,
            ToolNames.SHIPPING,
            ToolNames.COUPON,
            ToolNames.POLICY_RAG
    );

    /**
     * 의도별로 필요한 Tool 을 합집합으로 계산한다.
     */
    public List<String> requiredTools(List<Intent> intents) {
        Set<String> required = new LinkedHashSet<>();

        if (intents != null) {
            for (Intent intent : intents) {
                required.addAll(toolsFor(intent));
            }
        }

        // 정해진 실행 순서대로 정렬해 반환한다.
        List<String> ordered = new ArrayList<>();
        for (String toolName : TOOL_ORDER) {
            if (required.contains(toolName)) {
                ordered.add(toolName);
            }
        }
        return ordered;
    }

    /** 의도 하나가 필요로 하는 Tool 들. */
    private List<String> toolsFor(Intent intent) {
        return switch (intent) {
            // 주문 확인
            case ORDER_STATUS -> List.of(ToolNames.ORDER);

            // 배송 문의 → 주문을 찾아야 운송장을 조회할 수 있다
            case SHIPPING -> List.of(ToolNames.ORDER, ToolNames.SHIPPING);

            // 환불/취소 → 취소 가능 여부는 '배송 상태'와 '환불 정책'에 달려 있다
            case REFUND -> List.of(ToolNames.ORDER, ToolNames.SHIPPING, ToolNames.POLICY_RAG);

            // 쿠폰 → 어떤 주문의 쿠폰인지 알아야 하고, 복구 여부는 정책 문서에 있다
            case COUPON -> List.of(ToolNames.ORDER, ToolNames.COUPON, ToolNames.POLICY_RAG);

            // 정책 자체 문의 → 문서 검색(RAG)만 하면 된다
            case POLICY -> List.of(ToolNames.POLICY_RAG);

            // 별도 조회 없이 바로 답변
            case ACCOUNT, UNKNOWN -> List.of();
        };
    }

    /** 초기 실행 계획(청사진)을 세운다. */
    public ExecutionPlan plan(AgentContext context) {
        ExecutionPlan executionPlan = new ExecutionPlan();

        for (String toolName : requiredTools(context.intents())) {
            executionPlan.addStep(PlanStep.callTool(toolName, "의도 " + context.intents() + " 처리에 필요"));
        }
        executionPlan.addStep(PlanStep.finish("수집한 근거로 답변 생성"));
        return executionPlan;
    }

    /**
     * Agent Loop 가 매 반복마다 호출한다. 지금까지 모인 결과를 보고 다음 행동을 결정한다.
     */
    public PlanStep decideNextStep(AgentContext context) {
        for (String toolName : requiredTools(context.intents())) {

            if (context.hasToolResult(toolName)) {
                continue; // 이미 실행함
            }

            // 결과 기반 적응: 주문을 못 찾았으면 주문에 종속된 Tool 은 건너뛴다.
            if (requiresOrderId(toolName) && !hasResolvedOrderId(context)) {
                continue;
            }

            return PlanStep.callTool(toolName, "아직 확인하지 않은 정보");
        }

        return PlanStep.finish("필요한 정보를 모두 확인함");
    }

    /** 이 Tool 이 orderId 에 의존하는가? */
    private boolean requiresOrderId(String toolName) {
        return ToolNames.SHIPPING.equals(toolName) || ToolNames.COUPON.equals(toolName);
    }

    /** OrderTool 이 실제로 주문을 찾아냈는가? */
    private boolean hasResolvedOrderId(AgentContext context) {
        ToolResult orderResult = context.toolResult(ToolNames.ORDER);
        return orderResult != null
                && orderResult.isSuccess()
                && orderResult.get("orderId") != null;
    }
}
