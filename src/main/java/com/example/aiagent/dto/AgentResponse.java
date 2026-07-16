package com.example.aiagent.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Agent 파이프라인 실행 결과.
 *
 * <p>단순히 최종 답변만 담지 않는다. 교육용 프로젝트이므로 어떤 흐름을 거쳤는지
 * (Intent, 실행한 Tool 목록, Validation 통과 여부, Reflection 발생 여부, 단계별 로그)를
 * 함께 담아 파이프라인 전체를 눈으로 확인할 수 있게 한다.</p>
 */
@Getter
@Builder
@ToString
public class AgentResponse {

    /** 사용자에게 전달되는 최종 답변 */
    private final String answer;

    /** 분류된 Intent 이름 (예: REFUND) */
    private final String intent;

    /** 선택된 Workflow 이름 (예: RefundWorkflow) */
    private final String workflow;

    /** Agent Loop 에서 실제로 실행된 Tool 이름 순서 */
    private final List<String> executedTools;

    /** Validator 통과 여부 */
    private final boolean validationPassed;

    /** Reflection(재시도)이 발생했는지 여부 */
    private final boolean reflectionTriggered;

    /** 파이프라인 단계별 로그 (교육용 추적 정보) */
    private final List<String> trace;
}
