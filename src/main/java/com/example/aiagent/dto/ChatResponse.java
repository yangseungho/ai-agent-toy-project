package com.example.aiagent.dto;

import java.util.List;

/**
 * REST 응답 Body.
 *
 * <p>최종 답변과 함께 파이프라인 추적 정보를 내려준다. 실전에서는 trace 를
 * 로그/APM 으로만 보내고 고객 응답에는 answer 만 남기는 것이 보통이다.
 * 여기서는 학습을 위해 그대로 노출한다.</p>
 */
public record ChatResponse(
        String answer,
        String conversationId,
        List<String> intents,
        String primaryIntent,
        double intentConfidence,
        String intentReasoning,
        String workflow,
        List<String> executedTools,
        boolean validationPassed,
        boolean reflectionTriggered,
        List<String> trace
) {

    public static ChatResponse from(AgentResponse response) {
        return new ChatResponse(
                response.getAnswer(),
                response.getConversationId(),
                response.getIntents(),
                response.getPrimaryIntent(),
                response.getIntentConfidence(),
                response.getIntentReasoning(),
                response.getWorkflow(),
                response.getExecutedTools(),
                response.isValidationPassed(),
                response.isReflectionTriggered(),
                response.getTrace()
        );
    }
}
