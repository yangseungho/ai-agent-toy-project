package com.example.aiagent.router;

import com.example.aiagent.intent.Intent;
import com.example.aiagent.workflow.DefaultWorkflow;
import com.example.aiagent.workflow.Workflow;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rule 기반 Router.
 *
 * <p>Intent → Workflow 매핑을 담당한다. 예: {@code REFUND → RefundWorkflow}.</p>
 *
 * <p>Spring 이 등록한 모든 {@link Workflow} 빈을 주입받아, 각 Workflow 가 선언한
 * {@code supportedIntent()} 를 기준으로 매핑 테이블을 구성한다. 매핑에 없는 Intent 는
 * {@link DefaultWorkflow} 로 라우팅한다.</p>
 */
@Component
public class RuleBasedRouter {

    private final Map<Intent, Workflow> routingTable = new EnumMap<>(Intent.class);
    private final DefaultWorkflow defaultWorkflow;

    public RuleBasedRouter(List<Workflow> workflows, DefaultWorkflow defaultWorkflow) {
        this.defaultWorkflow = defaultWorkflow;
        for (Workflow workflow : workflows) {
            routingTable.put(workflow.supportedIntent(), workflow);
        }
    }

    /** Intent 에 해당하는 Workflow 를 반환한다. 없으면 기본 Workflow. */
    public Workflow route(Intent intent) {
        return routingTable.getOrDefault(intent, defaultWorkflow);
    }
}
