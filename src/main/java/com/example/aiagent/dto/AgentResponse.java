package com.example.aiagent.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Agent 파이프라인 실행 결과.
 *
 * <p>최종 답변뿐 아니라 어떤 경로로 그 답이 나왔는지(의도, 워크플로우, 호출한 Tool,
 * 검증/교정 여부, 단계별 로그)를 함께 담는다.</p>
 *
 * <p>이건 교육용 장식이 아니다. 실전 Agent 는 "왜 이렇게 답했는가"를 추적할 수 없으면
 * 디버깅도 개선도 불가능하다. 관측 가능성(observability)은 Agent 의 필수 요소다.</p>
 */
@Getter
@Builder
public class AgentResponse {

    /** 사용자에게 전달할 최종 답변 */
    private final String answer;

    /** 대화 세션 ID */
    private final String conversationId;

    /** 감지된 모든 의도 (복합 질의면 여러 개) */
    private final List<String> intents;

    /** 핵심 의도 */
    private final String primaryIntent;

    /** 의도 분류 신뢰도 */
    private final double intentConfidence;

    /** 의도 분류 근거 */
    private final String intentReasoning;

    /** 선택된 Workflow */
    private final String workflow;

    /** 실제 실행된 Tool 순서 */
    private final List<String> executedTools;

    /** 최종 검증 통과 여부 */
    private final boolean validationPassed;

    /** Reflection(재생성) 발생 여부 */
    private final boolean reflectionTriggered;

    /** 단계별 추적 로그 */
    private final List<String> trace;
}
