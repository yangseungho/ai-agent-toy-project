package com.example.aiagent.validator;

import com.example.aiagent.domain.ShippingStatus;
import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.springframework.stereotype.Component;

/**
 * Validator.
 *
 * <p>LLM 응답이 실제 데이터(특히 배송 상태)와 <b>모순</b>되는지 검사한다.</p>
 *
 * <p>대표 예시: 실제 배송 상태가 {@code NOT_SHIPPED} 인데 LLM 이 "배송기사가 출발했습니다",
 * "배송이 완료되었습니다" 처럼 말하면 → <b>모순 → Validation 실패</b>.</p>
 */
@Component
public class Validator {

    /** 아직 출발하지 않았는데(NOT_SHIPPED) 등장하면 모순인 표현들. */
    private static final String[] SHIPPED_CLAIM_PHRASES = {
            "배송이 완료", "배송 완료", "도착했", "출발했", "배송기사가 출발", "배송 중"
    };

    /** 배송이 완료(DELIVERED)됐는데 등장하면 모순인 표현들. */
    private static final String[] NOT_ARRIVED_CLAIM_PHRASES = {
            "아직 배송이 시작되지", "출발 전", "아직 도착하지"
    };

    /**
     * LLM 응답을 컨텍스트(실제 상태)와 비교해 검증한다.
     *
     * @param context   Tool 결과가 담긴 컨텍스트
     * @param llmAnswer LLM 이 생성한 답변
     * @return 검증 결과
     */
    public ValidationResult validate(AgentContext context, String llmAnswer) {
        if (llmAnswer == null || llmAnswer.isBlank()) {
            return ValidationResult.fail("LLM 응답이 비어 있습니다.");
        }

        ToolResult shippingResult = context.getToolResult(ToolNames.SHIPPING);
        if (shippingResult == null) {
            // 배송 정보를 조회하지 않은 흐름이면 배송 관련 모순 검사는 생략한다.
            return ValidationResult.ok();
        }

        String statusValue = shippingResult.get("status");
        ShippingStatus status = ShippingStatus.valueOf(statusValue);

        // 실제로 아직 출발 전인데 "출발/도착/완료"를 주장하면 모순
        if (status == ShippingStatus.NOT_SHIPPED && containsAny(llmAnswer, SHIPPED_CLAIM_PHRASES)) {
            return ValidationResult.fail(
                    "실제 배송 상태는 NOT_SHIPPED 인데 LLM 응답이 배송 출발/완료를 주장합니다.");
        }

        // 실제로 배송 완료인데 "아직 출발 전/도착 안 함"을 주장하면 모순
        if (status == ShippingStatus.DELIVERED && containsAny(llmAnswer, NOT_ARRIVED_CLAIM_PHRASES)) {
            return ValidationResult.fail(
                    "실제 배송 상태는 DELIVERED 인데 LLM 응답이 미배송을 주장합니다.");
        }

        return ValidationResult.ok();
    }

    private boolean containsAny(String text, String[] phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
