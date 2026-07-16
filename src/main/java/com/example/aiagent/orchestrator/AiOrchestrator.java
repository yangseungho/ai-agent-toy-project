package com.example.aiagent.orchestrator;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.intent.IntentClassifier;
import com.example.aiagent.router.RuleBasedRouter;
import com.example.aiagent.workflow.Workflow;
import org.springframework.stereotype.Component;

/**
 * AI Orchestrator.
 *
 * <p>전체 흐름의 중심. Gateway 는 오직 이 Orchestrator 만 호출하며, 나머지 모든 단계는
 * 여기서 조율된다.</p>
 *
 * <pre>
 * 질문 → IntentClassifier(의도 분류) → RuleBasedRouter(Workflow 선택) → Workflow.execute
 * </pre>
 */
@Component
public class AiOrchestrator {

    private final IntentClassifier intentClassifier;
    private final RuleBasedRouter router;

    public AiOrchestrator(IntentClassifier intentClassifier, RuleBasedRouter router) {
        this.intentClassifier = intentClassifier;
        this.router = router;
    }

    /**
     * 사용자 질문을 받아 Agent 파이프라인 전체를 실행한다.
     *
     * @param question 사용자 질문
     * @return 파이프라인 실행 결과
     */
    public AgentResponse process(String question) {
        // 1) Intent 분류 (Rule 기반)
        Intent intent = intentClassifier.classify(question);

        // 2) 컨텍스트 생성 (파이프라인 전체를 흐르는 작업 메모리)
        AgentContext context = new AgentContext(question, intent);
        context.log("[Orchestrator] 질문 수신: " + question);
        context.log("[IntentClassifier] 분류 결과: " + intent);

        // 3) Router 로 Workflow 선택
        Workflow workflow = router.route(intent);
        context.log("[Router] 선택된 Workflow: " + workflow.name());

        // 4) Workflow 실행 (내부에서 Planner Loop ~ Reflection 까지 수행)
        return workflow.execute(context);
    }
}
