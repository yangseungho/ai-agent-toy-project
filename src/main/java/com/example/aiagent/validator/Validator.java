package com.example.aiagent.validator;

import com.example.aiagent.domain.ShippingStatus;
import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validator — LLM 답변이 <b>실제 데이터와 모순되는지</b> 검사하는 가드레일.
 *
 * <p>왜 필요한가? LLM 은 근거를 줘도 가끔 지어낸다. 특히 "배송이 언제 오나요"처럼
 * 사용자가 원하는 답이 뻔한 질문에서, 모델이 사용자를 만족시키려고
 * "곧 도착합니다" 같은 말을 만들어내기 쉽다. 그런 답변이 그대로 고객에게 나가면
 * 그건 오안내이고, 실제 CS 사고로 이어진다.</p>
 *
 * <p>그래서 결정론적인 규칙으로 마지막에 한 번 더 막는다. 여기서 LLM 을 또 쓰지 않는
 * 이유는 비용/지연도 있지만, <b>검증기 자체가 환각하면 안 되기</b> 때문이다.
 * 검증은 빠르고 예측 가능해야 한다.</p>
 *
 * <p>실전에서는 이 클래스에 PII 마스킹, 금칙어, 약속 금지("100% 환불 보장" 등),
 * 특정 문구 강제 같은 규칙이 계속 추가된다.</p>
 */
@Slf4j
@Component
public class Validator {

    /** 배송 출발 전(NOT_SHIPPED)인데 등장하면 모순인 표현들. */
    private static final String[] CLAIMS_SHIPPED = {
            "배송이 완료", "배송 완료", "도착했", "도착 완료", "출발했", "배송기사", "배송 중", "배송중", "수령하신"
    };

    /** 배송 완료(DELIVERED)인데 등장하면 모순인 표현들. */
    private static final String[] CLAIMS_NOT_ARRIVED = {
            "아직 배송이 시작되지", "출발 전", "아직 도착하지", "출고되지 않"
    };

    /** 조회 실패인데 단정적으로 상태를 말하면 모순인 표현들. */
    private static final String[] CLAIMS_DEFINITE_STATUS = {
            "배송이 완료", "도착했", "출발했", "배송 중", "배송중", "아직 배송이 시작되지"
    };

    /**
     * LLM 답변을 실제 Tool 데이터와 비교해 검증한다.
     */
    public ValidationResult validate(AgentContext context, String llmAnswer) {
        if (llmAnswer == null || llmAnswer.isBlank()) {
            return ValidationResult.fail("LLM 응답이 비어 있습니다.");
        }

        ValidationResult shippingCheck = validateShippingConsistency(context, llmAnswer);
        if (!shippingCheck.isValid()) {
            return shippingCheck;
        }

        return validateNoFabricatedOrderId(context, llmAnswer);
    }

    /**
     * 답변의 배송 관련 주장이 실제 배송 상태와 일치하는지 검사한다.
     */
    private ValidationResult validateShippingConsistency(AgentContext context, String answer) {
        ToolResult shippingResult = context.toolResult(ToolNames.SHIPPING);

        if (shippingResult == null) {
            // 배송을 조회하지 않은 흐름이면 검사 대상이 아니다.
            return ValidationResult.ok();
        }

        // 조회에 실패했는데 단정적으로 배송 상태를 말하면 그건 지어낸 것이다.
        if (!shippingResult.isSuccess()) {
            if (containsAny(answer, CLAIMS_DEFINITE_STATUS)) {
                return ValidationResult.fail(
                        "배송 정보 조회에 실패했는데도 답변이 배송 상태를 단정적으로 말하고 있습니다.");
            }
            return ValidationResult.ok();
        }

        // 조회는 됐지만 데이터가 없는 경우(출고 전이라 송장 없음)
        if (!shippingResult.hasData()) {
            if (containsAny(answer, CLAIMS_SHIPPED)) {
                return ValidationResult.fail(
                        "아직 배송 정보가 생성되지 않았는데(출고 전) 답변이 배송 출발/완료를 주장합니다.");
            }
            return ValidationResult.ok();
        }

        ShippingStatus status = parseStatus(shippingResult.get("status"));

        if (status == ShippingStatus.NOT_SHIPPED && containsAny(answer, CLAIMS_SHIPPED)) {
            return ValidationResult.fail(
                    "실제 배송 상태는 NOT_SHIPPED(출고 전)인데 답변이 배송 출발/완료를 주장합니다.");
        }

        if (status == ShippingStatus.DELIVERED && containsAny(answer, CLAIMS_NOT_ARRIVED)) {
            return ValidationResult.fail(
                    "실제 배송 상태는 DELIVERED(배송 완료)인데 답변이 미배송을 주장합니다.");
        }

        return ValidationResult.ok();
    }

    /**
     * 답변에 등장하는 주문번호가 실제 조회된 주문번호와 같은지 검사한다.
     *
     * <p>모델이 주문번호를 그럴듯하게 지어내는 경우를 막는다.</p>
     */
    private ValidationResult validateNoFabricatedOrderId(AgentContext context, String answer) {
        ToolResult orderResult = context.toolResult(ToolNames.ORDER);
        if (orderResult == null || !orderResult.isSuccess()) {
            return ValidationResult.ok();
        }

        String actualOrderId = orderResult.get("orderId");
        if (actualOrderId == null) {
            // 주문이 없는데 답변이 특정 주문번호를 언급하면 지어낸 것이다.
            if (answer.contains("ORD-")) {
                return ValidationResult.fail("조회된 주문이 없는데 답변이 주문번호를 언급합니다.");
            }
            return ValidationResult.ok();
        }

        // 답변이 'ORD-' 를 언급하는데 실제 주문번호가 아닌 다른 번호를 말하면 모순이다.
        int index = answer.indexOf("ORD-");
        while (index >= 0) {
            String mentioned = extractOrderIdToken(answer, index);
            if (!mentioned.equals(actualOrderId)) {
                return ValidationResult.fail(
                        "답변이 실제 주문번호(" + actualOrderId + ")가 아닌 " + mentioned + " 를 언급합니다.");
            }
            index = answer.indexOf("ORD-", index + 1);
        }

        return ValidationResult.ok();
    }

    /** 'ORD-' 부터 주문번호 토큰을 잘라낸다. */
    private String extractOrderIdToken(String text, int start) {
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isLetterOrDigit(c) || c == '-') {
                end++;
            } else {
                break;
            }
        }
        return text.substring(start, end);
    }

    private ShippingStatus parseStatus(String raw) {
        if (raw == null) {
            return ShippingStatus.UNKNOWN;
        }
        try {
            return ShippingStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return ShippingStatus.UNKNOWN;
        }
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
