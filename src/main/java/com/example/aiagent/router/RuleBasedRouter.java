package com.example.aiagent.router;

import com.example.aiagent.intent.Intent;
import com.example.aiagent.workflow.CustomerSupportWorkflow;
import com.example.aiagent.workflow.PolicyQnaWorkflow;
import com.example.aiagent.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rule 기반 Router — Intent 에 따라 Workflow 를 선택한다.
 *
 * <p>의도 분류는 LLM 이 하지만, <b>라우팅은 규칙</b>이다. 어떤 코드 경로를 탈지는
 * 예측 가능하고 테스트 가능해야 하며, 모델이 매번 다르게 정하면 안 되기 때문이다.
 * (실전에서 "LLM 이 분류 → 코드가 라우팅"은 매우 일반적인 조합이다.)</p>
 *
 * <p><b>복합 의도 처리</b>: 여러 의도가 감지되어도 Workflow 는 하나만 고른다.
 * 기준은 {@code primaryIntent}. 나머지 의도는 버려지지 않고 Planner 가 Tool 을
 * 합집합으로 모을 때 사용된다. 즉, 라우팅은 primary 로, 정보 수집은 전체 의도로 한다.</p>
 */
@Slf4j
@Component
public class RuleBasedRouter {

    private final Map<Intent, Workflow> routingTable = new EnumMap<>(Intent.class);
    private final Workflow fallbackWorkflow;

    public RuleBasedRouter(CustomerSupportWorkflow customerSupportWorkflow,
                           PolicyQnaWorkflow policyQnaWorkflow) {

        // 주문/배송/환불/쿠폰 문의 → 고객지원 Workflow (Tool 로 실제 데이터를 조회)
        routingTable.put(Intent.ORDER_STATUS, customerSupportWorkflow);
        routingTable.put(Intent.SHIPPING, customerSupportWorkflow);
        routingTable.put(Intent.REFUND, customerSupportWorkflow);
        routingTable.put(Intent.COUPON, customerSupportWorkflow);

        // 정책 자체 문의 → RAG 중심 Workflow (문서 검색만)
        routingTable.put(Intent.POLICY, policyQnaWorkflow);

        // ACCOUNT / UNKNOWN 은 전용 Workflow 가 없으므로 fallback 으로 처리한다.
        this.fallbackWorkflow = policyQnaWorkflow;
    }

    /**
     * primaryIntent 에 해당하는 Workflow 를 반환한다.
     */
    public Workflow route(Intent primaryIntent) {
        Workflow workflow = routingTable.get(primaryIntent);

        if (workflow == null) {
            log.debug("[Router] {} 전용 Workflow 없음 → fallback({}) 사용", primaryIntent, fallbackWorkflow.name());
            return fallbackWorkflow;
        }
        return workflow;
    }

    /** 라우팅 테이블에 등록된 의도들 (디버깅용). */
    public List<Intent> supportedIntents() {
        return List.copyOf(routingTable.keySet());
    }
}
