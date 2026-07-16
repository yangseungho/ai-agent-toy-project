package com.example.aiagent.workflow;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.intent.Intent;

/**
 * Workflow 인터페이스.
 *
 * <p>Router 가 Intent 에 따라 하나의 Workflow 를 선택해 실행한다.
 * 각 Workflow 는 자신이 처리하는 Intent 를 선언한다.</p>
 */
public interface Workflow {

    /** 이 Workflow 의 이름 (예: RefundWorkflow) */
    String name();

    /** 이 Workflow 가 담당하는 Intent */
    Intent supportedIntent();

    /** 컨텍스트를 받아 Agent 파이프라인을 실행하고 결과를 반환한다. */
    AgentResponse execute(AgentContext context);
}
