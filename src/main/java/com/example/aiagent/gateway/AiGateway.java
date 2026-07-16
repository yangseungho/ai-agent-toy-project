package com.example.aiagent.gateway;

import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.orchestrator.AiOrchestrator;
import org.springframework.stereotype.Component;

/**
 * AI Gateway.
 *
 * <p>AI 시스템으로 들어가는 <b>단일 진입점</b>이다. 아키텍처 규칙상 Gateway 는
 * 오직 {@link AiOrchestrator} 만 호출한다. (인증/로깅/요청 검증 같은 횡단 관심사를
 * 넣기에 적합한 자리이지만, 교육용이므로 여기서는 최소한만 둔다.)</p>
 */
@Component
public class AiGateway {

    private final AiOrchestrator orchestrator;

    public AiGateway(AiOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** 질문을 받아 Orchestrator 로 전달하고 결과를 반환한다. */
    public AgentResponse handle(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question 은 비어 있을 수 없습니다.");
        }
        return orchestrator.process(question);
    }
}
