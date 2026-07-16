package com.example.aiagent.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * REST 응답 Body.
 *
 * <p>내부 실행 결과인 {@link AgentResponse} 를 그대로 노출하여
 * 학습자가 파이프라인의 결과를 HTTP 응답으로 바로 확인할 수 있게 한다.</p>
 */
@Getter
@RequiredArgsConstructor
public class ChatResponse {

    private final String answer;
    private final String intent;
    private final String workflow;
    private final List<String> executedTools;
    private final boolean validationPassed;
    private final boolean reflectionTriggered;
    private final List<String> trace;

    /** 내부 AgentResponse 를 REST 응답으로 변환한다. */
    public static ChatResponse from(AgentResponse response) {
        return new ChatResponse(
                response.getAnswer(),
                response.getIntent(),
                response.getWorkflow(),
                response.getExecutedTools(),
                response.isValidationPassed(),
                response.isReflectionTriggered(),
                response.getTrace()
        );
    }
}
