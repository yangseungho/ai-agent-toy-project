package com.example.aiagent.planner;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.tool.Tool;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Planner — 어떤 Tool 을 어떤 순서로, 무엇을 병렬로 호출할지 결정한다.
 *
 * <p><b>1) 복합 의도 처리</b><br>
 * 감지된 모든 의도에 필요한 Tool 을 합집합(union)으로 모은다.
 * "취소하면 쿠폰 돌려받나요?" → intents=[ORDER_STATUS, SHIPPING, REFUND, COUPON]
 * → 주문/배송/쿠폰/정책을 모두 확인해야 정확히 답할 수 있다.
 * 이 계산은 <b>턴당 정확히 한 번</b>만 수행되어 {@link ExecutionPlan} 에 담긴다.</p>
 *
 * <p><b>2) 의존성 기반 병렬 실행</b><br>
 * 실행 순서는 더 이상 Planner 안의 고정 목록이 아니라, 각 Tool 이 선언한
 * {@link Tool#requiredInputs()} 에서 <b>도출</b>된다. 지금 당장 입력이 갖춰진 Tool 을
 * 한 묶음(wave)으로 모아 동시에 실행하므로, 서로 무관한 조회가 직렬로 줄 서지 않는다.</p>
 *
 * <pre>
 *   wave 1 : OrderTool ∥ PolicyRagTool     (아무 입력도 필요 없음)
 *   wave 2 : ShippingTool ∥ CouponTool     (둘 다 orderId 만 필요 → 서로 독립)
 * </pre>
 * <p>순차 실행이면 외부 I/O 4번이 줄줄이 더해지지만, 이렇게 하면 2번의 대기로 끝난다.</p>
 *
 * <p><b>3) 결과에 따른 적응</b><br>
 * wave 가 끝날 때마다 다시 판단한다. 어떤 Tool 도 실행 준비가 되지 않았고 진행할
 * 방법도 없다면, 그 Tool 들을 계획에서 덜어낸다({@link ExecutionPlan#drop}).
 * 예) 주문을 못 찾았으면 orderId 가 영원히 생기지 않으므로 배송/쿠폰 조회는 포기한다.</p>
 */
@Component
public class Planner {

    private final ToolRegistry toolRegistry;

    public Planner(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 의도별로 필요한 Tool 을 합집합으로 계산한다.
     *
     * <p>실행 순서는 여기서 정하지 않는다 — 순서는 Tool 의 의존성 선언에서 나온다.</p>
     */
    public List<String> requiredTools(List<Intent> intents) {
        Set<String> required = new LinkedHashSet<>();

        if (intents != null) {
            for (Intent intent : intents) {
                required.addAll(toolsFor(intent));
            }
        }
        return new ArrayList<>(required);
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

    /**
     * 이번 턴의 계획을 세운다. 턴당 한 번만 호출된다.
     */
    public ExecutionPlan plan(AgentContext context) {
        return new ExecutionPlan(requiredTools(context.intents()));
    }

    /**
     * 지금 <b>동시에</b> 실행할 수 있는 Tool 묶음을 결정한다.
     *
     * <p>Agent Loop 가 wave 마다 호출한다. 반환이 비어 있으면 더 할 일이 없다는 뜻이다.</p>
     */
    public List<PlanStep> nextBatch(AgentContext context) {
        ExecutionPlan plan = context.getPlan();

        List<PlanStep> ready = new ArrayList<>();
        List<String> blocked = new ArrayList<>();
        Set<String> available = context.availableInputs();

        for (String toolName : plan.pending()) {
            Optional<Tool> found = toolRegistry.find(toolName);

            if (found.isEmpty()) {
                // MCP 서버가 죽어 원격 Tool 이 등록되지 않은 경우가 여기 걸린다.
                // 대화를 실패시키지 않고, 그 정보 없이 답변을 시도한다.
                plan.drop(toolName, "Tool 미등록 (MCP 서버 미연결 가능성)");
                continue;
            }

            Set<String> missing = missingInputs(found.get(), available);
            if (missing.isEmpty()) {
                ready.add(PlanStep.callTool(toolName, "입력 조건 충족"));
            } else {
                blocked.add(toolName);
            }
        }

        if (!ready.isEmpty()) {
            return ready;
        }

        // 실행 가능한 Tool 이 하나도 없는데 대기 중인 Tool 이 남아 있다.
        // 이번 wave 가 끝난 시점이므로 새 입력이 생길 여지가 없다 → 영구 차단이다.
        for (String toolName : blocked) {
            Set<String> missing = toolRegistry.find(toolName)
                    .map(tool -> missingInputs(tool, available))
                    .orElseGet(Set::of);
            plan.drop(toolName, "선행 정보 미확보: " + missing);
        }
        return List.of();
    }

    /** 이 Tool 이 요구하는 입력 중 아직 컨텍스트에 없는 것. */
    private Set<String> missingInputs(Tool tool, Set<String> available) {
        Set<String> missing = new LinkedHashSet<>(tool.requiredInputs());
        missing.removeAll(available);
        return missing;
    }
}
