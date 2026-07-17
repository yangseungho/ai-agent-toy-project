package com.example.aiagent.gateway;

import com.example.aiagent.dto.AgentResponse;
import com.example.aiagent.dto.ChatRequest;
import com.example.aiagent.orchestrator.AiOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AI Gateway — AI 시스템의 단일 진입점.
 *
 * <p>아키텍처 규칙상 Gateway 는 오직 {@link AiOrchestrator} 만 호출한다.</p>
 *
 * <p>Gateway 를 따로 두는 이유는 "AI 로직"과 "요청 처리 관심사"를 분리하기 위해서다.
 * 실전에서는 여기에 인증/인가, 요청 검증, 레이트 리밋, 감사 로그, 과금 집계,
 * PII 마스킹 같은 횡단 관심사가 붙는다. 그런 것들이 Orchestrator 안에 섞이면
 * Agent 로직을 읽을 수 없게 된다.</p>
 */
@Slf4j
@Component
public class AiGateway {

    private static final String DEFAULT_CUSTOMER_ID = "CUST-1";

    private final AiOrchestrator orchestrator;

    public AiGateway(AiOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 요청을 검증/보정한 뒤 Orchestrator 로 전달한다.
     */
    public AgentResponse handle(ChatRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("question 은 비어 있을 수 없습니다.");
        }

        // conversationId 가 없으면 새 대화로 간주하고 발급한다.
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? "conv-" + UUID.randomUUID()
                : request.conversationId();

        // customerId 는 데모 편의를 위해 기본값을 준다.
        // 실전에서는 절대 이렇게 하면 안 된다 — 인증 주체(JWT subject)에서 꺼내야 하며,
        // 클라이언트가 보낸 customerId 를 신뢰하면 남의 주문을 조회할 수 있다.
        String customerId = (request.customerId() == null || request.customerId().isBlank())
                ? DEFAULT_CUSTOMER_ID
                : request.customerId();

        log.info("[Gateway] 요청 수신 conversationId={} customerId={}", conversationId, customerId);

        return orchestrator.process(conversationId, customerId, request.question());
    }
}
