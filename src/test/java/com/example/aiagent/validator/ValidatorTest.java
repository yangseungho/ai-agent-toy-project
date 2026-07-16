package com.example.aiagent.validator;

import com.example.aiagent.dto.AgentContext;
import com.example.aiagent.intent.Intent;
import com.example.aiagent.tool.ToolNames;
import com.example.aiagent.tool.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validator 단위 테스트.
 */
class ValidatorTest {

    private final Validator validator = new Validator();

    private AgentContext contextWithShipping(String status) {
        AgentContext context = new AgentContext("취소 문의", Intent.REFUND);
        context.addToolResult(new ToolResult(ToolNames.SHIPPING, "배송", Map.of("status", status)));
        return context;
    }

    @Test
    @DisplayName("NOT_SHIPPED 인데 '배송 완료'를 주장하면 검증 실패")
    void failWhenClaimsShippedButNotShipped() {
        AgentContext context = contextWithShipping("NOT_SHIPPED");

        ValidationResult result = validator.validate(context, "고객님의 상품은 이미 배송이 완료되어 도착했습니다.");

        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("NOT_SHIPPED 이고 '아직 출발 전'이라고 하면 검증 통과")
    void passWhenConsistent() {
        AgentContext context = contextWithShipping("NOT_SHIPPED");

        ValidationResult result = validator.validate(context, "상품은 아직 배송이 시작되지 않았습니다.");

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("배송 정보를 조회하지 않은 흐름은 배송 모순 검사를 생략한다")
    void passWhenNoShippingInfo() {
        AgentContext context = new AgentContext("문의", Intent.UNKNOWN);

        ValidationResult result = validator.validate(context, "무엇이든 답변");

        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("빈 응답은 검증 실패")
    void failWhenEmptyAnswer() {
        AgentContext context = contextWithShipping("NOT_SHIPPED");

        assertFalse(validator.validate(context, "").isValid());
    }
}
