package com.example.aiagent.planner;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.tool.ToolNames;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Planner.
 *
 * <p>Planner 는 두 가지 역할을 한다.</p>
 * <ol>
 *     <li>{@link #plan(AgentContext)} : 질문/Intent 를 분석해 어떤 Tool 들을
 *         호출할지 초기 실행 계획({@link ExecutionPlan})을 세운다.</li>
 *     <li>{@link #decideNextStep(AgentContext)} : Agent Loop 안에서 매 반복마다
 *         "지금까지 모인 결과"를 보고 다음에 무엇을 할지 결정한다. 아직 실행하지 않은
 *         필요한 Tool 이 있으면 그 Tool 을, 모두 끝났으면 FINISH 를 반환한다.</li>
 * </ol>
 *
 * <p>요구사항대로 Planner 는 한 번만 호출되지 않고, Tool 결과를 받은 뒤 반복적으로
 * 다음 행동을 결정한다.</p>
 */
@Component
public class Planner {

    /**
     * Intent 에 따라 이 Agent 가 실행해야 할 Tool 목록(순서 포함)을 정의한다.
     * 교육용이므로 Rule 로 단순하게 매핑한다.
     */
    private List<String> requiredTools(Intent intent) {
        List<String> tools = new ArrayList<>();
        switch (intent) {
            case REFUND:
                // "취소하면 쿠폰 돌려받나?" → 주문 → 배송 → 정책(RAG) → 쿠폰 순으로 확인
                tools.add(ToolNames.ORDER);
                tools.add(ToolNames.SHIPPING);
                tools.add(ToolNames.POLICY_RAG);
                tools.add(ToolNames.COUPON);
                break;
            case SHIPPING:
                // 배송 문의 → 주문 → 배송 확인
                tools.add(ToolNames.ORDER);
                tools.add(ToolNames.SHIPPING);
                break;
            case ACCOUNT:
            case UNKNOWN:
            default:
                // 별도 Tool 없이 바로 답변 생성
                break;
        }
        return tools;
    }

    /** 초기 실행 계획을 세운다. */
    public ExecutionPlan plan(AgentContext context) {
        ExecutionPlan executionPlan = new ExecutionPlan();
        for (String toolName : requiredTools(context.getIntent())) {
            executionPlan.addStep(PlanStep.callTool(toolName));
        }
        // 마지막은 항상 답변 생성(FINISH)
        executionPlan.addStep(PlanStep.finish());
        return executionPlan;
    }

    /**
     * Agent Loop 에서 호출된다. 지금까지의 결과를 보고 다음 단계를 결정한다.
     *
     * <p>아직 실행되지 않은 '필요한 Tool' 중 가장 앞의 것을 반환하고,
     * 모두 실행되었다면 FINISH 를 반환한다.</p>
     */
    public PlanStep decideNextStep(AgentContext context) {
        for (String toolName : requiredTools(context.getIntent())) {
            if (!context.hasToolResult(toolName)) {
                return PlanStep.callTool(toolName);
            }
        }
        return PlanStep.finish();
    }
}
