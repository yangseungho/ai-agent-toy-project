package com.example.aiagent.workflow;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.dto.AgentResponse;

/**
 * Workflow — Router 가 선택하는 처리 전략.
 *
 * <p>같은 Agent 라도 문의 종류에 따라 다른 일을 해야 한다. 주문 문의는 DB/외부 API 를
 * 조회해야 하고, 정책 문의는 문서 검색만 하면 된다. 이를 분리하면 각 경로를
 * 독립적으로 테스트하고 튜닝할 수 있다.</p>
 */
public interface Workflow {

    /** Workflow 이름 (로그/응답 추적용) */
    String name();

    /** 파이프라인을 실행하고 결과를 반환한다. */
    AgentResponse execute(AgentContext context);
}
